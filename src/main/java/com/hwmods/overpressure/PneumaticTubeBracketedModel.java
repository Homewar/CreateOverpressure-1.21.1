package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class PneumaticTubeBracketedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BakedModel> BRACKET_PROPERTY = new ModelProperty<>();

    public PneumaticTubeBracketedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData blockEntityData) {
        BracketedBlockEntityBehaviour behaviour =
                BlockEntityBehaviour.get(level, pos, BracketedBlockEntityBehaviour.TYPE);

        if (behaviour == null || behaviour.getBracket() == null) {
            return blockEntityData;
        }

        BakedModel bracketModel = Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModel(behaviour.getBracket());

        return ModelData.builder()
                .with(BRACKET_PROPERTY, bracketModel)
                .build();
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

        if (!data.has(BRACKET_PROPERTY)) {
            return quads;
        }

        List<BakedQuad> withBracket = new ArrayList<>(quads);
        withBracket.addAll(data.get(BRACKET_PROPERTY).getQuads(state, side, random, data, renderType));
        return withBracket;
    }
}
