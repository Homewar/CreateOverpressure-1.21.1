package com.hwmods.boilingpoint;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PneumaticTubeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<PneumaticTubeBlock> CODEC = simpleCodec(PneumaticTubeBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty HAS_RIM = BooleanProperty.create("has_rim");
    public static final DirectionProperty RIM = DirectionProperty.create("rim");
    private static final VoxelShape CORE_SHAPE = Block.box(4, 4, 4, 12, 12, 12);
    private static final VoxelShape NORTH_SHAPE = Block.box(4, 4, 0, 12, 12, 4);
    private static final VoxelShape SOUTH_SHAPE = Block.box(4, 4, 12, 12, 12, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(12, 4, 4, 16, 12, 12);
    private static final VoxelShape WEST_SHAPE = Block.box(0, 4, 4, 4, 12, 12);
    private static final VoxelShape UP_SHAPE = Block.box(4, 12, 4, 12, 16, 12);
    private static final VoxelShape DOWN_SHAPE = Block.box(4, 0, 4, 12, 4, 12);

    public PneumaticTubeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(createDefaultState());
    }

    protected BlockState createDefaultState() {
        return this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(WATERLOGGED, false)
                .setValue(HAS_RIM, false)
                .setValue(RIM, Direction.NORTH);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PneumaticTubeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE_SHAPE;

        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, NORTH_SHAPE);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, SOUTH_SHAPE);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, EAST_SHAPE);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, WEST_SHAPE);
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, UP_SHAPE);
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, DOWN_SHAPE);
        }

        return shape;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getTubeStateForPlacement(context.getLevel(), context.getClickedPos());
    }

    public BlockState getTubeStateForPlacement(Level level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        BlockState state = defaultBlockState().setValue(WATERLOGGED, fluidState.is(Fluids.WATER));

        for (Direction direction : Direction.values()) {
            BlockState neighborState = level.getBlockState(pos.relative(direction));
            boolean canConnect = canAddConnection(state, direction)
                    && canNeighborAcceptConnection(neighborState, direction.getOpposite());
            state = state.setValue(getConnectionProperty(direction), canConnect);
        }

        List<Direction> connectedDirections = getConnectedDirections(state);

        if (shouldAddRandomRim(pos) && !connectedDirections.isEmpty()) {
            state = state
                    .setValue(HAS_RIM, true)
                    .setValue(RIM, connectedDirections.get(level.random.nextInt(connectedDirections.size())));
        }

        return avoidCurvatureRim(level, pos, state);
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

        boolean canConnect = canAddConnection(state, direction)
                && canNeighborAcceptConnection(neighborState, direction.getOpposite());
        BlockState newState = state.setValue(getConnectionProperty(direction), canConnect);

        if (newState.getValue(HAS_RIM) && !newState.getValue(getConnectionProperty(newState.getValue(RIM)))) {
            List<Direction> connectedDirections = getConnectedDirections(newState);

            if (connectedDirections.isEmpty()) {
                return newState.setValue(HAS_RIM, false).setValue(RIM, Direction.NORTH);
            }

            return newState.setValue(RIM, connectedDirections.get(0));
        }

        return avoidCurvatureRim(level, pos, newState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                NORTH,
                SOUTH,
                EAST,
                WEST,
                UP,
                DOWN,
                WATERLOGGED,
                HAS_RIM,
                RIM
        );
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private boolean shouldAddRandomRim(BlockPos pos) {
        return Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), 3) == 0;
    }

    private List<Direction> getConnectedDirections(BlockState state) {
        List<Direction> directions = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (state.getValue(getConnectionProperty(direction))) {
                directions.add(direction);
            }
        }

        return directions;
    }

    private boolean canAddConnection(BlockState state, Direction direction) {
        List<Direction> connectedDirections = getConnectedDirections(state);

        if (state.getValue(getConnectionProperty(direction))) {
            return true;
        }

        if (connectedDirections.isEmpty()) {
            return true;
        }

        if (connectedDirections.size() == 1) {
            return connectedDirections.get(0) == direction.getOpposite();
        }

        return false;
    }

    private boolean canNeighborAcceptConnection(BlockState neighborState, Direction directionFromNeighbor) {
        if (neighborState.getBlock() instanceof CurvaturePneumaticTubeBlock) {
            return neighborState.getValue(getConnectionProperty(directionFromNeighbor));
        }

        if (neighborState.getBlock() instanceof PneumaticTubeBlock) {
            return canAddConnection(neighborState, directionFromNeighbor);
        }

        if (neighborState.getBlock() instanceof PneumaticConnectionBlock) {
            return true;
        }

        return neighborState.getBlock() instanceof ItemPumpBlock pump
                && pump.canTravelTo(neighborState, directionFromNeighbor);
    }

    public static BooleanProperty getConnectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    private BlockState avoidCurvatureRim(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.getValue(HAS_RIM)) {
            return state;
        }

        Direction rim = state.getValue(RIM);

        if (!isConnectedCurvature(level, pos, state, rim)) {
            return state;
        }

        for (Direction direction : getConnectedDirections(state)) {
            if (!isConnectedCurvature(level, pos, state, direction)) {
                return state.setValue(RIM, direction);
            }
        }

        return state.setValue(HAS_RIM, false).setValue(RIM, Direction.NORTH);
    }

    private boolean isConnectedCurvature(
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            BlockState state,
            Direction direction
    ) {
        if (!state.getValue(getConnectionProperty(direction))) {
            return false;
        }

        BlockState neighborState = level.getBlockState(pos.relative(direction));
        return neighborState.getBlock() instanceof CurvaturePneumaticTubeBlock
                && neighborState.getValue(getConnectionProperty(direction.getOpposite()));
    }

    @Override
    @Nullable
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
            ModBlockEntities.PNEUMATIC_TUBE.get(),
            PneumaticTubeBlockEntity::serverTick
        );
    }

}
