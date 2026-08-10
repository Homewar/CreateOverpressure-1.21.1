package com.hwmods.overpressure;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ValveBlock extends BaseEntityBlock implements IWrenchable, SimpleWaterloggedBlock {
    public static final MapCodec<ValveBlock> CODEC = simpleCodec(ValveBlock::new);
    private static final VoxelShape X_SHAPE = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape Y_SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape Z_SHAPE = Block.box(3, 3, 0, 13, 13, 16);

    public ValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(BlockStateProperties.POWERED, false)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BlockStateProperties.FACING).getAxis()) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState()
                .setValue(BlockStateProperties.FACING, facing)
                .setValue(BlockStateProperties.POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(
                        BlockStateProperties.WATERLOGGED,
                        context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER)
                );
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(
                BlockStateProperties.FACING,
                originalState.getValue(BlockStateProperties.FACING).getOpposite()
        );
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        return newState;
    }

    public boolean canTravelTo(BlockState state, Direction direction) {
        return state.getValue(BlockStateProperties.FACING).getAxis() == direction.getAxis();
    }

    public boolean allowsTravel(BlockState state, Direction movementDirection) {
        return !state.getValue(BlockStateProperties.POWERED)
                && canTravelTo(state, movementDirection);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                BlockStateProperties.FACING,
                BlockStateProperties.POWERED,
                BlockStateProperties.WATERLOGGED
        );
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
            level.setBlock(
                    pos,
                    state.setValue(BlockStateProperties.POWERED, powered),
                    Block.UPDATE_CLIENTS
            );
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : Fluids.EMPTY.defaultFluidState();
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
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateClientPathAt(level, pos);
        }

        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ValveBlockEntity valve) {
            valve.ejectMovingItem(level, net.minecraft.world.phys.Vec3.atCenterOf(pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
                type,
                ModBlockEntities.VALVE.get(),
                PneumaticTubeBlockEntity::serverTick
        );
    }
}
