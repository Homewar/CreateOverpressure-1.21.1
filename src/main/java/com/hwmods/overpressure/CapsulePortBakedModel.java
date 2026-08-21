package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Uses one geometry model and swaps its atlas sprite for the open block state. */
public class CapsulePortBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation OPEN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Overpressure.MODID,
            "block/capsule_port/capsule_terminal_open"
    );
    private static final int VERTEX_COUNT = 4;
    private static final int U_INDEX = 4;
    private static final int V_INDEX = 5;

    public CapsulePortBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(
            BlockState state,
            Direction side,
            RandomSource random,
            ModelData data,
        RenderType renderType
    ) {
        List<BakedQuad> quads = super.getQuads(state, side, random, data, renderType);
        if (quads.isEmpty()) {
            return quads;
        }

        boolean open = state != null
                && state.hasProperty(CapsulePortBlock.OPEN)
                && state.getValue(CapsulePortBlock.OPEN);
        TextureAtlasSprite openSprite = open
                ? Minecraft.getInstance()
                        .getModelManager()
                        .getAtlas(InventoryMenu.BLOCK_ATLAS)
                        .getSprite(OPEN_TEXTURE)
                : null;

        List<BakedQuad> doubleSidedQuads = new ArrayList<>(quads.size() * 2);
        for (BakedQuad quad : quads) {
            BakedQuad visibleQuad = open ? withSprite(quad, openSprite) : quad;
            doubleSidedQuads.add(visibleQuad);
            doubleSidedQuads.add(reverseWinding(visibleQuad));
        }
        return doubleSidedQuads;
    }

    private static BakedQuad withSprite(BakedQuad quad, TextureAtlasSprite newSprite) {
        TextureAtlasSprite oldSprite = quad.getSprite();
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / VERTEX_COUNT;

        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            int offset = vertex * stride;
            float oldU = Float.intBitsToFloat(vertices[offset + U_INDEX]);
            float oldV = Float.intBitsToFloat(vertices[offset + V_INDEX]);
            float newU = remap(oldU, oldSprite.getU0(), oldSprite.getU1(), newSprite.getU0(), newSprite.getU1());
            float newV = remap(oldV, oldSprite.getV0(), oldSprite.getV1(), newSprite.getV0(), newSprite.getV1());
            vertices[offset + U_INDEX] = Float.floatToRawIntBits(newU);
            vertices[offset + V_INDEX] = Float.floatToRawIntBits(newV);
        }

        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                newSprite,
                quad.isShade()
        );
    }

    private static BakedQuad reverseWinding(BakedQuad quad) {
        int[] source = quad.getVertices();
        int[] reversed = new int[source.length];
        int stride = source.length / VERTEX_COUNT;

        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            int sourceOffset = (VERTEX_COUNT - 1 - vertex) * stride;
            int targetOffset = vertex * stride;
            System.arraycopy(source, sourceOffset, reversed, targetOffset, stride);
            reversed[targetOffset + stride - 1] = invertPackedNormal(reversed[targetOffset + stride - 1]);
        }

        return new BakedQuad(
                reversed,
                quad.getTintIndex(),
                quad.getDirection().getOpposite(),
                quad.getSprite(),
                quad.isShade()
        );
    }

    private static int invertPackedNormal(int packedNormal) {
        int x = -(byte) (packedNormal & 0xff);
        int y = -(byte) ((packedNormal >>> 8) & 0xff);
        int z = -(byte) ((packedNormal >>> 16) & 0xff);
        return (packedNormal & 0xff000000)
                | (x & 0xff)
                | (y & 0xff) << 8
                | (z & 0xff) << 16;
    }

    private static float remap(float value, float oldMin, float oldMax, float newMin, float newMax) {
        float progress = (value - oldMin) / (oldMax - oldMin);
        return newMin + progress * (newMax - newMin);
    }
}
