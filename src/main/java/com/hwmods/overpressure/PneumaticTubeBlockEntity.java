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
import com.hwmods.overpressure.transport.TubeTransportManager;
import com.hwmods.overpressure.transport.ClientTubeTransportManager;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int BASE_MOVE_TIME = 12;
    public static final int MIN_PUMPED_MOVE_TIME = 2;
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
    private static long ponderAnimationSequence = Long.MIN_VALUE;
    private MovingTubeItem movingItem;
    private int tubeColor = 0xFFFFFF;
    private boolean tubeGlowing;

    public boolean isTubeGlowing() {
        return tubeGlowing;
    }

    public void setTubeGlowing(boolean glowing) {
        tubeGlowing = glowing;
        setChanged();
        requestModelDataUpdate();
        notifyUpdate();
    }

    public int getTubeColor() {
        return tubeColor;
    }

    public void setTubeColor(int color) {
        tubeColor = color & 0xFFFFFF;
        setChanged();
        notifyUpdate();
    }

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
    public void onLoad() {
        super.onLoad();
        if (level == null || level instanceof PonderLevel) {
            return;
        }
        if (level.isClientSide) {
            ClientTubeTransportManager.get(level).update(worldPosition, movingItem);
        } else if (movingItem != null) {
            TubeTransportManager.get(level).restore(worldPosition, movingItem);
        }
        movingItem = null;
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

    public boolean acceptItem(ItemStack stack, TubePath path) {
        return acceptItem(stack, path, null);
    }

    public boolean acceptItem(ItemStack stack, TubePath path, BlockPos sourceConnector) {
        return level != null
                && !level.isClientSide
                && TubeTransportManager.get(level).accept(worldPosition, stack, path, sourceConnector);
    }

    public boolean canAcceptItem(ItemStack stack, TubePath path) {
        return level != null
                && !level.isClientSide
                && TubeTransportManager.get(level).canAccept(worldPosition, stack, path);
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

    static boolean isOutputBranchSaturated(Level level, PneumaticTubeBlockEntity firstTube) {
        return TubeTransportManager.get(level).isOutputBranchSaturated(firstTube.worldPosition);
    }

    public void ejectMovingItem(Level level, Vec3 position) {
        if (!level.isClientSide) {
            TubeTransportManager.get(level).eject(worldPosition, position);
        }
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
        if (level == null || level instanceof PonderLevel) {
            return movingItem;
        }
        if (level.isClientSide) {
            return ClientTubeTransportManager.get(level).snapshot(worldPosition);
        }
        return TubeTransportManager.get(level).getSnapshot(worldPosition);
    }

    public MovingTubeItem getRenderMovingItem() {
        return getMovingItem();
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

        TubeTransportManager.get(level).invalidateTopology(pos);
    }

    private static long getTransportTopologyVersion(Level level) {
        return level == null || level.isClientSide ? 0L : TubeTransportManager.get(level).topologyVersion();
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

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("TubeColor", tubeColor);
        tag.putBoolean("TubeGlowing", tubeGlowing);
        MovingTubeItem localItem = movingItem;
        if (level != null && !level.isClientSide) {
            movingItem = TubeTransportManager.get(level).getSnapshot(worldPosition);
        }
        saveMovingItem(tag, registries);
        movingItem = localItem;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        int previousColor = tubeColor;
        boolean previousGlow = tubeGlowing;
        tubeGlowing = tag.getBoolean("TubeGlowing");
        tubeColor = tag.contains("TubeColor") ? tag.getInt("TubeColor") & 0xFFFFFF : 0xFFFFFF;
        if (clientPacket && (previousColor != tubeColor || previousGlow != tubeGlowing) && level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 16);
        }
        movingItem = loadMovingItem(tag, registries);
        relocateLoadedMovingItem();
        if (level != null && !(level instanceof PonderLevel)) {
            if (level.isClientSide) {
                ClientTubeTransportManager.get(level).update(worldPosition, movingItem);
            } else if (movingItem != null) {
                TubeTransportManager.get(level).restore(worldPosition, movingItem);
            }
            movingItem = null;
        }
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
