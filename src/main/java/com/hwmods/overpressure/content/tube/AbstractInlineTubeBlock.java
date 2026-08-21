package com.hwmods.overpressure.content.tube;

import com.hwmods.overpressure.ClogSensorBlock;
import com.hwmods.overpressure.CurvaturePneumaticTubeBlock;
import com.hwmods.overpressure.ItemPumpBlock;
import com.hwmods.overpressure.PneumaticConnectionBlock;
import com.hwmods.overpressure.PneumaticLine;
import com.hwmods.overpressure.PneumaticTubeBlock;
import com.hwmods.overpressure.PneumaticTubeBlockEntity;
import com.hwmods.overpressure.ValveBlock;
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
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction nearestLookingDirection = context.getNearestLookingDirection();
        boolean isSneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = pos.relative(clickedFace.getOpposite());
        boolean placedAgainstPneumaticLine = PneumaticLine.isPathNode(level, clickedPos);
        Direction targetDirection = placedAgainstPneumaticLine
                ? (isSneaking ? clickedFace.getOpposite() : clickedFace)
                : (isSneaking ? nearestLookingDirection.getOpposite() : nearestLookingDirection);
        BlockState toPlace = defaultBlockState()
                .setValue(BlockStateProperties.FACING, targetDirection)
                .setValue(BlockStateProperties.POWERED, getPoweredStateForPlacement(context))
                .setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER));

        Direction bestConnectedDirection = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockState neighborState = level.getBlockState(pos.relative(direction));

            if (!canConnectToPneumaticLine(neighborState, direction.getOpposite())) {
                continue;
            }

            double distance = Vec3.atLowerCornerOf(direction.getNormal())
                    .distanceTo(Vec3.atLowerCornerOf(targetDirection.getNormal()));

            if (distance > bestDistance) {
                continue;
            }

            bestDistance = distance;
            bestConnectedDirection = direction;
        }

        if (bestConnectedDirection != null
                && bestConnectedDirection.getAxis() != targetDirection.getAxis()
                && !placedAgainstPneumaticLine
                && !isSneaking) {
            return toPlace.setValue(BlockStateProperties.FACING, bestConnectedDirection);
        }

        return toPlace;
    }

    protected boolean getPoweredStateForPlacement(BlockPlaceContext context) {
        return false;
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

    private boolean canConnectToPneumaticLine(BlockState state, Direction directionFromNeighbor) {
        if (state.getBlock() instanceof PneumaticTubeBlock) {
            return state.getValue(PneumaticTubeBlock.getConnectionProperty(directionFromNeighbor));
        }

        if (state.getBlock() instanceof CurvaturePneumaticTubeBlock) {
            return state.getValue(PneumaticTubeBlock.getConnectionProperty(directionFromNeighbor));
        }

        if (state.getBlock() instanceof PneumaticConnectionBlock) {
            return state.getValue(PneumaticConnectionBlock.FACING).getAxis() == directionFromNeighbor.getAxis();
        }

        if (state.getBlock() instanceof ItemPumpBlock pump) {
            return pump.canTravelTo(state, directionFromNeighbor);
        }

        if (state.getBlock() instanceof ValveBlock valve) {
            return valve.canTravelTo(state, directionFromNeighbor);
        }

        if (state.getBlock() instanceof ClogSensorBlock sensor) {
            return sensor.canTravelTo(state, directionFromNeighbor);
        }

        return false;
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
        return null;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);

            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube) {
                tube.ejectMovingItem(level, Vec3.atCenterOf(pos));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
