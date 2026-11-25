package com.hwmods.overpressure;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

import com.hwmods.overpressure.math.CubicBezier;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the decorative 2x2-pixel pneumatic hose between a connector and a held multitool. */
@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class PneumaticMultitoolCableRenderer {
    private static final int CONNECTOR_RADIUS = 8;
    private static final int SEGMENTS = 24;
    private static final double HALF_WIDTH = 1.0 / 16.0;
    // The four socket faces sit at model coordinates 1 and 15.
    private static final double PORT_OFFSET = 7.0 / 16.0 - 0.001;
    private static final double PILLAR_PORT_OFFSET = 2.0 / 16.0 - 0.001;
    private static final double PILLAR_PORT_HEIGHT_FROM_CENTER = (15.25 - 8.0) / 16.0;
    // Repeat the dark, two-row ribbed section of Create's hose texture by arc length.
    private static final double HOSE_RIB_PERIOD = 2.0 / 16.0;
    private static final float TEXTURE_U_MIN = 13.5F / 16.0F;
    private static final float TEXTURE_U_MAX = 14.5F / 16.0F;
    private static final float TEXTURE_V_MIN = 0.5F / 16.0F;
    private static final float TEXTURE_V_MAX = 1.5F / 16.0F;
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_EAST = new Vec3(1.0, 0.0, 0.0);
    private static final Direction[] CABLE_PORTS = {
            Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
    };
    private static final ResourceLocation HOSE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "create", "textures/block/hose.png");
    private static final RenderType HOSE_RENDER_TYPE = RenderType.entityCutoutNoCull(HOSE_TEXTURE);
    private static final Map<ItemStack, CapturedToolPort> CAPTURED_TOOL_PORTS = new IdentityHashMap<>();
    private static final Map<Player, CapturedBacktankPorts> CAPTURED_BACKTANK_PORTS = new WeakHashMap<>();
    private static final Map<Player, CachedPillarSupply> DIVING_SUPPLIES = new WeakHashMap<>();
    private static final Map<ItemStack, FirstPersonToolPort> FIRST_PERSON_TOOL_PORTS = new WeakHashMap<>();
    private static final long CAPTURE_LIFETIME_NANOS = 250_000_000L;
    // Center of the exposed 2x2 face of dark_port_socket in the item model.
    private static final Vector4f TOOL_SOCKET_MODEL_POINT = new Vector4f(
            14.91F / 16.0F, 6.525F / 16.0F, 9.1F / 16.0F, 1.0F);

    private PneumaticMultitoolCableRenderer() {
    }

    @SubscribeEvent
    static void renderCable(RenderLevelStageEvent event) {
        // Draw the cutout hoses into the main depth buffer before water and
        // other translucent terrain blend over anything behind them.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
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
            boolean hasTool = PneumaticMultitoolItem.from(tool) != null
                    && PneumaticMultitoolItem.selected(tool) != PneumaticMultitoolItem.Attachment.NONE;
            ToolConnection toolPort = hasTool ? toolConnection(player, tool, partialTick, event) : null;
            if (BacktankItem.getWornBy(player) != null && (hasTool || OxygenPillarBreathing.canUsePillarAir(player))) {
                rendered |= renderBacktankHoses(player, toolPort, network, partialTick, camera, poseStack, buffer);
                continue;
            }
            if (toolPort == null) {
                continue;
            }
            NetworkNode end = network.stream().min(Comparator.comparingDouble(p -> p.position.distanceToSqr(toolPort.position()))).orElse(null);
            if (end != null && end.position.distanceToSqr(toolPort.position()) <= CONNECTOR_RADIUS * CONNECTOR_RADIUS) {
                renderNetworkCable(end.portTowards(toolPort.position()), toolPort, camera, poseStack, buffer);
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
            nodes.add(NetworkNode.forPillar(pillar));
        BlockPos min = BlockPos.containing(camera.add(-96, 0, -96));
        BlockPos max = BlockPos.containing(camera.add(96, 0, 96));
        for (int x = min.getX() >> 4; x <= (max.getX() >> 4); x++) {
            for (int z = min.getZ() >> 4; z <= (max.getZ() >> 4); z++) {
                if (minecraft.level.getChunkSource().getChunk(x, z,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                    for (var entry : chunk.getBlockEntities().entrySet()) {
                        if (entry.getValue() instanceof PneumaticConnectorBlockEntity connector && !connector.isRemoved()) {
                            nodes.add(new NetworkNode(Vec3.atCenterOf(entry.getKey()), null));
                        }
                    }
                }
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
                    renderNetworkCable(a.portTowards(b.position), b.portTowards(a.position), camera, poseStack, buffer);
                    joined[child] = true; added = true;
                }
            } while (added);
        }
    }

    private static void renderNetworkCable(ToolConnection from, ToolConnection to, Vec3 camera, PoseStack poseStack, VertexConsumer buffer) {
        Vec3 start = from.position;
        if (start.distanceToSqr(to.position) < 0.0001) return;
        CubicBezier curve = cableCurve(start, from.outward, to.position, to.outward);
        poseStack.pushPose();
        poseStack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
        renderCurve(curve.offset(start), start, poseStack, buffer);
        poseStack.popPose();
    }

    @SubscribeEvent
    static void clearCache(ClientPlayerNetworkEvent.LoggingOut event) {
        CAPTURED_TOOL_PORTS.clear();
        CAPTURED_BACKTANK_PORTS.clear();
        DIVING_SUPPLIES.clear();
        FIRST_PERSON_TOOL_PORTS.clear();
    }

    public static void captureBacktankPort(Player player, PoseStack pose) {
        // The bottom cap begins at Y=0 in both Create backtank models.
        var worldPose = pose.last().pose();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BacktankPorts ports = new BacktankPorts(
                transformTankPort(worldPose, camera, 0.5F, 0.02F / 16.0F, 0, -1),
                transformTankPort(worldPose, camera, 5.02F / 16.0F, 11.0F / 16.0F, -1, 0),
                transformTankPort(worldPose, camera, 10.98F / 16.0F, 11.0F / 16.0F, 1, 0));
        CAPTURED_BACKTANK_PORTS.put(player, new CapturedBacktankPorts(ports, Util.getNanos()));
    }

    private static ToolConnection transformTankPort(org.joml.Matrix4f pose, Vec3 camera,
            float x, float y, float normalX, float normalY) {
        Vector3f point = pose.transformPosition(new Vector3f(x, y, 0.5F));
        Vector3f normal = pose.transformDirection(new Vector3f(normalX, normalY, 0));
        return new ToolConnection(camera.add(point.x, point.y, point.z), new Vec3(normal.x, normal.y, normal.z).normalize());
    }

    private static boolean renderBacktankHoses(Player player, ToolConnection toolPort, List<NetworkNode> network,
            float partialTick, Vec3 camera,
            PoseStack poseStack, VertexConsumer buffer) {
        long tick = player.level().getGameTime();
        CachedPillarSupply cached = DIVING_SUPPLIES.get(player);
        if (cached == null || cached.tick() != tick) {
            cached = new CachedPillarSupply(tick, PneumaticAirNetwork.findPillarSupply(player.level(), player.position()));
            DIVING_SUPPLIES.put(player, cached);
        }
        var supply = cached.supply();
        NetworkNode source = null;
        if (supply != null && supply.entryPillar().isAlive() && supply.connector().getItemSpeed() > 0.0F) {
            source = NetworkNode.forPillar(supply.entryPillar());
        } else if (toolPort != null) {
            source = network.stream()
                    .filter(node -> node.mountFace() == null
                            && node.position().distanceToSqr(player.position()) <= CONNECTOR_RADIUS * CONNECTOR_RADIUS
                            && player.level().getBlockEntity(BlockPos.containing(node.position()))
                                    instanceof PneumaticConnectorBlockEntity connector && connector.getItemSpeed() > 0.0F)
                    .min(Comparator.comparingDouble(node -> node.position().distanceToSqr(player.position())))
                    .orElse(null);
        }
        if (source == null && (toolPort == null || !BacktankUtil.hasAirRemaining(player.getItemBySlot(BacktankItem.SLOT)))) {
            return false;
        }
        BacktankPorts tank = backtankConnections(player, partialTick);
        if (source != null) {
            renderNetworkCable(source.portTowards(tank.inlet().position()), tank.inlet(), camera, poseStack, buffer);
        }
        if (toolPort != null) {
            renderNetworkCable(tank.outletTowards(toolPort.position()), toolPort, camera, poseStack, buffer);
        }
        return true;
    }

    private static BacktankPorts backtankConnections(Player player, float partialTick) {
        CapturedBacktankPorts captured = CAPTURED_BACKTANK_PORTS.get(player);
        Minecraft minecraft = Minecraft.getInstance();
        if (!(player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) && captured != null
                && Util.getNanos() - captured.capturedAtNanos() <= CAPTURE_LIFETIME_NANOS) {
            return captured.ports();
        }
        // First-person rendering omits the worn armor layer.
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 side = forward.cross(WORLD_UP);
        Vec3 eye = player.getEyePosition(partialTick);
        ToolConnection collar = player.isVisuallySwimming()
                ? new ToolConnection(eye.subtract(player.getViewVector(partialTick).scale(0.48)).add(0, 0.36, 0),
                        player.getViewVector(partialTick))
                : new ToolConnection(eye.subtract(forward.scale(0.5)).subtract(0, 0.06, 0), WORLD_UP);
        Vec3 down = collar.outward().scale(-1);
        ToolConnection inlet = new ToolConnection(collar.position().add(down.scale(12.0 / 16.0)), down);
        Vec3 outletHeight = collar.position().add(down.scale(1.0 / 16.0));
        return new BacktankPorts(inlet, new ToolConnection(outletHeight.add(side.scale(0.18)), side),
                new ToolConnection(outletHeight.subtract(side.scale(0.18)), side.scale(-1)));
    }

    /** Called from the item renderer after all hand and item model transforms are applied. */
    public static void captureToolPort(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            boolean mirrored
    ) {
        boolean firstPerson = displayContext.firstPerson();
        boolean thirdPerson = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (PneumaticMultitoolItem.from(stack) == null || !firstPerson && !thirdPerson) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vector4f transformed = new Vector4f(TOOL_SOCKET_MODEL_POINT);
        if (mirrored) {
            transformed.x = 1.0F - transformed.x;
        }
        poseStack.last().pose().transform(transformed);
        Vector4f transformedNormal = new Vector4f(mirrored ? -1.0F : 1.0F, 0.0F, 0.0F, 0.0F);
        poseStack.last().pose().transform(transformedNormal);
        Vec3 outward = new Vec3(
                transformedNormal.x(), transformedNormal.y(), transformedNormal.z()).normalize();
        var camera = minecraft.gameRenderer.getMainCamera();
        long now = Util.getNanos();
        if (firstPerson) {
            // The hand pass cancels the camera rotation in its pose stack.
            // Store the resulting socket in view space for the next world pass.
            Quaternionf worldToView = camera.rotation().conjugate(new Quaternionf());
            worldToView.transform(transformed);
            worldToView.transform(transformedNormal);
            var projection = RenderSystem.getProjectionMatrix();
            FIRST_PERSON_TOOL_PORTS.put(stack, new FirstPersonToolPort(transformed, transformedNormal,
                    projection.m00(), projection.m11(), now));
        } else {
            CAPTURED_TOOL_PORTS.put(stack, new CapturedToolPort(
                    camera.getPosition().add(transformed.x(), transformed.y(), transformed.z()), outward, now));
        }
    }

    private static ToolConnection toolConnection(Player player, ItemStack tool, float partialTick, RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            FirstPersonToolPort captured = FIRST_PERSON_TOOL_PORTS.get(tool);
            if (captured != null && Util.getNanos() - captured.capturedAtNanos <= CAPTURE_LIFETIME_NANOS) {
                var projection = event.getProjectionMatrix();
                float xScale = captured.projectionX / projection.m00();
                float yScale = captured.projectionY / projection.m11();
                var camera = event.getCamera();
                Vector4f position = reprojectHandPoint(captured.position, xScale, yScale, camera.rotation());
                Vector4f normal = reprojectHandPoint(captured.outward, xScale, yScale, camera.rotation());
                return new ToolConnection(camera.getPosition().add(position.x(), position.y(), position.z()),
                        new Vec3(normal.x(), normal.y(), normal.z()).normalize());
            }
        } else {
            CapturedToolPort captured = CAPTURED_TOOL_PORTS.get(tool);
            if (isFresh(captured)) {
                return new ToolConnection(captured.position, captured.outward);
            }
        }
        Vec3 fallback = player.getRopeHoldPosition(partialTick);
        Vec3 outward = fallback.subtract(player.getEyePosition(partialTick));
        return new ToolConnection(fallback,
                outward.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : outward.normalize());
    }

    private static Vector4f reprojectHandPoint(Vector4f point, float xScale, float yScale, Quaternionf viewToWorld) {
        return viewToWorld.transform(new Vector4f(point.x() * xScale, point.y() * yScale, point.z(), point.w()));
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
        double lead = Math.min(0.45, distance * 0.25);
        Vec3 p1 = start.add(startNormal.scale(lead));
        Vec3 p2 = end.add(endOutward.scale(lead));
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
        Vec3 span = curve.p3().subtract(curve.p0());
        double sag = Math.min(0.26, span.horizontalDistance() * 0.05);

        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double sagWeight = 4.0 * t * (1.0 - t);
            Vec3 center = curve.pointAt(t).add(0, -sag * sagWeight * sagWeight, 0);
            Vec3 tangent = curve.derivativeAt(t).add(0, -8.0 * sag * sagWeight * (1.0 - 2.0 * t), 0);
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
        double distance = 0.0;
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 worldCenter = centers[i].add(worldOrigin);
            int light = minecraft.level == null ? 0xF000F0
                    : LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(worldCenter));
            double length = centers[i].distanceTo(centers[i + 1]);
            renderRibbedSegment(buffer, poseStack, sections[i], sections[i + 1], distance, length, light);
            distance += length;
        }
    }

    private static void renderRibbedSegment(VertexConsumer buffer, PoseStack poseStack, Vec3[] start, Vec3[] end,
            double distance, double length, int light) {
        double segmentEnd = distance + length;
        // Cut quads at the edge of each UV tile so none sample the adjacent fittings.
        for (double cursor = distance; cursor < segmentEnd;) {
            double tileStart = Math.floor(cursor / HOSE_RIB_PERIOD) * HOSE_RIB_PERIOD;
            double next = Math.min(segmentEnd, tileStart + HOSE_RIB_PERIOD);
            double t0 = (cursor - distance) / length;
            double t1 = (next - distance) / length;
            float v0 = hoseTextureV(cursor - tileStart);
            float v1 = hoseTextureV(next - tileStart);
            for (int side = 0; side < 4; side++) {
                int adjacent = (side + 1) % 4;
                addQuad(buffer, poseStack,
                        start[side].lerp(end[side], t0), start[adjacent].lerp(end[adjacent], t0),
                        start[adjacent].lerp(end[adjacent], t1), start[side].lerp(end[side], t1),
                        TEXTURE_U_MIN, v0, TEXTURE_U_MAX, v1, light);
            }
            cursor = next;
        }
    }

    private static float hoseTextureV(double tileDistance) {
        return TEXTURE_V_MIN + (TEXTURE_V_MAX - TEXTURE_V_MIN) * (float) (tileDistance / HOSE_RIB_PERIOD);
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

    private record NetworkNode(Vec3 position, Direction mountFace) {
        static NetworkNode forPillar(OxygenPillarEntity pillar) {
            return new NetworkNode(pillar.position().add(0.0D, 0.5D, 0.0D), pillar.mountFace());
        }

        ToolConnection portTowards(Vec3 target) {
            if (mountFace != null) {
                Quaternionf rotation = mountFace.getRotation();
                Vec3 delta = rotate(target.subtract(position), new Quaternionf(rotation).conjugate());
                Direction side = Math.abs(delta.x) >= Math.abs(delta.z)
                        ? (delta.x >= 0 ? Direction.EAST : Direction.WEST)
                        : (delta.z >= 0 ? Direction.SOUTH : Direction.NORTH);
                Vec3 outward = rotate(Vec3.atLowerCornerOf(side.getNormal()), rotation);
                Vec3 head = position.add(rotate(new Vec3(0, PILLAR_PORT_HEIGHT_FROM_CENTER, 0), rotation));
                return new ToolConnection(head.add(outward.scale(PILLAR_PORT_OFFSET)), outward);
            }
            Direction direction = nearestPortDirection(position, target);
            Vec3 outward = Vec3.atLowerCornerOf(direction.getNormal());
            return new ToolConnection(position.add(outward.scale(PORT_OFFSET)), outward);
        }
    }

    private static Vec3 rotate(Vec3 vector, Quaternionf rotation) {
        Vector3f result = rotation.transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(result.x, result.y, result.z);
    }

    private record CachedPillarSupply(long tick, PneumaticAirNetwork.Supply supply) {}

    private record CapturedToolPort(Vec3 position, Vec3 outward, long capturedAtNanos) {
    }

    private record BacktankPorts(ToolConnection inlet, ToolConnection firstOutlet, ToolConnection secondOutlet) {
        ToolConnection outletTowards(Vec3 target) {
            return firstOutlet.position().distanceToSqr(target) <= secondOutlet.position().distanceToSqr(target)
                    ? firstOutlet : secondOutlet;
        }
    }

    private record CapturedBacktankPorts(BacktankPorts ports, long capturedAtNanos) {}

    private record FirstPersonToolPort(Vector4f position, Vector4f outward,
            float projectionX, float projectionY, long capturedAtNanos) {
    }

    private record ToolConnection(Vec3 position, Vec3 outward) {
    }

}
