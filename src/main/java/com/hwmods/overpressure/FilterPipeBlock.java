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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FilterPipeBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<FilterPipeBlock> CODEC = simpleCodec(FilterPipeBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty INPUT = DirectionProperty.create("input");

    private static final VoxelShape DEFAULT_SHAPE = createDefaultShape();
    private static final Map<Orientation, VoxelShape> ORIENTED_SHAPES = new HashMap<>();

    public FilterPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(FACING, Direction.SOUTH)
                .setValue(INPUT, Direction.DOWN));
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
                orientation -> rotateShape(DEFAULT_SHAPE, orientation.input(), orientation.front())
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
                .setValue(AXIS, getBranchDirection(input, front).getAxis());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, FACING, INPUT);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction input = originalState.getValue(INPUT);
        Direction front = getFrontDirection(originalState);
        Direction rotatedFront = cross(front, input.getOpposite());
        return originalState
                .setValue(FACING, rotatedFront)
                .setValue(AXIS, getBranchDirection(input, rotatedFront).getAxis());
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        PneumaticTubeBlockEntity.invalidateTransportTopologyAt(context.getLevel(), context.getClickedPos());
        return newState;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction input = rotation.rotate(state.getValue(INPUT));
        Direction front = rotation.rotate(getFrontDirection(state));
        return state
                .setValue(INPUT, input)
                .setValue(FACING, front)
                .setValue(AXIS, getBranchDirection(input, front).getAxis());
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction input = mirror.mirror(state.getValue(INPUT));
        Direction front = mirror.mirror(getFrontDirection(state));
        return state
                .setValue(INPUT, input)
                .setValue(FACING, front)
                .setValue(AXIS, getBranchDirection(input, front).getAxis());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilterPipeBlockEntity(pos, state);
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof FilterPipeBlockEntity filterPipe) {
                filterPipe.ejectMovingItem(level, Vec3.atCenterOf(pos));
                filterPipe.destroy();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static Direction getFrontDirection(BlockState state) {
        Direction input = state.getValue(INPUT);
        Direction front = state.getValue(FACING);
        if (front.getAxis() != input.getAxis()) {
            return front;
        }
        return input.getAxis().isVertical() ? Direction.SOUTH : Direction.UP;
    }

    public static Direction getStraightOutputDirection(BlockState state) {
        return state.getValue(INPUT).getOpposite();
    }

    public static Direction getBranchDirection(BlockState state) {
        return getBranchDirection(state.getValue(INPUT), getFrontDirection(state));
    }

    private static Direction getBranchDirection(Direction input, Direction front) {
        return cross(front, input.getOpposite());
    }

    private static Direction cross(Direction first, Direction second) {
        int x = first.getStepY() * second.getStepZ() - first.getStepZ() * second.getStepY();
        int y = first.getStepZ() * second.getStepX() - first.getStepX() * second.getStepZ();
        int z = first.getStepX() * second.getStepY() - first.getStepY() * second.getStepX();
        return Direction.getNearest(x, y, z);
    }

    private static VoxelShape createDefaultShape() {
        VoxelShape shape = Shapes.or(
                Block.box(3, 3, 3, 13, 13, 13),
                Block.box(4, 0, 4, 12, 3, 12),
                Block.box(4, 13, 4, 12, 16, 12)
        );

        double angle = Math.toRadians(45.0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (int x = -3; x < 11; x++) {
            for (int y = 2; y < 16; y++) {
                double worldX = x + 0.5;
                double worldY = y + 0.5;
                double localX = 1.6 + (worldX - 1.6) * cos + (worldY - 5.0) * sin;
                double localY = 5.0 - (worldX - 1.6) * sin + (worldY - 5.0) * cos;
                if (localX >= 1.6 && localX <= 9.6 && localY >= 3.0 && localY <= 10.0) {
                    shape = Shapes.or(shape, Block.box(x, y, 4, x + 1, y + 1, 12));
                }
            }
        }
        return shape.optimize();
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
