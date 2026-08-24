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

import com.hwmods.overpressure.transport.TransportJunction;
import com.hwmods.overpressure.transport.TubeTransportManager;
import com.hwmods.overpressure.transport.TransportGate;
import com.hwmods.overpressure.transport.TransportEndpoint;
import com.hwmods.overpressure.transport.TransportNodeComponent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TubeNetworkPathfinder {
    public static TubePath findPathToInsertConnector(Level level, BlockPos sourceConnector, BlockPos firstTube) {
        return findPathToInsertConnector(level, sourceConnector, firstTube, ItemStack.EMPTY);
    }

    public static TubePath findPathToInsertConnector(
            Level level,
            BlockPos sourceConnector,
            BlockPos firstTube,
            ItemStack cargo
    ) {
        return findPathToConnector(level, sourceConnector, firstTube, false, cargo);
    }

    public static TubePath findPathFromOccupiedTube(Level level, BlockPos sourceTube, BlockPos firstTube) {
        return findPathFromOccupiedTube(level, sourceTube, firstTube, ItemStack.EMPTY);
    }

    public static TubePath findPathFromOccupiedTube(
            Level level,
            BlockPos sourceTube,
            BlockPos firstTube,
            ItemStack cargo
    ) {
        return findPathToConnector(level, sourceTube, firstTube, true, cargo);
    }

    private static TubePath findPathToConnector(
            Level level,
            BlockPos sourceConnector,
            BlockPos firstTube,
            boolean allowOccupiedFirstTube,
            ItemStack cargo
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
                    && !(level.getBlockEntity(current) instanceof TransportJunction)
                    && !hasConnectedContinuation(level, current, previousPos)) {
                BlockPos possibleSpillTarget = current.relative(forward);
                List<BlockPos> possibleSpillPath = buildPath(previous, sourceConnector, current);

                if (!isPathNode(level, possibleSpillTarget)
                        && !(level.getBlockEntity(possibleSpillTarget) instanceof TransportEndpoint)
                        && possibleSpillPath.size() > spillPath.size()) {
                    spillPath = possibleSpillPath;
                    spillTarget = possibleSpillTarget;
                }
            }

            BlockPos saturatedPreferredBranch = getSaturatedPreferredBranch(level, current, previousPos, cargo);

            for (BlockPos next : getSearchNeighbors(level, current, previousPos, cargo)) {
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
                        node.score.branchPenalty + getDeviderBranchPenalty(level, current, previousPos, next, cargo),
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
            if (level.getBlockEntity(path.get(index)) instanceof TransportJunction junction
                    && junction.isMergerPassage(path.get(index - 1), path.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> getSearchNeighbors(
            Level level,
            BlockPos current,
            BlockPos previous,
            ItemStack cargo
    ) {
        if (!(level.getBlockEntity(current) instanceof TransportJunction junction) || previous == null) {
            return PneumaticLine.getForwardNeighbors(level, current, previous);
        }

        if (junction.isStraightPort(previous)) {
            return junction.orderedBranchPorts(cargo);
        }
        if (junction.isBranchPort(previous)) {
            return List.of(junction.straightPort());
        }
        return List.of();
    }

    private static boolean isPotentialJunctionTransition(
            Level level,
            BlockPos current,
            @Nullable BlockPos previous,
            BlockPos next
    ) {
        if (level.getBlockEntity(next) instanceof TransportJunction junction) {
            return isUsableJunctionPort(level, junction, current);
        }

        if (!(level.getBlockEntity(current) instanceof TransportJunction junction) || previous == null) {
            return false;
        }

        boolean dividerPassage = junction.isStraightPort(previous)
                && junction.isBranchPort(next);
        boolean mergerPassage = junction.isBranchPort(previous)
                && junction.isStraightPort(next);
        return (dividerPassage || mergerPassage)
                && isUsableJunctionPort(level, junction, previous)
                && isUsableJunctionPort(level, junction, next);
    }

    private static boolean isUsableJunctionPort(Level level, TransportJunction junction, BlockPos portPos) {
        return junction.isPortUsable(level, portPos);
    }

    @Nullable
    private static BlockPos getSaturatedPreferredBranch(
            Level level,
            BlockPos deviderPos,
            BlockPos previousPos,
            ItemStack cargo
    ) {
        if (!(level.getBlockEntity(deviderPos) instanceof TransportJunction junction)
                || !junction.isStraightPort(previousPos)) {
            return null;
        }

        List<BlockPos> orderedBranches = junction.orderedBranchPorts(cargo);
        if (orderedBranches.isEmpty()) {
            return null;
        }
        BlockPos preferredBranch = orderedBranches.get(0);
        if (!(level.getBlockEntity(preferredBranch) instanceof PneumaticTubeBlockEntity branchTube)
                || !TubeTransportManager.get(level).isOccupied(preferredBranch)) {
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
            BlockPos to,
            ItemStack cargo
    ) {
        if (!(level.getBlockEntity(from) instanceof TransportJunction junction)
                || !junction.isStraightPort(previous)) {
            return 0;
        }
        List<BlockPos> orderedBranches = junction.orderedBranchPorts(cargo);
        return !orderedBranches.isEmpty() && to.equals(orderedBranches.get(0)) ? 0 : 1;
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

            if (!(level.getBlockEntity(neighborPos) instanceof TransportEndpoint endpoint)) {
                continue;
            }
            if (endpoint.canReceiveFrom(level, pos)
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
                || !(level.getBlockEntity(candidate) instanceof TransportEndpoint)) {
            return null;
        }

        return canTravelBetween(level, current, candidate, forward) ? null : candidate;
    }

    private static boolean isOccupiedTube(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity
                && TubeTransportManager.get(level).isOccupied(pos);
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

        if (!gateAllowsRoute(level, from, movementDirection)
                || !gateAllowsRoute(level, to, movementDirection)) {
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

        if (level.getBlockEntity(to) instanceof TransportEndpoint) {
            return strictTubeEndpointAllowsMovement(level, from, movementDirection);
        }

        return PneumaticLine.isRouteAllowed(level, from, to, movementDirection);
    }

    private static boolean canTravelBetween(Level level, BlockPos from, BlockPos to) {
        if (level.getBlockEntity(from) instanceof TransportJunction
                || level.getBlockEntity(to) instanceof TransportJunction) {
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
            if (level.getBlockEntity(positions.get(index)) instanceof TransportJunction junction
                    && junction.isStraightPort(positions.get(index - 1))
                    && junction.isBranchPort(positions.get(index + 1))) {
                junction.markBranchUsed(positions.get(index + 1));
            }
        }
    }

    private static boolean strictTubeEndpointAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        if (level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube) {
            return tube.canTravelTo(level, movementDirection);
        }

        if (level.getBlockEntity(pos) instanceof TransportGate gate) {
            return gate.allowsRoute(level, movementDirection);
        }

        return true;
    }

    private static boolean connectorAllowsMovement(Level level, BlockPos pos, Direction movementDirection) {
        if (!(level.getBlockEntity(pos) instanceof TransportEndpoint endpoint)) {
            return true;
        }
        return endpoint.allowsRoute(level, movementDirection);
    }

    private static boolean gateAllowsRoute(Level level, BlockPos pos, Direction movementDirection) {
        if (!(level.getBlockEntity(pos) instanceof TransportGate gate)) {
            return true;
        }
        return gate.allowsRoute(level, movementDirection);
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
