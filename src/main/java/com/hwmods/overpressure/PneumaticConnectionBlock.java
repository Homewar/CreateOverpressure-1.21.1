package com.hwmods.overpressure;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class PneumaticConnectionBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<PneumaticConnectionBlock> CODEC = simpleCodec(PneumaticConnectionBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty ARROW_FACING =
            DirectionProperty.create("arrow_facing", Direction.Plane.HORIZONTAL);
    private static final VoxelShape CORE_SHAPE = Block.box(3, 3, 3, 13, 13, 13);
    private static final VoxelShape PIPE_X_SHAPE = Block.box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape PIPE_Y_SHAPE = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape PIPE_Z_SHAPE = Block.box(4, 4, 0, 12, 12, 16);
    private static final VoxelShape X_SHAPE = Shapes.or(CORE_SHAPE, PIPE_X_SHAPE);
    private static final VoxelShape Y_SHAPE = Shapes.or(CORE_SHAPE, PIPE_Y_SHAPE);
    private static final VoxelShape Z_SHAPE = Shapes.or(CORE_SHAPE, PIPE_Z_SHAPE);
    private static final VoxelShape X_OUTLINE_SHAPE = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape Y_OUTLINE_SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape Z_OUTLINE_SHAPE = Block.box(3, 3, 0, 13, 13, 16);
    public enum ConnectionMode implements StringRepresentable {
        EXTRACT("extract"),
        INSERT("insert"),
        DISABLED("disabled");

        private final String name;
        ConnectionMode(String name) {
            this.name = name;
        }
        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<ConnectionMode> MODE =
        EnumProperty.create("mode", ConnectionMode.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PneumaticConnectionBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(ARROW_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(MODE, ConnectionMode.DISABLED)
                .setValue(POWERED, false));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PneumaticConnectionBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return getOutlineShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return getModelShape(state);
    }

    private VoxelShape getModelShape(BlockState state) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    private VoxelShape getOutlineShape(BlockState state) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> X_OUTLINE_SHAPE;
            case Y -> Y_OUTLINE_SHAPE;
            case Z -> Z_OUTLINE_SHAPE;
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level,
            BlockState state, 
            BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(
                type,
                ModBlockEntities.PNEUMATIC_CONNECTION.get(),
                PneumaticConnectionBlockEntity::serverTick
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        Direction arrowFacing = context.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(ARROW_FACING, arrowFacing)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
        return state.setValue(MODE, getModeFromNeighbors(context.getLevel(), context.getClickedPos(), state));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        builder.add(ARROW_FACING);
        builder.add(MODE);
        builder.add(POWERED);
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
        return state
                .setValue(MODE, getModeFromNeighbors(level, pos, state))
                .setValue(POWERED, level instanceof Level realLevel && realLevel.hasNeighborSignal(pos));
    }

    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }

        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    public static Direction getFilterSlotFace(BlockState state) {
        Direction facing = state.getValue(FACING);

        if (facing.getAxis().isVertical()) {
            return state.getValue(ARROW_FACING);
        }

        return Direction.UP;
    }

    @Nullable
    public Direction getTubeDirection(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);

        return switch (state.getValue(MODE)) {
            case EXTRACT -> facing;
            case INSERT -> facing.getOpposite();
            case DISABLED -> {
                if (hasInventory(level, pos, facing.getOpposite())) {
                    yield facing;
                }
                if (hasInventory(level, pos, facing)) {
                    yield facing.getOpposite();
                }
                yield null;
            }
        };
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction facing = originalState.getValue(FACING).getOpposite();
        Direction arrowFacing = originalState.getValue(ARROW_FACING);

        if (facing.getAxis().isHorizontal()) {
            arrowFacing = facing;
        }

        return originalState
                .setValue(FACING, facing)
                .setValue(ARROW_FACING, arrowFacing);
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        return newState.setValue(MODE, getModeFromNeighbors(context.getLevel(), context.getClickedPos(), newState));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity instanceof PneumaticConnectionBlockEntity connector) {
                connector.destroy();
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private ConnectionMode getModeFromNeighbors(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction front = state.getValue(FACING);
        Direction back = front.getOpposite();

        boolean frontHasTube = hasTube(level, pos, front);
        boolean backHasTube = hasTube(level, pos, back);
        boolean frontHasInventory = hasInventory(level, pos, front);
        boolean backHasInventory = hasInventory(level, pos, back);

        ConnectionMode mode = ConnectionMode.DISABLED;

        if (backHasTube && frontHasInventory) {
            mode = ConnectionMode.INSERT;
        } else if (frontHasTube && backHasInventory) {
            mode = ConnectionMode.EXTRACT;
        }

        return mode;
    }

    private boolean hasTube(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos.relative(direction));
        if (state.getBlock() instanceof PneumaticTubeBlock) {
            return true;
        }

        return (state.getBlock() instanceof ItemPumpBlock pump
                && pump.canTravelTo(state, direction.getOpposite()))
                || (state.getBlock() instanceof ValveBlock valve
                && valve.canTravelTo(state, direction.getOpposite()))
                || (state.getBlock() instanceof ClogSensorBlock sensor
                && sensor.canTravelTo(state, direction.getOpposite()));
    }

    private boolean hasInventory(LevelAccessor level, BlockPos pos, Direction direction) {
        if (!(level instanceof Level realLevel)) {
            return false;
        }

        return realLevel.getCapability(
                Capabilities.ItemHandler.BLOCK,
                pos.relative(direction),
                direction.getOpposite()
        ) != null;
    }
}
