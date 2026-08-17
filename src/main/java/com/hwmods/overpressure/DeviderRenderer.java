package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class DeviderRenderer extends PneumaticTubeRenderer {
    public DeviderRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            PneumaticTubeBlockEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (tube instanceof DeviderBlockEntity devider) {
            renderBlock(devider, poseStack, bufferSource, packedLight, packedOverlay);
        }
        super.render(tube, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void renderBlock(
            DeviderBlockEntity devider,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = devider.getBlockState();
        Direction main = state.getValue(DeviderBlock.INPUT).getOpposite();
        Direction front = DeviderBlock.getFrontDirection(state);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        applyMainRotation(poseStack, main);
        poseStack.mulPose(Axis.YP.rotationDegrees(findRoll(main, front)));
        poseStack.translate(-0.5, -0.5, -0.5);

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                bufferSource.getBuffer(Sheets.cutoutBlockSheet()),
                Blocks.AIR.defaultBlockState(),
                minecraft.getModelManager().getModel(devider.getJunctionRole()
                        == DeviderBlockEntity.JunctionRole.MERGER
                        ? OverpressureClient.DEVIDER_MERGE_BLOCK_MODEL
                        : OverpressureClient.DEVIDER_BLOCK_MODEL),
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

    private static void applyMainRotation(PoseStack poseStack, Direction main) {
        switch (main) {
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case EAST -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> poseStack.mulPose(Axis.ZP.rotationDegrees(90));
            case UP -> {
            }
        }
    }

    private static int findRoll(Direction main, Direction targetFront) {
        for (int roll : new int[] { 0, 90, 180, 270 }) {
            if (transformFront(main, roll) == targetFront) {
                return roll;
            }
        }
        return 0;
    }

    private static Direction transformFront(Direction main, int roll) {
        int x = switch (roll) {
            case 90 -> 1;
            case 270 -> -1;
            default -> 0;
        };
        int z = switch (roll) {
            case 0 -> 1;
            case 180 -> -1;
            default -> 0;
        };
        int transformedX = x;
        int transformedY = 0;
        int transformedZ = z;

        return switch (main) {
            case UP -> Direction.getNearest(transformedX, transformedY, transformedZ);
            case DOWN -> Direction.getNearest(transformedX, -transformedY, -transformedZ);
            case NORTH -> Direction.getNearest(transformedX, transformedZ, -transformedY);
            case SOUTH -> Direction.getNearest(transformedX, -transformedZ, transformedY);
            case EAST -> Direction.getNearest(transformedY, -transformedX, transformedZ);
            case WEST -> Direction.getNearest(-transformedY, transformedX, transformedZ);
        };
    }
}
