package com.hwmods.boilingpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeBlockItem extends BlockItem {
    private static final Map<UUID, CurveStart> CURVE_STARTS = new HashMap<>();
    private static final int CURVE_SAMPLES_PER_BLOCK = 12;
    private static final int MIN_CURVE_SAMPLES = 24;
    private static final int CURVATURE_CHECK_SAMPLES = 32;
    private static final double MIN_CURVATURE_RADIUS = 0.55;
    private static final double MAX_SECOND_DERIVATIVE = 36.0;

    public PneumaticTubeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();

        if (player == null) {
            return super.useOn(context);
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        if (player.isShiftKeyDown()) {
            CURVE_STARTS.remove(player.getUUID());
            if (canPlaceSingleTubeInWater(clickedState)) {
                return placeSingleTubeInWater(context, player, clickedPos);
            }

            return super.useOn(context);
        }

        if (isFullyConnectedTube(clickedState)) {
            CURVE_STARTS.remove(player.getUUID());

            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(Component.literal("Tube already has both neighbors"), true);
            return InteractionResult.CONSUME;
        }

        if (!canStartCurveFrom(clickedState)) {
            CURVE_STARTS.remove(player.getUUID());
            if (canPlaceSingleTubeInWater(clickedState)) {
                return placeSingleTubeInWater(context, player, clickedPos);
            }

            return super.useOn(context);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        UUID playerId = player.getUUID();
        Direction clickedFace = context.getClickedFace();
        CurveStart start = CURVE_STARTS.remove(playerId);

        if (start == null) {
            CURVE_STARTS.put(playerId, new CurveStart(clickedPos, clickedFace));
            player.displayClientMessage(Component.literal("Curve start selected"), true);
            return InteractionResult.CONSUME;
        }

        CurveEnd end = new CurveEnd(clickedPos, clickedFace);
        boolean built = buildTubeSection(level, player, context.getItemInHand(), start, end);

        if (!built) {
            CURVE_STARTS.put(playerId, new CurveStart(clickedPos, clickedFace));
            player.displayClientMessage(Component.literal("Curve start moved"), true);
        }

        return InteractionResult.CONSUME;
    }

    private boolean canPlaceSingleTubeInWater(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().is(Fluids.WATER);
    }

    private InteractionResult placeSingleTubeInWater(UseOnContext context, Player player, BlockPos pos) {
        Level level = context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(getBlock() instanceof PneumaticTubeBlock tubeBlock)) {
            return InteractionResult.PASS;
        }

        BlockState state = tubeBlock.getTubeStateForPlacement(level, pos)
                .setValue(PneumaticTubeBlock.WATERLOGGED, true);

        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    private boolean buildTubeSection(Level level, Player player, ItemStack stack, CurveStart start, CurveEnd end) {
        BlockPos startPos = start.pos.relative(start.direction);
        BlockPos endPos = end.pos.relative(end.direction);

        if (startPos.equals(endPos)) {
            return false;
        }

        if (!isInSinglePlane(startPos, endPos)) {
            player.displayClientMessage(Component.literal("Tube curve must stay in one fixed X/Y/Z plane"), false);
            return false;
        }

        if (!isStraight(startPos, endPos)) {
            Direction endTravelDirection = end.direction.getOpposite();

            if (getCurvePlaneFixedAxis(startPos, endPos, start.direction, endTravelDirection) == null) {
                player.displayClientMessage(Component.literal("Tube curve endpoints must face inside one plane"), true);
                return false;
            }

            BezierData curve = createFullBezierCurve(startPos, endPos, start.direction, endTravelDirection);

            if (!isCurveSmoothEnough(curve)) {
                player.displayClientMessage(Component.literal("Tube curve is too sharp"), true);
                return false;
            }
        }

        List<PlacedTube> tubes = buildPlacedTubes(start, startPos, end, endPos);

        if (tubes.isEmpty() || !canPlaceAll(level, tubes)) {
            player.displayClientMessage(Component.literal("Tube section is blocked"), true);
            return false;
        }

        if (!player.getAbilities().instabuild && stack.getCount() < tubes.size()) {
            player.displayClientMessage(Component.literal("Not enough pneumatic tubes"), true);
            return false;
        }

        List<BlockPos> curvatureTubePositions = new ArrayList<>();
        UUID curvatureSectionId = UUID.randomUUID();

        for (PlacedTube tube : tubes) {
            BlockState state = tube.state.setValue(
                    PneumaticTubeBlock.WATERLOGGED,
                    level.getFluidState(tube.pos).is(Fluids.WATER)
            );

            level.setBlock(tube.pos, state, Block.UPDATE_ALL);

            if (state.getBlock() instanceof CurvaturePneumaticTubeBlock) {
                applyBezierCurve(level, tube, curvatureSectionId);
                curvatureTubePositions.add(tube.pos);
            }
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(tubes.size());
        }

        if (curvatureTubePositions.isEmpty()) {
            player.displayClientMessage(Component.literal("Tube section built: " + tubes.size() + ", curvature: none"), true);
            BoilingPoint.LOGGER.info("Built straight pneumatic tube section: {} blocks from {} to {}", tubes.size(), startPos, endPos);
        } else {
            player.displayClientMessage(Component.literal(
                    "Tube section built: " + tubes.size() + ", curvature at " + curvatureTubePositions
            ), false);
            BoilingPoint.LOGGER.info(
                    "Built Bezier pneumatic tube section: {} blocks, curvature tubes at {}",
                    tubes.size(),
                    curvatureTubePositions
            );
        }

        return true;
    }

    private List<PlacedTube> buildPlacedTubes(CurveStart start, BlockPos startPos, CurveEnd end, BlockPos endPos) {
        if (isStraight(startPos, endPos)) {
            Direction direction = directionAlongLine(startPos, endPos);

            if (direction == null
                    || start.direction != direction
                    || end.direction.getOpposite() != direction) {
                return List.of();
            }

            return buildStraightTubeLine(startPos, endPos, direction, false);
        }

        Direction firstLegDirection = start.direction;
        Direction secondLegDirection = end.direction.getOpposite();

        Direction.Axis fixedAxis = getCurvePlaneFixedAxis(startPos, endPos, firstLegDirection, secondLegDirection);

        if (fixedAxis == null) {
            return List.of();
        }

        BezierData fullCurve = createFullBezierCurve(startPos, endPos, firstLegDirection, secondLegDirection);
        List<BlockPos> curvePositions = buildRightAnglePathPositions(
                startPos,
                endPos,
                firstLegDirection,
                secondLegDirection
        );

        if (curvePositions.isEmpty()) {
            curvePositions = buildCurvePathPositions(startPos, endPos, fullCurve, fixedAxis);
        }

        if (curvePositions.size() < 2) {
            return List.of();
        }

        List<BezierData> curveSegments = splitBezierByCount(
                fullCurve,
                curvePositions.size()
        );

        List<PlacedTube> tubes = new ArrayList<>();

        for (int i = 0; i < curvePositions.size(); i++) {
            BlockPos current = curvePositions.get(i);
            Direction previousDirection = i == 0
                    ? start.direction.getOpposite()
                    : directionBetween(current, curvePositions.get(i - 1));
            Direction nextDirection = i == curvePositions.size() - 1
                    ? end.direction.getOpposite()
                    : directionBetween(current, curvePositions.get(i + 1));

            if (previousDirection == null || nextDirection == null) {
                return List.of();
            }

            tubes.add(new PlacedTube(
                    current,
                    createTubeState(true, previousDirection, nextDirection),
                    offsetBezier(curveSegments.get(i), Vec3.atLowerCornerOf(current))
            ));
        }

        return tubes;
    }

    private List<PlacedTube> buildStraightTubeLine(
            BlockPos startPos,
            BlockPos endPos,
            Direction direction,
            boolean curvature
    ) {
        if (!startPos.equals(endPos) && directionAlongLine(startPos, endPos) == null) {
            return List.of();
        }

        List<PlacedTube> tubes = new ArrayList<>();
        BlockPos current = startPos;

        while (true) {
            tubes.add(new PlacedTube(current, createTubeState(curvature, direction.getOpposite(), direction), null));

            if (current.equals(endPos)) {
                break;
            }

            current = current.relative(direction);
        }

        return tubes;
    }

    private List<BlockPos> buildRightAnglePathPositions(
            BlockPos startPos,
            BlockPos endPos,
            Direction firstLegDirection,
            Direction secondLegDirection
    ) {
        if (firstLegDirection.getAxis() == secondLegDirection.getAxis()) {
            return List.of();
        }

        if (getDeltaAlongAxis(startPos, endPos, firstLegDirection.getAxis()) * firstLegDirection.getAxisDirection().getStep() < 0
                || getDeltaAlongAxis(startPos, endPos, secondLegDirection.getAxis()) * secondLegDirection.getAxisDirection().getStep() < 0) {
            return List.of();
        }

        BlockPos corner = setCoordinate(startPos, firstLegDirection.getAxis(), getCoordinate(endPos, firstLegDirection.getAxis()));
        List<BlockPos> positions = new ArrayList<>();
        BlockPos current = startPos;

        positions.add(current);

        while (!current.equals(corner)) {
            current = current.relative(firstLegDirection);
            positions.add(current);
        }

        while (!current.equals(endPos)) {
            current = current.relative(secondLegDirection);
            positions.add(current);
        }

        return positions;
    }

    private int getDeltaAlongAxis(BlockPos startPos, BlockPos endPos, Direction.Axis axis) {
        return getCoordinate(endPos, axis) - getCoordinate(startPos, axis);
    }

    private BlockPos setCoordinate(BlockPos pos, Direction.Axis axis, int value) {
        return switch (axis) {
            case X -> new BlockPos(value, pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), value, pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), value);
        };
    }

    private BlockState createTubeState(boolean curvature, Direction first, Direction second) {
        BlockState state = (curvature ? ModBlocks.CURVATURE_PNEUMATIC_TUBE.get() : ModBlocks.PNEUMATIC_TUBE.get())
                .defaultBlockState()
                .setValue(PneumaticTubeBlock.WATERLOGGED, false)
                .setValue(PneumaticTubeBlock.HAS_RIM, false)
                .setValue(PneumaticTubeBlock.RIM, Direction.NORTH);

        for (Direction direction : Direction.values()) {
            state = state.setValue(PneumaticTubeBlock.getConnectionProperty(direction), false);
        }

        return state
                .setValue(PneumaticTubeBlock.getConnectionProperty(first), true)
                .setValue(PneumaticTubeBlock.getConnectionProperty(second), true);
    }

    private List<BlockPos> buildCurvePathPositions(BlockPos startPos, BlockPos endPos, BezierData renderCurve, Direction.Axis fixedAxis) {
        List<BlockPos> positions = new ArrayList<>();
        BezierData centerCurve = centerCurveFromRenderCurve(renderCurve);
        int samples = Math.max(MIN_CURVE_SAMPLES, (int) Math.ceil(renderCurve.p0.distanceTo(renderCurve.p3) * CURVE_SAMPLES_PER_BLOCK));

        positions.add(startPos);

        for (int i = 1; i < samples; i++) {
            Vec3 point = getBezierPoint(centerCurve, (double) i / samples);
            BlockPos sampled = BlockPos.containing(point.x, point.y, point.z);
            appendAdjacentPath(positions, clampToPlane(sampled, startPos, fixedAxis), fixedAxis);
        }

        appendAdjacentPath(positions, endPos, fixedAxis);
        return positions;
    }

    private void appendAdjacentPath(List<BlockPos> positions, BlockPos target, Direction.Axis fixedAxis) {
        BlockPos current = positions.get(positions.size() - 1);

        while (!current.equals(target)) {
            Direction step = stepToward(current, target, fixedAxis);

            if (step == null) {
                return;
            }

            current = current.relative(step);

            if (!current.equals(positions.get(positions.size() - 1))) {
                positions.add(current);
            }
        }
    }

    private Direction stepToward(BlockPos current, BlockPos target, Direction.Axis fixedAxis) {
        int dx = target.getX() - current.getX();
        int dy = target.getY() - current.getY();
        int dz = target.getZ() - current.getZ();

        if (fixedAxis != Direction.Axis.X && dx != 0
                && (fixedAxis == Direction.Axis.Y || Math.abs(dx) >= Math.abs(dy))
                && (fixedAxis == Direction.Axis.Z || Math.abs(dx) >= Math.abs(dz))) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }

        if (fixedAxis != Direction.Axis.Y && dy != 0
                && (fixedAxis == Direction.Axis.X || Math.abs(dy) >= Math.abs(dx))
                && (fixedAxis == Direction.Axis.Z || Math.abs(dy) >= Math.abs(dz))) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }

        if (fixedAxis != Direction.Axis.Z && dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        if (fixedAxis != Direction.Axis.X && dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }

        if (fixedAxis != Direction.Axis.Y && dy != 0) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }

        return null;
    }

    private BlockPos clampToPlane(BlockPos pos, BlockPos planeOrigin, Direction.Axis fixedAxis) {
        return switch (fixedAxis) {
            case X -> new BlockPos(planeOrigin.getX(), pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), planeOrigin.getY(), pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), planeOrigin.getZ());
        };
    }

    private BezierData createFullBezierCurve(
            BlockPos startPos,
            BlockPos endPos,
            Direction incomingDirection,
            Direction outgoingDirection
    ) {
        Vec3 incoming = Vec3.atLowerCornerOf(incomingDirection.getNormal());
        Vec3 outgoing = Vec3.atLowerCornerOf(outgoingDirection.getNormal());
        Vec3 startCenter = Vec3.atCenterOf(startPos);
        Vec3 endCenter = Vec3.atCenterOf(endPos);

        Vec3 p0 = startCenter.subtract(incoming.scale(0.5));
        Vec3 p3 = endCenter.add(outgoing.scale(0.5));
        double handleLength = Math.max(1.5, p0.distanceTo(p3) * 0.45);

        Vec3 p1 = p0.add(incoming.scale(handleLength));
        Vec3 p2 = p3.subtract(outgoing.scale(handleLength));

        return new BezierData(p0, p1, p2, p3);
    }

    private List<BezierData> splitBezierByCount(BezierData curve, int count) {
        List<BezierData> segments = new ArrayList<>();
        BezierData remaining = curve;

        for (int i = 0; i < count - 1; i++) {
            double t = 1.0 / (count - i);
            BezierSplit split = splitBezier(remaining, t);
            segments.add(split.left);
            remaining = split.right;
        }

        segments.add(remaining);
        return segments;
    }

    private BezierSplit splitBezier(BezierData curve, double t) {
        Vec3 p01 = lerp(curve.p0, curve.p1, t);
        Vec3 p12 = lerp(curve.p1, curve.p2, t);
        Vec3 p23 = lerp(curve.p2, curve.p3, t);
        Vec3 p012 = lerp(p01, p12, t);
        Vec3 p123 = lerp(p12, p23, t);
        Vec3 p0123 = lerp(p012, p123, t);

        return new BezierSplit(
                new BezierData(curve.p0, p01, p012, p0123),
                new BezierData(p0123, p123, p23, curve.p3)
        );
    }

    private BezierData offsetBezier(BezierData curve, Vec3 offset) {
        return new BezierData(
                curve.p0.subtract(offset),
                curve.p1.subtract(offset),
                curve.p2.subtract(offset),
                curve.p3.subtract(offset)
        );
    }

    private Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.scale(1.0 - t).add(b.scale(t));
    }

    private BezierData centerCurveFromRenderCurve(BezierData curve) {
        Vec3 startTangent = curve.p1.subtract(curve.p0);
        Vec3 endTangent = curve.p3.subtract(curve.p2);

        if (startTangent.lengthSqr() > 1.0E-6) {
            startTangent = startTangent.normalize().scale(0.5);
        }

        if (endTangent.lengthSqr() > 1.0E-6) {
            endTangent = endTangent.normalize().scale(0.5);
        }

        Vec3 p0 = curve.p0.add(startTangent);
        Vec3 p3 = curve.p3.subtract(endTangent);
        Vec3 p1 = curve.p1.add(startTangent);
        Vec3 p2 = curve.p2.subtract(endTangent);

        return new BezierData(p0, p1, p2, p3);
    }

    private Vec3 getBezierPoint(BezierData curve, double t) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;

        return curve.p0.scale(uu * u)
                .add(curve.p1.scale(3.0 * uu * t))
                .add(curve.p2.scale(3.0 * u * tt))
                .add(curve.p3.scale(tt * t));
    }

    private Vec3 getBezierDerivative(BezierData curve, double t) {
        double u = 1.0 - t;

        return curve.p1.subtract(curve.p0).scale(3.0 * u * u)
                .add(curve.p2.subtract(curve.p1).scale(6.0 * u * t))
                .add(curve.p3.subtract(curve.p2).scale(3.0 * t * t));
    }

    private Vec3 getBezierSecondDerivative(BezierData curve, double t) {
        double u = 1.0 - t;

        return curve.p2.subtract(curve.p1.scale(2.0)).add(curve.p0).scale(6.0 * u)
                .add(curve.p3.subtract(curve.p2.scale(2.0)).add(curve.p1).scale(6.0 * t));
    }

    private boolean isCurveSmoothEnough(BezierData curve) {
        for (int i = 0; i <= CURVATURE_CHECK_SAMPLES; i++) {
            double t = (double) i / CURVATURE_CHECK_SAMPLES;
            Vec3 derivative = getBezierDerivative(curve, t);
            Vec3 secondDerivative = getBezierSecondDerivative(curve, t);
            double speedSquared = derivative.lengthSqr();

            if (secondDerivative.length() > MAX_SECOND_DERIVATIVE) {
                return false;
            }

            if (speedSquared <= 1.0E-8) {
                return false;
            }

            double curvature = derivative.cross(secondDerivative).length() / Math.pow(speedSquared, 1.5);

            if (curvature > 0.0 && 1.0 / curvature < MIN_CURVATURE_RADIUS) {
                return false;
            }
        }

        return true;
    }

    private void applyBezierCurve(Level level, PlacedTube tube, UUID sectionId) {
        if (tube.bezier == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(tube.pos);

        if (blockEntity instanceof CurvaturePneumaticTubeEntity curvatureTube) {
            curvatureTube.setSectionId(sectionId);
            curvatureTube.setCurve(tube.bezier.p0, tube.bezier.p1, tube.bezier.p2, tube.bezier.p3);
            BoilingPoint.LOGGER.info(
                    "Placed Bezier curvature tube at {} in section {} with p0={}, p1={}, p2={}, p3={}",
                    tube.pos,
                    sectionId,
                    tube.bezier.p0,
                    tube.bezier.p1,
                    tube.bezier.p2,
                    tube.bezier.p3
            );
        }
    }

    private boolean canPlaceAll(Level level, List<PlacedTube> tubes) {
        for (PlacedTube tube : tubes) {
            BlockState state = level.getBlockState(tube.pos);

            if (!state.isAir() && !level.getFluidState(tube.pos).is(Fluids.WATER)) {
                return false;
            }
        }

        return true;
    }

    private boolean canStartCurveFrom(BlockState state) {
        if (state.getBlock() instanceof PneumaticConnectionBlock) {
            return true;
        }

        if (!(state.getBlock() instanceof PneumaticTubeBlock)) {
            return false;
        }

        return countTubeConnections(state) < 2;
    }

    private boolean isFullyConnectedTube(BlockState state) {
        return state.getBlock() instanceof PneumaticTubeBlock && countTubeConnections(state) >= 2;
    }

    private int countTubeConnections(BlockState state) {
        int connections = 0;

        for (Direction direction : Direction.values()) {
            if (state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) {
                connections++;
            }
        }

        return connections;
    }

    private boolean isStraight(BlockPos startPos, BlockPos endPos) {
        return startPos.getX() == endPos.getX() && startPos.getY() == endPos.getY()
                || startPos.getX() == endPos.getX() && startPos.getZ() == endPos.getZ()
                || startPos.getY() == endPos.getY() && startPos.getZ() == endPos.getZ();
    }

    private boolean isInSinglePlane(BlockPos startPos, BlockPos endPos) {
        return startPos.getX() == endPos.getX()
                || startPos.getY() == endPos.getY()
                || startPos.getZ() == endPos.getZ();
    }

    private Direction.Axis getCurvePlaneFixedAxis(
            BlockPos startPos,
            BlockPos endPos,
            Direction startDirection,
            Direction endTravelDirection
    ) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (getCoordinate(startPos, axis) == getCoordinate(endPos, axis)
                    && startDirection.getAxis() != axis
                    && endTravelDirection.getAxis() != axis) {
                return axis;
            }
        }

        return null;
    }

    private int getCoordinate(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private Direction directionAlongLine(BlockPos startPos, BlockPos endPos) {
        int dx = Integer.compare(endPos.getX(), startPos.getX());
        int dy = Integer.compare(endPos.getY(), startPos.getY());
        int dz = Integer.compare(endPos.getZ(), startPos.getZ());

        if (dx != 0 && dy == 0 && dz == 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (dx == 0 && dy != 0 && dz == 0) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        if (dx == 0 && dy == 0 && dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        return null;
    }

    private Direction directionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }

        return null;
    }

    private record CurveStart(BlockPos pos, Direction direction) {
    }

    private record CurveEnd(BlockPos pos, Direction direction) {
    }

    private record BezierData(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
    }

    private record BezierSplit(BezierData left, BezierData right) {
    }

    private record PlacedTube(BlockPos pos, BlockState state, BezierData bezier) {
    }
}
