package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.decoration.encasing.EncasableBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchableWithBracket;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PneumaticTubeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock, IWrenchableWithBracket, EncasableBlock {
    public static final MapCodec<PneumaticTubeBlock> CODEC = simpleCodec(PneumaticTubeBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty HAS_RIM = BooleanProperty.create("has_rim");
    public static final BooleanProperty HAS_SECOND_RIM = BooleanProperty.create("has_second_rim");
    public static final BooleanProperty HAS_CONNECTION = BooleanProperty.create("has_connection");
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
                .setValue(HAS_SECOND_RIM, false)
                .setValue(HAS_CONNECTION, false)
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
    public Optional<ItemStack> removeBracket(BlockGetter world, BlockPos pos, boolean inOnReplacedContext) {
        BracketedBlockEntityBehaviour behaviour =
                BlockEntityBehaviour.get(world, pos, BracketedBlockEntityBehaviour.TYPE);

        if (behaviour == null) {
            return Optional.empty();
        }

        BlockState bracket = behaviour.removeBracket(inOnReplacedContext);

        if (bracket == null) {
            return Optional.empty();
        }

        return Optional.of(new ItemStack(bracket.getBlock()));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        ItemInteractionResult encasingResult = tryEncase(state, level, pos, stack, player, hand, hit);
        if (encasingResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return encasingResult;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof BracketBlock bracketBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Direction.Axis tubeAxis = getStraightTubeAxis(state);

        if (tubeAxis == null) {
            return ItemInteractionResult.FAIL;
        }

        Direction bracketFace = getBracketFace(tubeAxis, hit.getDirection(), player);

        if (bracketFace == null) {
            return ItemInteractionResult.FAIL;
        }

        BracketedBlockEntityBehaviour behaviour =
                BlockEntityBehaviour.get(level, pos, BracketedBlockEntityBehaviour.TYPE);

        if (behaviour == null || !behaviour.canHaveBracket()) {
            return ItemInteractionResult.FAIL;
        }

        boolean alongFirst = bracketFace.getAxis() != Direction.Axis.Z
                ? tubeAxis == Direction.Axis.Z
                : tubeAxis == Direction.Axis.Y;
        BlockState bracketState = bracketBlock.defaultBlockState()
                .setValue(BracketBlock.TYPE, BracketBlock.BracketType.PIPE)
                .setValue(BracketBlock.FACING, bracketFace)
                .setValue(BracketBlock.AXIS_ALONG_FIRST_COORDINATE, !alongFirst);

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockState previousBracket = behaviour.getBracket();

        if (previousBracket == bracketState) {
            return ItemInteractionResult.SUCCESS;
        }

        level.playSound(
                null,
                pos,
                bracketState.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS,
                0.75f,
                1.0f
        );
        behaviour.applyBracket(bracketState);

        if (!player.isCreative()) {
            stack.shrink(1);

            if (previousBracket != null) {
                player.getInventory().placeItemBackInInventory(new ItemStack(previousBracket.getBlock()));
            }
        }

        return ItemInteractionResult.SUCCESS;
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
        state = state.setValue(HAS_CONNECTION, !connectedDirections.isEmpty());

        if (state.getBlock() instanceof EncasedPneumaticTubeBlock) {
            return configureEncasedEnd(state, connectedDirections);
        }

        return applyPreferredRim(level, pos, state);
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
        newState = newState.setValue(HAS_CONNECTION, !getConnectedDirections(newState).isEmpty());

        if (newState.getBlock() instanceof EncasedPneumaticTubeBlock) {
            return configureEncasedEnd(newState, getConnectedDirections(newState));
        }

        return applyPreferredRim(level, pos, newState);
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
                HAS_SECOND_RIM,
                HAS_CONNECTION,
                RIM
        );
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() instanceof PneumaticTubeBlock && newState.getBlock() instanceof PneumaticTubeBlock) {
            if (!state.is(newState.getBlock())) {
                PneumaticTubeBlockEntity.invalidateClientPathAt(level, pos);
            }
            return;
        }

        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateClientPathAt(level, pos);
        }

        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube) {
            tube.ejectMovingItem(level, net.minecraft.world.phys.Vec3.atCenterOf(pos));
        }

        if (state != newState && !movedByPiston) {
            removeBracket(level, pos, true).ifPresent(stack -> Block.popResource(level, pos, stack));
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static BlockState transferTubeProperties(BlockState from, BlockState to) {
        for (Direction direction : Direction.values()) {
            to = to.setValue(getConnectionProperty(direction), from.getValue(getConnectionProperty(direction)));
        }

        to = to
                .setValue(WATERLOGGED, from.getValue(WATERLOGGED))
                .setValue(HAS_RIM, from.getValue(HAS_RIM))
                .setValue(HAS_SECOND_RIM, from.getValue(HAS_SECOND_RIM))
                .setValue(HAS_CONNECTION, from.getValue(HAS_CONNECTION))
                .setValue(RIM, from.getValue(RIM));

        return to.getBlock() instanceof EncasedPneumaticTubeBlock
                ? configureEncasedEnd(to, getConnectedDirections(to))
                : to;
    }

    private static BlockState configureEncasedEnd(BlockState state, List<Direction> connectedDirections) {
        if (connectedDirections.size() != 1) {
            return state
                    .setValue(HAS_RIM, false)
                    .setValue(HAS_SECOND_RIM, false);
        }

        return state
                .setValue(HAS_RIM, true)
                .setValue(HAS_SECOND_RIM, false)
                .setValue(RIM, connectedDirections.get(0).getOpposite());
    }

    private static List<Direction> getConnectedDirections(BlockState state) {
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

    @Nullable
    private Direction.Axis getStraightTubeAxis(BlockState state) {
        Direction.Axis axis = null;

        for (Direction direction : Direction.values()) {
            if (!state.getValue(getConnectionProperty(direction))) {
                continue;
            }

            if (axis == null) {
                axis = direction.getAxis();
            } else if (axis != direction.getAxis()) {
                return null;
            }
        }

        return axis;
    }

    @Nullable
    private Direction getBracketFace(Direction.Axis tubeAxis, Direction clickedFace, @Nullable Player player) {
        if (clickedFace.getAxis() != tubeAxis) {
            return clickedFace;
        }

        if (player == null) {
            return null;
        }

        for (Direction direction : Direction.orderedByNearest(player)) {
            Direction candidate = direction.getOpposite();

            if (candidate.getAxis() != tubeAxis) {
                return candidate;
            }
        }

        return null;
    }

    private boolean canNeighborAcceptConnection(BlockState neighborState, Direction directionFromNeighbor) {
        if (neighborState.getBlock() instanceof DeviderBlock) {
            return directionFromNeighbor == neighborState.getValue(DeviderBlock.INPUT);
        }

        if (neighborState.getBlock() instanceof CurvaturePneumaticTubeBlock) {
            return neighborState.getValue(getConnectionProperty(directionFromNeighbor));
        }

        if (neighborState.getBlock() instanceof PneumaticTubeBlock) {
            return canAddConnection(neighborState, directionFromNeighbor);
        }

        if (neighborState.getBlock() instanceof PneumaticConnectionBlock) {
            return true;
        }

        if (neighborState.getBlock() instanceof ValveBlock valve) {
            return valve.canTravelTo(neighborState, directionFromNeighbor);
        }

        if (neighborState.getBlock() instanceof ClogSensorBlock sensor) {
            return sensor.canTravelTo(neighborState, directionFromNeighbor);
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

    /**
     * Gives functional tube endpoints priority when choosing the primary rim,
     * while still preserving an opposite rim at a curvature joint.
     * This is also used by planned sections, whose block states are assembled
     * before they are placed in the world.
     */
    BlockState applyPreferredRim(BlockGetter level, BlockPos pos, BlockState state) {
        List<Direction> functionalDirections = new ArrayList<>(2);
        List<Direction> curvatureDirections = new ArrayList<>(2);

        for (Direction direction : getConnectedDirections(state)) {
            if (isConnectedFunctionalDevice(level, pos, state, direction)) {
                functionalDirections.add(direction);
            }
            if (isConnectedCurvature(level, pos, state, direction)) {
                curvatureDirections.add(direction);
            }
        }

        if (!functionalDirections.isEmpty()) {
            Direction primaryRim = functionalDirections.get(0);
            Direction oppositeRim = primaryRim.getOpposite();
            boolean hasOppositeRim = functionalDirections.contains(oppositeRim)
                    || curvatureDirections.contains(oppositeRim);
            return state
                    .setValue(HAS_RIM, true)
                    .setValue(HAS_SECOND_RIM, hasOppositeRim)
                    .setValue(RIM, primaryRim);
        }

        if (!curvatureDirections.isEmpty()) {
            Direction primaryRim = curvatureDirections.get(0);
            return state
                    .setValue(HAS_RIM, true)
                    .setValue(HAS_SECOND_RIM, curvatureDirections.contains(primaryRim.getOpposite()))
                    .setValue(RIM, primaryRim);
        }

        return state
                .setValue(HAS_RIM, false)
                .setValue(HAS_SECOND_RIM, false)
                .setValue(RIM, Direction.NORTH);
    }

    private boolean isConnectedFunctionalDevice(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            Direction direction
    ) {
        if (!state.getValue(getConnectionProperty(direction))) {
            return false;
        }

        Block neighborBlock = level.getBlockState(pos.relative(direction)).getBlock();
        return neighborBlock instanceof PneumaticConnectionBlock
                || neighborBlock instanceof ValveBlock
                || neighborBlock instanceof ClogSensorBlock;
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
