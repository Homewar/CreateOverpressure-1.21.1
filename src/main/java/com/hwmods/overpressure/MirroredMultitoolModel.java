package com.hwmods.overpressure;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Reflects hand-rendered geometry about the centre of the item. */
public final class MirroredMultitoolModel extends BakedModelWrapper<BakedModel> {
    private static final Map<BakedQuad, BakedQuad> MIRRORED_QUADS = new WeakHashMap<>();

    public MirroredMultitoolModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        Direction originalSide = reflect(side);
        return originalModel.getQuads(state, originalSide, random).stream()
                .map(quad -> MIRRORED_QUADS.computeIfAbsent(quad, MirroredMultitoolModel::mirror))
                .toList();
    }

    private static BakedQuad mirror(BakedQuad quad) {
        int[] original = quad.getVertices();
        int[] reflected = new int[original.length];
        int stride = IQuadTransformer.STRIDE;
        for (int i = 0; i < 4; i++) {
            // Reflection flips winding. Copy in reverse order to keep the outside face visible.
            int target = i * stride;
            System.arraycopy(original, ((4 - i) % 4) * stride, reflected, target, stride);
            int x = target + IQuadTransformer.POSITION;
            reflected[x] = Float.floatToRawIntBits(1.0F - Float.intBitsToFloat(reflected[x]));
            int normal = target + IQuadTransformer.NORMAL;
            int reflectedX = -(byte) reflected[normal] & 0xFF;
            reflected[normal] = (reflected[normal] & ~0xFF) | reflectedX;
        }
        return new BakedQuad(reflected, quad.getTintIndex(), reflect(quad.getDirection()),
                quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static Direction reflect(Direction direction) {
        return direction != null && direction.getAxis() == Direction.Axis.X ? direction.getOpposite() : direction;
    }
}
