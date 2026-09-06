package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import org.joml.Matrix4f;

@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public class PlanningModeRenderer {
    private static final float GHOST_ALPHA = 0.45f;
    private static final int SEGMENTS = 18;
    private static final double HALF_SIZE = 0.2425;
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_EAST = new Vec3(1.0, 0.0, 0.0);
    private static final int VALID_ROUTE_COLOR = 0x58D68D;
    private static final int INVALID_ROUTE_COLOR = 0xE74C3C;
    private static Level previewLevel;
    private static Player previewPlayer;
    private static long previewTick = Long.MIN_VALUE;
    private static PneumaticTubeBlockItem.CurveStart previewStart;
    private static PneumaticTubeBlockItem.PlacementPreview preview;

    public static void invalidatePreview() {
        preview = null;
    }

    @SubscribeEvent
    static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            PneumaticTubeBlockItem.clearClientCurveStart(event.getPlayer().getUUID());
        }
        preview = null;
        previewLevel = null;
        previewPlayer = null;
        previewStart = null;
        previewTick = Long.MIN_VALUE;
    }

    private static ItemStack tubeStack(Player player) {
        return player.getMainHandItem().getItem() instanceof PneumaticTubeBlockItem
                ? player.getMainHandItem() : player.getOffhandItem();
    }

    private static PneumaticTubeBlockItem.PlacementPreview getPreview(
            Player player, PneumaticTubeBlockItem tubeItem, PneumaticTubeBlockItem.CurveStart start
    ) {
        if (preview == null || previewLevel != player.level() || previewPlayer != player
                || previewTick != player.level().getGameTime() || !start.equals(previewStart)) {
            previewLevel = player.level();
            previewPlayer = player;
            previewTick = previewLevel.getGameTime();
            previewStart = start;
            preview = tubeItem.previewPlacement(previewLevel, player, start);
        }
        return preview;
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || !(tubeStack(player).getItem() instanceof PneumaticTubeBlockItem tubeItem)) {
            return;
        }
        Level level = player.level();
        PneumaticTubeBlockItem.CurveStart start = tubeItem.getSelectedStart(level, player);
        if (start == null && !PlanningMode.isActive()) {
            return;
        }
        PneumaticTubeBlockItem.PlacementPreview route = start == null ? null : getPreview(player, tubeItem, start);
        PneumaticTubeBlockItem.PlanResult result;
        if (route != null) {
            result = route.plan();
        } else if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            result = tubeItem.planSingle(level, hit.getBlockPos(), hit.getDirection());
        } else {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean enoughItems = player.getAbilities().instabuild || tubeStack(player).getCount() >= result.tubes().size();
        int routeColor = result.valid() && enoughItems ? VALID_ROUTE_COLOR : INVALID_ROUTE_COLOR;
        boolean hasStraightTubes = false;

        for (PneumaticTubeBlockItem.PlanTube tube : result.tubes()) {
            if (tube.curveP0() == null) {
                drawGhostTube(poseStack, buffers.getBuffer(OverpressureRenderTypes.ghostTube()), tube, routeColor);
                hasStraightTubes = true;
            }
        }
        if (hasStraightTubes) {
            buffers.endBatch(OverpressureRenderTypes.ghostTube());
        }

        boolean hasCurveTubes = false;
        for (PneumaticTubeBlockItem.PlanTube tube : result.tubes()) {
            if (tube.curveP0() != null) {
                drawGhostTube(poseStack, buffers.getBuffer(OverpressureRenderTypes.ghostCurveTube()), tube, routeColor);
                hasCurveTubes = true;
            }
        }
        if (hasCurveTubes) {
            buffers.endBatch(OverpressureRenderTypes.ghostCurveTube());
        }
        poseStack.popPose();
    }

    private static void drawGhostTube(
            PoseStack poseStack,
            VertexConsumer buffer,
            PneumaticTubeBlockItem.PlanTube tube,
            int color
    ) {
        Matrix4f pose = poseStack.last().pose();
        Vec3 origin = Vec3.atLowerCornerOf(tube.pos());

        if (tube.curveP0() != null) {
            renderCurve(origin, tube.curveP0(), tube.curveP1(), tube.curveP2(), tube.curveP3(), pose, buffer, color);
        } else {
            renderStraight(tube.pos(), tube.state(), pose, buffer, color);
        }
    }

    private static void renderStraight(BlockPos pos, BlockState state, Matrix4f pose, VertexConsumer buffer, int color) {
        Vec3 center = Vec3.atLowerCornerOf(pos).add(0.5, 0.5, 0.5);
        int packedLight = 0xF000F0;

        for (Direction direction : Direction.values()) {
            if (!state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) {
                continue;
            }
            Vec3 to = center.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5));
            drawCapsule(center, to, pose, buffer, packedLight, color);
        }
        drawCapsule(center, center, pose, buffer, packedLight, color);
    }

    private static void renderCurve(Vec3 origin, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Matrix4f pose, VertexConsumer buffer, int color) {
        int packedLight = 0xF000F0;
        Vec3[][] sections = new Vec3[SEGMENTS + 1][4];
        Vec3 previousRight = null;
        Vec3 previousUp = null;

        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            Vec3 center = getPoint(p0, p1, p2, p3, t).add(origin);
            Vec3 tangent = getTangent(p0, p1, p2, p3, t);
            Vec3[] basis = getSectionBasis(tangent, previousRight, previousUp);
            Vec3 right = basis[0];
            Vec3 up = basis[1];
            sections[i] = makeSquareSection(center, right, up);
            previousRight = right;
            previousUp = up;
        }

        for (int i = 0; i < SEGMENTS; i++) {
            Vec3[] from = sections[i];
            Vec3[] to = sections[i + 1];
            float u0 = (float) i / SEGMENTS;
            float u1 = (float) (i + 1) / SEGMENTS;
            addQuad(buffer, pose, from[0], from[1], to[1], to[0], u0, u1, packedLight, color);
            addQuad(buffer, pose, from[1], from[2], to[2], to[1], u0, u1, packedLight, color);
            addQuad(buffer, pose, from[2], from[3], to[3], to[2], u0, u1, packedLight, color);
            addQuad(buffer, pose, from[3], from[0], to[0], to[3], u0, u1, packedLight, color);
        }
    }

    private static void drawCapsule(Vec3 from, Vec3 to, Matrix4f pose, VertexConsumer buffer, int packedLight, int color) {
        Vec3 forward = to.subtract(from);
        if (forward.lengthSqr() < 1.0E-8) {
            return;
        }
        forward = forward.normalize();
        Vec3 reference = Math.abs(forward.y) > 0.92 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = forward.cross(reference).normalize().scale(HALF_SIZE);
        Vec3 up = right.cross(forward).normalize().scale(HALF_SIZE);
        Vec3 a = from.subtract(right).subtract(up);
        Vec3 b = from.subtract(right).add(up);
        Vec3 c = from.add(right).add(up);
        Vec3 d = from.add(right).subtract(up);
        Vec3 e = to.subtract(right).subtract(up);
        Vec3 f = to.subtract(right).add(up);
        Vec3 g = to.add(right).add(up);
        Vec3 h = to.add(right).subtract(up);
        addQuad(buffer, pose, a, b, f, e, 0.0f, 1.0f, packedLight, color);
        addQuad(buffer, pose, b, c, g, f, 0.0f, 1.0f, packedLight, color);
        addQuad(buffer, pose, c, d, h, g, 0.0f, 1.0f, packedLight, color);
        addQuad(buffer, pose, d, a, e, h, 0.0f, 1.0f, packedLight, color);
    }

    private static Vec3 getPoint(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;
        return p0.scale(uu * u)
                .add(p1.scale(3.0 * uu * t))
                .add(p2.scale(3.0 * u * tt))
                .add(p3.scale(tt * t));
    }

    private static Vec3 getTangent(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float before = Math.max(0.0f, t - 0.01f);
        float after = Math.min(1.0f, t + 0.01f);
        Vec3 tangent = getPoint(p0, p1, p2, p3, after).subtract(getPoint(p0, p1, p2, p3, before));
        return tangent.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : tangent.normalize();
    }

    private static Vec3[] getSectionBasis(Vec3 tangent, Vec3 previousRight, Vec3 previousUp) {
        if (previousRight != null && previousUp != null) {
            Vec3 right = previousRight.subtract(tangent.scale(previousRight.dot(tangent)));
            if (right.lengthSqr() > 1.0E-6) {
                right = right.normalize();
                Vec3 up = right.cross(tangent).normalize();
                return new Vec3[] { right, up };
            }
        }
        Vec3 reference = Math.abs(tangent.dot(WORLD_UP)) > 0.92 ? WORLD_EAST : WORLD_UP;
        Vec3 right = tangent.cross(reference).normalize();
        Vec3 up = right.cross(tangent).normalize();
        return new Vec3[] { right, up };
    }

    private static Vec3[] makeSquareSection(Vec3 center, Vec3 right, Vec3 up) {
        Vec3 horizontal = right.scale(HALF_SIZE);
        Vec3 vertical = up.scale(HALF_SIZE);
        return new Vec3[] {
                center.add(horizontal).add(vertical),
                center.subtract(horizontal).add(vertical),
                center.subtract(horizontal).subtract(vertical),
                center.add(horizontal).subtract(vertical)
        };
    }

    private static void addQuad(VertexConsumer buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, int packedLight, int color) {
        Vec3 normal = c.subtract(a).cross(b.subtract(a));
        normal = normal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : normal.normalize();
        addVertex(buffer, pose, a, u0, 0.0f, normal, packedLight, color);
        addVertex(buffer, pose, b, u0, 1.0f, normal, packedLight, color);
        addVertex(buffer, pose, c, u1, 1.0f, normal, packedLight, color);
        addVertex(buffer, pose, d, u1, 0.0f, normal, packedLight, color);
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f pose, Vec3 position, float u, float v, Vec3 normal, int packedLight, int color) {
        buffer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (int) (GHOST_ALPHA * 255))
                .setUv(u, v)
                .setOverlay(0)
                .setLight(packedLight)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || minecraft.options.hideGui
                || !(tubeStack(player).getItem() instanceof PneumaticTubeBlockItem tubeItem)) {
            return;
        }
        PneumaticTubeBlockItem.CurveStart start = tubeItem.getSelectedStart(player.level(), player);
        if (start == null && !PlanningMode.isActive()) {
            return;
        }

        java.util.List<Component> lines = new java.util.ArrayList<>();
        Component title = Component.translatable(start == null ? "overpressure.planning.title" : "overpressure.routing.title");
        boolean valid = true;
        if (start != null) {
            var route = getPreview(player, tubeItem, start);
            boolean enough = player.getAbilities().instabuild || tubeStack(player).getCount() >= route.plan().tubes().size();
            valid = route.plan().valid() && enough;
            lines.add(Component.translatable("overpressure.routing.summary", route.plan().tubes().size(),
                    Component.translatable("overpressure.routing.direction." + route.outgoing().getName())));
            lines.add(Component.translatable("overpressure.routing.reach", tubeItem.getPlacementReach(player, start)));
            lines.add(Component.translatable(route.connected() ? "overpressure.routing.connected" : "overpressure.routing.free_end"));
            lines.add(Component.translatable("overpressure.routing.controls"));
            if (!route.plan().valid()) {
                lines.add(Component.translatable(route.plan().errorMessage()));
            } else if (!enough) {
                lines.add(Component.translatable("overpressure.routing.error.items"));
            }
        } else {
            lines.add(Component.translatable("overpressure.routing.select_start"));
            lines.add(Component.translatable("overpressure.routing.aim"));
        }

        GuiGraphics gui = event.getGuiGraphics();
        int x = 12;
        int y = 12;
        int maxWidth = Math.max(80, gui.guiWidth() - 28);
        int width = Math.min(maxWidth, Math.max(220, minecraft.font.width(title)));
        for (Component line : lines) {
            width = Math.min(maxWidth, Math.max(width, minecraft.font.width(line)));
        }
        java.util.List<net.minecraft.util.FormattedCharSequence> wrapped = new java.util.ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(minecraft.font.split(line, width));
        }
        int color = valid ? VALID_ROUTE_COLOR : INVALID_ROUTE_COLOR;
        gui.fill(x - 4, y - 4, x + width + 4, y + 18 + wrapped.size() * 12, 0xC0101010);
        gui.fill(x - 4, y - 4, x + width + 4, y - 3, 0xFF000000 | color);
        gui.drawString(minecraft.font, title, x, y, color, false);
        int lineY = y + 16;
        for (var line : wrapped) {
            gui.drawString(minecraft.font, line, x, lineY, 0xE0E0E0, false);
            lineY += 12;
        }
    }
}
