package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class OverpressureRenderTypes {
    private static final ResourceLocation CURVE_TUBE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Overpressure.MODID,
            "textures/block/item_pipe_texture/curve_tube.png"
    );
    private static final ResourceLocation PONDER_CURVE_TUBE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Overpressure.MODID,
            "textures/block/item_pipe_texture/ponder_only/curve_tube.png"
    );
    private static final ResourceLocation TUBE_CORE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Overpressure.MODID,
            "textures/block/item_pipe_texture/core.png"
    );

    private static final RenderType CURVE_TUBE = RenderType.create(
            "overpressure_curve_tube",
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

    private static final RenderType CURVE_TUBE_IN_PONDER = RenderType.create(
            "overpressure_curve_tube_in_ponder",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(PONDER_CURVE_TUBE_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );

    public static RenderType curveTube() {
        return CURVE_TUBE;
    }

    public static RenderType curveTubeInPonder() {
        return CURVE_TUBE_IN_PONDER;
    }

    private static final RenderType GHOST_TUBE = RenderType.create(
            "overpressure_ghost_tube",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TUBE_CORE_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
    );

    public static RenderType ghostTube() {
        return GHOST_TUBE;
    }

    private static final RenderType GHOST_CURVE_TUBE = RenderType.create(
            "overpressure_ghost_curve_tube",
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

    public static RenderType ghostCurveTube() {
        return GHOST_CURVE_TUBE;
    }

    private OverpressureRenderTypes() {
    }
}
