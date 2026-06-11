package com.hwmods.boilingpoint;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
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

public class ItemPumpBlock extends DirectionalKineticBlock
        implements SimpleWaterloggedBlock, ICogWheel, IBE<ItemPumpBlockEntity> {
    public static final MapCodec<ItemPumpBlock> CODEC = simpleCodec(ItemPumpBlock::new);
    private static final VoxelShape X_SHAPE = Block.box(0, 2, 2, 16, 14, 14);
    private static final VoxelShape Y_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape Z_SHAPE = Block.box(2, 2, 0, 14, 14, 16);

    public ItemPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getOpposite());
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState toPlace = super.getStateForPlacement(context);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        toPlace = ProperWaterloggedBlock.withWater(level, toPlace, pos);

        Direction nearestLookingDirection = context.getNearestLookingDirection();
        Direction targetDirection = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? nearestLookingDirection
                : nearestLookingDirection.getOpposite();
        Direction bestConnectedDirection = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);

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
                && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())) {
            return toPlace.setValue(FACING, bestConnectedDirection);
        }

        return toPlace;
    }

    public boolean canTravelTo(BlockState state, Direction direction) {
        return direction.getAxis() == state.getValue(FACING).getAxis();
    }

    @Nullable
    public Direction getFlowDirection(Level level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof ItemPumpBlockEntity pump) || pump.getSpeed() == 0) {
            return null;
        }

        return state.getValue(FACING);
    }

    public boolean allowsTravel(Level level, BlockPos pos, BlockState state, Direction travelDirection) {
        Direction flowDirection = getFlowDirection(level, pos, state);
        return flowDirection == null
                ? canTravelTo(state, travelDirection)
                : flowDirection == travelDirection;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public BlockState updateShape(
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
    public Class<ItemPumpBlockEntity> getBlockEntityClass() {
        return ItemPumpBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ItemPumpBlockEntity> getBlockEntityType() {
        return ModBlockEntities.ITEM_PUMP.get();
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

        return false;
    }
}
