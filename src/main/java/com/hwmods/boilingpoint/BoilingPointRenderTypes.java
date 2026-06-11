package com.hwmods.boilingpoint;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class BoilingPointRenderTypes {
    private static final ResourceLocation CURVE_TUBE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BoilingPoint.MODID,
            "textures/block/item_pipe_texture/curve_tube.png"
    );

    private static final RenderType CURVE_TUBE = RenderType.create(
            "boilingpoint_curve_tube",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(CURVE_TUBE_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
    );

    public static RenderType curveTube() {
        return CURVE_TUBE;
    }

    private BoilingPointRenderTypes() {
    }
}
