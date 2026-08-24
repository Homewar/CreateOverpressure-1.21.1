package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class FilterPipeRenderer extends PneumaticTubeRenderer {
    public FilterPipeRenderer(BlockEntityRendererProvider.Context context) {
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
        if (tube instanceof FilterPipeBlockEntity filterPipe) {
            renderBlock(filterPipe, poseStack, bufferSource, packedLight, packedOverlay);
            FilteringRenderer.renderOnBlockEntity(
                    filterPipe,
                    partialTick,
                    poseStack,
                    bufferSource,
                    packedLight,
                    packedOverlay
            );
        }
        super.render(tube, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private static void renderBlock(
            FilterPipeBlockEntity filterPipe,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = filterPipe.getBlockState();
        Direction main = state.getValue(FilterPipeBlock.INPUT).getOpposite();
        Direction front = FilterPipeBlock.getFrontDirection(state);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        DeviderRenderer.applyMainRotation(poseStack, main);
        poseStack.mulPose(Axis.YP.rotationDegrees(DeviderRenderer.findRoll(main, front)));
        poseStack.translate(-0.5, -0.5, -0.5);

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                bufferSource.getBuffer(Sheets.cutoutBlockSheet()),
                Blocks.AIR.defaultBlockState(),
                minecraft.getModelManager().getModel(OverpressureClient.FILTER_PIPE_BLOCK_MODEL),
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
}
