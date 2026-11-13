package com.hwmods.overpressure;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * A pneumatic-line component that owns network-level settings. For now its
 * block entity only persists the configured item travel speed.
 */
public class PneumaticConnectorBlock extends PneumaticTubeBlock {
    public static final MapCodec<PneumaticConnectorBlock> CODEC = simpleCodec(PneumaticConnectorBlock::new);

    public PneumaticConnectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState createDefaultState() {
        return super.createDefaultState()
                .setValue(NORTH, true)
                .setValue(SOUTH, true)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(HAS_CONNECTION, true);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                WATERLOGGED,
                context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER)
        );
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PneumaticConnectorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide
                ? null
                : createTickerHelper(
                        type,
                        ModBlockEntities.PNEUMATIC_CONNECTOR.get(),
                        PneumaticConnectorBlockEntity::serverTick
                );
    }
}
