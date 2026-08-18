package com.hwmods.overpressure;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DeviderBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<DeviderBlock> CODEC = simpleCodec(DeviderBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty INPUT = DirectionProperty.create("input");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape X_SHAPE = createModelShape(false);
    private static final Map<Orientation, VoxelShape> ORIENTED_SHAPES = new HashMap<>();

    public DeviderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(FACING, Direction.SOUTH)
                .setValue(INPUT, Direction.DOWN)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction input = state.getValue(INPUT);
        Direction front = getFrontDirection(state);
        return ORIENTED_SHAPES.computeIfAbsent(
                new Orientation(input, front),
                orientation -> rotateShape(X_SHAPE, orientation.input(), orientation.front())
        );
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
        Direction input = context.getClickedFace().getOpposite();
        Direction front = input.getAxis().isVertical()
                ? context.getHorizontalDirection().getOpposite()
                : Direction.UP;
        return defaultBlockState()
                .setValue(INPUT, input)
                .setValue(FACING, front)
                .setValue(AXIS, getLeftOutputDirection(input, front).getAxis())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FACING, INPUT, POWERED);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction input = originalState.getValue(INPUT);
        Direction front = getFrontDirection(originalState);
        Direction rotatedFront = cross(front, input.getOpposite());
        return originalState
                .setValue(FACING, rotatedFront)
                .setValue(AXIS, getLeftOutputDirection(input, rotatedFront).getAxis());
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        return newState;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction input = rotation.rotate(state.getValue(INPUT));
        Direction front = rotation.rotate(getFrontDirection(state));
        return state
                .setValue(INPUT, input)
                .setValue(FACING, front)
                .setValue(AXIS, getLeftOutputDirection(input, front).getAxis());
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction input = mirror.mirror(state.getValue(INPUT));
        Direction front = mirror.mirror(getFrontDirection(state));
        return state
                .setValue(INPUT, input)
                .setValue(FACING, front)
                .setValue(AXIS, getLeftOutputDirection(input, front).getAxis());
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
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
        }

        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviderBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
        }

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

    public static Direction getFrontDirection(BlockState state) {
        Direction input = state.getValue(INPUT);
        Direction front = state.getValue(FACING);
        if (front.getAxis() != input.getAxis()) {
            return front;
        }
        return input.getAxis().isVertical() ? Direction.SOUTH : Direction.UP;
    }

    public static Direction getLeftOutputDirection(BlockState state) {
        return getLeftOutputDirection(state.getValue(INPUT), getFrontDirection(state));
    }

    public static Direction getRightOutputDirection(BlockState state) {
        return getLeftOutputDirection(state).getOpposite();
    }

    private static Direction getLeftOutputDirection(Direction input, Direction front) {
        return cross(front, input.getOpposite());
    }

    private static Direction cross(Direction first, Direction second) {
        int x = first.getStepY() * second.getStepZ() - first.getStepZ() * second.getStepY();
        int y = first.getStepZ() * second.getStepX() - first.getStepX() * second.getStepZ();
        int z = first.getStepX() * second.getStepY() - first.getStepY() * second.getStepX();
        return Direction.getNearest(x, y, z);
    }

    private static VoxelShape rotateShape(VoxelShape source, Direction input, Direction front) {
        Direction main = input.getOpposite();
        Direction right = cross(main, front);
        VoxelShape result = Shapes.empty();

        for (AABB box : source.toAabbs()) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            for (double x : new double[] { box.minX, box.maxX }) {
                for (double y : new double[] { box.minY, box.maxY }) {
                    for (double z : new double[] { box.minZ, box.maxZ }) {
                        Vec3 transformed = new Vec3(0.5, 0.5, 0.5)
                                .add(Vec3.atLowerCornerOf(right.getNormal()).scale(x - 0.5))
                                .add(Vec3.atLowerCornerOf(main.getNormal()).scale(y - 0.5))
                                .add(Vec3.atLowerCornerOf(front.getNormal()).scale(z - 0.5));
                        minX = Math.min(minX, transformed.x);
                        minY = Math.min(minY, transformed.y);
                        minZ = Math.min(minZ, transformed.z);
                        maxX = Math.max(maxX, transformed.x);
                        maxY = Math.max(maxY, transformed.y);
                        maxZ = Math.max(maxZ, transformed.z);
                    }
                }
            }
            result = Shapes.or(result, Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return result.optimize();
    }

    private record Orientation(Direction input, Direction front) {
    }
}
