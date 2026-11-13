package com.hwmods.overpressure;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

import com.hwmods.overpressure.math.CubicBezier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;

/** Renders the decorative 2x2-pixel pneumatic hose between a connector and a held multitool. */
@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class PneumaticMultitoolCableRenderer {
    private static final int CONNECTOR_RADIUS = 8;
    private static final int SEGMENTS = 24;
    private static final double HALF_WIDTH = 1.0 / 16.0;
    private static final double PORT_OFFSET = 7.0 / 16.0 + 0.002;
    // The complete Create hose texture contains transparent model parts. This
    // opaque 2-pixel strip is the actual hose surface and keeps the curve solid.
    private static final float TEXTURE_U_MIN = 0.5F / 16.0F;
    private static final float TEXTURE_U_MAX = 2.5F / 16.0F;
    private static final float TEXTURE_V_MIN = 2.5F / 16.0F;
    private static final float TEXTURE_V_MAX = 15.5F / 16.0F;
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_EAST = new Vec3(1.0, 0.0, 0.0);
    private static final Direction[] CABLE_PORTS = {
            Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
    };
    private static final ResourceLocation HOSE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "create", "textures/block/hose.png");
    private static final RenderType HOSE_RENDER_TYPE = RenderType.entityCutoutNoCull(HOSE_TEXTURE);
    private static final Map<ItemStack, CapturedToolPort> CAPTURED_TOOL_PORTS = new IdentityHashMap<>();
    private static CapturedToolPort firstPersonToolPort;
    private static final long CAPTURE_LIFETIME_NANOS = 250_000_000L;
    // Center of the exposed 2x2 face of dark_port_socket in the item model.
    private static final Vector4f TOOL_SOCKET_MODEL_POINT = new Vector4f(
            14.96F / 16.0F, 6.525F / 16.0F, 9.1F / 16.0F, 1.0F);

    private PneumaticMultitoolCableRenderer() {
    }

    @SubscribeEvent
    static void renderCable(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(HOSE_RENDER_TYPE);
        boolean rendered = false;
        List<NetworkNode> network = networkNodes(minecraft, camera);
        if (!network.isEmpty()) {
            renderNetworkTree(network, camera, poseStack, buffer);
            rendered = true;
        }
        for (Player player : minecraft.level.players()) {
            ItemStack tool = PneumaticMultitoolItem.from(player.getMainHandItem()) != null
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (PneumaticMultitoolItem.from(tool) == null
                    || PneumaticMultitoolItem.selected(tool) == PneumaticMultitoolItem.Attachment.NONE) {
                continue;
            }
            ToolConnection toolConnection = toolConnection(player, partialTick);
            Vec3 toolPort = toolConnection.position;
            NetworkNode end = network.stream().min(Comparator.comparingDouble(p -> p.position.distanceToSqr(toolPort))).orElse(null);
            if (end != null && end.position.distanceToSqr(toolPort) <= CONNECTOR_RADIUS * CONNECTOR_RADIUS) {
                renderNetworkCable(end.anchorTowards(toolPort), toolPort, camera, poseStack, buffer);
                rendered = true;
            }
        }

        if (rendered) {
            buffers.endBatch(HOSE_RENDER_TYPE);
        }
    }

    private static List<NetworkNode> networkNodes(Minecraft minecraft, Vec3 camera) {
        List<NetworkNode> nodes = new ArrayList<>();
        for (OxygenPillarEntity pillar : minecraft.level.getEntitiesOfClass(OxygenPillarEntity.class,
                new net.minecraft.world.phys.AABB(camera, camera).inflate(96.0D)))
            nodes.add(new NetworkNode(pillar.position().add(0.0D, .94D, 0.0D), false));
        // A connector can only attach within eight blocks. Scan those small
        // volumes around relays rather than scanning a giant cube every frame.
        List<NetworkNode> relayNodes = List.copyOf(nodes);
        for (NetworkNode relay : relayNodes) {
            BlockPos origin = BlockPos.containing(relay.position);
            for (BlockPos p : BlockPos.betweenClosed(origin.offset(-CONNECTOR_RADIUS, -CONNECTOR_RADIUS, -CONNECTOR_RADIUS),
                    origin.offset(CONNECTOR_RADIUS, CONNECTOR_RADIUS, CONNECTOR_RADIUS))) {
                if (minecraft.level.getBlockState(p).is(ModBlocks.PNEUMATIC_CONNECTOR.get())
                        || minecraft.level.getBlockState(p).is(ModBlocks.OXYGEN_PILLAR.get()))
                    nodes.add(new NetworkNode(Vec3.atCenterOf(p), minecraft.level.getBlockState(p).is(ModBlocks.PNEUMATIC_CONNECTOR.get())));
            }
        }
        return nodes;
    }

    /** A Prim forest provides the shortest available links without visual loops. */
    private static void renderNetworkTree(List<NetworkNode> nodes, Vec3 camera, PoseStack poseStack, VertexConsumer buffer) {
        boolean[] joined = new boolean[nodes.size()];
        for (int root = 0; root < nodes.size(); root++) {
            if (joined[root]) continue;
            joined[root] = true;
            boolean added;
            do {
                added = false; int child = -1, parent = -1; double best = CONNECTOR_RADIUS * CONNECTOR_RADIUS;
                for (int i = 0; i < nodes.size(); i++) if (joined[i]) for (int j = 0; j < nodes.size(); j++) if (!joined[j]) {
                    double distance = nodes.get(i).position.distanceToSqr(nodes.get(j).position);
                    if (distance <= best) { best = distance; parent = i; child = j; }
                }
                if (child >= 0) {
                    NetworkNode a = nodes.get(parent), b = nodes.get(child);
                    renderNetworkCable(a.anchorTowards(b.position), b.anchorTowards(a.position), camera, poseStack, buffer);
                    joined[child] = true; added = true;
                }
            } while (added);
        }
    }

    private static void renderNetworkCable(Vec3 start, Vec3 end, Vec3 camera, PoseStack poseStack, VertexConsumer buffer) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 0.01) return;
        Vec3 normal = delta.normalize();
        CubicBezier curve = cableCurve(start, normal, end, normal.scale(-1));
        poseStack.pushPose();
        poseStack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
        renderCurve(curve.offset(start), start, poseStack, buffer);
        poseStack.popPose();
    }

    @SubscribeEvent
    static void clearCache(ClientPlayerNetworkEvent.LoggingOut event) {
        CAPTURED_TOOL_PORTS.clear();
        firstPersonToolPort = null;
    }

    /** Called from the item renderer after all hand and item model transforms are applied. */
    public static void captureToolPort(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack
    ) {
        boolean firstPerson = displayContext.firstPerson();
        boolean thirdPerson = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (PneumaticMultitoolItem.from(stack) == null || !firstPerson && !thirdPerson) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vector4f transformed = new Vector4f(TOOL_SOCKET_MODEL_POINT);
        poseStack.last().pose().transform(transformed);
        Vector4f transformedNormal = new Vector4f(1.0F, 0.0F, 0.0F, 0.0F);
        poseStack.last().pose().transform(transformedNormal);
        Vec3 outward = new Vec3(
                transformedNormal.x(), transformedNormal.y(), transformedNormal.z()).normalize();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        CapturedToolPort captured = new CapturedToolPort(
                camera.add(transformed.x(), transformed.y(), transformed.z()),
                outward,
                Util.getNanos());
        if (firstPerson) {
            firstPersonToolPort = captured;
        } else {
            CAPTURED_TOOL_PORTS.put(stack, captured);
        }
    }

    private static ToolConnection toolConnection(Player player, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            if (isFresh(firstPersonToolPort)) {
                return new ToolConnection(firstPersonToolPort.position, firstPersonToolPort.outward);
            }
        } else {
            CapturedToolPort captured = CAPTURED_TOOL_PORTS.get(player.getMainHandItem());
            if (isFresh(captured)) {
                return new ToolConnection(captured.position, captured.outward);
            }
        }
        Vec3 fallback = player.getRopeHoldPosition(partialTick);
        Vec3 outward = fallback.subtract(player.getEyePosition(partialTick));
        return new ToolConnection(fallback,
                outward.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : outward.normalize());
    }

    private static boolean isFresh(CapturedToolPort captured) {
        return captured != null && Util.getNanos() - captured.capturedAtNanos <= CAPTURE_LIFETIME_NANOS;
    }

    private static Direction nearestPortDirection(Vec3 blockCenter, Vec3 toolPort) {
        Vec3 towardTool = toolPort.subtract(blockCenter);
        Direction nearest = Direction.EAST;
        double bestDot = -Double.MAX_VALUE;
        for (Direction direction : CABLE_PORTS) {
            double dot = towardTool.dot(Vec3.atLowerCornerOf(direction.getNormal()));
            if (dot > bestDot) {
                bestDot = dot;
                nearest = direction;
            }
        }
        return nearest;
    }

    private static CubicBezier cableCurve(
            Vec3 start,
            Vec3 startNormal,
            Vec3 end,
            Vec3 endOutward
    ) {
        double distance = start.distanceTo(end);
        double lead = Math.min(0.8, Math.max(0.25, distance * 0.18));
        Vec3 p1 = start.add(startNormal.scale(lead)).add(0.0D, -Math.min(0.35D, distance * 0.05D), 0.0D);
        // The curve travels from the block to the tool, so its final tangent
        // must point opposite the socket's outward normal.
        Vec3 p2 = end.add(endOutward.scale(lead)).add(0.0D, -Math.min(0.35D, distance * 0.05D), 0.0D);
        return new CubicBezier(start, p1, p2, end);
    }

    private static void renderCurve(
            CubicBezier curve,
            Vec3 worldOrigin,
            PoseStack poseStack,
            VertexConsumer buffer
    ) {
        Vec3[][] sections = new Vec3[SEGMENTS + 1][4];
        Vec3[] centers = new Vec3[SEGMENTS + 1];
        Vec3 previousRight = null;
        Vec3 previousUp = null;

        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            Vec3 center = curve.pointAt(t);
            Vec3 tangent = curve.derivativeAt(t);
            if (tangent.lengthSqr() < 1.0E-8) {
                tangent = i == 0 ? curve.p3().subtract(curve.p0()) : centers[i - 1].vectorTo(center);
            }
            tangent = tangent.normalize();
            Vec3[] basis = sectionBasis(tangent, previousRight, previousUp);
            previousRight = basis[0];
            previousUp = basis[1];
            centers[i] = center;
            sections[i] = sectionCorners(center, previousRight, previousUp);
        }

        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 worldCenter = centers[i].add(worldOrigin);
            int light = minecraft.level == null ? 0xF000F0
                    : LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(worldCenter));
            float curveV0 = i / (float) SEGMENTS;
            float curveV1 = (i + 1) / (float) SEGMENTS;
            float v0 = TEXTURE_V_MIN + (TEXTURE_V_MAX - TEXTURE_V_MIN) * curveV0;
            float v1 = TEXTURE_V_MIN + (TEXTURE_V_MAX - TEXTURE_V_MIN) * curveV1;
            for (int side = 0; side < 4; side++) {
                int next = (side + 1) % 4;
                addQuad(buffer, poseStack,
                        sections[i][side], sections[i][next],
                        sections[i + 1][next], sections[i + 1][side],
                        TEXTURE_U_MIN, v0, TEXTURE_U_MAX, v1, light);
            }
        }
    }

    private static Vec3[] sectionBasis(Vec3 tangent, Vec3 previousRight, Vec3 previousUp) {
        Vec3 right;
        if (previousRight == null) {
            Vec3 reference = Math.abs(tangent.dot(WORLD_UP)) > 0.9 ? WORLD_EAST : WORLD_UP;
            right = tangent.cross(reference).normalize();
        } else {
            right = previousRight.subtract(tangent.scale(previousRight.dot(tangent)));
            if (right.lengthSqr() < 1.0E-8 && previousUp != null) {
                right = tangent.cross(previousUp);
            }
            right = right.lengthSqr() < 1.0E-8 ? tangent.cross(WORLD_EAST).normalize() : right.normalize();
        }
        Vec3 up = right.cross(tangent).normalize();
        return new Vec3[]{right, up};
    }

    private static Vec3[] sectionCorners(Vec3 center, Vec3 right, Vec3 up) {
        Vec3 horizontal = right.scale(HALF_WIDTH);
        Vec3 vertical = up.scale(HALF_WIDTH);
        return new Vec3[]{
                center.add(horizontal).add(vertical),
                center.subtract(horizontal).add(vertical),
                center.subtract(horizontal).subtract(vertical),
                center.add(horizontal).subtract(vertical)
        };
    }

    private static void addQuad(
            VertexConsumer buffer,
            PoseStack poseStack,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            float u0,
            float v0,
            float u1,
            float v1,
            int light
    ) {
        Vec3 normal = c.subtract(a).cross(b.subtract(a));
        normal = normal.lengthSqr() < 1.0E-8 ? WORLD_UP : normal.normalize();
        addVertex(buffer, poseStack, a, u0, v0, normal, light);
        addVertex(buffer, poseStack, d, u0, v1, normal, light);
        addVertex(buffer, poseStack, c, u1, v1, normal, light);
        addVertex(buffer, poseStack, b, u1, v0, normal, light);
    }

    private static void addVertex(
            VertexConsumer buffer,
            PoseStack poseStack,
            Vec3 point,
            float u,
            float v,
            Vec3 normal,
            int light
    ) {
        buffer.addVertex(poseStack.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record NetworkNode(Vec3 position, boolean connector) {
        Vec3 anchorTowards(Vec3 target) {
            if (!connector) return position;
            Vec3 horizontal = target.subtract(position).multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.lengthSqr() < 1.0E-8) return position;
            // Connect at the connector's outer tube rim, not at its centre.
            return position.add(horizontal.normalize().scale(.49D));
        }
    }

    private record CapturedToolPort(Vec3 position, Vec3 outward, long capturedAtNanos) {
    }

    private record ToolConnection(Vec3 position, Vec3 outward) {
    }

}
