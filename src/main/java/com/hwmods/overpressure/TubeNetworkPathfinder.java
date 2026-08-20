package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class TubeNetworkPathfinder {
    public static TubePath findPathToInsertConnector(Level level, BlockPos sourceConnector, BlockPos firstTube) {
        return findPathToConnector(level, sourceConnector, firstTube, false);
    }

    public static TubePath findPathFromOccupiedTube(Level level, BlockPos sourceTube, BlockPos firstTube) {
        return findPathToConnector(level, sourceTube, firstTube, true);
    }

    private static TubePath findPathToConnector(
            Level level,
            BlockPos sourceConnector,
            BlockPos firstTube,
            boolean allowOccupiedFirstTube
    ) {
        if (!isPathNode(level, firstTube)) {
            return new TubePath(List.of(), null);
        }

        if (!allowOccupiedFirstTube && isOccupiedTube(level, firstTube)) {
            return new TubePath(List.of(), null);
        }

        Queue<SearchNode> queue = new PriorityQueue<>(Comparator
                .comparingInt((SearchNode node) -> node.score.branchPenalty)
                .thenComparingInt(node -> node.score.distance)
                .thenComparingLong(node -> node.sequence));
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, RouteScore> bestScores = new HashMap<>();
        long sequence = 0;

        RouteScore firstScore = new RouteScore(0, 0);
        queue.add(new SearchNode(firstTube, firstScore, sequence++));
        bestScores.put(firstTube, firstScore);
        previous.put(firstTube, sourceConnector);

        boolean waitingForJunctionMode = false;
        if (!canTravelBetween(level, sourceConnector, firstTube)) {
            if (!isPotentialJunctionTransition(level, sourceConnector, null, firstTube)) {
                return new TubePath(List.of(), null);
            }
            waitingForJunctionMode = true;
        }

        List<BlockPos> spillPath = List.of();
        BlockPos spillTarget = null;
        List<BlockPos> blockedConnectorPath = List.of();
        BlockPos blockedConnectorTarget = null;
        RouteScore blockedConnectorScore = null;
        boolean waitingForSaturatedBranch = false;

        while (!queue.isEmpty()) {
            SearchNode node = queue.remove();
            BlockPos current = node.pos;
            if (!node.score.equals(bestScores.get(current))) {
                continue;
            }

            if (!isPathNode(level, current)) {
                continue;
            }

            BlockPos targetConnector = findAdjacentInsertConnector(
                    level,
                    current,
                    sourceConnector
            );

            if (targetConnector != null) {
                return new TubePath(buildPath(previous, sourceConnector, current), targetConnector);
            }

            BlockPos previousPos = previous.get(current);
            BlockPos blockedConnector = findForwardBlockedConnector(
                    level,
                    current,
                    previousPos,
                    sourceConnector
            );
            if (blockedConnector != null
                    && (blockedConnectorScore == null
                    || compareScores(node.score, blockedConnectorScore) < 0)) {
                blockedConnectorPath = buildPath(previous, sourceConnector, current);
                blockedConnectorTarget = blockedConnector;
                blockedConnectorScore = node.score;
            }

            Direction forward = previousPos == null ? null : getDirectionBetween(previousPos, current);
            if (forward != null
                    && !(level.getBlockEntity(current) instanceof DeviderBlockEntity)
                    && !hasConnectedContinuation(level, current, previousPos)) {
                BlockPos possibleSpillTarget = current.relative(forward);
                List<BlockPos> possibleSpillPath = buildPath(previous, sourceConnector, current);

                if (!isPathNode(level, possibleSpillTarget)
                        && !(level.getBlockState(possibleSpillTarget).getBlock()
                        instanceof PneumaticConnectionBlock)
                        && possibleSpillPath.size() > spillPath.size()) {
                    spillPath = possibleSpillPath;
                    spillTarget = possibleSpillTarget;
                }
            }

            BlockPos saturatedPreferredBranch = getSaturatedPreferredBranch(level, current, previousPos);

            for (BlockPos next : getSearchNeighbors(level, current, previousPos)) {
                if (next.equals(sourceConnector)) {
                    continue;
                }

                if (!isPathNode(level, next)) {
                    continue;
                }

                if (next.equals(saturatedPreferredBranch) && isOccupiedTube(level, next)) {
                    waitingForSaturatedBranch = true;
                    continue;
                }

                if (!canTravelBetween(level, current, next)) {
                    if (!isPotentialJunctionTransition(level, current, previousPos, next)) {
                        continue;
                    }
                    waitingForJunctionMode = true;
                }

                RouteScore nextScore = new RouteScore(
                        node.score.branchPenalty + getDeviderBranchPenalty(level, current, previousPos, next),
                        node.score.distance + 1
                );
                RouteScore previousScore = bestScores.get(next);
                if (previousScore != null && compareScores(previousScore, nextScore) <= 0) {
                    continue;
                }

                bestScores.put(next, nextScore);
                previous.put(next, current);
                queue.add(new SearchNode(next, nextScore, sequence++));
            }
        }

        if (blockedConnectorTarget != null) {
            return new TubePath(blockedConnectorPath, blockedConnectorTarget);
        }

        if (spillTarget != null
                && !waitingForSaturatedBranch
                && !waitingForJunctionMode
                && !containsMergerPassage(level, spillPath)) {
            return new TubePath(spillPath, spillTarget, true);
        }

        return new TubePath(List.of(), null);
    }

    private static boolean containsMergerPassage(Level level, List<BlockPos> path) {
        for (int index = 1; index + 1 < path.size(); index++) {
            if (level.getBlockEntity(path.get(index)) instanceof DeviderBlockEntity devider
                    && devider.isMergerPassage(path.get(index - 1), path.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> getSearchNeighbors(Level level, BlockPos current, BlockPos previous) {
        if (!(level.getBlockEntity(current) instanceof DeviderBlockEntity devider) || previous == null) {
            return PneumaticLine.getForwardNeighbors(level, current, previous);
        }

        if (devider.isStraightPosition(previous)) {
            return devider.getOrderedBranchPositions();
        }
        if (devider.isBranchPosition(previous)) {
            return List.of(devider.getStraightPosition());
        }
        return List.of();
    }

    private static boolean isPotentialJunctionTransition(
            Level level,
            BlockPos current,
            @Nullable BlockPos previous,
            BlockPos next
    ) {
        if (level.getBlockEntity(next) instanceof DeviderBlockEntity devider) {
            return isUsableJunctionPort(level, devider, current);
        }

        if (!(level.getBlockEntity(current) instanceof DeviderBlockEntity devider) || previous == null) {
            return false;
        }

        boolean dividerPassage = devider.isStraightPosition(previous)
                && devider.isBranchPosition(next);
        boolean mergerPassage = devider.isBranchPosition(previous)
                && devider.isStraightPosition(next);
        return (dividerPassage || mergerPassage)
                && isUsableJunctionPort(level, devider, previous)
                && isUsableJunctionPort(level, devider, next);
    }

    private static boolean isUsableJunctionPort(Level level, DeviderBlockEntity devider, BlockPos portPos) {
        if (devider.isStraightPosition(portPos)) {
            return level.getBlockEntity(portPos) instanceof PneumaticTubeBlockEntity
                    || level.getBlockEntity(portPos) instanceof ItemPumpBlockEntity;
        }
        return devider.isBranchPosition(portPos)
                && level.getBlockState(portPos).getBlock() instanceof CurvaturePneumaticTubeBlock
                && level.getBlockEntity(portPos) instanceof PneumaticTubeBlockEntity;
    }

    @Nullable
    private static BlockPos getSaturatedPreferredBranch(
            Level level,
            BlockPos deviderPos,
            BlockPos previousPos
    ) {
        if (!(level.getBlockEntity(deviderPos) instanceof DeviderBlockEntity devider)
                || !devider.isStraightPosition(previousPos)) {
            return null;
        }

        BlockPos preferredBranch = devider.getOrderedBranchPositions().get(0);
        if (!(level.getBlockEntity(preferredBranch) instanceof PneumaticTubeBlockEntity branchTube)
                || branchTube.getMovingItem() == null) {
            return null;
        }

        return PneumaticTubeBlockEntity.isOutputBranchSaturated(level, branchTube)
                ? preferredBranch
                : null;
    }

    private static boolean hasConnectedContinuation(Level level, BlockPos current, BlockPos previous) {
        for (BlockPos neighbor : PneumaticLine.getForwardNeighbors(level, current)) {
            if (neighbor.equals(previous) || !isPathNode(level, neighbor)) {
                continue;
            }

            if (canTravelBetween(level, current, neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static int getDeviderBranchPenalty(
            Level level,
            BlockPos from,
            BlockPos previous,
            BlockPos to
    ) {
        if (!(level.getBlockEntity(from) instanceof DeviderBlockEntity devider)
                || !devider.isStraightPosition(previous)) {
            return 0;
        }
        return to.equals(devider.getOrderedBranchPositions().get(0)) ? 0 : 1;
    }

    private static int compareScores(RouteScore first, RouteScore second) {
        int branchComparison = Integer.compare(first.branchPenalty, second.branchPenalty);
        return branchComparison != 0 ? branchComparison : Integer.compare(first.distance, second.distance);
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

            PneumaticConnectionBlock.ConnectionMode mode = state.getValue(PneumaticConnectionBlock.MODE);
            if (mode == PneumaticConnectionBlock.ConnectionMode.INSERT
                    && canTravelBetween(level, pos, neighborPos, direction)) {
                return neighborPos;
            }
        }

        return null;
    }

    @Nullable
    private static BlockPos findForwardBlockedConnector(
            Level level,
            BlockPos current,
            @Nullable BlockPos previous,
            BlockPos ignoredConnector
    ) {
        Direction forward = previous == null ? null : getDirectionBetween(previous, current);
        if (forward == null) {
            return null;
        }

        BlockPos candidate = current.relative(forward);
        if (candidate.equals(ignoredConnector)
                || !(level.getBlockState(candidate).getBlock() instanceof PneumaticConnectionBlock)) {
            return null;
        }

        return canTravelBetween(level, current, candidate, forward) ? null : candidate;
    }

    private static boolean isOccupiedTube(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube
                && tube.getMovingItem() != null;
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

        if (!pumpAllowsMovement(level, from, movementDirection)
                || !pumpAllowsMovement(level, to, movementDirection)) {
            return false;
        }

        if (!valveAllowsMovement(level, from, movementDirection)
                || !valveAllowsMovement(level, to, movementDirection)) {
            return false;
        }

        if (level.getBlockEntity(from) instanceof PneumaticTubeBlockEntity fromTube
                && !fromTube.canTravelTo(level, movementDirection)) {
            return PneumaticLine.isRouteAllowed(level, from, to, movementDirection);
        }

        if (level.getBlockEntity(to) instanceof PneumaticTubeBlockEntity toTube
                && !toTube.canTravelTo(level, movementDirection.getOpposite())) {
            return PneumaticLine.isRouteAllowed(level, from, to, movementDirection);
        }

        if (level.getBlockState(to).getBlock() instanceof PneumaticConnectionBlock) {
            return strictTubeEndpointAllowsMovement(level, from, movementDirection);
        }

        return PneumaticLine.isRouteAllowed(level, from, to, movementDirection);
    }

    private static boolean canTravelBetween(Level level, BlockPos from, BlockPos to) {
        if (level.getBlockEntity(from) instanceof DeviderBlockEntity
                || level.getBlockEntity(to) instanceof DeviderBlockEntity) {
            return PneumaticLine.isRouteAllowed(level, from, to);
        }

        Direction movementDirection = getDirectionBetween(from, to);
        if (movementDirection == null) {
            return PneumaticLine.isRouteAllowed(level, from, to);
        }
        return canTravelBetween(level, from, to, movementDirection);
    }

    public static void commitDeviderChoices(Level level, TubePath path) {
        List<BlockPos> positions = path.tubePositions();
        for (int index = 1; index + 1 < positions.size(); index++) {
            if (level.getBlockEntity(positions.get(index)) instanceof DeviderBlockEntity devider
                    && devider.isStraightPosition(positions.get(index - 1))
                    && devider.isBranchPosition(positions.get(index + 1))) {
                devider.markBranchUsed(positions.get(index + 1));
            }
        }
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
        Direction facing = state.getValue(PneumaticConnectionBlock.FACING);
        return switch (mode) {
            case INSERT -> facing == movementDirection;
            case EXTRACT -> facing == movementDirection;
            case DISABLED -> false;
        };
    }

    private static boolean pumpAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof ItemPumpBlock pump)) {
            return true;
        }

        return pump.allowsTravel(level, pos, state, movementDirection);
    }

    private static boolean valveAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        BlockState state = level.getBlockState(pos);
        return !(state.getBlock() instanceof ValveBlock valve)
                || valve.canTravelTo(state, movementDirection);
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

    private record RouteScore(int branchPenalty, int distance) {
    }

    private record SearchNode(BlockPos pos, RouteScore score, long sequence) {
    }
}
