package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hwmods.overpressure.math.CubicBezier;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeBlockItem extends BlockItem {
    private static final Map<UUID, CurveStart> CURVE_STARTS = new HashMap<>();
    // Kept separate from the server state so route previews also work on dedicated servers.
    private static final Map<UUID, CurveStart> CLIENT_CURVE_STARTS = new HashMap<>();
    private static final int CURVE_SAMPLES_PER_BLOCK = 12;
    private static final int MIN_CURVE_SAMPLES = 24;
    private static final int CURVATURE_CHECK_SAMPLES = 32;
    private static final int CURVE_SEGMENT_TURN_CHECK_SAMPLES = 16;
    private static final double MIN_CURVATURE_RADIUS = 0.55;
    private static final double MAX_SECOND_DERIVATIVE = 36.0;
    private static final double MAX_CURVE_SEGMENT_TURN_DEGREES = 60.0;
    private static final String CURVE_SEGMENT_TOO_SHARP_MESSAGE =
            "Tube curve cannot turn more than 60 degrees inside one block";
    private final int placementHelperId;

    public PneumaticTubeBlockItem(Block block, Properties properties) {
        super(block, properties);
        placementHelperId = PlacementHelpers.register(new StraightTubePlacementHelper());
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();

        if (player == null || player.isShiftKeyDown()) {
            return super.onItemUseFirst(stack, context);
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        IPlacementHelper helper = PlacementHelpers.get(placementHelperId);

        if (!helper.matchesState(state)) {
            return super.onItemUseFirst(stack, context);
        }

        BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(),
                context.getClickedFace(),
                pos,
                true
        );
        PlacementOffset offset = helper.getOffset(player, level, state, pos, hit);

        if (!offset.isSuccessful()) {
            return super.onItemUseFirst(stack, context);
        }

        clearCurveStarts(player.getUUID());
        return offset.placeInWorld(level, this, player, context.getHand(), hit).result();
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
        Direction clickedFace = context.getClickedFace();

        if (player.isShiftKeyDown()) {
            clearCurveStarts(player.getUUID());
            if (canPlaceSingleTubeInWater(clickedState)) {
                return placeSingleTubeInWater(context, player, clickedPos);
            }

            return super.useOn(context);
        }

        CurveStart deviderStart = getDeviderCurveStart(
                clickedPos,
                clickedState,
                clickedFace,
                context.getClickLocation()
        );
        if (deviderStart != null) {
            return selectOrConnectDevider(level, player, context.getItemInHand(), deviderStart);
        }

        // Прямой участок трубы (две соединённые стороны на одной оси) не может быть
        // точкой начала построения пути — только вход/выход (торцы) и концы кривых
        // участков могут им быть.
        if (isStraightSection(clickedState)) {
            clearCurveStarts(player.getUUID());
            return super.useOn(context);
        }

        if (isFullyConnectedTube(clickedState) && !isCurvatureEndpoint(clickedState)) {
            clearCurveStarts(player.getUUID());

            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(Component.literal("Tube already has both neighbors"), true);
            return InteractionResult.CONSUME;
        }

        if (!canStartCurveFrom(level, clickedPos, clickedState, clickedFace)) {
            clearCurveStarts(player.getUUID());
            if (canPlaceSingleTubeInWater(clickedState)) {
                return placeSingleTubeInWater(context, player, clickedPos);
            }

            return super.useOn(context);
        }

        UUID playerId = player.getUUID();

        if (level.isClientSide) {
            CurveStart clientStart = CLIENT_CURVE_STARTS.get(playerId);
            if (clientStart == null) {
                CLIENT_CURVE_STARTS.put(playerId, new CurveStart(clickedPos, clickedFace));
            } else if (clientStart.pos.equals(clickedPos) && clientStart.direction == clickedFace) {
                CLIENT_CURVE_STARTS.remove(playerId);
            } else if (planSection(level, clientStart, new CurveEnd(clickedPos, clickedFace)).valid()) {
                CLIENT_CURVE_STARTS.remove(playerId);
            }
            return InteractionResult.SUCCESS;
        }

        CurveStart start = CURVE_STARTS.remove(playerId);

        if (start != null && !isCurveStartValid(level, start)) {
            start = null;
        }

        if (start == null) {
            CURVE_STARTS.put(playerId, new CurveStart(clickedPos, clickedFace));
            return InteractionResult.CONSUME;
        }

        if (start.pos.equals(clickedPos) && start.direction == clickedFace) {
            return InteractionResult.CONSUME;
        }

        CurveEnd end = new CurveEnd(clickedPos, clickedFace);
        boolean built = buildTubeSection(level, player, context.getItemInHand(), start, end);

        if (!built) {
            CURVE_STARTS.put(playerId, start);
        }

        return InteractionResult.CONSUME;
    }

    private InteractionResult selectOrConnectDevider(
            Level level,
            Player player,
            ItemStack stack,
            CurveStart selected
    ) {
        UUID playerId = player.getUUID();
        Map<UUID, CurveStart> starts = level.isClientSide ? CLIENT_CURVE_STARTS : CURVE_STARTS;
        CurveStart current = starts.get(playerId);

        if (current != null && !isCurveStartValid(level, current)) {
            starts.remove(playerId);
            current = null;
        }

        if (current != null && !current.isDeviderPort()) {
            CurveEnd originalStartAsEnd = new CurveEnd(current.pos, current.direction);

            if (level.isClientSide) {
                if (planSection(level, selected, originalStartAsEnd).valid()) {
                    starts.remove(playerId);
                }
                return InteractionResult.SUCCESS;
            }

            starts.remove(playerId);
            if (!buildTubeSection(level, player, stack, selected, originalStartAsEnd)) {
                starts.put(playerId, current);
            }
            return InteractionResult.CONSUME;
        }

        if (selected.equals(current)) {
            starts.remove(playerId);
        } else {
            starts.put(playerId, selected);
        }

        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Nullable
    private CurveStart getDeviderCurveStart(
            BlockPos pos,
            BlockState state,
            Direction clickedFace,
            Vec3 clickLocation
    ) {
        if (!(state.getBlock() instanceof DeviderBlock)) {
            return null;
        }

        Vec3 localClick = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        Direction input = state.getValue(DeviderBlock.INPUT);
        Direction main = input.getOpposite();
        Direction left = DeviderBlock.getLeftOutputDirection(state);
        Direction right = left.getOpposite();
        Vec3 inputPoint = new Vec3(0.5, 0.5, 0.5)
                .add(Vec3.atLowerCornerOf(input.getNormal()).scale(0.5));
        Vec3 leftPoint = DeviderBlockEntity.getLocalOutputPoint(left, input);
        Vec3 rightPoint = DeviderBlockEntity.getLocalOutputPoint(right, input);

        double inputDistance = localClick.distanceToSqr(inputPoint);
        double leftDistance = localClick.distanceToSqr(leftPoint);
        double rightDistance = localClick.distanceToSqr(rightPoint);
        if (inputDistance <= leftDistance && inputDistance <= rightDistance) {
            return new CurveStart(pos, input, input);
        }

        Direction side = leftDistance <= rightDistance ? left : right;
        return new CurveStart(pos, main, side);
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
        BlockPos startPos = getStartTubePos(start);
        BlockPos endPos = end.pos.relative(end.direction);

        if (startPos.equals(endPos)) {
            return false;
        }

        if (!isInSinglePlane(startPos, endPos)) {
            player.displayClientMessage(Component.literal("Tube curve must stay in one fixed X/Y/Z plane"), false);
            return false;
        }

        if (start.isDeviderOutput() || !isStraight(startPos, endPos)) {
            Direction endTravelDirection = end.direction.getOpposite();

            if (getCurvePlaneFixedAxis(start, startPos, endPos, endTravelDirection) == null) {
                player.displayClientMessage(Component.literal("Tube curve endpoints must face inside one plane"), true);
                return false;
            }

            CubicBezier curve = createFullBezierCurve(start, startPos, endPos, endTravelDirection);

            if (!start.isDeviderOutput() && !isCurveSmoothEnough(curve)) {
                player.displayClientMessage(Component.literal("Tube curve is too sharp"), true);
                return false;
            }
        }

        List<PlacedTube> tubes = buildPlacedTubes(start, startPos, end, endPos);

        if (tubes.isEmpty()) {
            player.displayClientMessage(Component.literal("Tube section is blocked"), true);
            return false;
        }

        if (!areCurveSegmentsWithinTurnLimit(tubes)) {
            player.displayClientMessage(Component.literal(CURVE_SEGMENT_TOO_SHARP_MESSAGE), true);
            return false;
        }

        if (!canPlaceAll(level, tubes)) {
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
            BlockState state = tube.state;

            if (state.hasProperty(PneumaticTubeBlock.WATERLOGGED)) {
                state = state.setValue(
                        PneumaticTubeBlock.WATERLOGGED,
                        level.getFluidState(tube.pos).is(Fluids.WATER)
                );
            }

            if (state.getBlock() instanceof PneumaticTubeBlock tubeBlock) {
                state = tubeBlock.applyPreferredRim(level, tube.pos, state);
            }

            level.setBlock(tube.pos, state, Block.UPDATE_ALL);

            if (state.getBlock() instanceof CurvaturePneumaticTubeBlock) {
                applyBezierCurve(level, tube, curvatureSectionId);
                curvatureTubePositions.add(tube.pos);
            }
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(tubes.size());
        }

        return true;
    }

    private List<PlacedTube> buildPlacedTubes(CurveStart start, BlockPos startPos, CurveEnd end, BlockPos endPos) {
        if (!start.isDeviderOutput() && isStraight(startPos, endPos)) {
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

        Direction.Axis fixedAxis = getCurvePlaneFixedAxis(start, startPos, endPos, secondLegDirection);

        if (fixedAxis == null) {
            return List.of();
        }

        CubicBezier fullCurve = createFullBezierCurve(start, startPos, endPos, secondLegDirection);
        List<BlockPos> curvePositions = start.isDeviderOutput()
                ? List.of()
                : buildRightAnglePathPositions(startPos, endPos, firstLegDirection, secondLegDirection);

        if (curvePositions.isEmpty()) {
            curvePositions = buildCurvePathPositions(startPos, endPos, fullCurve, fixedAxis);
        }

        if (curvePositions.size() < 2) {
            return List.of();
        }

        Set<BlockPos> uniquePositions = new HashSet<>(curvePositions);

        if (uniquePositions.size() != curvePositions.size()) {
            return List.of();
        }

        List<CubicBezier> curveSegments = fullCurve.splitEqually(curvePositions.size());

        List<PlacedTube> tubes = new ArrayList<>();

        for (int i = 0; i < curvePositions.size(); i++) {
            BlockPos current = curvePositions.get(i);
            Direction previousDirection = i == 0
                    ? (start.isDeviderOutput() ? null : start.direction.getOpposite())
                    : directionBetween(current, curvePositions.get(i - 1));
            Direction nextDirection = i == curvePositions.size() - 1
                    ? end.direction.getOpposite()
                    : directionBetween(current, curvePositions.get(i + 1));

            if ((previousDirection == null && (!start.isDeviderOutput() || i != 0)) || nextDirection == null) {
                return List.of();
            }

            tubes.add(new PlacedTube(
                    current,
                    previousDirection == null
                            ? createTubeState(true, nextDirection)
                            : createTubeState(true, previousDirection, nextDirection),
                    curveSegments.get(i).offset(Vec3.atLowerCornerOf(current))
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

    private BlockState createTubeState(boolean curvature, Direction... connections) {
        BlockState state = (curvature ? ModBlocks.CURVATURE_PNEUMATIC_TUBE.get() : ModBlocks.PNEUMATIC_TUBE.get())
                .defaultBlockState();

        if (state.hasProperty(PneumaticTubeBlock.WATERLOGGED)) {
            state = state.setValue(PneumaticTubeBlock.WATERLOGGED, false);
        }
        if (state.hasProperty(PneumaticTubeBlock.HAS_RIM)) {
            state = state.setValue(PneumaticTubeBlock.HAS_RIM, false);
        }
        if (state.hasProperty(PneumaticTubeBlock.HAS_SECOND_RIM)) {
            state = state.setValue(PneumaticTubeBlock.HAS_SECOND_RIM, false);
        }
        if (state.hasProperty(PneumaticTubeBlock.RIM)) {
            state = state.setValue(PneumaticTubeBlock.RIM, Direction.NORTH);
        }

        for (Direction direction : Direction.values()) {
            state = state.setValue(PneumaticTubeBlock.getConnectionProperty(direction), false);
        }

        for (Direction connection : connections) {
            state = state.setValue(PneumaticTubeBlock.getConnectionProperty(connection), true);
        }
        return state.setValue(PneumaticTubeBlock.HAS_CONNECTION, connections.length > 0);
    }

    private List<BlockPos> buildCurvePathPositions(
            BlockPos startPos,
            BlockPos endPos,
            CubicBezier renderCurve,
            Direction.Axis fixedAxis
    ) {
        List<BlockPos> positions = new ArrayList<>();
        CubicBezier centerCurve = renderCurve.toBlockCenterCurve();
        int samples = Math.max(
                MIN_CURVE_SAMPLES,
                (int) Math.ceil(renderCurve.p0().distanceTo(renderCurve.p3()) * CURVE_SAMPLES_PER_BLOCK)
        );

        positions.add(startPos);

        for (int i = 1; i < samples; i++) {
            Vec3 point = centerCurve.pointAt((double) i / samples);
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
            appendLoopErasedPosition(positions, current);
        }
    }

    private void appendLoopErasedPosition(List<BlockPos> positions, BlockPos pos) {
        int existingIndex = positions.indexOf(pos);

        if (existingIndex < 0) {
            positions.add(pos);
            return;
        }

        positions.subList(existingIndex + 1, positions.size()).clear();
    }

    @Nullable
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

    private CubicBezier createFullBezierCurve(
            CurveStart start,
            BlockPos startPos,
            BlockPos endPos,
            Direction outgoingDirection
    ) {
        Vec3 incoming;
        Vec3 p0;
        if (start.isDeviderOutput()) {
            Direction side = start.deviderSide;
            Direction input = start.direction.getOpposite();
            incoming = Vec3.atLowerCornerOf(side.getNormal())
                    .add(Vec3.atLowerCornerOf(start.direction.getNormal()))
                    .normalize();
            p0 = Vec3.atLowerCornerOf(start.pos)
                    .add(DeviderBlockEntity.getLocalOutputPoint(side, input));
        } else {
            incoming = Vec3.atLowerCornerOf(start.direction.getNormal());
            p0 = Vec3.atCenterOf(startPos).subtract(incoming.scale(0.5));
        }

        Vec3 outgoing = Vec3.atLowerCornerOf(outgoingDirection.getNormal());
        Vec3 endCenter = Vec3.atCenterOf(endPos);
        Vec3 p3 = endCenter.add(outgoing.scale(0.5));
        double distance = p0.distanceTo(p3);
        double handleLength = start.isDeviderOutput()
                ? Math.max(0.35, distance * 0.35)
                : Math.max(1.5, distance * 0.45);

        Vec3 p1 = p0.add(incoming.scale(handleLength));
        Vec3 p2 = p3.subtract(outgoing.scale(handleLength));

        return new CubicBezier(p0, p1, p2, p3);
    }

    private boolean isCurveSmoothEnough(CubicBezier curve) {
        return curve.satisfiesCurvatureLimits(
                CURVATURE_CHECK_SAMPLES,
                MIN_CURVATURE_RADIUS,
                MAX_SECOND_DERIVATIVE
        );
    }

    private boolean areCurveSegmentsWithinTurnLimit(List<PlacedTube> tubes) {
        for (PlacedTube tube : tubes) {
            if (tube.bezier != null && !tube.bezier.satisfiesTurnAngleLimit(
                    CURVE_SEGMENT_TURN_CHECK_SAMPLES,
                    MAX_CURVE_SEGMENT_TURN_DEGREES
            )) {
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
            curvatureTube.setCurve(
                    tube.bezier.p0(),
                    tube.bezier.p1(),
                    tube.bezier.p2(),
                    tube.bezier.p3()
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

    private boolean canStartCurveFrom(Level level, BlockPos pos, BlockState state, Direction direction) {
        if (state.getBlock() instanceof PneumaticConnectionBlock connection) {
            return connection.getTubeDirection(level, pos, state) == direction;
        }

        if (state.getBlock() instanceof ItemPumpBlock) {
            return state.getValue(BlockStateProperties.FACING).getAxis() == direction.getAxis();
        }

        if (state.getBlock() instanceof ValveBlock) {
            return state.getValue(BlockStateProperties.FACING).getAxis() == direction.getAxis();
        }

        if (state.getBlock() instanceof ClogSensorBlock) {
            return state.getValue(BlockStateProperties.FACING).getAxis() == direction.getAxis();
        }

        if (!(state.getBlock() instanceof PneumaticTubeBlock)) {
            return false;
        }

        Direction openEnd = null;
        int connections = 0;

        for (Direction connectedDirection : Direction.values()) {
            if (!state.getValue(PneumaticTubeBlock.getConnectionProperty(connectedDirection))) {
                continue;
            }

            connections++;
            openEnd = connectedDirection.getOpposite();
        }

        if (connections == 0) {
            return true;
        }

        return connections == 1 && direction == openEnd;
    }

    private boolean isFullyConnectedTube(BlockState state) {
        return state.getBlock() instanceof PneumaticTubeBlock && countTubeConnections(state) >= 2;
    }

    private boolean isStraightSection(BlockState state) {
        if (!(state.getBlock() instanceof PneumaticTubeBlock)) {
            return false;
        }

        List<Direction> connected = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) {
                connected.add(direction);
            }
        }

        if (connected.size() != 2) {
            return false;
        }

        return connected.get(0).getAxis() == connected.get(1).getAxis();
    }

    private boolean isCurvatureEndpoint(BlockState state) {
        if (state.getBlock() instanceof CurvaturePneumaticTubeBlock) {
            return true;
        }

        if (!(state.getBlock() instanceof PneumaticTubeBlock)) {
            return false;
        }

        List<Direction> connected = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) {
                connected.add(direction);
            }
        }

        if (connected.size() != 2) {
            return false;
        }

        return connected.get(0).getAxis() != connected.get(1).getAxis();
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

    private BlockPos getStartTubePos(CurveStart start) {
        if (start.isDeviderOutput()) {
            return start.pos.relative(start.deviderSide);
        }
        return start.pos.relative(start.direction);
    }

    @Nullable
    private Direction.Axis getCurvePlaneFixedAxis(
            CurveStart start,
            BlockPos startPos,
            BlockPos endPos,
            Direction endTravelDirection
    ) {
        if (!start.isDeviderOutput()) {
            return getCurvePlaneFixedAxis(startPos, endPos, start.direction, endTravelDirection);
        }

        Direction.Axis fixedAxis = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != start.deviderSide.getAxis() && axis != start.direction.getAxis()) {
                fixedAxis = axis;
                break;
            }
        }
        if (fixedAxis == null) {
            return null;
        }
        return getCoordinate(startPos, fixedAxis) == getCoordinate(endPos, fixedAxis)
                && endTravelDirection.getAxis() != fixedAxis
                ? fixedAxis
                : null;
    }

    @Nullable
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

    @Nullable
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

    @Nullable
    private Direction directionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }

        return null;
    }

    private record PlacedTube(BlockPos pos, BlockState state, @Nullable CubicBezier bezier) {
    }

    public record CurveStart(BlockPos pos, Direction direction, @Nullable Direction deviderSide) {
        public CurveStart(BlockPos pos, Direction direction) {
            this(pos, direction, null);
        }

        public boolean isDeviderOutput() {
            return deviderSide != null && deviderSide.getAxis() != direction.getAxis();
        }

        public boolean isDeviderInput() {
            return deviderSide != null && deviderSide == direction;
        }

        public boolean isDeviderPort() {
            return deviderSide != null;
        }
    }

    public record CurveEnd(BlockPos pos, Direction direction) {
    }

    /**
     * A single tube in a planned (not yet placed) section, exposed to the renderer.
     */
    public record PlanTube(
            BlockPos pos,
            BlockState state,
            @Nullable Vec3 curveP0,
            @Nullable Vec3 curveP1,
            @Nullable Vec3 curveP2,
            @Nullable Vec3 curveP3
    ) {
    }

    /**
     * Result of planning a tube section without placing any blocks.
     * {@link #errorMessage()} is null when the planned layout is valid.
     */
    public static final class PlanResult {
        private final List<PlanTube> tubes;
        @Nullable
        private final String errorMessage;

        public PlanResult(List<PlanTube> tubes, @Nullable String errorMessage) {
            this.tubes = tubes;
            this.errorMessage = errorMessage;
        }

        public List<PlanTube> tubes() {
            return tubes;
        }

        @Nullable
        public String errorMessage() {
            return errorMessage;
        }

        public boolean valid() {
            return errorMessage == null && !tubes.isEmpty();
        }
    }

    @Nullable
    public static CurveStart getCurveStart(UUID playerId) {
        return CURVE_STARTS.get(playerId);
    }

    private static void clearCurveStarts(UUID playerId) {
        CURVE_STARTS.remove(playerId);
        CLIENT_CURVE_STARTS.remove(playerId);
    }

    /**
     * Computes the tube section that would be built between the stored start
     * (for {@code playerId}) and the targeted block face, without placing anything.
     * Used by the planning/ghost preview ("thinking mode").
     */
    public PlanResult planSection(Level level, UUID playerId, BlockPos clickedPos, Direction clickedFace) {
        CurveStart start = CURVE_STARTS.get(playerId);

        if (start == null) {
            return new PlanResult(List.of(), null);
        }

        return planSection(level, start, new CurveEnd(clickedPos, clickedFace));
    }

    /**
     * Plans a single tube at the targeted block face, used as the "thinking mode"
     * ghost when no section anchor has been set yet (i.e. just aiming with the item).
     */
    public PlanResult planSingle(Level level, BlockPos clickedPos, Direction clickedFace) {
        if (!(getBlock() instanceof PneumaticTubeBlock tubeBlock)) {
            return new PlanResult(List.of(), null);
        }

        BlockState state = tubeBlock.getTubeStateForPlacement(level, clickedPos);
        PlanTube tube = new PlanTube(clickedPos, state, null, null, null, null);
        return new PlanResult(List.of(tube), null);
    }

    private PlanResult planSection(Level level, CurveStart start, CurveEnd end) {
        BlockPos startPos = getStartTubePos(start);
        BlockPos endPos = end.pos.relative(end.direction);

        if (startPos.equals(endPos)) {
            return new PlanResult(List.of(), "Tube curve is too short");
        }

        if (!isInSinglePlane(startPos, endPos)) {
            return new PlanResult(List.of(), "Tube curve must stay in one fixed X/Y/Z plane");
        }

        if (start.isDeviderOutput() || !isStraight(startPos, endPos)) {
            Direction endTravelDirection = end.direction.getOpposite();

            if (getCurvePlaneFixedAxis(start, startPos, endPos, endTravelDirection) == null) {
                return new PlanResult(List.of(), "Tube curve endpoints must face inside one plane");
            }

            CubicBezier curve = createFullBezierCurve(start, startPos, endPos, endTravelDirection);

            if (!start.isDeviderOutput() && !isCurveSmoothEnough(curve)) {
                return new PlanResult(List.of(), "Tube curve is too sharp");
            }
        }

        List<PlacedTube> tubes = buildPlacedTubes(start, startPos, end, endPos);

        if (tubes.isEmpty()) {
            return new PlanResult(List.of(), "Invalid tube layout");
        }

        if (!areCurveSegmentsWithinTurnLimit(tubes)) {
            return new PlanResult(List.of(), CURVE_SEGMENT_TOO_SHARP_MESSAGE);
        }

        boolean blocked = !canPlaceAll(level, tubes);

        List<PlanTube> plan = new ArrayList<>();

        for (PlacedTube tube : tubes) {
            BlockState state = tube.state;
            if (state.getBlock() instanceof PneumaticTubeBlock tubeBlock) {
                state = tubeBlock.applyPreferredRim(level, tube.pos, state);
            }

            plan.add(new PlanTube(
                    tube.pos,
                    state,
                    tube.bezier == null ? null : tube.bezier.p0(),
                    tube.bezier == null ? null : tube.bezier.p1(),
                    tube.bezier == null ? null : tube.bezier.p2(),
                    tube.bezier == null ? null : tube.bezier.p3()
            ));
        }

        return new PlanResult(plan, blocked ? "Tube section is blocked" : null);
    }

    @Nullable
    public static CurveStart getClientCurveStart(UUID playerId) {
        return CLIENT_CURVE_STARTS.get(playerId);
    }

    public static void clearClientCurveStart(UUID playerId) {
        CLIENT_CURVE_STARTS.remove(playerId);
    }

    public boolean isCurveStartValid(Level level, CurveStart start) {
        if (start.isDeviderPort()) {
            BlockState state = level.getBlockState(start.pos);
            return state.getBlock() instanceof DeviderBlock
                    && (start.isDeviderInput()
                    ? start.direction == state.getValue(DeviderBlock.INPUT)
                    : start.direction == state.getValue(DeviderBlock.INPUT).getOpposite()
                    && (start.deviderSide == DeviderBlock.getLeftOutputDirection(state)
                    || start.deviderSide == DeviderBlock.getRightOutputDirection(state)));
        }
        return canStartCurveFrom(level, start.pos, level.getBlockState(start.pos), start.direction);
    }

    public PlanResult planClientSection(
            Level level,
            CurveStart start,
            BlockPos clickedPos,
            Direction clickedFace,
            Vec3 clickLocation
    ) {
        CurveStart deviderEnd = getDeviderCurveStart(
                clickedPos,
                level.getBlockState(clickedPos),
                clickedFace,
                clickLocation
        );
        if (deviderEnd != null && !start.isDeviderPort()) {
            return planSection(level, deviderEnd, new CurveEnd(start.pos, start.direction));
        }

        return planSection(level, start, new CurveEnd(clickedPos, clickedFace));
    }

    private static class StraightTubePlacementHelper implements IPlacementHelper {
        @Override
        public java.util.function.Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof PneumaticTubeBlockItem;
        }

        @Override
        public java.util.function.Predicate<BlockState> getStatePredicate() {
            return state -> countConnections(state) == 2 && getAxis(state) != null;
        }

        @Override
        public PlacementOffset getOffset(
                Player player,
                Level level,
                BlockState state,
                BlockPos pos,
                BlockHitResult hit
        ) {
            Direction.Axis axis = getAxis(state);

            if (axis == null) {
                return PlacementOffset.fail();
            }

            for (Direction direction : IPlacementHelper.orderedByDistanceOnlyAxis(pos, hit.getLocation(), axis)) {
                int range = getPlacementAssistRange(player);
                int attachedTubes = countAttachedTubes(level, pos, direction, axis);

                if (attachedTubes >= range) {
                    continue;
                }

                BlockPos placementPos = pos.relative(direction, attachedTubes + 1);

                if (!level.getBlockState(placementPos).canBeReplaced()) {
                    continue;
                }

                return PlacementOffset.success(
                        placementPos,
                        ignored -> ModBlocks.PNEUMATIC_TUBE.get()
                                .getTubeStateForPlacement(level, placementPos)
                );
            }

            return PlacementOffset.fail();
        }

        private static int countAttachedTubes(
                Level level,
                BlockPos pos,
                Direction direction,
                Direction.Axis axis
        ) {
            int count = 0;
            BlockPos checkPos = pos.relative(direction);

            while (getAxis(level.getBlockState(checkPos)) == axis) {
                count++;
                checkPos = checkPos.relative(direction);
            }

            return count;
        }

        private static int getPlacementAssistRange(Player player) {
            int range = AllConfigs.server().equipment.placementAssistRange.get();
            AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);

            if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) {
                range += 4;
            }

            return range;
        }

        @Nullable
        private static Direction.Axis getAxis(BlockState state) {
            if (!(state.getBlock() instanceof PneumaticTubeBlock)
                    || state.getBlock() instanceof CurvaturePneumaticTubeBlock) {
                return null;
            }

            Direction.Axis axis = null;

            for (Direction direction : Direction.values()) {
                if (!state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) {
                    continue;
                }

                if (axis != null && axis != direction.getAxis()) {
                    return null;
                }

                axis = direction.getAxis();
            }

            return axis;
        }

        private static int countConnections(BlockState state) {
            if (!(state.getBlock() instanceof PneumaticTubeBlock)) {
                return 0;
            }

            int connections = 0;

            for (Direction direction : Direction.values()) {
                if (state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) {
                    connections++;
                }
            }

            return connections;
        }
    }
}
