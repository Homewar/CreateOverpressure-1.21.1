package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Renders the placed relay using the matching inventory model geometry. */
public class OxygenPillarRenderer extends EntityRenderer<OxygenPillarEntity> {
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public OxygenPillarRenderer(EntityRendererProvider.Context context) { super(context); blocks = context.getBlockRenderDispatcher(); }
    @Override public void render(OxygenPillarEntity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.0D, 0.5D, 0.0D);
        pose.mulPose(entity.mountFace().getRotation());
        pose.translate(-0.5D, -0.5D, -0.5D);
        var state = ModBlocks.OXYGEN_PILLAR.get().defaultBlockState();
        var model = entity.hasGlowingSocket()
                ? Minecraft.getInstance().getModelManager().getModel(OverpressureClient.GLOWING_PILLAR_MODEL)
                : blocks.getBlockModel(state);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                state, model, 1, 1, 1, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(OxygenPillarEntity entity) { return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; }
}
