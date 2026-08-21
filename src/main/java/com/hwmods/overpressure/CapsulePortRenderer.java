package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

public class CapsulePortRenderer implements BlockEntityRenderer<CapsulePortBlockEntity> {
    private static final float CAPSULE_SCALE = 0.72f;

    public CapsulePortRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            CapsulePortBlockEntity port,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (!port.hasVisibleCapsule()) {
            return;
        }

        Direction facing = CapsulePortBlock.getOutputDirection(port.getBlockState());
        Vec3 direction = Vec3.atLowerCornerOf(facing.getNormal());
        poseStack.pushPose();
        poseStack.translate(
                0.5 + direction.x * 0.16,
                0.5 + direction.y * 0.16,
                0.5 + direction.z * 0.16
        );
        rotateCapsule(poseStack, direction);
        poseStack.scale(CAPSULE_SCALE, CAPSULE_SCALE, CAPSULE_SCALE);
        poseStack.translate(-0.5, -0.25, -0.5);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(OverpressureClient.PNEUMATIC_CAPSULE_MODEL);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                bufferSource.getBuffer(Sheets.cutoutBlockSheet()),
                Blocks.AIR.defaultBlockState(),
                model,
                1.0f,
                1.0f,
                1.0f,
                packedLight,
                packedOverlay,
                ModelData.EMPTY,
                null
        );
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CapsulePortBlockEntity port) {
        return new AABB(port.getBlockPos()).inflate(0.25);
    }

    private static void rotateCapsule(PoseStack poseStack, Vec3 direction) {
        float yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.asin(direction.y));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
    }
}
