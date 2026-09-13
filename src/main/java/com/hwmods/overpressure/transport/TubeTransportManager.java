package com.hwmods.overpressure.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.hwmods.overpressure.Config;
import com.hwmods.overpressure.CurvaturePneumaticTubeEntity;
import com.hwmods.overpressure.MovingTubeItem;
import com.hwmods.overpressure.PneumaticLine;
import com.hwmods.overpressure.PneumaticTubeBlockEntity;
import com.hwmods.overpressure.TubeNetworkPathfinder;
import com.hwmods.overpressure.TubePath;
import com.hwmods.overpressure.Overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The single server-side owner of pneumatic cargo for a level. Blocks expose
 * ports and configuration; this manager owns routes, occupancy, queues and
 * movement. A tube block entity is now only a persistence/render adapter.
 */
@EventBusSubscriber(modid = Overpressure.MODID)
public final class TubeTransportManager {
    private static final int SPEED_CHECK_INTERVAL = 2;
    private static final Map<Level, TubeTransportManager> BY_LEVEL = new IdentityHashMap<>();

    private final Level level;
    private final Map<BlockPos, TransitEntry> occupants = new java.util.HashMap<>();
    private long topologyVersion;
    private long lastTickedGameTime = Long.MIN_VALUE;
    private long animationSequence;

    private TubeTransportManager(Level level) {
        this.level = level;
    }

    public static TubeTransportManager get(Level level) {
        synchronized (BY_LEVEL) {
            return BY_LEVEL.computeIfAbsent(level, TubeTransportManager::new);
        }
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        synchronized (BY_LEVEL) {
            BY_LEVEL.remove(event.getLevel());
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        Level tickLevel = event.getLevel();
        if (tickLevel.isClientSide) {
            return;
        }
        TubeTransportManager manager;
        synchronized (BY_LEVEL) {
            manager = BY_LEVEL.get(tickLevel);
        }
        if (manager != null) {
            manager.tick();
        }
    }

    @Nullable
    public MovingTubeItem getSnapshot(BlockPos owner) {
        TransitEntry entry = occupants.get(owner);
        return entry == null ? null : entry.snapshot();
    }

    public boolean isOccupied(BlockPos owner) {
        return occupants.containsKey(owner) || com.hwmods.overpressure.tube.SectionTransport.isOccupied(level, owner);
    }

    public boolean isHeadingTo(BlockPos owner, int pathOffset, BlockPos target) {
        TransitEntry entry = occupants.get(owner);
        if (entry == null) {
            return false;
        }
        int targetIndex = entry.state().pathIndex + pathOffset;
        List<BlockPos> path = entry.route().blockPath();
        return targetIndex >= 0 && targetIndex < path.size() && path.get(targetIndex).equals(target);
    }

    public boolean canAccept(BlockPos owner, ItemStack stack, TubePath path) {
        if (isOccupied(owner) || stack.isEmpty() || path.isEmpty() || !Config.canEnterTube(stack)) {
            return false;
        }
        int pathIndex = path.tubePositions().indexOf(owner);
        return pathIndex >= 0 && calculateMoveTime(findSpeedControllers(path.tubePositions())) > 0;
    }

    public boolean accept(BlockPos owner, ItemStack stack, TubePath path, @Nullable BlockPos sourceConnector) {
        if (!canAccept(owner, stack, path)) {
            return false;
        }

        List<BlockPos> blockPath = List.copyOf(path.tubePositions());
        List<BlockPos> speedControllers = findSpeedControllers(blockPath);
        TransportRoute route = new TransportRoute(
                blockPath,
                TubeGraphRoute.compile(level, blockPath, sourceConnector, path.targetConnector()),
                sourceConnector,
                path.targetConnector(),
                path.spillsAtEnd()
        );
        route.setProtectedFromJunctionSpill(containsMergerPassage(blockPath));
        route.setReservedMergerPassages(findConfiguredMergerPassages(blockPath));
        route.setSpeedControllers(speedControllers);
        route.setSpeedControllerCacheValid(true);
        route.setTopologyVersion(topologyVersion);

        TransportRuntimeState state = new TransportRuntimeState(owner);
        state.startPathIndex = blockPath.indexOf(owner);
        state.pathIndex = state.startPathIndex;
        state.moveTime = calculateMoveTime(speedControllers);
        state.animationId = createAnimationId(owner);
        state.startedAtGameTime = level.getGameTime();
        state.lastTickedGameTime = level.getGameTime();

        TransitEntry entry = new TransitEntry(new TransportCargo(stack), route, state);
        state.segmentDuration = calculateSegmentDuration(entry, state.pathIndex);
        occupants.put(owner.immutable(), entry);
        sync(owner);
        return true;
    }

    /** Restores a chunk-owned snapshot into the central registry once its BE is live. */
    public void restore(BlockPos owner, MovingTubeItem snapshot) {
        if (snapshot == null || occupants.containsKey(owner)) {
            return;
        }
        TubeGraphRoute graphRoute = TubeGraphRoute.compile(
                level,
                snapshot.path,
                snapshot.sourceConnector,
                snapshot.targetConnector
        );
        occupants.put(owner.immutable(), TransitEntry.restore(snapshot, owner, graphRoute));
    }

    public void tick() {
        long gameTime = level.getGameTime();
        if (lastTickedGameTime == gameTime) {
            return;
        }
        lastTickedGameTime = gameTime;

        Set<TransitEntry> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(occupants.values());
        for (TransitEntry entry : List.copyOf(unique)) {
            if (occupants.get(entry.state().owner()) != entry) {
                continue;
            }
            if (!level.isLoaded(entry.state().owner())) {
                continue;
            }
            tickEntry(entry);
        }
    }

    public void invalidateTopology(BlockPos changedPos) {
        topologyVersion = topologyVersion == Long.MAX_VALUE ? 0L : topologyVersion + 1L;
    }

    public long topologyVersion() {
        return topologyVersion;
    }

    public void eject(BlockPos owner, Vec3 position) {
        TransitEntry entry = occupants.remove(owner);
        if (entry == null) {
            return;
        }
        ItemStack stack = entry.cargo().stack();
        level.addFreshEntity(new ItemEntity(level, position.x, position.y, position.z, stack));
        sync(owner);
    }

    /** Moves persistence ownership during legacy-section migration without spawning or duplicating items. */
    public MovingTubeItem takeForMigration(BlockPos owner) {
        TransitEntry entry = occupants.remove(owner);
        return entry == null ? null : entry.snapshot();
    }

    public boolean isOutputBranchSaturated(BlockPos firstOwner) {
        Set<BlockPos> visited = new HashSet<>();
        TransitEntry entry = occupants.get(firstOwner);
        while (entry != null && visited.add(entry.state().owner())) {
            if (entry.state().status == TransitStatus.WAITING_AT_DESTINATION
                    || entry.state().status == TransitStatus.SPILLING) {
                return true;
            }
            if (entry.state().status != TransitStatus.WAITING_FOR_SPACE) {
                return false;
            }

            int nextIndex = nextOwnerPathIndex(entry.route().blockPath(), entry.state().pathIndex);
            if (nextIndex < 0) {
                return true;
            }
            entry = occupants.get(entry.route().blockPath().get(nextIndex));
            if (entry == null) {
                return false;
            }
        }
        return entry != null;
    }

    private void tickEntry(TransitEntry entry) {
        TransportRuntimeState state = entry.state();
        if (state.lastTickedGameTime == level.getGameTime()) {
            return;
        }
        state.lastTickedGameTime = level.getGameTime();

        if (state.status == TransitStatus.WAITING_AT_DESTINATION
                || state.status == TransitStatus.SPILLING) {
            tryInsertIntoTarget(entry);
            return;
        }

        if (state.status == TransitStatus.WAITING_FOR_FLOW) {
            if (!hasRunningPump(entry)) {
                return;
            }
            state.status = TransitStatus.MOVING;
            sync(state.owner());
        }

        if (state.status == TransitStatus.WAITING_FOR_SPACE) {
            tryRerouteJunctionOutput(entry);
            if (markBrokenNextSegmentAsSpill(entry)) {
                return;
            }
            if (!hasRunningPump(entry)) {
                return;
            }
            if (!canMoveToNextPathNode(entry)) {
                state.status = TransitStatus.WAITING_FOR_SPACE;
                return;
            }
            state.status = TransitStatus.MOVING;
            moveToNextPathNode(entry);
            return;
        }

        if (!hasRunningPump(entry)) {
            state.status = TransitStatus.WAITING_FOR_FLOW;
            sync(state.owner());
            return;
        }

        int segmentDuration = getCurrentSegmentDuration(entry);
        if (segmentDuration <= 0) {
            return;
        }
        if (isApproachingFallbackEnd(entry)
                && state.segmentProgress + 1.0f / segmentDuration >= 0.5f) {
            tryInsertIntoTarget(entry);
            return;
        }
        if (findBrokenNextSegment(entry) != null
                && state.segmentProgress + 1.0f / segmentDuration >= 0.5f) {
            markBrokenNextSegmentAsSpill(entry);
            return;
        }

        state.segmentProgress = Math.min(1.0f, state.segmentProgress + 1.0f / segmentDuration);
        state.progress = Math.round(state.segmentProgress * segmentDuration);
        if (state.segmentProgress >= 1.0f) {
            moveToNextPathNode(entry);
        } else {
            markDirty(state.owner());
        }
    }

    private boolean tryRerouteJunctionOutput(TransitEntry entry) {
        BlockEntity ownerEntity = level.getBlockEntity(entry.state().owner());
        if (!(ownerEntity instanceof TransportJunction junction)) {
            return false;
        }

        List<BlockPos> path = entry.route().blockPath();
        int nextIndex = entry.state().pathIndex + 1;
        if (nextIndex >= path.size()) {
            return false;
        }
        BlockPos previous = entry.state().pathIndex > 0 ? path.get(entry.state().pathIndex - 1) : null;
        List<BlockPos> enabledOutputs = junction.forwardPorts(previous, entry.cargo().stack());
        BlockPos currentOutput = path.get(nextIndex);
        if (enabledOutputs.contains(currentOutput)
                && level.getBlockEntity(currentOutput) instanceof PneumaticTubeBlockEntity) {
            TransitEntry currentBranchEntry = occupants.get(currentOutput);
            if (currentBranchEntry == null || !isOutputBranchSaturated(currentOutput)) {
                return false;
            }
        }

        for (BlockPos alternate : enabledOutputs) {
            if (alternate.equals(currentOutput)) {
                continue;
            }
            TubePath alternatePath = TubeNetworkPathfinder.findPathFromOccupiedTube(
                    level,
                    entry.state().owner(),
                    alternate,
                    entry.cargo().stack()
            );
            if (alternatePath.isEmpty() || alternatePath.spillsAtEnd()) {
                continue;
            }

            List<BlockPos> rerouted = new ArrayList<>(path.subList(0, entry.state().pathIndex + 1));
            rerouted.addAll(alternatePath.tubePositions());
            replaceRemainingRoute(entry, rerouted, alternatePath.targetConnector(), alternatePath.spillsAtEnd());
            entry.state().status = TransitStatus.MOVING;
            sync(entry.state().owner());
            return true;
        }
        return false;
    }

    private void moveToNextPathNode(TransitEntry entry) {
        TransportRuntimeState state = entry.state();
        int previousIndex = state.pathIndex;
        if (!canTravelToNextPathNode(entry, previousIndex)) {
            state.status = TransitStatus.WAITING_FOR_SPACE;
            sync(state.owner());
            return;
        }

        state.progress = 0;
        state.segmentProgress = 0.0f;
        state.pathIndex++;
        List<BlockPos> path = entry.route().blockPath();
        if (state.pathIndex >= path.size()) {
            state.pathIndex = previousIndex;
            tryInsertIntoTarget(entry);
            return;
        }

        BlockPos crossedPos = path.get(state.pathIndex);
        if (isPassThroughNode(crossedPos)) {
            state.pathIndex++;
            if (state.pathIndex >= path.size()) {
                state.pathIndex = previousIndex;
                tryInsertIntoTarget(entry);
                return;
            }
            crossedPos = path.get(state.pathIndex);
        }

        BlockEntity destinationEntity = level.getBlockEntity(crossedPos);
        if (destinationEntity instanceof PneumaticTubeBlockEntity && !isOccupied(crossedPos)) {
            if (destinationEntity instanceof TransportJunction junction) {
                BlockPos enteredFrom = path.get(Math.max(0, state.pathIndex - 1));
                if (junction.isBranchPort(enteredFrom)) {
                    junction.markMergeInputUsed(enteredFrom);
                }
            }
            transfer(entry, crossedPos);
            return;
        }
        if (destinationEntity instanceof PneumaticTubeBlockEntity) {
            state.pathIndex = previousIndex;
            state.status = TransitStatus.WAITING_FOR_SPACE;
            sync(state.owner());
            return;
        }

        state.pathIndex = previousIndex;
        eject(state.owner(), getOpenEndPosition(state.owner(), crossedPos));
    }

    private void transfer(TransitEntry entry, BlockPos destination) {
        BlockPos previousOwner = entry.state().owner();
        occupants.remove(previousOwner, entry);
        entry.state().setOwner(destination);
        entry.state().moveTime = calculateMoveTime(entry.route().speedControllers());
        entry.state().segmentDuration = calculateSegmentDuration(entry, entry.state().pathIndex);
        entry.state().lastSpeedCheckGameTime = Long.MIN_VALUE;
        entry.state().startedAtGameTime = level.getGameTime();
        entry.state().status = TransitStatus.MOVING;
        occupants.put(destination.immutable(), entry);
        sync(previousOwner);
        sync(destination);
    }

    @Nullable
    private BlockPos findBrokenNextSegment(TransitEntry entry) {
        List<BlockPos> path = entry.route().blockPath();
        int nextIndex = entry.state().pathIndex + 1;
        if (nextIndex >= path.size()) {
            return null;
        }
        BlockPos next = path.get(nextIndex);
        if (!level.isLoaded(next)) {
            return null;
        }
        if (!PneumaticLine.isPathNode(level, next)) {
            return next;
        }
        if (isPassThroughNode(next) && ++nextIndex < path.size()) {
            BlockPos afterPump = path.get(nextIndex);
            if (!level.isLoaded(afterPump)) {
                return null;
            }
            if (!PneumaticLine.isPathNode(level, afterPump)) {
                return afterPump;
            }
        }
        return null;
    }

    private boolean markBrokenNextSegmentAsSpill(TransitEntry entry) {
        BlockPos broken = findBrokenNextSegment(entry);
        if (broken == null) {
            return false;
        }
        entry.route().replacePath(
                entry.route().blockPath(),
                TubeGraphRoute.compile(level, entry.route().blockPath(), entry.route().sourceConnector(), broken),
                broken,
                true
        );
        entry.state().status = TransitStatus.SPILLING;
        sync(entry.state().owner());
        tryInsertIntoTarget(entry);
        return true;
    }

    private boolean isApproachingFallbackEnd(TransitEntry entry) {
        return entry.route().spillsAtEnd()
                && entry.state().pathIndex + 1 >= entry.route().blockPath().size();
    }

    private void tryInsertIntoTarget(TransitEntry entry) {
        TransportRoute route = entry.route();
        BlockPos target = route.targetConnector();
        if (target == null) {
            occupants.remove(entry.state().owner(), entry);
            sync(entry.state().owner());
            return;
        }
        if (!level.isLoaded(target)) {
            holdAtDestination(entry, route.spillsAtEnd()
                    ? TransitStatus.SPILLING
                    : TransitStatus.WAITING_AT_DESTINATION);
            return;
        }

        if (route.spillsAtEnd() && isRestoredInsertConnector(entry)) {
            route.setSpillsAtEnd(false);
        }
        if (route.spillsAtEnd()) {
            if (restoreBlockedPath(entry)) {
                return;
            }
            if (level.getBlockState(target).isAir()) {
                eject(entry.state().owner(), getOpenEndPosition(entry.state().owner(), target));
                return;
            }
            if (pathContainsMerger(entry)) {
                holdAtDestination(entry, TransitStatus.SPILLING);
                return;
            }
            holdAtDestination(entry, TransitStatus.SPILLING);
            return;
        }

        BlockEntity targetEntity = level.getBlockEntity(target);
        if (!(targetEntity instanceof TransportEndpoint endpoint)) {
            occupants.remove(entry.state().owner(), entry);
            sync(entry.state().owner());
            return;
        }
        if (!canInsertIntoTargetConnector(entry)) {
            holdAtDestination(entry, TransitStatus.WAITING_AT_DESTINATION);
            return;
        }

        ItemStack remaining = endpoint.insertCargo(level, entry.cargo().stack());
        if (remaining.isEmpty()) {
            occupants.remove(entry.state().owner(), entry);
            sync(entry.state().owner());
        } else {
            entry.cargo().setStack(remaining);
            holdAtDestination(entry, TransitStatus.WAITING_AT_DESTINATION);
        }
    }

    private void holdAtDestination(TransitEntry entry, TransitStatus status) {
        if (entry.state().status != status) {
            entry.state().status = status;
            sync(entry.state().owner());
        }
    }

    private boolean canInsertIntoTargetConnector(TransitEntry entry) {
        BlockPos target = entry.route().targetConnector();
        // Cargo stays in the last tube while crossing a pump, so its owner
        // need not be the path node directly connected to the destination.
        List<BlockPos> path = entry.route().blockPath();
        if (path.isEmpty()) {
            return false;
        }
        BlockPos lastNode = path.getLast();
        return level.getBlockEntity(target) instanceof TransportEndpoint endpoint
                && endpoint.canReceiveFrom(level, lastNode)
                && canTravelToNextPathNode(entry, entry.state().pathIndex)
                && PneumaticLine.isTravelAllowed(level, lastNode, target);
    }

    private boolean isRestoredInsertConnector(TransitEntry entry) {
        return canInsertIntoTargetConnector(entry);
    }

    private boolean restoreBlockedPath(TransitEntry entry) {
        BlockPos target = entry.route().targetConnector();
        if (!PneumaticLine.isPathNode(level, target)) {
            return false;
        }
        TubePath extension = TubeNetworkPathfinder.findPathToInsertConnector(
                level,
                entry.state().owner(),
                target,
                entry.cargo().stack()
        );
        if (extension.isEmpty()) {
            return false;
        }

        List<BlockPos> restored = new ArrayList<>(entry.route().blockPath()
                .subList(0, entry.state().pathIndex + 1));
        restored.addAll(extension.tubePositions());
        replaceRemainingRoute(entry, restored, extension.targetConnector(), extension.spillsAtEnd());
        entry.state().status = TransitStatus.MOVING;
        entry.state().progress = 0;
        entry.state().segmentProgress = 0.0f;
        entry.state().segmentDuration = calculateSegmentDuration(entry, entry.state().pathIndex);
        entry.state().startedAtGameTime = level.getGameTime();
        TubeNetworkPathfinder.commitDeviderChoices(level, extension);
        sync(entry.state().owner());
        return true;
    }

    private void replaceRemainingRoute(
            TransitEntry entry,
            List<BlockPos> path,
            BlockPos target,
            boolean spillsAtEnd
    ) {
        TransportRoute route = entry.route();
        route.replacePath(
                path,
                TubeGraphRoute.compile(level, path, route.sourceConnector(), target),
                target,
                spillsAtEnd
        );
        updateReservedMergerPassages(route, path);
        route.setSpeedControllers(findSpeedControllers(path));
        route.setSpeedControllerCacheValid(true);
        route.setTopologyVersion(topologyVersion);
        entry.state().lastSpeedCheckGameTime = Long.MIN_VALUE;
    }

    private boolean canMoveToNextPathNode(TransitEntry entry) {
        if (!canTravelToNextPathNode(entry, entry.state().pathIndex)) {
            return false;
        }
        int nextOwnerIndex = nextOwnerPathIndex(entry.route().blockPath(), entry.state().pathIndex);
        return nextOwnerIndex < 0 || !isOccupied(entry.route().blockPath().get(nextOwnerIndex));
    }

    private boolean canTravelToNextPathNode(TransitEntry entry, int currentIndex) {
        List<BlockPos> path = entry.route().blockPath();
        int nextIndex = currentIndex + 1;
        if (nextIndex >= path.size()) {
            return true;
        }

        BlockPos current = path.get(currentIndex);
        BlockPos next = path.get(nextIndex);
        if (!level.isLoaded(current) || !level.isLoaded(next)) {
            return false;
        }
        boolean finishingReservation = false;
        if (level.getBlockEntity(current) instanceof TransportJunction junction) {
            BlockPos previous = currentIndex > 0 ? path.get(currentIndex - 1) : entry.route().sourceConnector();
            finishingReservation = isReservedMergerPassage(entry, currentIndex);
            if (!finishingReservation && !junction.forwardPorts(previous, entry.cargo().stack()).contains(next)) {
                return false;
            }
        }
        if (level.getBlockEntity(next) instanceof TransportJunction junction) {
            if (junction.forwardPorts(current, entry.cargo().stack()).isEmpty()) {
                return false;
            }
            if (junction.isBranchPort(current) && !junction.canMergeFrom(level, current)) {
                return false;
            }
        }
        if (!PneumaticLine.isTravelAllowed(level, current, next) && !finishingReservation) {
            return false;
        }

        if (!isPassThroughNode(next) || ++nextIndex >= path.size()) {
            return true;
        }
        BlockPos afterPump = path.get(nextIndex);
        if (!level.isLoaded(afterPump)) {
            return false;
        }
        if (level.getBlockEntity(afterPump) instanceof TransportJunction junction) {
            if (junction.forwardPorts(next, entry.cargo().stack()).isEmpty()) {
                return false;
            }
            if (junction.isBranchPort(next) && !junction.canMergeFrom(level, next)) {
                return false;
            }
        }
        return PneumaticLine.isTravelAllowed(level, next, afterPump);
    }

    private boolean isReservedMergerPassage(TransitEntry entry, int dividerIndex) {
        List<BlockPos> path = entry.route().blockPath();
        return dividerIndex > 0
                && dividerIndex + 1 < path.size()
                && entry.route().reservedMergerPassages().contains(path.get(dividerIndex))
                && level.getBlockEntity(path.get(dividerIndex)) instanceof TransportJunction junction
                && junction.isMergerPassage(path.get(dividerIndex - 1), path.get(dividerIndex + 1));
    }

    private boolean pathContainsMerger(TransitEntry entry) {
        if (entry.route().protectedFromJunctionSpill()) {
            return true;
        }
        boolean contains = containsMergerPassage(entry.route().blockPath());
        entry.route().setProtectedFromJunctionSpill(contains);
        return contains;
    }

    private boolean containsMergerPassage(List<BlockPos> path) {
        for (int index = 1; index + 1 < path.size(); index++) {
            if (level.getBlockEntity(path.get(index)) instanceof TransportJunction junction
                    && junction.isMergerPassage(path.get(index - 1), path.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> findConfiguredMergerPassages(List<BlockPos> path) {
        List<BlockPos> passages = new ArrayList<>();
        for (int index = 1; index + 1 < path.size(); index++) {
            BlockPos junction = path.get(index);
            if (level.getBlockEntity(junction) instanceof TransportJunction node
                    && node.isMerger()
                    && node.isMergerPassage(path.get(index - 1), path.get(index + 1))) {
                passages.add(junction.immutable());
            }
        }
        return List.copyOf(passages);
    }

    private void updateReservedMergerPassages(TransportRoute route, List<BlockPos> path) {
        List<BlockPos> passages = new ArrayList<>();
        for (BlockPos reserved : route.reservedMergerPassages()) {
            if (path.contains(reserved)) {
                passages.add(reserved);
            }
        }
        for (BlockPos configured : findConfiguredMergerPassages(path)) {
            if (!passages.contains(configured)) {
                passages.add(configured);
            }
        }
        route.setReservedMergerPassages(passages);
    }

    private boolean hasRunningPump(TransitEntry entry) {
        ensureSpeedControllers(entry.route());
        int moveTime = calculateMoveTime(entry.route().speedControllers());
        if (moveTime <= 0) {
            entry.state().moveTime = 0;
            entry.state().segmentDuration = 0;
            return false;
        }
        return true;
    }

    private void ensureSpeedControllers(TransportRoute route) {
        if (route.speedControllerCacheValid() && route.topologyVersion() == topologyVersion) {
            return;
        }
        route.setSpeedControllers(findSpeedControllers(route.blockPath()));
        route.setSpeedControllerCacheValid(true);
        route.setTopologyVersion(topologyVersion);
    }

    private List<BlockPos> findSpeedControllers(List<BlockPos> path) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : path) {
            if (level.getBlockEntity(pos) instanceof TransportFlowSource) {
                result.add(pos.immutable());
            }
        }
        return List.copyOf(result);
    }

    private int calculateMoveTime(List<BlockPos> controllers) {
        int moveTime = 0;
        for (BlockPos pos : controllers) {
            if (level.getBlockEntity(pos) instanceof TransportFlowSource source && source.isRunning()) {
                moveTime = moveTime == 0
                        ? source.getMoveTime()
                        : Math.min(moveTime, source.getMoveTime());
            }
        }
        return moveTime;
    }

    private int getCurrentSegmentDuration(TransitEntry entry) {
        TransportRuntimeState state = entry.state();
        ensureSpeedControllers(entry.route());
        long gameTime = level.getGameTime();
        if (state.lastSpeedCheckGameTime == Long.MIN_VALUE
                || gameTime - state.lastSpeedCheckGameTime >= SPEED_CHECK_INTERVAL) {
            state.moveTime = calculateMoveTime(entry.route().speedControllers());
            state.lastSpeedCheckGameTime = gameTime;
        }
        if (state.moveTime <= 0) {
            state.segmentDuration = 0;
            return 0;
        }
        state.segmentDuration = calculateSegmentDuration(entry, state.pathIndex);
        return state.segmentDuration;
    }

    private int calculateSegmentDuration(TransitEntry entry, int pathIndex) {
        List<BlockPos> path = entry.route().blockPath();
        if (pathIndex < 0 || pathIndex >= path.size()) {
            return 0;
        }
        int fallback = calculateMoveTime(entry.route().speedControllers());
        if (fallback <= 0) {
            fallback = entry.state().moveTime;
        }
        if (fallback <= 0) {
            return 0;
        }

        int duration = 0;
        if (entry.route().sourceConnector() != null && pathIndex == entry.state().startPathIndex) {
            duration += scaleMoveTime(fallback, getSourceEntryDistance(entry));
            for (int edge = 0; edge < pathIndex; edge++) {
                duration += edgeDuration(path, edge, fallback);
            }
        }
        BlockEntity owner = level.getBlockEntity(path.get(pathIndex));
        if (owner instanceof CurvaturePneumaticTubeEntity || owner instanceof TransportJunction) {
            return Math.max(1, duration + fallback);
        }

        double curveIngressDistance = getCurveIngressDistance(path, pathIndex);
        if (curveIngressDistance > 1.0E-6) {
            duration += scaleMoveTime(fallback, curveIngressDistance);
        }

        int nextOwner = nextOwnerPathIndex(path, pathIndex);
        if (nextOwner < 0) {
            return Math.max(1, duration + scaleMoveTime(fallback, getTargetEdgeDistance(entry, pathIndex)));
        }
        for (int edge = pathIndex; edge < nextOwner; edge++) {
            duration += edgeDuration(path, edge, fallback);
        }
        return Math.max(1, duration);
    }

    private int edgeDuration(List<BlockPos> path, int edgeIndex, int moveTime) {
        double distance = Vec3.atCenterOf(path.get(edgeIndex))
                .distanceTo(Vec3.atCenterOf(path.get(edgeIndex + 1)));
        int pumpEndpoints = 0;
        if (isPassThroughNode(path.get(edgeIndex))) {
            pumpEndpoints++;
        }
        if (isPassThroughNode(path.get(edgeIndex + 1))) {
            pumpEndpoints++;
        }
        return scaleMoveTime(moveTime, distance * (1.0 - pumpEndpoints * 0.5));
    }

    private double getCurveIngressDistance(List<BlockPos> path, int pathIndex) {
        if (pathIndex <= 0 || pathIndex >= path.size()) {
            return 0.0;
        }
        BlockPos previousPos = path.get(pathIndex - 1);
        if (!(level.getBlockEntity(previousPos) instanceof CurvaturePneumaticTubeEntity curve)) {
            return 0.0;
        }
        Vec3 origin = Vec3.atLowerCornerOf(previousPos);
        Vec3 p0 = origin.add(curve.getP0());
        Vec3 p3 = origin.add(curve.getP3());
        Vec3 currentCenter = Vec3.atCenterOf(path.get(pathIndex));
        return Math.min(p0.distanceTo(currentCenter), p3.distanceTo(currentCenter));
    }

    private double getSourceEntryDistance(TransitEntry entry) {
        BlockPos source = entry.route().sourceConnector();
        if (source == null || entry.route().blockPath().isEmpty()) {
            return 0.0;
        }
        BlockPos first = entry.route().blockPath().get(0);
        Direction direction = directionBetween(source, first);
        if (direction == null) {
            return Vec3.atCenterOf(source).distanceTo(Vec3.atCenterOf(first));
        }
        Vec3 outlet = Vec3.atCenterOf(source)
                .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.42));
        return outlet.distanceTo(Vec3.atCenterOf(first));
    }

    private double getTargetEdgeDistance(TransitEntry entry, int pathIndex) {
        BlockPos target = entry.route().targetConnector();
        if (target == null) {
            return 1.0;
        }
        return Vec3.atCenterOf(entry.route().blockPath().get(pathIndex))
                .distanceTo(Vec3.atCenterOf(target));
    }

    private static int scaleMoveTime(int moveTime, double distance) {
        return Math.max(1, (int) Math.ceil(moveTime * Math.max(0.0, distance)));
    }

    private int nextOwnerPathIndex(List<BlockPos> path, int currentIndex) {
        for (int index = currentIndex + 1; index < path.size(); index++) {
            if (!isPassThroughNode(path.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private long createAnimationId(BlockPos owner) {
        return (level.getGameTime() ^ owner.asLong()) + animationSequence++;
    }

    private boolean isPassThroughNode(BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TransportNodeComponent node && !node.hasCargoSlot();
    }

    private Vec3 getOpenEndPosition(BlockPos owner, BlockPos missingSegment) {
        int deltaX = missingSegment.getX() - owner.getX();
        int deltaY = missingSegment.getY() - owner.getY();
        int deltaZ = missingSegment.getZ() - owner.getZ();
        if (deltaX == 0 && deltaY == 0 && deltaZ == 0) {
            return Vec3.atCenterOf(owner);
        }
        Direction direction = Direction.getNearest(deltaX, deltaY, deltaZ);
        return Vec3.atCenterOf(owner).add(
                direction.getStepX() * 0.5,
                direction.getStepY() * 0.5,
                direction.getStepZ() * 0.5
        );
    }

    @Nullable
    private static Direction directionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
    }

    private void sync(BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            if (blockEntity instanceof TransportQueueObserver observer) {
                TransitEntry entry = occupants.get(pos);
                boolean clogged = entry != null
                        && (entry.state().status == TransitStatus.WAITING_FOR_SPACE
                        || entry.state().status == TransitStatus.WAITING_AT_DESTINATION
                        || entry.state().status == TransitStatus.SPILLING);
                observer.onQueueStateChanged(level, clogged);
            }
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }

    private void markDirty(BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }
}
