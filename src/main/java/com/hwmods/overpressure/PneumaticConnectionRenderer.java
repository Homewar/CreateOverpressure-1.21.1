package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class PneumaticConnectionRenderer implements BlockEntityRenderer<PneumaticConnectionBlockEntity> {
    public PneumaticConnectionRenderer(BlockEntityRendererProvider.Context context) {
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
        FilteringRenderer.renderOnBlockEntity(
                connector,
                partialTick,
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );
    }
}
