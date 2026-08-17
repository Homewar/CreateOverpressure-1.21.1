package com.hwmods.overpressure;

import com.hwmods.overpressure.content.tube.AbstractInlineTubeBlock;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ValveBlock extends AbstractInlineTubeBlock {
    public static final MapCodec<ValveBlock> CODEC = simpleCodec(ValveBlock::new);

    public ValveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean getPoweredStateForPlacement(BlockPlaceContext context) {
        return context.getLevel().hasNeighborSignal(context.getClickedPos());
    }

    @Override
    protected BlockEntityType<? extends PneumaticTubeBlockEntity> getInlineBlockEntityType() {
        return ModBlockEntities.VALVE.get();
    }

    public boolean allowsTravel(BlockState state, Direction movementDirection) {
        return !state.getValue(BlockStateProperties.POWERED)
                && canTravelTo(state, movementDirection);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(BlockStateProperties.POWERED) != powered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

}
