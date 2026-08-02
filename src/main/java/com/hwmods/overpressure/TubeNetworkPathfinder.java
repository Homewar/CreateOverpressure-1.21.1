package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class TubeNetworkPathfinder {
    public static TubePath findPathToInsertConnector(Level level, BlockPos sourceConnector, BlockPos firstTube) {
        if (!isPathNode(level, firstTube)) {
            return new TubePath(List.of(), null);
        }

        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(firstTube);
        visited.add(sourceConnector);
        visited.add(firstTube);
        previous.put(firstTube, sourceConnector);

        Direction firstDirection = getDirectionBetween(sourceConnector, firstTube);

        if (firstDirection == null || !canTravelBetween(level, sourceConnector, firstTube, firstDirection)) {
            return new TubePath(List.of(), null);
        }

        List<BlockPos> spillPath = List.of();
        BlockPos spillTarget = null;

        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();

            if (!isPathNode(level, current)) {
                continue;
            }

            BlockPos targetConnector = findAdjacentInsertConnector(level, current, sourceConnector);

            if (targetConnector != null) {
                return new TubePath(buildPath(previous, sourceConnector, current), targetConnector);
            }

            BlockPos previousPos = previous.get(current);
            Direction forward = previousPos == null ? null : getDirectionBetween(previousPos, current);
            if (forward != null) {
                BlockPos possibleSpillTarget = current.relative(forward);
                List<BlockPos> possibleSpillPath = buildPath(previous, sourceConnector, current);

                if (!isPathNode(level, possibleSpillTarget)
                        && possibleSpillPath.size() > spillPath.size()) {
                    spillPath = possibleSpillPath;
                    spillTarget = possibleSpillTarget;
                }
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);

                if (visited.contains(next)) {
                    continue;
                }

                if (!isPathNode(level, next)) {
                    continue;
                }

                if (!canTravelBetween(level, current, next, direction)) {
                    continue;
                }

                visited.add(next);
                previous.put(next, current);
                queue.add(next);
            }
        }

        if (spillTarget != null) {
            return new TubePath(spillPath, spillTarget, true);
        }

        return new TubePath(List.of(), null);
    }

    @Nullable
    private static BlockPos findAdjacentInsertConnector(
            Level level,
            BlockPos pos,
            BlockPos ignoredConnector
    ) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);

            if (neighborPos.equals(ignoredConnector)) {
                continue;
            }

            BlockState state = level.getBlockState(neighborPos);

            if (!(state.getBlock() instanceof PneumaticConnectionBlock)) {
                continue;
            }

            if (state.getValue(PneumaticConnectionBlock.MODE) == PneumaticConnectionBlock.ConnectionMode.INSERT
                    && canTravelBetween(level, pos, neighborPos, direction)) {
                return neighborPos;
            }
        }

        return null;
    }

    private static List<BlockPos> buildPath(Map<BlockPos, BlockPos> previous, BlockPos start, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = end;

        while (!current.equals(start)) {
            path.add(current);
            current = previous.get(current);
        }

        Collections.reverse(path);
        return path;
    }

    private static boolean isPathNode(Level level, BlockPos pos) {
        return PneumaticLine.isPathNode(level, pos);
    }

    private static boolean canTravelBetween(Level level, BlockPos from, BlockPos to, Direction movementDirection) {
        if (!connectorAllowsMovement(level, from, movementDirection)
                || !connectorAllowsMovement(level, to, movementDirection)) {
            return false;
        }

        if (level.getBlockEntity(from) instanceof PneumaticTubeBlockEntity fromTube
                && !fromTube.canTravelTo(level, movementDirection)) {
            return PneumaticLine.isTravelAllowed(level, from, to, movementDirection);
        }

        if (level.getBlockEntity(to) instanceof PneumaticTubeBlockEntity toTube
                && !toTube.canTravelTo(level, movementDirection.getOpposite())) {
            return PneumaticLine.isTravelAllowed(level, from, to, movementDirection);
        }

        if (!pumpAllowsMovement(level, from, movementDirection)
                || !pumpAllowsMovement(level, to, movementDirection)) {
            return false;
        }

        if (level.getBlockState(to).getBlock() instanceof PneumaticConnectionBlock) {
            return strictTubeEndpointAllowsMovement(level, from, movementDirection);
        }

        return PneumaticLine.isTravelAllowed(level, from, to, movementDirection);
    }

    private static boolean strictTubeEndpointAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        if (level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube) {
            return tube.canTravelTo(level, movementDirection);
        }

        if (level.getBlockEntity(pos) instanceof ItemPumpBlockEntity pump) {
            return pump.canTravelTo(level, movementDirection);
        }

        return true;
    }

    private static boolean connectorAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof PneumaticConnectionBlock)) {
            return true;
        }

        PneumaticConnectionBlock.ConnectionMode mode = state.getValue(PneumaticConnectionBlock.MODE);
        return mode != PneumaticConnectionBlock.ConnectionMode.DISABLED
                && state.getValue(PneumaticConnectionBlock.FACING) == movementDirection;
    }

    private static boolean pumpAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof ItemPumpBlock pump)) {
            return true;
        }

        if (!pump.canTravelTo(state, movementDirection)) {
            return false;
        }

        Direction flowDirection = pump.getFlowDirection(level, pos, state);
        return flowDirection == null || flowDirection == movementDirection;
    }

    @Nullable
    private static Direction getDirectionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }

        return null;
    }

    private TubeNetworkPathfinder() {
    }
}
