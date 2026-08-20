package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int BASE_MOVE_TIME = 24;
    public static final int MIN_PUMPED_MOVE_TIME = 4;
    private static final int SPEED_BAR_SEGMENTS = 18;
    private static final int SPEED_CHECK_INTERVAL = 2;
    private static final float CLIENT_MAX_OWNER_LEAD = 2.0f;
    private static final float CLIENT_QUEUE_SPACING = 0.72f;
    private static final float CLIENT_QUEUE_CATCH_UP_SPEED = 1.25f;
    private static final float CLIENT_MAX_QUEUE_RELEASE_LAG = 1.0f;
    private static final float CLIENT_QUEUE_EPSILON = 0.01f;
    private static final long CLIENT_MOTION_TTL = 200L;
    private static final Map<Level, Map<Long, ClientMotion>> CLIENT_MOTIONS = new WeakHashMap<>();
    private static final Map<Level, Long> CLIENT_LAST_CLEANUP = new WeakHashMap<>();
    private static final Map<Level, Long> TRANSPORT_TOPOLOGY_VERSIONS = new WeakHashMap<>();
    private static long ponderAnimationSequence = Long.MIN_VALUE;
    private MovingTubeItem movingItem;

    public record ClientRenderStep(int pathIndex, int nextOwnerPathIndex, float progress) {
    }

    private static class ClientMotion {
        private final List<BlockPos> path;
        private final List<Integer> ownerPathIndexes;
        private final List<BlockPos> reservedMergerPassages;
        private int authoritativeOwnerOrdinal;
        private BlockPos authoritativeOwner;
        private float routeProgress;
        private float routeLimit;
        private float lastRenderTime;
        private long lastSeenGameTime;
        private int cachedSegmentDurationPathIndex = -1;
        private int cachedSegmentDuration;
        private long lastSegmentDurationCheck = Long.MIN_VALUE;
        private boolean topologyDirty = true;
        private boolean recoveringFromQueue;

        private ClientMotion(
                MovingTubeItem item,
                List<Integer> ownerPathIndexes,
                int ownerOrdinal,
                BlockPos owner,
                float renderTime,
                long gameTime
        ) {
            path = List.copyOf(item.path);
            this.ownerPathIndexes = List.copyOf(ownerPathIndexes);
            reservedMergerPassages = List.copyOf(item.reservedMergerPassages);
            authoritativeOwnerOrdinal = ownerOrdinal;
            authoritativeOwner = owner.immutable();
            routeProgress = ownerOrdinal + item.segmentProgress;
            routeLimit = ownerPathIndexes.size();
            lastRenderTime = renderTime;
            lastSeenGameTime = gameTime;
        }
    }

    public PneumaticTubeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PNEUMATIC_TUBE.get(), pos, blockState);
    }

    protected PneumaticTubeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new BracketedBlockEntityBehaviour(this, state -> state.getBlock() instanceof PneumaticTubeBlock
                && !(state.getBlock() instanceof CurvaturePneumaticTubeBlock)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState blockState) {
        BlockState previousState = getBlockState();
        super.setBlockState(blockState);
        if (level != null && level.isClientSide && !previousState.equals(blockState)) {
            invalidateClientPathAt(level, worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int moveTime = getDisplayedMoveTime();
        addTransportSpeedTooltip(tooltip, moveTime);
        return true;
    }

    public int getDisplayedMoveTime() {
        MovingTubeItem item = getRenderMovingItem();

        if (item != null) {
            return item.moveTime;
        }

        return calculateConnectedLineMoveTime();
    }

    public static void addTransportSpeedTooltip(List<Component> tooltip, int moveTime) {
        int clampedMoveTime = Math.max(0, moveTime);
        double blocksPerSecond = clampedMoveTime == 0 ? 0.0 : 20.0 / clampedMoveTime;

        CreateLang.builder()
                .add(Component.translatable("overpressure.goggles.transport_speed")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip);
        CreateLang.builder()
                .add(speedBar(clampedMoveTime))
                .space()
                .add(CreateLang.number(blocksPerSecond)
                        .style(ChatFormatting.AQUA))
                .add(Component.translatable("overpressure.goggles.blocks_per_second")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
    }

    private static MutableComponent speedBar(int moveTime) {
        if (moveTime <= 0) {
            return Component.literal("|".repeat(SPEED_BAR_SEGMENTS)).withStyle(ChatFormatting.DARK_GRAY);
        }

        double speed = 20.0 / moveTime;
        double maxSpeed = 20.0 / Config.applyTubeSpeed(MIN_PUMPED_MOVE_TIME);
        int filled = Math.max(1, Math.min(SPEED_BAR_SEGMENTS, (int) Math.round(speed / maxSpeed * SPEED_BAR_SEGMENTS)));
        return Component.empty()
                .append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal("|".repeat(SPEED_BAR_SEGMENTS - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PneumaticTubeBlockEntity tube) {
        if (tube.movingItem != null) {
            tube.tickMovingItem(level);
        }
        tube.afterTransportTick(level);
    }

    protected void afterTransportTick(Level level) {
    }

    public boolean acceptItem(ItemStack stack, TubePath path) {
        return acceptItem(stack, path, null);
    }

    public boolean acceptItem(ItemStack stack, TubePath path, BlockPos sourceConnector) {
        if (!canAcceptItem(stack, path)) {
            return false;
        }

        int pathIndex = path.tubePositions().indexOf(worldPosition);
        List<MovingTubeItem.SpeedController> speedControllers = findSpeedControllers(path.tubePositions());

        movingItem = new MovingTubeItem(stack, path.tubePositions(), path.targetConnector());
        movingItem.sourceConnector = sourceConnector == null ? null : sourceConnector.immutable();
        movingItem.spillsAtEnd = path.spillsAtEnd();
        movingItem.protectedFromJunctionSpill = containsMergerPassage(level, path.tubePositions());
        movingItem.reservedMergerPassages = findConfiguredMergerPassages(level, path.tubePositions());
        movingItem.startPathIndex = pathIndex;
        movingItem.pathIndex = pathIndex;
        movingItem.speedControllers = speedControllers;
        movingItem.moveTime = calculateMoveTime(speedControllers);
        movingItem.segmentDuration = calculateSegmentDuration(movingItem, pathIndex);
        movingItem.hasSpeedControllerCache = true;
        movingItem.transportTopologyVersion = getTransportTopologyVersion(level);
        movingItem.animationId = createAnimationId(level);
        movingItem.startedAtGameTime = level.getGameTime();
        movingItem.lastTickedGameTime = level.getGameTime();

        setChanged();
        syncMovingItem(level);
        return true;
    }

    public boolean canAcceptItem(ItemStack stack, TubePath path) {
        if (movingItem != null || stack.isEmpty() || path.isEmpty() || !Config.canEnterTube(stack)) {
            return false;
        }

        int pathIndex = path.tubePositions().indexOf(worldPosition);

        if (pathIndex < 0) {
            return false;
        }

        List<MovingTubeItem.SpeedController> speedControllers = findSpeedControllers(path.tubePositions());
        if (!hasRunningPump(speedControllers)) {
            return false;
        }

        return true;
    }

    public boolean canTravelTo(Level level, Direction direction) {
        BlockState state = getBlockState();

        if (!(state.getBlock() instanceof PneumaticTubeBlock)) {
            return false;
        }

        if (!state.getValue(getConnectionProperty(direction))) {
            return canTravelToAdjacentCurvatureTube(level, direction);
        }

        Direction.Axis travelAxis = direction.getAxis();

        for (Direction connectedDirection : Direction.values()) {
            if (state.getValue(getConnectionProperty(connectedDirection))
                    && connectedDirection.getAxis() != travelAxis) {
                return false;
            }
        }

        return true;
    }

    private boolean canTravelToAdjacentCurvatureTube(Level level, Direction direction) {
        BlockEntity neighborBlockEntity = level.getBlockEntity(worldPosition.relative(direction));

        if (!(neighborBlockEntity instanceof CurvaturePneumaticTubeEntity curvatureTube)) {
            return false;
        }

        if (!curvatureTube.canTravelTo(level, direction.getOpposite())) {
            return false;
        }

        BlockState state = getBlockState();
        int connectedDirections = 0;
        Direction onlyConnectedDirection = null;

        for (Direction connectedDirection : Direction.values()) {
            if (state.getValue(getConnectionProperty(connectedDirection))) {
                connectedDirections++;
                onlyConnectedDirection = connectedDirection;
            }
        }

        if (connectedDirections == 0) {
            return true;
        }

        return connectedDirections == 1 && onlyConnectedDirection == direction.getOpposite();
    }

    private void tickMovingItem(Level level) {
        if (movingItem.lastTickedGameTime == level.getGameTime()) {
            return;
        }

        movingItem.lastTickedGameTime = level.getGameTime();

        if (movingItem.waitingAtDestination) {
            tryInsertIntoTargetConnector(level);
            return;
        }

        if (movingItem.waitingForNextTube) {
            tryRerouteDeviderOutput(level);

            if (markBrokenNextSegmentAsSpill(level)) {
                return;
            }

            if (!hasRunningPump(movingItem)) {
                return;
            }

            if (!canMoveToNextPathNode(level)) {
                return;
            }

            movingItem.waitingForNextTube = false;
            moveToNextPathNode(level);
            return;
        }

        if (!hasRunningPump(movingItem)) {
            return;
        }

        int segmentDuration = getCurrentSegmentDuration();
        if (segmentDuration <= 0) {
            return;
        }

        if (isApproachingFallbackEnd()
                && movingItem.segmentProgress + 1.0f / segmentDuration >= 0.5f) {
            tryInsertIntoTargetConnector(level);
            return;
        }

        if (isApproachingBrokenSegment(level)
                && movingItem.segmentProgress + 1.0f / segmentDuration >= 0.5f) {
            markBrokenNextSegmentAsSpill(level);
            return;
        }

        movingItem.segmentProgress = Math.min(1.0f, movingItem.segmentProgress + 1.0f / segmentDuration);
        movingItem.progress = Math.round(movingItem.segmentProgress * segmentDuration);

        if (movingItem.segmentProgress < 1.0f) {
            setChanged();
            return;
        }

        moveToNextPathNode(level);
    }

    private boolean tryRerouteDeviderOutput(Level level) {
        if (!(this instanceof DeviderBlockEntity devider)) {
            return false;
        }

        int nextPathIndex = movingItem.pathIndex + 1;
        if (nextPathIndex >= movingItem.path.size()) {
            return false;
        }

        BlockPos previousPos = movingItem.pathIndex > 0
                ? movingItem.path.get(movingItem.pathIndex - 1)
                : null;
        List<BlockPos> enabledOutputs = devider.getForwardPositions(previousPos);
        BlockPos currentOutput = movingItem.path.get(nextPathIndex);
        if (enabledOutputs.contains(currentOutput)
                && level.getBlockEntity(currentOutput) instanceof PneumaticTubeBlockEntity currentTube) {
            if (currentTube.movingItem == null || !isOutputBranchSaturated(level, currentTube)) {
                return false;
            }
        }

        for (BlockPos alternateOutput : enabledOutputs) {
            if (alternateOutput.equals(currentOutput)) {
                continue;
            }

            TubePath alternatePath = TubeNetworkPathfinder.findPathFromOccupiedTube(
                    level,
                    worldPosition,
                    alternateOutput
            );
            if (alternatePath.isEmpty() || alternatePath.spillsAtEnd()) {
                continue;
            }

            List<BlockPos> reroutedPath = new java.util.ArrayList<>(
                    movingItem.path.subList(0, movingItem.pathIndex + 1)
            );
            reroutedPath.addAll(alternatePath.tubePositions());
            movingItem.path = reroutedPath;
            updateReservedMergerPassages(level, movingItem, reroutedPath);
            movingItem.targetConnector = alternatePath.targetConnector();
            movingItem.spillsAtEnd = alternatePath.spillsAtEnd();
            movingItem.speedControllers = findSpeedControllers(reroutedPath);
            movingItem.hasSpeedControllerCache = true;
            movingItem.transportTopologyVersion = getTransportTopologyVersion(level);
            movingItem.lastSpeedCheckGameTime = Long.MIN_VALUE;
            movingItem.segmentDuration = calculateSegmentDuration(movingItem, movingItem.pathIndex);
            movingItem.waitingAtDestination = false;
            setChanged();
            syncMovingItem(level);
            return true;
        }

        return false;
    }

    static boolean isOutputBranchSaturated(Level level, PneumaticTubeBlockEntity firstTube) {
        Set<BlockPos> visited = new HashSet<>();
        PneumaticTubeBlockEntity tube = firstTube;

        while (visited.add(tube.worldPosition)) {
            MovingTubeItem queuedItem = tube.movingItem;
            if (queuedItem == null) {
                return false;
            }
            if (queuedItem.waitingAtDestination) {
                return true;
            }
            if (!queuedItem.waitingForNextTube) {
                return false;
            }

            int nextIndex = queuedItem.pathIndex + 1;
            if (nextIndex >= queuedItem.path.size()) {
                return true;
            }

            BlockEntity nextEntity = level.getBlockEntity(queuedItem.path.get(nextIndex));
            if (nextEntity instanceof ItemPumpBlockEntity) {
                nextIndex++;
                if (nextIndex >= queuedItem.path.size()) {
                    return true;
                }
                nextEntity = level.getBlockEntity(queuedItem.path.get(nextIndex));
            }

            if (!(nextEntity instanceof PneumaticTubeBlockEntity nextTube)) {
                return true;
            }
            tube = nextTube;
        }

        return true;
    }

    private void moveToNextPathNode(Level level) {
        int previousPathIndex = movingItem.pathIndex;
        if (!canTravelToNextPathNode(level, previousPathIndex)) {
            movingItem.waitingForNextTube = true;
            movingItem.waitingAtDestination = false;
            setChanged();
            syncMovingItem(level);
            return;
        }

        movingItem.progress = 0;
        movingItem.segmentProgress = 0.0f;
        movingItem.pathIndex++;

        if (movingItem.pathIndex >= movingItem.path.size()) {
            movingItem.pathIndex = previousPathIndex;
            tryInsertIntoTargetConnector(level);
            return;
        }

        BlockPos nextPos = movingItem.path.get(movingItem.pathIndex);
        BlockEntity nextBlockEntity = level.getBlockEntity(nextPos);

        if (nextBlockEntity instanceof PneumaticTubeBlockEntity nextTube && nextTube.movingItem == null) {
            if (nextTube instanceof DeviderBlockEntity devider && devider.isBranchPosition(worldPosition)) {
                devider.markMergeInputUsed(worldPosition);
            }
            transferToTube(level, nextTube);
        } else if (nextBlockEntity instanceof PneumaticTubeBlockEntity) {
            movingItem.pathIndex = previousPathIndex;
            movingItem.waitingForNextTube = true;
            setChanged();
            syncMovingItem(level);
        } else if (nextBlockEntity instanceof ItemPumpBlockEntity) {
            movingItem.pathIndex++;
            if (movingItem.pathIndex >= movingItem.path.size()) {
                movingItem.pathIndex = previousPathIndex;
                tryInsertIntoTargetConnector(level);
                return;
            }

            BlockEntity afterPumpBlockEntity = level.getBlockEntity(movingItem.path.get(movingItem.pathIndex));

            if (afterPumpBlockEntity instanceof PneumaticTubeBlockEntity afterPumpTube && afterPumpTube.movingItem == null) {
                if (afterPumpTube instanceof DeviderBlockEntity devider && devider.isBranchPosition(nextPos)) {
                    devider.markMergeInputUsed(nextPos);
                }
                transferToTube(level, afterPumpTube);
            } else if (!isPathNode(level, movingItem.path.get(movingItem.pathIndex))) {
                ejectMovingItem(level, Vec3.atCenterOf(nextPos));
            } else {
                movingItem.pathIndex = previousPathIndex;
                movingItem.waitingForNextTube = true;
                setChanged();
                syncMovingItem(level);
            }
        } else if (!(nextBlockEntity instanceof PneumaticTubeBlockEntity)) {
            movingItem.pathIndex = previousPathIndex;
            ejectMovingItem(level, getOpenEndPosition(nextPos));
        }
    }

    private void transferToTube(Level level, PneumaticTubeBlockEntity nextTube) {
        nextTube.movingItem = movingItem;
        nextTube.movingItem.progress = 0;
        nextTube.movingItem.segmentProgress = 0.0f;
        nextTube.movingItem.moveTime = nextTube.calculateMoveTime(nextTube.movingItem.speedControllers);
        nextTube.movingItem.segmentDuration = nextTube.calculateSegmentDuration(
                nextTube.movingItem,
                nextTube.movingItem.pathIndex
        );
        nextTube.movingItem.lastSpeedCheckGameTime = Long.MIN_VALUE;
        nextTube.movingItem.startedAtGameTime = level.getGameTime();
        nextTube.movingItem.waitingForNextTube = false;
        nextTube.movingItem.waitingAtDestination = false;
        movingItem = null;
        nextTube.setChanged();
        setChanged();
        nextTube.syncMovingItem(level);
        syncMovingItem(level);
    }

    public void ejectMovingItem(Level level, Vec3 position) {
        if (movingItem == null) {
            return;
        }

        ItemStack stack = movingItem.stack;
        movingItem = null;
        level.addFreshEntity(new ItemEntity(level, position.x, position.y, position.z, stack));
        setChanged();
        syncMovingItem(level);
    }

    private boolean isApproachingBrokenSegment(Level level) {
        return findBrokenNextSegment(level) != null;
    }

    @javax.annotation.Nullable
    private BlockPos findBrokenNextSegment(Level level) {
        int nextPathIndex = movingItem.pathIndex + 1;
        if (nextPathIndex >= movingItem.path.size()) {
            return null;
        }

        BlockPos nextPos = movingItem.path.get(nextPathIndex);
        if (!isPathNode(level, nextPos)) {
            return nextPos;
        }

        if (level.getBlockEntity(nextPos) instanceof ItemPumpBlockEntity
                && ++nextPathIndex < movingItem.path.size()) {
            BlockPos afterPumpPos = movingItem.path.get(nextPathIndex);
            if (!isPathNode(level, afterPumpPos)) {
                return afterPumpPos;
            }
        }

        return null;
    }

    private boolean markBrokenNextSegmentAsSpill(Level level) {
        BlockPos brokenSegment = findBrokenNextSegment(level);
        if (brokenSegment == null) {
            return false;
        }

        movingItem.targetConnector = brokenSegment;
        movingItem.spillsAtEnd = true;
        movingItem.waitingAtDestination = true;
        movingItem.waitingForNextTube = false;
        setChanged();
        syncMovingItem(level);
        tryInsertIntoTargetConnector(level);
        return true;
    }

    private boolean isApproachingFallbackEnd() {
        return movingItem.spillsAtEnd && movingItem.pathIndex + 1 >= movingItem.path.size();
    }

    private Vec3 getOpenEndPosition(BlockPos missingSegment) {
        Direction direction = Direction.getNearest(
                missingSegment.getX() - worldPosition.getX(),
                missingSegment.getY() - worldPosition.getY(),
                missingSegment.getZ() - worldPosition.getZ()
        );
        return Vec3.atCenterOf(worldPosition).add(
                direction.getStepX() * 0.5,
                direction.getStepY() * 0.5,
                direction.getStepZ() * 0.5
        );
    }

    private static boolean isPathNode(Level level, BlockPos pos) {
        return PneumaticLine.isPathNode(level, pos);
    }

    private void tryInsertIntoTargetConnector(Level level) {
        if (movingItem.spillsAtEnd && isRestoredInsertConnector(level)) {
            movingItem.spillsAtEnd = false;
        }

        if (movingItem.spillsAtEnd) {
            if (restoreBlockedPath(level)) {
                return;
            }

            if (level.getBlockState(movingItem.targetConnector).isAir()) {
                ejectMovingItem(level, getOpenEndPosition(movingItem.targetConnector));
                return;
            }

            if (pathContainsRedstoneMerger(level)) {
                holdMovingItemForRoute(level);
                return;
            }

            boolean wasWaitingAtDestination = movingItem.waitingAtDestination;
            movingItem.waitingAtDestination = true;
            movingItem.waitingForNextTube = false;

            if (!wasWaitingAtDestination) {
                setChanged();
                syncMovingItem(level);
            }
            return;
        }

        BlockEntity targetBlockEntity = level.getBlockEntity(movingItem.targetConnector);

        if (!(targetBlockEntity instanceof PneumaticConnectionBlockEntity connector)) {
            movingItem = null;
            setChanged();
            syncMovingItem(level);
            return;
        }

        if (!canInsertIntoTargetConnector(level)) {
            holdMovingItemForRoute(level);
            return;
        }

        boolean wasWaitingAtDestination = movingItem.waitingAtDestination;
        ItemStack remaining = connector.insertIntoAttachedInventory(level, movingItem.stack);

        if (remaining.isEmpty()) {
            movingItem = null;
            setChanged();
            syncMovingItem(level);
        } else {
            movingItem.stack = remaining;
            movingItem.waitingAtDestination = true;
            movingItem.waitingForNextTube = false;

            if (!wasWaitingAtDestination) {
                setChanged();
                syncMovingItem(level);
            }
        }
    }

    private boolean canInsertIntoTargetConnector(Level level) {
        BlockState targetState = level.getBlockState(movingItem.targetConnector);
        if (!(targetState.getBlock() instanceof PneumaticConnectionBlock)
                || targetState.getValue(PneumaticConnectionBlock.MODE)
                != PneumaticConnectionBlock.ConnectionMode.INSERT) {
            return false;
        }

        Direction direction = Direction.getNearest(
                movingItem.targetConnector.getX() - worldPosition.getX(),
                movingItem.targetConnector.getY() - worldPosition.getY(),
                movingItem.targetConnector.getZ() - worldPosition.getZ()
        );
        return targetState.getValue(PneumaticConnectionBlock.FACING) == direction
                && canTravelTo(level, direction);
    }

    private boolean pathContainsRedstoneMerger(Level level) {
        if (movingItem.protectedFromJunctionSpill) {
            return true;
        }

        movingItem.protectedFromJunctionSpill = containsMergerPassage(level, movingItem.path);
        return movingItem.protectedFromJunctionSpill;
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

    private static List<BlockPos> findConfiguredMergerPassages(Level level, List<BlockPos> path) {
        List<BlockPos> passages = new java.util.ArrayList<>();
        for (int index = 1; index + 1 < path.size(); index++) {
            BlockPos junctionPos = path.get(index);
            if (level.getBlockEntity(junctionPos) instanceof DeviderBlockEntity devider
                    && devider.getJunctionRole() == DeviderBlockEntity.JunctionRole.MERGER
                    && devider.isMergerPassage(path.get(index - 1), path.get(index + 1))) {
                passages.add(junctionPos.immutable());
            }
        }
        return List.copyOf(passages);
    }

    private static void updateReservedMergerPassages(
            Level level,
            MovingTubeItem item,
            List<BlockPos> path
    ) {
        List<BlockPos> passages = new java.util.ArrayList<>();
        for (BlockPos reserved : item.reservedMergerPassages) {
            if (path.contains(reserved)) {
                passages.add(reserved);
            }
        }
        for (BlockPos configured : findConfiguredMergerPassages(level, path)) {
            if (!passages.contains(configured)) {
                passages.add(configured);
            }
        }
        item.reservedMergerPassages = List.copyOf(passages);
    }

    private void holdMovingItemForRoute(Level level) {
        boolean stateChanged = !movingItem.waitingAtDestination || movingItem.waitingForNextTube;
        movingItem.waitingAtDestination = true;
        movingItem.waitingForNextTube = false;
        if (stateChanged) {
            setChanged();
            syncMovingItem(level);
        }
    }

    private boolean isRestoredInsertConnector(Level level) {
        BlockEntity targetBlockEntity = level.getBlockEntity(movingItem.targetConnector);
        if (!(targetBlockEntity instanceof PneumaticConnectionBlockEntity)
                || level.getBlockState(movingItem.targetConnector).getValue(PneumaticConnectionBlock.MODE)
                != PneumaticConnectionBlock.ConnectionMode.INSERT) {
            return false;
        }

        Direction direction = Direction.getNearest(
                movingItem.targetConnector.getX() - worldPosition.getX(),
                movingItem.targetConnector.getY() - worldPosition.getY(),
                movingItem.targetConnector.getZ() - worldPosition.getZ()
        );
        return getBlockState().getValue(getConnectionProperty(direction))
                && level.getBlockState(movingItem.targetConnector).getValue(PneumaticConnectionBlock.FACING) == direction;
    }

    private boolean restoreBlockedPath(Level level) {
        if (!isPathNode(level, movingItem.targetConnector)) {
            return false;
        }

        TubePath extension = TubeNetworkPathfinder.findPathToInsertConnector(
                level,
                worldPosition,
                movingItem.targetConnector
        );
        if (extension.isEmpty()) {
            return false;
        }

        List<BlockPos> restoredPath = new java.util.ArrayList<>(
                movingItem.path.subList(0, movingItem.pathIndex + 1)
        );
        restoredPath.addAll(extension.tubePositions());
        movingItem.path = restoredPath;
        updateReservedMergerPassages(level, movingItem, restoredPath);
        movingItem.targetConnector = extension.targetConnector();
        movingItem.spillsAtEnd = extension.spillsAtEnd();
        movingItem.speedControllers = findSpeedControllers(restoredPath);
        movingItem.hasSpeedControllerCache = true;
        movingItem.transportTopologyVersion = getTransportTopologyVersion(level);
        movingItem.lastSpeedCheckGameTime = Long.MIN_VALUE;
        movingItem.waitingAtDestination = false;
        movingItem.waitingForNextTube = false;
        movingItem.progress = 0;
        movingItem.segmentProgress = 0.0f;
        movingItem.segmentDuration = calculateSegmentDuration(movingItem, movingItem.pathIndex);
        movingItem.startedAtGameTime = level.getGameTime();
        TubeNetworkPathfinder.commitDeviderChoices(level, extension);
        setChanged();
        syncMovingItem(level);
        return true;
    }

    private boolean canMoveToNextPathNode(Level level) {
        if (!canTravelToNextPathNode(level, movingItem.pathIndex)) {
            return false;
        }

        int nextPathIndex = movingItem.pathIndex + 1;
        if (nextPathIndex >= movingItem.path.size()) {
            return true;
        }

        BlockEntity nextBlockEntity = level.getBlockEntity(movingItem.path.get(nextPathIndex));
        if (nextBlockEntity instanceof PneumaticTubeBlockEntity nextTube) {
            return nextTube.movingItem == null;
        }

        if (!(nextBlockEntity instanceof ItemPumpBlockEntity) || ++nextPathIndex >= movingItem.path.size()) {
            return true;
        }

        BlockEntity afterPumpBlockEntity = level.getBlockEntity(movingItem.path.get(nextPathIndex));
        return !(afterPumpBlockEntity instanceof PneumaticTubeBlockEntity afterPumpTube)
                || afterPumpTube.movingItem == null;
    }

    private boolean canTravelToNextPathNode(Level level, int currentPathIndex) {
        int nextPathIndex = currentPathIndex + 1;
        if (nextPathIndex >= movingItem.path.size()) {
            return true;
        }

        BlockPos currentPos = movingItem.path.get(currentPathIndex);
        BlockPos nextPos = movingItem.path.get(nextPathIndex);
        boolean finishingExistingMergerPassage = false;
        if (level.getBlockEntity(currentPos) instanceof DeviderBlockEntity devider) {
            BlockPos previousPos = currentPathIndex > 0
                    ? movingItem.path.get(currentPathIndex - 1)
                    : movingItem.sourceConnector;
            finishingExistingMergerPassage = isReservedMergerPassage(
                    level,
                    movingItem.path,
                    movingItem.reservedMergerPassages,
                    currentPathIndex
            );
            if (!finishingExistingMergerPassage
                    && !devider.getForwardPositions(previousPos).contains(nextPos)) {
                return false;
            }
        }
        if (level.getBlockEntity(nextPos) instanceof DeviderBlockEntity devider
                && devider.getForwardPositions(currentPos).isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(nextPos) instanceof DeviderBlockEntity devider
                && devider.isBranchPosition(currentPos)
                && !devider.canMergeFrom(level, currentPos)) {
            return false;
        }
        if (!canMoveBetween(currentPos, nextPos)
                && !finishingExistingMergerPassage) {
            return false;
        }

        if (!(level.getBlockEntity(nextPos) instanceof ItemPumpBlockEntity) || ++nextPathIndex >= movingItem.path.size()) {
            return true;
        }

        BlockPos afterPumpPos = movingItem.path.get(nextPathIndex);
        if (level.getBlockEntity(afterPumpPos) instanceof DeviderBlockEntity devider
                && devider.getForwardPositions(nextPos).isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(afterPumpPos) instanceof DeviderBlockEntity devider
                && devider.isBranchPosition(nextPos)
                && !devider.canMergeFrom(level, nextPos)) {
            return false;
        }
        return canMoveBetween(nextPos, afterPumpPos);
    }

    private static boolean isMergerPassage(Level level, List<BlockPos> path, int dividerPathIndex) {
        if (dividerPathIndex <= 0 || dividerPathIndex + 1 >= path.size()) {
            return false;
        }

        return level.getBlockEntity(path.get(dividerPathIndex)) instanceof DeviderBlockEntity devider
                && devider.isMergerPassage(
                path.get(dividerPathIndex - 1),
                path.get(dividerPathIndex + 1)
        );
    }

    private static boolean isReservedMergerPassage(
            Level level,
            List<BlockPos> path,
            List<BlockPos> reservedPassages,
            int dividerPathIndex
    ) {
        return dividerPathIndex >= 0
                && dividerPathIndex < path.size()
                && reservedPassages.contains(path.get(dividerPathIndex))
                && isMergerPassage(level, path, dividerPathIndex);
    }

    public MovingTubeItem getMovingItem() {
        return movingItem;
    }

    public MovingTubeItem getRenderMovingItem() {
        return movingItem;
    }

    /**
     * Starts a visual-only transport inside a Ponder schematic.
     *
     * Ponder uses a client-side schematic level, so the normal server transport
     * ticker is not available there. Keeping the demo item in the regular tube
     * renderer makes packages follow exactly the same path as real transported
     * items without touching inventories or spawning dropped items.
     */
    public void startPonderTransport(
            ItemStack stack,
            List<BlockPos> path,
            BlockPos sourceConnector,
            BlockPos targetConnector,
            int moveTime
    ) {
        if (!(level instanceof PonderLevel)
                || movingItem != null
                || stack.isEmpty()
                || path.isEmpty()) {
            return;
        }

        int pathIndex = path.indexOf(worldPosition);
        if (pathIndex < 0) {
            return;
        }

        MovingTubeItem item = new MovingTubeItem(stack.copy(), List.copyOf(path), targetConnector.immutable());
        item.sourceConnector = sourceConnector.immutable();
        item.startsAtTubeOpenEnd = !(level.getBlockEntity(sourceConnector)
                instanceof PneumaticConnectionBlockEntity);
        item.endsAtTubeOpenEnd = !(level.getBlockEntity(targetConnector)
                instanceof PneumaticConnectionBlockEntity);
        item.startPathIndex = pathIndex;
        item.pathIndex = pathIndex;
        item.speedControllers = List.of();
        item.moveTime = Math.max(MIN_PUMPED_MOVE_TIME, moveTime);
        item.segmentDuration = calculateSegmentDuration(item, pathIndex);
        item.hasSpeedControllerCache = true;
        item.transportTopologyVersion = getTransportTopologyVersion(level);
        item.animationId = nextPonderAnimationId();
        item.startedAtGameTime = level.getGameTime();
        item.lastTickedGameTime = Long.MIN_VALUE;
        movingItem = item;
        setChanged();
    }

    /** Advances one Ponder-only package by one scene tick. */
    public void tickPonderTransport() {
        if (!(level instanceof PonderLevel) || movingItem == null) {
            return;
        }

        if (movingItem.waitingAtDestination) {
            return;
        }

        int segmentDuration = Math.max(1, movingItem.segmentDuration);
        movingItem.segmentProgress = Math.min(
                1.0f,
                movingItem.segmentProgress + 1.0f / segmentDuration
        );
        movingItem.progress = Math.round(movingItem.segmentProgress * segmentDuration);
        if (movingItem.segmentProgress < 1.0f) {
            setChanged();
            return;
        }

        int nextOwnerPathIndex = findNextOwnerPathIndex(movingItem.path, movingItem.pathIndex);
        if (nextOwnerPathIndex < 0) {
            movingItem.waitingAtDestination = true;
            setChanged();
            return;
        }

        BlockEntity nextBlockEntity = level.getBlockEntity(movingItem.path.get(nextOwnerPathIndex));
        if (!(nextBlockEntity instanceof PneumaticTubeBlockEntity nextTube)
                || nextTube.movingItem != null) {
            movingItem.waitingForNextTube = true;
            setChanged();
            return;
        }

        if (isPoweredPonderValve(this) || isPoweredPonderValve(nextTube)) {
            movingItem.waitingForNextTube = true;
            setChanged();
            return;
        }

        MovingTubeItem item = movingItem;
        movingItem = null;
        item.pathIndex = nextOwnerPathIndex;
        item.progress = 0;
        item.segmentProgress = 0.0f;
        item.segmentDuration = nextTube.calculateSegmentDuration(item, nextOwnerPathIndex);
        item.startedAtGameTime = level.getGameTime();
        item.waitingForNextTube = false;
        item.waitingAtDestination = false;
        nextTube.movingItem = item;
        nextTube.setChanged();
        setChanged();
    }

    /** Changes the speed of an item already travelling in a Ponder scene. */
    public void setPonderTransportMoveTime(int moveTime) {
        if (!(level instanceof PonderLevel) || movingItem == null) {
            return;
        }

        movingItem.moveTime = Math.max(MIN_PUMPED_MOVE_TIME, moveTime);
        movingItem.segmentDuration = calculateSegmentDuration(movingItem, movingItem.pathIndex);
        setChanged();
    }

    /** Removes a package once the receiving Packager starts its Ponder animation. */
    public void finishPonderTransport() {
        if (!(level instanceof PonderLevel)
                || movingItem == null
                || !movingItem.waitingAtDestination) {
            return;
        }

        clearPonderTransport();
    }

    /** Clears unfinished demo items when a Ponder scene ends or restarts. */
    public void clearPonderTransport() {
        if (!(level instanceof PonderLevel) || movingItem == null) {
            return;
        }

        long animationId = movingItem.animationId;
        movingItem = null;
        Map<Long, ClientMotion> motions = CLIENT_MOTIONS.get(level);
        if (motions != null) {
            motions.remove(animationId);
        }
        setChanged();
    }

    private static synchronized long nextPonderAnimationId() {
        return ponderAnimationSequence++;
    }

    private static boolean isPoweredPonderValve(PneumaticTubeBlockEntity tube) {
        return tube instanceof ValveBlockEntity
                && tube.getBlockState().hasProperty(BlockStateProperties.POWERED)
                && tube.getBlockState().getValue(BlockStateProperties.POWERED);
    }

    public boolean shouldRenderMovingItem(float partialTick) {
        return getClientRenderStep(partialTick) != null;
    }

    public float getMovingProgress(float partialTick) {
        ClientRenderStep step = getClientRenderStep(partialTick);
        return step == null ? 0.0f : step.progress();
    }

    public ClientRenderStep getClientRenderStep(float partialTick) {
        MovingTubeItem item = getRenderMovingItem();
        if (item == null
                || item.pathIndex < 0
                || item.pathIndex >= item.path.size()
                || !item.path.get(item.pathIndex).equals(worldPosition)) {
            return null;
        }

        if (level == null || !level.isClientSide) {
            int nextOwnerPathIndex = findNextOwnerPathIndex(item.path, item.pathIndex);
            return new ClientRenderStep(
                    item.pathIndex,
                    nextOwnerPathIndex,
                    getRenderSegmentProgress(item, item.pathIndex, nextOwnerPathIndex, item.segmentProgress)
            );
        }

        ensureSpeedControllers(item);
        float renderTime = getClientGameTime() + partialTick;
        long gameTime = level.getGameTime();
        Map<Long, ClientMotion> motions = CLIENT_MOTIONS.computeIfAbsent(level, ignored -> new HashMap<>());
        cleanupClientMotions(level, motions, gameTime);

        ClientMotion motion = motions.get(item.animationId);
        if (motion == null || !motion.path.equals(item.path)) {
            motion = createClientMotion(item, renderTime, gameTime);
            if (motion == null) {
                return null;
            }
            motions.put(item.animationId, motion);
        }

        int ownerOrdinal = motion.ownerPathIndexes.indexOf(item.pathIndex);
        if (ownerOrdinal < 0) {
            motions.remove(item.animationId);
            return null;
        }
        if (ownerOrdinal < motion.authoritativeOwnerOrdinal) {
            return null;
        }

        if (ownerOrdinal > motion.authoritativeOwnerOrdinal) {
            motion.authoritativeOwnerOrdinal = ownerOrdinal;
            motion.authoritativeOwner = worldPosition.immutable();
        } else if (!motion.authoritativeOwner.equals(worldPosition)) {
            return null;
        }

        if (motion.topologyDirty) {
            refreshClientRouteLimit(motion);
        }

        float elapsed = Math.max(0.0f, renderTime - motion.lastRenderTime);
        motion.lastRenderTime = renderTime;
        motion.lastSeenGameTime = gameTime;

        float serverProgress = ownerOrdinal + item.segmentProgress;
        float movementLimit = Math.min(motion.routeLimit, getClientQueueLimit(motion, item));
        boolean queueConstrained = movementLimit + CLIENT_QUEUE_EPSILON < serverProgress;
        if (queueConstrained) {
            motion.recoveringFromQueue = true;
        }

        float constrainedServerProgress = Math.min(serverProgress, movementLimit);
        if (item.waitingForNextTube || item.waitingAtDestination) {
            motion.routeProgress = Math.max(motion.routeProgress, constrainedServerProgress);
        } else {
            motion.routeProgress = Math.max(motion.routeProgress, constrainedServerProgress);
            float catchUpMultiplier = motion.recoveringFromQueue && !queueConstrained
                    ? CLIENT_QUEUE_CATCH_UP_SPEED
                    : 1.0f;
            advanceClientRoute(motion, item, elapsed * catchUpMultiplier, movementLimit);
        }

        float authorityFloor;
        if (queueConstrained) {
            authorityFloor = constrainedServerProgress;
        } else if (motion.recoveringFromQueue) {
            authorityFloor = Math.max(0.0f, serverProgress - CLIENT_MAX_QUEUE_RELEASE_LAG);
            if (motion.routeProgress + CLIENT_QUEUE_EPSILON >= serverProgress) {
                motion.recoveringFromQueue = false;
            }
        } else {
            authorityFloor = serverProgress;
        }
        float leadLimit = Math.min(
                motion.ownerPathIndexes.size(),
                ownerOrdinal + CLIENT_MAX_OWNER_LEAD
        );
        motion.routeProgress = Math.max(authorityFloor, Math.min(motion.routeProgress, leadLimit));
        return toClientRenderStep(motion, item);
    }

    private ClientMotion createClientMotion(MovingTubeItem item, float renderTime, long gameTime) {
        List<Integer> ownerPathIndexes = findOwnerPathIndexes(item.path);
        int ownerOrdinal = ownerPathIndexes.indexOf(item.pathIndex);
        if (ownerOrdinal < 0) {
            return null;
        }

        ClientMotion motion = new ClientMotion(
                item,
                ownerPathIndexes,
                ownerOrdinal,
                worldPosition,
                renderTime,
                gameTime
        );

        if (!item.waitingForNextTube && !item.waitingAtDestination) {
            int segmentDuration = getClientSegmentDuration(motion, item, item.pathIndex);
            if (segmentDuration > 0) {
                float elapsedSinceStart = Math.max(0.0f, renderTime - item.startedAtGameTime);
                motion.routeProgress = Math.max(
                        motion.routeProgress,
                        ownerOrdinal + elapsedSinceStart / segmentDuration
                );
            }
        }
        return motion;
    }

    private List<Integer> findOwnerPathIndexes(List<BlockPos> path) {
        List<Integer> ownerPathIndexes = new ArrayList<>();
        for (int pathIndex = 0; pathIndex < path.size(); pathIndex++) {
            if (!(level.getBlockState(path.get(pathIndex)).getBlock() instanceof ItemPumpBlock)) {
                ownerPathIndexes.add(pathIndex);
            }
        }
        return ownerPathIndexes;
    }

    private int findNextOwnerPathIndex(List<BlockPos> path, int currentPathIndex) {
        for (int pathIndex = currentPathIndex + 1; pathIndex < path.size(); pathIndex++) {
            if (level == null || !(level.getBlockState(path.get(pathIndex)).getBlock() instanceof ItemPumpBlock)) {
                return pathIndex;
            }
        }
        return -1;
    }

    private void advanceClientRoute(
            ClientMotion motion,
            MovingTubeItem item,
            float elapsedTicks,
            float movementLimit
    ) {
        float maxProgress = Math.min(
                motion.ownerPathIndexes.size(),
                Math.min(movementLimit, motion.authoritativeOwnerOrdinal + CLIENT_MAX_OWNER_LEAD)
        );

        while (elapsedTicks > 0.0f && motion.routeProgress < maxProgress) {
            int ownerOrdinal = Math.min(
                    (int) Math.floor(motion.routeProgress),
                    motion.ownerPathIndexes.size() - 1
            );
            int pathIndex = motion.ownerPathIndexes.get(ownerOrdinal);
            int segmentDuration = getClientSegmentDuration(motion, item, pathIndex);
            if (segmentDuration <= 0) {
                return;
            }

            float segmentEnd = Math.min(ownerOrdinal + 1.0f, maxProgress);
            float ticksToSegmentEnd = (segmentEnd - motion.routeProgress) * segmentDuration;
            if (elapsedTicks < ticksToSegmentEnd) {
                motion.routeProgress += elapsedTicks / segmentDuration;
                return;
            }

            motion.routeProgress = segmentEnd;
            elapsedTicks -= ticksToSegmentEnd;
        }
    }

    private float getClientQueueLimit(ClientMotion motion, MovingTubeItem item) {
        int nextOwnerOrdinal = motion.authoritativeOwnerOrdinal + 1;
        if (nextOwnerOrdinal >= motion.ownerPathIndexes.size()) {
            return motion.ownerPathIndexes.size();
        }

        int nextOwnerPathIndex = motion.ownerPathIndexes.get(nextOwnerOrdinal);
        BlockEntity nextOwner = level.getBlockEntity(item.path.get(nextOwnerPathIndex));
        if (!(nextOwner instanceof PneumaticTubeBlockEntity nextTube)) {
            return motion.routeLimit;
        }

        MovingTubeItem nextItem = nextTube.getMovingItem();
        if (nextItem == null || nextItem.animationId == item.animationId) {
            return motion.ownerPathIndexes.size();
        }

        if (!motion.path.equals(nextItem.path)) {
            return motion.authoritativeOwnerOrdinal + 1.0f;
        }

        if (motion.routeLimit + CLIENT_QUEUE_EPSILON < motion.ownerPathIndexes.size()) {
            int queuedItemsAhead = 0;
            int lastQueueOwnerOrdinal = Math.min(
                    motion.ownerPathIndexes.size() - 1,
                    (int) Math.floor(motion.routeLimit)
            );
            for (int ownerOrdinal = nextOwnerOrdinal;
                 ownerOrdinal <= lastQueueOwnerOrdinal;
                 ownerOrdinal++) {
                int ownerPathIndex = motion.ownerPathIndexes.get(ownerOrdinal);
                BlockEntity owner = level.getBlockEntity(item.path.get(ownerPathIndex));
                if (!(owner instanceof PneumaticTubeBlockEntity queuedTube)) {
                    break;
                }
                MovingTubeItem queuedItem = queuedTube.getMovingItem();
                if (queuedItem == null || queuedItem.animationId == item.animationId) {
                    break;
                }
                queuedItemsAhead++;
            }

            return Math.max(
                    motion.authoritativeOwnerOrdinal,
                    motion.routeLimit - queuedItemsAhead * CLIENT_QUEUE_SPACING
            );
        }

        float nextItemProgress = nextOwnerOrdinal + nextItem.segmentProgress;
        Map<Long, ClientMotion> motions = CLIENT_MOTIONS.get(level);
        if (motions != null) {
            ClientMotion nextMotion = motions.get(nextItem.animationId);
            if (nextMotion != null && motion.path.equals(nextMotion.path)) {
                nextItemProgress = nextMotion.routeProgress;
            }
        }

        return Math.max(
                motion.authoritativeOwnerOrdinal,
                nextItemProgress - CLIENT_QUEUE_SPACING
        );
    }

    private int getClientSegmentDuration(ClientMotion motion, MovingTubeItem item, int pathIndex) {
        int authoritativePathIndex = motion.ownerPathIndexes.get(motion.authoritativeOwnerOrdinal);
        if (pathIndex == authoritativePathIndex) {
            return item.segmentDuration;
        }

        long gameTime = level.getGameTime();
        if (motion.cachedSegmentDurationPathIndex == pathIndex
                && motion.lastSegmentDurationCheck != Long.MIN_VALUE
                && gameTime - motion.lastSegmentDurationCheck < SPEED_CHECK_INTERVAL) {
            return motion.cachedSegmentDuration;
        }

        ensureSpeedControllers(item);
        motion.cachedSegmentDuration = calculateSegmentDuration(item, pathIndex);
        if (motion.cachedSegmentDuration <= 0 && item.segmentDuration > 0) {
            motion.cachedSegmentDuration = item.segmentDuration;
        }
        motion.cachedSegmentDurationPathIndex = pathIndex;
        motion.lastSegmentDurationCheck = gameTime;
        return motion.cachedSegmentDuration;
    }

    private ClientRenderStep toClientRenderStep(ClientMotion motion, MovingTubeItem item) {
        int ownerCount = motion.ownerPathIndexes.size();
        if (ownerCount == 0) {
            return null;
        }

        int ownerOrdinal = (int) Math.floor(motion.routeProgress);
        float progress;
        if (ownerOrdinal >= ownerCount) {
            ownerOrdinal = ownerCount - 1;
            progress = 1.0f;
        } else {
            progress = motion.routeProgress - ownerOrdinal;
        }

        int pathIndex = motion.ownerPathIndexes.get(ownerOrdinal);
        int nextOwnerPathIndex = ownerOrdinal + 1 < ownerCount
                ? motion.ownerPathIndexes.get(ownerOrdinal + 1)
                : -1;
        return new ClientRenderStep(
                pathIndex,
                nextOwnerPathIndex,
                getRenderSegmentProgress(item, pathIndex, nextOwnerPathIndex, progress)
        );
    }

    private void refreshClientRouteLimit(ClientMotion motion) {
        motion.routeLimit = motion.ownerPathIndexes.size();
        motion.cachedSegmentDurationPathIndex = -1;
        motion.lastSegmentDurationCheck = Long.MIN_VALUE;

        int authoritativePathIndex = motion.ownerPathIndexes.get(motion.authoritativeOwnerOrdinal);
        for (int pathIndex = authoritativePathIndex;
             pathIndex + 1 < motion.path.size();
             pathIndex++) {
            boolean oldMergerPassageAfterRoleChange = isMergerPassageAfterRoleChange(
                    level,
                    motion.path,
                    motion.reservedMergerPassages,
                    pathIndex
            );
            if (PneumaticLine.isTravelAllowed(level, motion.path.get(pathIndex), motion.path.get(pathIndex + 1))
                    || oldMergerPassageAfterRoleChange) {
                continue;
            }

            int ownerOrdinal = findOwnerOrdinalAtOrBefore(motion.ownerPathIndexes, pathIndex);
            if (ownerOrdinal >= 0) {
                BlockEntity owner = level.getBlockEntity(motion.path.get(
                        motion.ownerPathIndexes.get(ownerOrdinal)
                ));
                float boundary = owner instanceof CurvaturePneumaticTubeEntity
                        || owner instanceof DeviderBlockEntity ? 1.0f : 0.42f;
                motion.routeLimit = ownerOrdinal + boundary;
            }
            break;
        }
        motion.topologyDirty = false;
    }

    private static boolean isMergerPassageAfterRoleChange(
            Level level,
            List<BlockPos> path,
            List<BlockPos> reservedPassages,
            int dividerPathIndex
    ) {
        return isReservedMergerPassage(level, path, reservedPassages, dividerPathIndex)
                && level.getBlockEntity(path.get(dividerPathIndex)) instanceof DeviderBlockEntity devider
                && devider.getJunctionRole() == DeviderBlockEntity.JunctionRole.DIVIDER;
    }

    private static int findOwnerOrdinalAtOrBefore(List<Integer> ownerPathIndexes, int pathIndex) {
        for (int ownerOrdinal = ownerPathIndexes.size() - 1; ownerOrdinal >= 0; ownerOrdinal--) {
            if (ownerPathIndexes.get(ownerOrdinal) <= pathIndex) {
                return ownerOrdinal;
            }
        }
        return -1;
    }

    public static void invalidateClientPathAt(Level level, BlockPos pos) {
        if (level == null || !level.isClientSide) {
            return;
        }

        Map<Long, ClientMotion> motions = CLIENT_MOTIONS.get(level);
        if (motions == null) {
            return;
        }

        for (ClientMotion motion : motions.values()) {
            if (motion.path.contains(pos)) {
                motion.topologyDirty = true;
            }
        }
    }

    public static void invalidateTransportTopologyAt(Level level, BlockPos pos) {
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            invalidateClientPathAt(level, pos);
            return;
        }

        long currentVersion = TRANSPORT_TOPOLOGY_VERSIONS.getOrDefault(level, 0L);
        TRANSPORT_TOPOLOGY_VERSIONS.put(level, currentVersion == Long.MAX_VALUE ? 0L : currentVersion + 1L);
    }

    private static long getTransportTopologyVersion(Level level) {
        return level == null || level.isClientSide
                ? 0L
                : TRANSPORT_TOPOLOGY_VERSIONS.getOrDefault(level, 0L);
    }

    private static void cleanupClientMotions(Level level, Map<Long, ClientMotion> motions, long gameTime) {
        long lastCleanup = CLIENT_LAST_CLEANUP.getOrDefault(level, Long.MIN_VALUE);
        if (lastCleanup != Long.MIN_VALUE && gameTime - lastCleanup < CLIENT_MOTION_TTL / 2) {
            return;
        }

        motions.values().removeIf(motion -> gameTime - motion.lastSeenGameTime > CLIENT_MOTION_TTL);
        CLIENT_LAST_CLEANUP.put(level, gameTime);
    }

    private float getClientGameTime() {
        return level == null ? 0.0f : level.getGameTime();
    }
    private int getCurrentMoveTime() {
        if (movingItem == null || level == null) {
            return BASE_MOVE_TIME;
        }

        ensureSpeedControllers(movingItem);
        if (movingItem.speedControllers.isEmpty()) {
            movingItem.moveTime = 0;
            movingItem.segmentDuration = 0;
            return 0;
        }

        long gameTime = level.getGameTime();
        if (movingItem.lastSpeedCheckGameTime != Long.MIN_VALUE
                && gameTime - movingItem.lastSpeedCheckGameTime < SPEED_CHECK_INTERVAL) {
            return movingItem.moveTime;
        }

        movingItem.moveTime = calculateMoveTime(movingItem.speedControllers);
        movingItem.lastSpeedCheckGameTime = gameTime;
        return movingItem.moveTime;
    }

    private int getCurrentSegmentDuration() {
        int moveTime = getCurrentMoveTime();
        if (movingItem == null || moveTime <= 0) {
            if (movingItem != null) {
                movingItem.segmentDuration = 0;
            }
            return 0;
        }

        movingItem.segmentDuration = calculateSegmentDuration(movingItem, movingItem.pathIndex);
        return movingItem.segmentDuration;
    }

    private long createAnimationId(Level level) {
        return level.getGameTime() ^ worldPosition.asLong();
    }

    private void syncMovingItem(Level level) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        saveMovingItem(tag, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        movingItem = loadMovingItem(tag, registries);
        relocateLoadedMovingItem();
    }

    private void relocateLoadedMovingItem() {
        if (movingItem == null
                || movingItem.pathIndex < 0
                || movingItem.pathIndex >= movingItem.path.size()) {
            return;
        }

        BlockPos savedPosition = movingItem.path.get(movingItem.pathIndex);
        BlockPos offset = worldPosition.subtract(savedPosition);
        if (offset.equals(BlockPos.ZERO)) {
            return;
        }

        movingItem.path = offsetPositions(movingItem.path, offset);
        movingItem.reservedMergerPassages = offsetPositions(movingItem.reservedMergerPassages, offset);
        movingItem.speedControllers = List.of();
        movingItem.hasSpeedControllerCache = false;
        if (movingItem.sourceConnector != null) {
            movingItem.sourceConnector = movingItem.sourceConnector.offset(offset);
        }
        movingItem.targetConnector = movingItem.targetConnector.offset(offset);
    }

    private static List<BlockPos> offsetPositions(List<BlockPos> positions, BlockPos offset) {
        List<BlockPos> relocated = new java.util.ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            relocated.add(position.offset(offset));
        }
        return relocated;
    }

    private void saveMovingItem(CompoundTag tag, HolderLookup.Provider registries) {
        if (movingItem == null) {
            tag.remove("moving_item");
            return;
        }

        CompoundTag movingTag = new CompoundTag();
        movingTag.put("stack", movingItem.stack.save(registries));
        if (movingItem.sourceConnector != null) {
            movingTag.put("source", saveBlockPos(movingItem.sourceConnector));
        }
        movingTag.put("target", saveBlockPos(movingItem.targetConnector));
        movingTag.putBoolean("spills_at_end", movingItem.spillsAtEnd);
        movingTag.putInt("start_path_index", movingItem.startPathIndex);
        movingTag.putInt("path_index", movingItem.pathIndex);
        movingTag.putInt("progress", movingItem.progress);
        movingTag.putFloat("segment_progress", movingItem.segmentProgress);
        movingTag.putInt("move_time", movingItem.moveTime);
        movingTag.putInt("segment_duration", movingItem.segmentDuration);
        movingTag.putLong("animation_id", movingItem.animationId);
        movingTag.putLong("started_at", movingItem.startedAtGameTime);
        movingTag.putBoolean("waiting_for_next", movingItem.waitingForNextTube);
        movingTag.putBoolean("waiting_at_destination", movingItem.waitingAtDestination);
        movingTag.putBoolean("protected_from_junction_spill", movingItem.protectedFromJunctionSpill);

        ListTag pathTag = new ListTag();

        for (BlockPos pathPos : movingItem.path) {
            pathTag.add(saveBlockPos(pathPos));
        }

        movingTag.put("path", pathTag);

        ListTag reservedPassagesTag = new ListTag();
        for (BlockPos passagePos : movingItem.reservedMergerPassages) {
            reservedPassagesTag.add(saveBlockPos(passagePos));
        }
        movingTag.put("reserved_merger_passages", reservedPassagesTag);
        tag.put("moving_item", movingTag);
    }

    private MovingTubeItem loadMovingItem(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("moving_item", Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag movingTag = tag.getCompound("moving_item");
        ItemStack stack = ItemStack.parseOptional(registries, movingTag.getCompound("stack"));

        if (stack.isEmpty()) {
            return null;
        }

        ListTag pathTag = movingTag.getList("path", Tag.TAG_COMPOUND);
        java.util.List<BlockPos> path = new java.util.ArrayList<>();

        for (int i = 0; i < pathTag.size(); i++) {
            path.add(loadBlockPos(pathTag.getCompound(i)));
        }

        if (path.isEmpty()) {
            return null;
        }

        MovingTubeItem item = new MovingTubeItem(stack, path, loadBlockPos(movingTag.getCompound("target")));
        ListTag reservedPassagesTag = movingTag.getList("reserved_merger_passages", Tag.TAG_COMPOUND);
        List<BlockPos> reservedPassages = new java.util.ArrayList<>();
        for (int i = 0; i < reservedPassagesTag.size(); i++) {
            reservedPassages.add(loadBlockPos(reservedPassagesTag.getCompound(i)));
        }
        item.reservedMergerPassages = List.copyOf(reservedPassages);
        item.sourceConnector = movingTag.contains("source", Tag.TAG_COMPOUND)
                ? loadBlockPos(movingTag.getCompound("source"))
                : null;
        item.spillsAtEnd = movingTag.getBoolean("spills_at_end");
        item.startPathIndex = movingTag.contains("start_path_index", Tag.TAG_INT)
                ? movingTag.getInt("start_path_index")
                : 0;
        item.pathIndex = movingTag.getInt("path_index");
        item.progress = movingTag.getInt("progress");
        item.moveTime = movingTag.contains("move_time", Tag.TAG_INT)
                ? Math.max(0, movingTag.getInt("move_time"))
                : BASE_MOVE_TIME;
        item.segmentDuration = movingTag.contains("segment_duration", Tag.TAG_INT)
                ? Math.max(0, movingTag.getInt("segment_duration"))
                : item.moveTime;
        item.segmentProgress = movingTag.contains("segment_progress", Tag.TAG_FLOAT)
                ? movingTag.getFloat("segment_progress")
                : Math.min(1.0f, item.progress / (float) Math.max(1, item.segmentDuration));
        item.animationId = movingTag.getLong("animation_id");
        item.startedAtGameTime = movingTag.getLong("started_at");
        item.waitingForNextTube = movingTag.getBoolean("waiting_for_next");
        item.waitingAtDestination = movingTag.getBoolean("waiting_at_destination");
        item.protectedFromJunctionSpill = movingTag.getBoolean("protected_from_junction_spill");
        item.hasSpeedControllerCache = false;
        return item;
    }

    private int calculateMoveTime(List<MovingTubeItem.SpeedController> controllers) {
        if (level == null) {
            return BASE_MOVE_TIME;
        }

        int moveTime = 0;
        for (MovingTubeItem.SpeedController controller : controllers) {
            BlockPos controllerPos = controller.pos();
            if (level.getBlockEntity(controllerPos) instanceof ItemPumpBlockEntity pump && pump.isRunning()) {
                moveTime = moveTime == 0 ? pump.getMoveTime() : Math.min(moveTime, pump.getMoveTime());
            }
        }
        return moveTime;
    }

    private int calculateSegmentDuration(MovingTubeItem item, int pathIndex) {
        if (item == null || pathIndex < 0 || pathIndex >= item.path.size()) {
            return 0;
        }

        int fallbackMoveTime = calculateMoveTime(item.speedControllers);
        if (fallbackMoveTime <= 0) {
            fallbackMoveTime = item.moveTime;
        }
        if (fallbackMoveTime <= 0) {
            return 0;
        }

        int duration = 0;
        if (item.sourceConnector != null && pathIndex == item.startPathIndex) {
            duration += scaleMoveTime(fallbackMoveTime, getSourceEntryDistance(item));
            for (int edgeIndex = 0; edgeIndex < pathIndex; edgeIndex++) {
                duration += getSegmentEdgeDuration(item, edgeIndex, fallbackMoveTime);
            }
        }

        BlockEntity owner = level == null ? null : level.getBlockEntity(item.path.get(pathIndex));
        if (owner instanceof CurvaturePneumaticTubeEntity || owner instanceof DeviderBlockEntity) {
            return duration + fallbackMoveTime;
        }

        double curveIngressDistance = getCurveIngressDistance(item, pathIndex);
        if (curveIngressDistance > 1.0E-6) {
            duration += scaleMoveTime(fallbackMoveTime, curveIngressDistance);
        }

        int nextOwnerPathIndex = findNextOwnerPathIndex(item.path, pathIndex);
        if (nextOwnerPathIndex <= pathIndex) {
            return duration + scaleMoveTime(fallbackMoveTime, getTargetEdgeDistance(item, pathIndex));
        }

        for (int edgeIndex = pathIndex; edgeIndex < nextOwnerPathIndex; edgeIndex++) {
            duration += getSegmentEdgeDuration(item, edgeIndex, fallbackMoveTime);
        }
        return Math.max(1, duration);
    }

    private float getRenderSegmentProgress(
            MovingTubeItem item,
            int pathIndex,
            int nextOwnerPathIndex,
            float temporalProgress
    ) {
        float clampedProgress = Math.max(0.0f, Math.min(1.0f, temporalProgress));
        if (clampedProgress <= 0.0f || clampedProgress >= 1.0f || level == null) {
            return clampedProgress;
        }

        BlockEntity owner = level.getBlockEntity(item.path.get(pathIndex));
        if (owner instanceof CurvaturePneumaticTubeEntity || owner instanceof DeviderBlockEntity) {
            return clampedProgress;
        }

        int fallbackMoveTime = calculateMoveTime(item.speedControllers);
        if (fallbackMoveTime <= 0) {
            fallbackMoveTime = item.moveTime;
        }
        if (fallbackMoveTime <= 0) {
            return clampedProgress;
        }

        double totalDistance = getSegmentDistance(item, pathIndex, nextOwnerPathIndex);
        int totalDuration = calculateSegmentDuration(item, pathIndex);
        if (totalDistance < 1.0E-6 || totalDuration <= 0) {
            return clampedProgress;
        }

        double elapsedTicks = clampedProgress * totalDuration;
        double traveledDistance = 0.0;
        int elapsedDuration = 0;

        if (item.sourceConnector != null && pathIndex == item.startPathIndex) {
            double sourceDistance = getSourceEntryDistance(item);
            int sourceDuration = scaleMoveTime(fallbackMoveTime, sourceDistance);
            if (elapsedTicks <= elapsedDuration + sourceDuration) {
                double localProgress = (elapsedTicks - elapsedDuration) / sourceDuration;
                return (float) ((traveledDistance + sourceDistance * localProgress) / totalDistance);
            }
            elapsedDuration += sourceDuration;
            traveledDistance += sourceDistance;

            for (int edgeIndex = 0; edgeIndex < pathIndex; edgeIndex++) {
                double edgeDistance = getPathEdgeDistance(item, edgeIndex);
                int edgeDuration = getSegmentEdgeDuration(item, edgeIndex, fallbackMoveTime);
                if (elapsedTicks <= elapsedDuration + edgeDuration) {
                    double localProgress = (elapsedTicks - elapsedDuration) / edgeDuration;
                    return (float) ((traveledDistance + edgeDistance * localProgress) / totalDistance);
                }
                elapsedDuration += edgeDuration;
                traveledDistance += edgeDistance;
            }
        }

        double curveIngressDistance = getCurveIngressDistance(item, pathIndex);
        if (curveIngressDistance > 1.0E-6) {
            int curveIngressDuration = scaleMoveTime(fallbackMoveTime, curveIngressDistance);
            if (elapsedTicks <= elapsedDuration + curveIngressDuration) {
                double localProgress = (elapsedTicks - elapsedDuration) / curveIngressDuration;
                return (float) ((traveledDistance + curveIngressDistance * localProgress) / totalDistance);
            }
            elapsedDuration += curveIngressDuration;
            traveledDistance += curveIngressDistance;
        }

        if (nextOwnerPathIndex > pathIndex) {
            for (int edgeIndex = pathIndex; edgeIndex < nextOwnerPathIndex; edgeIndex++) {
                double edgeDistance = getPathEdgeDistance(item, edgeIndex);
                int edgeDuration = getSegmentEdgeDuration(item, edgeIndex, fallbackMoveTime);
                if (elapsedTicks <= elapsedDuration + edgeDuration) {
                    double localProgress = (elapsedTicks - elapsedDuration) / edgeDuration;
                    return (float) ((traveledDistance + edgeDistance * localProgress) / totalDistance);
                }
                elapsedDuration += edgeDuration;
                traveledDistance += edgeDistance;
            }
        } else {
            double targetDistance = getTargetEdgeDistance(item, pathIndex);
            int targetDuration = scaleMoveTime(fallbackMoveTime, targetDistance);
            double localProgress = Math.min(1.0, (elapsedTicks - elapsedDuration) / targetDuration);
            return (float) ((traveledDistance + targetDistance * localProgress) / totalDistance);
        }

        return 1.0f;
    }

    private double getSegmentDistance(MovingTubeItem item, int pathIndex, int nextOwnerPathIndex) {
        double distance = getCurveIngressDistance(item, pathIndex);
        if (item.sourceConnector != null && pathIndex == item.startPathIndex) {
            distance += getSourceEntryDistance(item);
            for (int edgeIndex = 0; edgeIndex < pathIndex; edgeIndex++) {
                distance += getPathEdgeDistance(item, edgeIndex);
            }
        }

        if (nextOwnerPathIndex > pathIndex) {
            for (int edgeIndex = pathIndex; edgeIndex < nextOwnerPathIndex; edgeIndex++) {
                distance += getPathEdgeDistance(item, edgeIndex);
            }
            return distance;
        }
        return distance + getTargetEdgeDistance(item, pathIndex);
    }

    private double getCurveIngressDistance(MovingTubeItem item, int pathIndex) {
        if (level == null || item == null || pathIndex <= 0 || pathIndex >= item.path.size()) {
            return 0.0;
        }

        BlockPos previousPos = item.path.get(pathIndex - 1);
        if (!(level.getBlockEntity(previousPos) instanceof CurvaturePneumaticTubeEntity curvatureTube)) {
            return 0.0;
        }

        Vec3 previousOrigin = Vec3.atLowerCornerOf(previousPos);
        Vec3 p0 = previousOrigin.add(curvatureTube.getP0());
        Vec3 p3 = previousOrigin.add(curvatureTube.getP3());
        Vec3 currentCenter = Vec3.atCenterOf(item.path.get(pathIndex));
        return Math.min(p0.distanceTo(currentCenter), p3.distanceTo(currentCenter));
    }

    private int getSegmentEdgeDuration(MovingTubeItem item, int edgeIndex, int fallbackMoveTime) {
        return scaleMoveTime(fallbackMoveTime, getTransportEdgeDistance(item, edgeIndex));
    }

    private double getTransportEdgeDistance(MovingTubeItem item, int edgeIndex) {
        double distance = getPathEdgeDistance(item, edgeIndex);
        if (level == null || distance < 1.0E-6) {
            return distance;
        }

        int pumpEndpoints = 0;
        if (level.getBlockState(item.path.get(edgeIndex)).getBlock() instanceof ItemPumpBlock) {
            pumpEndpoints++;
        }
        if (level.getBlockState(item.path.get(edgeIndex + 1)).getBlock() instanceof ItemPumpBlock) {
            pumpEndpoints++;
        }

        // A pump has no item slot of its own. Count only the half-edge on each
        // side so that tube -> pump -> tube keeps the same cadence as tube -> tube.
        return distance * (1.0 - pumpEndpoints * 0.5);
    }

    private static int scaleMoveTime(int moveTime, double distance) {
        return Math.max(1, (int) Math.ceil(moveTime * Math.max(0.0, distance)));
    }

    private static double getPathEdgeDistance(MovingTubeItem item, int edgeIndex) {
        if (edgeIndex < 0 || edgeIndex + 1 >= item.path.size()) {
            return 0.0;
        }
        return Vec3.atCenterOf(item.path.get(edgeIndex))
                .distanceTo(Vec3.atCenterOf(item.path.get(edgeIndex + 1)));
    }

    private static double getTargetEdgeDistance(MovingTubeItem item, int pathIndex) {
        if (item.targetConnector == null || pathIndex < 0 || pathIndex >= item.path.size()) {
            return 1.0;
        }
        if (item.endsAtTubeOpenEnd) {
            return 0.42;
        }
        return Vec3.atCenterOf(item.path.get(pathIndex))
                .distanceTo(Vec3.atCenterOf(item.targetConnector));
    }

    private double getSourceEntryDistance(MovingTubeItem item) {
        if (item.sourceConnector == null || item.path.isEmpty()) {
            return 0.0;
        }

        if (item.startsAtTubeOpenEnd) {
            return 0.42;
        }

        BlockPos firstPathPos = item.path.get(0);
        Direction direction = Direction.getNearest(
                firstPathPos.getX() - item.sourceConnector.getX(),
                firstPathPos.getY() - item.sourceConnector.getY(),
                firstPathPos.getZ() - item.sourceConnector.getZ()
        );
        Vec3 sourceOutlet = Vec3.atCenterOf(item.sourceConnector)
                .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.42));
        return sourceOutlet.distanceTo(Vec3.atCenterOf(firstPathPos));
    }

    private boolean hasRunningPump(MovingTubeItem item) {
        if (level == null || item == null) {
            return false;
        }

        ensureSpeedControllers(item);
        int moveTime = calculateMoveTime(item.speedControllers);
        if (moveTime <= 0) {
            item.moveTime = 0;
            item.segmentDuration = 0;
            return false;
        }
        return true;
    }

    private boolean hasRunningPump(List<MovingTubeItem.SpeedController> speedControllers) {
        return calculateMoveTime(speedControllers) > 0;
    }

    private void ensureSpeedControllers(MovingTubeItem item) {
        long topologyVersion = getTransportTopologyVersion(level);
        if (item.hasSpeedControllerCache && item.transportTopologyVersion == topologyVersion) {
            return;
        }
        item.speedControllers = findSpeedControllers(item.path);
        item.hasSpeedControllerCache = true;
        item.transportTopologyVersion = topologyVersion;
        item.lastSpeedCheckGameTime = Long.MIN_VALUE;
    }

    private List<MovingTubeItem.SpeedController> findSpeedControllers(List<BlockPos> path) {
        if (level == null || path.isEmpty()) {
            return List.of();
        }

        java.util.ArrayList<MovingTubeItem.SpeedController> speedControllers = new java.util.ArrayList<>();
        for (BlockPos pathPos : path) {
            if (level.getBlockEntity(pathPos) instanceof ItemPumpBlockEntity) {
                speedControllers.add(new MovingTubeItem.SpeedController(pathPos.immutable()));
            }
        }
        return List.copyOf(speedControllers);
    }

    private int calculateConnectedLineMoveTime() {
        if (level == null) {
            return BASE_MOVE_TIME;
        }

        int moveTime = 0;
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        visited.add(worldPosition);
        queue.add(worldPosition);

        while (!queue.isEmpty() && visited.size() < 128) {
            BlockPos current = queue.remove();
            BlockEntity currentBlockEntity = level.getBlockEntity(current);

            if (currentBlockEntity instanceof ItemPumpBlockEntity pump && pump.isRunning()) {
                moveTime = moveTime == 0 ? pump.getMoveTime() : Math.min(moveTime, pump.getMoveTime());
            }

            if (currentBlockEntity instanceof DeviderBlockEntity && !current.equals(worldPosition)) {
                continue;
            }

            for (BlockPos next : PneumaticLine.getForwardNeighbors(level, current)) {

                if (visited.contains(next)) {
                    continue;
                }

                if (!canMoveBetween(current, next)) {
                    continue;
                }

                visited.add(next);
                queue.add(next);
            }
        }

        return moveTime;
    }

    private boolean canMoveBetween(BlockPos from, BlockPos to, Direction direction) {
        BlockEntity fromBlockEntity = level.getBlockEntity(from);
        BlockEntity toBlockEntity = level.getBlockEntity(to);

        if (!PneumaticLine.isPathNode(level, to)) {
            return false;
        }

        if (fromBlockEntity instanceof ValveBlockEntity fromValve
                && !((ValveBlock) fromValve.getBlockState().getBlock())
                .allowsTravel(fromValve.getBlockState(), direction)) {
            return false;
        }

        if (toBlockEntity instanceof ValveBlockEntity toValve
                && !((ValveBlock) toValve.getBlockState().getBlock())
                .allowsTravel(toValve.getBlockState(), direction)) {
            return false;
        }

        if (fromBlockEntity instanceof ItemPumpBlockEntity fromPump
                && !fromPump.canTravelTo(level, direction)) {
            return false;
        }

        if (fromBlockEntity instanceof ItemPumpBlockEntity fromPump) {
            ItemPumpBlock pump = (ItemPumpBlock) fromPump.getBlockState().getBlock();
            if (!pump.allowsTravel(level, from, fromPump.getBlockState(), direction)) {
                return false;
            }
        }

        if (fromBlockEntity instanceof PneumaticTubeBlockEntity fromTube
                && !fromTube.canTravelTo(level, direction)) {
            return false;
        }

        if (toBlockEntity instanceof ItemPumpBlockEntity toPump) {
            if (!toPump.canTravelTo(level, direction.getOpposite())) {
                return false;
            }

            ItemPumpBlock pump = (ItemPumpBlock) toPump.getBlockState().getBlock();
            return pump.allowsTravel(level, to, toPump.getBlockState(), direction);
        }

        if (toBlockEntity instanceof PneumaticTubeBlockEntity toTube
                && toTube.canTravelTo(level, direction.getOpposite())) {
            return true;
        }

        return false;
    }

    private boolean canMoveBetween(BlockPos from, BlockPos to) {
        BlockEntity fromBlockEntity = level.getBlockEntity(from);
        BlockEntity toBlockEntity = level.getBlockEntity(to);
        if (fromBlockEntity instanceof DeviderBlockEntity || toBlockEntity instanceof DeviderBlockEntity) {
            return PneumaticLine.isTravelAllowed(level, from, to);
        }

        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return canMoveBetween(from, to, direction);
            }
        }
        return false;
    }

    private CompoundTag saveBlockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private BlockPos loadBlockPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    protected BooleanProperty getConnectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> PneumaticTubeBlock.NORTH;
            case SOUTH -> PneumaticTubeBlock.SOUTH;
            case EAST -> PneumaticTubeBlock.EAST;
            case WEST -> PneumaticTubeBlock.WEST;
            case UP -> PneumaticTubeBlock.UP;
            case DOWN -> PneumaticTubeBlock.DOWN;
        };
    }
}
