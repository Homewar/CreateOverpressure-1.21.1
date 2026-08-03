package com.hwmods.overpressure;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class GrindRailPlanningRenderer {
    @SubscribeEvent
    static void renderPreview(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof GrindRailBlockItem railItem)) {
            return;
        }
        GrindRailBlockItem.RailAnchor start = GrindRailBlockItem.getClientStart(player.getUUID());
        if (start == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        GrindRailBlockItem.RailPlan plan = railItem.createClientPlan(
                player.level(),
                start,
                hit.getBlockPos(),
                hit.getDirection(),
                hit.getLocation()
        );
        if (plan.p0() == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lines());
        int red = plan.valid() ? 88 : 231;
        int green = plan.valid() ? 214 : 76;
        int blue = plan.valid() ? 141 : 60;
        Vec3 previous = plan.p0();

        for (int index = 1; index <= 96; index++) {
            double t = index / 96.0;
            Vec3 next = GrindRailBlockItem.getPoint(plan.p0(), plan.p1(), plan.p2(), plan.p3(), t);
            addLine(buffer, pose, previous, next, red, green, blue);
            previous = next;
        }

        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void addLine(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 from,
            Vec3 to,
            int red,
            int green,
            int blue
    ) {
        Vec3 normal = to.subtract(from).cross(new Vec3(0.0, 1.0, 0.0));
        normal = normal.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : normal.normalize();
        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, 220)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, 220)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private GrindRailPlanningRenderer() {
    }
}
