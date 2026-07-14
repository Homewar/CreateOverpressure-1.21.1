package com.hwmods.boilingpoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PneumaticConnectionRenderer implements BlockEntityRenderer<PneumaticConnectionBlockEntity> {
    private static final float FILTER_SCALE = 0.42f;
    private static final float CORE_FACE = 13.25f / 16.0f;
    private static final float CORE_BACK_FACE = 2.75f / 16.0f;

    private final ItemRenderer itemRenderer;

    public PneumaticConnectionRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            PneumaticConnectionBlockEntity connector,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        ItemStack filter = connector.getFilter();
        if (connector.getBlockState().getValue(PneumaticConnectionBlock.MODE) != PneumaticConnectionBlock.ConnectionMode.EXTRACT) {
            return;
        }

        Direction slotFace = PneumaticConnectionBlock.getFilterSlotFace(connector.getBlockState());
        renderFilterSlot(slotFace, poseStack, bufferSource);

        if (filter.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        translateToSlot(slotFace, poseStack);
        rotateToFace(slotFace, poseStack);
        poseStack.scale(FILTER_SCALE, FILTER_SCALE, FILTER_SCALE);
        itemRenderer.renderStatic(
                filter,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                connector.getLevel(),
                0
        );
        poseStack.popPose();
    }

    private void translateToSlot(Direction face, PoseStack poseStack) {
        double x = 0.5;
        double y = 0.5;
        double z = 0.5;

        switch (face) {
            case UP -> y = CORE_FACE;
            case DOWN -> y = CORE_BACK_FACE;
            case NORTH -> z = CORE_BACK_FACE;
            case SOUTH -> z = CORE_FACE;
            case EAST -> x = CORE_FACE;
            case WEST -> x = CORE_BACK_FACE;
        }

        poseStack.translate(x, y, z);
    }

    private void rotateToFace(Direction face, PoseStack poseStack) {
        switch (face) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            case NORTH -> {
            }
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        }
    }

    private void renderFilterSlot(Direction face, PoseStack poseStack, MultiBufferSource bufferSource) {
        float min = 4.75f / 16.0f;
        float max = 11.25f / 16.0f;
        float front = 13.35f / 16.0f;
        float back = 2.65f / 16.0f;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        switch (face) {
            case UP -> drawRect(buffer, pose, min, front, min, max, front, max, face);
            case DOWN -> drawRect(buffer, pose, min, back, min, max, back, max, face);
            case NORTH -> drawRect(buffer, pose, min, min, back, max, max, back, face);
            case SOUTH -> drawRect(buffer, pose, min, min, front, max, max, front, face);
            case EAST -> drawRect(buffer, pose, front, min, min, front, max, max, face);
            case WEST -> drawRect(buffer, pose, back, min, min, back, max, max, face);
        }
    }

    private void drawRect(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            Direction normal
    ) {
        if (x0 == x1) {
            addLine(buffer, pose, x0, y0, z0, x0, y1, z0, normal);
            addLine(buffer, pose, x0, y1, z0, x0, y1, z1, normal);
            addLine(buffer, pose, x0, y1, z1, x0, y0, z1, normal);
            addLine(buffer, pose, x0, y0, z1, x0, y0, z0, normal);
            return;
        }

        if (y0 == y1) {
            addLine(buffer, pose, x0, y0, z0, x1, y0, z0, normal);
            addLine(buffer, pose, x1, y0, z0, x1, y0, z1, normal);
            addLine(buffer, pose, x1, y0, z1, x0, y0, z1, normal);
            addLine(buffer, pose, x0, y0, z1, x0, y0, z0, normal);
            return;
        }

        addLine(buffer, pose, x0, y0, z0, x1, y0, z0, normal);
        addLine(buffer, pose, x1, y0, z0, x1, y1, z0, normal);
        addLine(buffer, pose, x1, y1, z0, x0, y1, z0, normal);
        addLine(buffer, pose, x0, y1, z0, x0, y0, z0, normal);
    }

    private void addLine(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            Direction normal
    ) {
        addLineVertex(buffer, pose, x0, y0, z0, normal);
        addLineVertex(buffer, pose, x1, y1, z1, normal);
    }

    private void addLineVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, Direction normal) {
        buffer.addVertex(pose, x, y, z)
                .setColor(255, 214, 83, 180)
                .setNormal(normal.getStepX(), normal.getStepY(), normal.getStepZ());
    }
}
