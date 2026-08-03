package com.hwmods.overpressure;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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

public class DeviderBlock extends BaseEntityBlock {
    public static final MapCodec<DeviderBlock> CODEC = simpleCodec(DeviderBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape X_SHAPE = createModelShape(false);
    private static final VoxelShape Z_SHAPE = createModelShape(true);

    public DeviderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(FACING, Direction.SOUTH)
                .setValue(POWERED, false));
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
        return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
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
        Direction.Axis axis = context.getHorizontalDirection().getClockWise().getAxis();
        return defaultBlockState()
                .setValue(AXIS, axis)
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FACING, POWERED);
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviderBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof DeviderBlockEntity devider) {
            devider.ejectMovingItem(level, net.minecraft.world.phys.Vec3.atCenterOf(pos));
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
                ModBlockEntities.DEVIDER.get(),
                PneumaticTubeBlockEntity::serverTick
        );
    }

    private static VoxelShape createModelShape(boolean rotateToZ) {
        VoxelShape shape = Shapes.or(
                Block.box(3, 3, 3, 13, 13, 13),
                Block.box(4, 0, 4, 12, 3, 12)
        );
        shape = addRotatedBranch(shape, rotateToZ, 6, 14, 3, 10, -45, 14, 5);
        shape = addRotatedBranch(shape, rotateToZ, 2, 10, 3, 10, 45, 2, 5);
        return shape.optimize();
    }

    private static VoxelShape addRotatedBranch(
            VoxelShape shape,
            boolean rotateToZ,
            double fromX,
            double toX,
            double fromY,
            double toY,
            double angleDegrees,
            double originX,
            double originY
    ) {
        double angle = Math.toRadians(angleDegrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        for (int x = -2; x < 18; x++) {
            for (int y = 3; y < 15; y++) {
                double worldX = x + 0.5;
                double worldY = y + 0.5;
                double localX = originX
                        + (worldX - originX) * cos
                        + (worldY - originY) * sin;
                double localY = originY
                        - (worldX - originX) * sin
                        + (worldY - originY) * cos;

                if (localX < fromX || localX > toX || localY < fromY || localY > toY) {
                    continue;
                }

                VoxelShape pixel = rotateToZ
                        ? Block.box(4, y, x, 12, y + 1, x + 1)
                        : Block.box(x, y, 4, x + 1, y + 1, 12);
                shape = Shapes.or(shape, pixel);
            }
        }

        return shape;
    }
}
