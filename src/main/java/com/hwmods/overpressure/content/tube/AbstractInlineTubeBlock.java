package com.hwmods.overpressure.content.tube;

import com.hwmods.overpressure.PneumaticTubeBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared block mechanics for one-axis devices embedded in a pneumatic tube.
 * Device-specific behavior, such as reacting to or emitting redstone, belongs
 * in the concrete block.
 */
public abstract class AbstractInlineTubeBlock extends BaseEntityBlock
        implements IWrenchable, SimpleWaterloggedBlock {
    private static final VoxelShape X_SHAPE = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape Y_SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape Z_SHAPE = Block.box(3, 3, 0, 13, 13, 16);

    protected AbstractInlineTubeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(BlockStateProperties.POWERED, false)
                .setValue(BlockStateProperties.WATERLOGGED, false));
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
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(BlockStateProperties.FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.POWERED, getPoweredStateForPlacement(context))
                .setValue(BlockStateProperties.WATERLOGGED, context.getLevel().getFluidState(pos).is(Fluids.WATER));
    }

    protected boolean getPoweredStateForPlacement(BlockPlaceContext context) {
        return false;
    }

    protected abstract BlockEntityType<? extends PneumaticTubeBlockEntity> getInlineBlockEntityType();

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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                BlockStateProperties.FACING,
                BlockStateProperties.POWERED,
                BlockStateProperties.WATERLOGGED
        );
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
                getInlineBlockEntityType(),
                PneumaticTubeBlockEntity::serverTick
        );
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateClientPathAt(level, pos);

            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube) {
                tube.ejectMovingItem(level, Vec3.atCenterOf(pos));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
