package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Reuses the old block model while the relay itself remains an entity. */
public class OxygenPillarRenderer extends EntityRenderer<OxygenPillarEntity> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public OxygenPillarRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); }
    @Override public void render(OxygenPillarEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(-0.5D, 0.0D, -0.5D);
        blocks.renderSingleBlock(ModBlocks.OXYGEN_PILLAR.get().defaultBlockState(), pose, buffers, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(OxygenPillarEntity entity) { return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; }
}
