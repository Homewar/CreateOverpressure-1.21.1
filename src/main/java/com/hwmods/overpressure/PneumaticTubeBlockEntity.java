package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

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
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int BASE_MOVE_TIME = 24;
    public static final int MIN_PUMPED_MOVE_TIME = 4;
    private static final int SPEED_BAR_SEGMENTS = 18;
    private static final int SPEED_CHECK_INTERVAL = 2;
    private static final Map<Long, ClientMotion> CLIENT_MOTIONS = new HashMap<>();
    private MovingTubeItem movingItem;

    private static class ClientMotion {
        private int pathIndex;
        private boolean waitingForNextTube;
        private boolean waitingAtDestination;
        private float progress;
        private float lastRenderTime;

        private ClientMotion(MovingTubeItem item, float renderTime) {
            pathIndex = item.pathIndex;
            waitingForNextTube = item.waitingForNextTube;
            waitingAtDestination = item.waitingAtDestination;
            progress = item.segmentProgress;
            lastRenderTime = renderTime;
        }

        private boolean matches(MovingTubeItem item) {
            return pathIndex == item.pathIndex
                    && waitingForNextTube == item.waitingForNextTube
                    && waitingAtDestination == item.waitingAtDestination;
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
        int clampedMoveTime = moveTime <= 0 ? 0
                : Math.max(MIN_PUMPED_MOVE_TIME, Math.min(BASE_MOVE_TIME, moveTime));
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
        double maxSpeed = 20.0 / MIN_PUMPED_MOVE_TIME;
        int filled = Math.max(1, Math.min(SPEED_BAR_SEGMENTS, (int) Math.round(speed / maxSpeed * SPEED_BAR_SEGMENTS)));
        return Component.empty()
                .append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal("|".repeat(SPEED_BAR_SEGMENTS - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PneumaticTubeBlockEntity tube) {
        if (tube.movingItem != null) {
            tube.tickMovingItem(level);
        }
    }

    public boolean acceptItem(ItemStack stack, TubePath path) {
        if (!canAcceptItem(stack, path)) {
            return false;
        }

        int pathIndex = path.tubePositions().indexOf(worldPosition);
        List<BlockPos> speedControllers = findSpeedControllers(path.tubePositions());

        movingItem = new MovingTubeItem(stack, path.tubePositions(), path.targetConnector());
        movingItem.spillsAtEnd = path.spillsAtEnd();
        movingItem.pathIndex = pathIndex;
        movingItem.moveTime = calculateMoveTime(path);
        movingItem.speedControllers = speedControllers;
        movingItem.hasSpeedControllerCache = true;
        movingItem.animationId = createAnimationId(level);
        movingItem.startedAtGameTime = level.getGameTime();
        movingItem.lastTickedGameTime = level.getGameTime();

        setChanged();
        syncMovingItem(level);
        return true;
    }

    public boolean canAcceptItem(ItemStack stack, TubePath path) {
        if (movingItem != null || stack.isEmpty() || path.isEmpty()) {
            return false;
        }

        int pathIndex = path.tubePositions().indexOf(worldPosition);

        if (pathIndex < 0) {
            return false;
        }

        List<BlockPos> speedControllers = findSpeedControllers(path.tubePositions());
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
            tryRerouteBlockedDeviderOutput(level);

            if (!canMoveToNextPathNode(level)) {
                return;
            }

            movingItem.waitingForNextTube = false;
            moveToNextPathNode(level);
            return;
        }

        if (!hasRunningPump(movingItem.speedControllers)) {
            return;
        }

        if (isApproachingFallbackEnd()
                && movingItem.segmentProgress + 1.0f / getCurrentMoveTime() >= 0.5f) {
            tryInsertIntoTargetConnector(level);
            return;
        }

        if (isApproachingBrokenSegment(level)
                && movingItem.segmentProgress + 1.0f / getCurrentMoveTime() >= 0.5f) {
            BlockPos brokenSegment = movingItem.path.get(movingItem.pathIndex + 1);
            movingItem.targetConnector = brokenSegment;
            movingItem.spillsAtEnd = true;
            movingItem.waitingAtDestination = true;
            movingItem.waitingForNextTube = false;
            setChanged();
            syncMovingItem(level);
            tryInsertIntoTargetConnector(level);
            return;
        }

        movingItem.moveTime = getCurrentMoveTime();
        movingItem.segmentProgress = Math.min(1.0f, movingItem.segmentProgress + 1.0f / movingItem.moveTime);
        movingItem.progress = Math.round(movingItem.segmentProgress * movingItem.moveTime);

        if (movingItem.segmentProgress < 1.0f) {
            setChanged();
            return;
        }

        moveToNextPathNode(level);
    }

    private boolean tryRerouteBlockedDeviderOutput(Level level) {
        if (!(this instanceof DeviderBlockEntity devider)) {
            return false;
        }

        int nextPathIndex = movingItem.pathIndex + 1;
        if (nextPathIndex >= movingItem.path.size()) {
            return false;
        }

        BlockPos blockedOutput = movingItem.path.get(nextPathIndex);
        if (!devider.isOutputPosition(blockedOutput)
                || (devider.isOutputEnabled(blockedOutput)
                && (!(level.getBlockEntity(blockedOutput) instanceof PneumaticTubeBlockEntity blockedTube)
                || blockedTube.movingItem == null))) {
            return false;
        }

        for (BlockPos alternateOutput : devider.getOrderedOutputPositions()) {
            if (alternateOutput.equals(blockedOutput)) {
                continue;
            }

            TubePath alternatePath = TubeNetworkPathfinder.findPathToInsertConnector(
                    level,
                    worldPosition,
                    alternateOutput
            );
            if (alternatePath.isEmpty()) {
                continue;
            }

            List<BlockPos> reroutedPath = new java.util.ArrayList<>(
                    movingItem.path.subList(0, movingItem.pathIndex + 1)
            );
            reroutedPath.addAll(alternatePath.tubePositions());
            movingItem.path = reroutedPath;
            movingItem.targetConnector = alternatePath.targetConnector();
            movingItem.spillsAtEnd = alternatePath.spillsAtEnd();
            movingItem.speedControllers = findSpeedControllers(reroutedPath);
            movingItem.hasSpeedControllerCache = true;
            movingItem.lastSpeedCheckGameTime = Long.MIN_VALUE;
            movingItem.waitingForNextTube = false;
            movingItem.waitingAtDestination = false;
            devider.markOutputUsed(alternateOutput);
            setChanged();
            syncMovingItem(level);
            return true;
        }

        return false;
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
        if (movingItem.pathIndex + 1 >= movingItem.path.size()) {
            return false;
        }

        return !isPathNode(level, movingItem.path.get(movingItem.pathIndex + 1));
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
        movingItem.targetConnector = extension.targetConnector();
        movingItem.spillsAtEnd = extension.spillsAtEnd();
        movingItem.speedControllers = findSpeedControllers(restoredPath);
        movingItem.hasSpeedControllerCache = true;
        movingItem.lastSpeedCheckGameTime = Long.MIN_VALUE;
        movingItem.waitingAtDestination = false;
        movingItem.waitingForNextTube = false;
        movingItem.progress = 0;
        movingItem.segmentProgress = 0.0f;
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
        if (!canMoveBetween(currentPos, nextPos)) {
            return false;
        }

        if (!(level.getBlockEntity(nextPos) instanceof ItemPumpBlockEntity) || ++nextPathIndex >= movingItem.path.size()) {
            return true;
        }

        BlockPos afterPumpPos = movingItem.path.get(nextPathIndex);
        return canMoveBetween(nextPos, afterPumpPos);
    }

    public MovingTubeItem getMovingItem() {
        return movingItem;
    }

    public MovingTubeItem getRenderMovingItem() {
        return movingItem;
    }

    public boolean shouldRenderMovingItem(float partialTick) {
        MovingTubeItem item = getRenderMovingItem();

        if (item == null) {
            return false;
        }

        return item.pathIndex >= 0
                && item.pathIndex < item.path.size()
                && item.path.get(item.pathIndex).equals(worldPosition);
    }

    public float getMovingProgress(float partialTick) {
        MovingTubeItem item = getRenderMovingItem();

        if (item == null) {
            return 0.0f;
        }

        if (level != null && level.isClientSide) {
            return getClientMovingProgress(item, partialTick);
        }

        return item.segmentProgress;
    }

    private float getClientMovingProgress(MovingTubeItem item, float partialTick) {
        if (item.waitingForNextTube || item.waitingAtDestination || !hasRunningPump(item.speedControllers)) {
            return item.segmentProgress;
        }

        float renderTime = getClientGameTime() + partialTick;
        ClientMotion motion = CLIENT_MOTIONS.get(item.animationId);

        if (motion == null || !motion.matches(item)) {
            motion = new ClientMotion(item, renderTime);
            CLIENT_MOTIONS.put(item.animationId, motion);
            return motion.progress;
        }

        float elapsed = Math.max(0.0f, renderTime - motion.lastRenderTime);
        motion.progress = Math.min(getClientProgressLimit(item),
                motion.progress + elapsed / getCurrentMoveTime());
        motion.lastRenderTime = renderTime;
        return motion.progress;
    }

    private float getClientProgressLimit(MovingTubeItem item) {
        if (item.spillsAtEnd && item.pathIndex + 1 >= item.path.size()) {
            return 0.5f;
        }

        if (level != null && item.pathIndex + 1 < item.path.size()
                && !isPathNode(level, item.path.get(item.pathIndex + 1))) {
            return 0.5f;
        }

        return 1.0f;
    }

    private float getClientGameTime() {
        return level == null ? 0.0f : level.getGameTime();
    }

    private int getCurrentMoveTime() {
        if (movingItem == null || level == null) {
            return BASE_MOVE_TIME;
        }

        long gameTime = level.getGameTime();
        if (movingItem.lastSpeedCheckGameTime != Long.MIN_VALUE
                && gameTime - movingItem.lastSpeedCheckGameTime < SPEED_CHECK_INTERVAL) {
            return movingItem.moveTime;
        }

        if (!movingItem.hasSpeedControllerCache) {
            movingItem.speedControllers = findSpeedControllers(movingItem.path);
            movingItem.hasSpeedControllerCache = true;
        }

        int moveTime = 0;
        for (BlockPos controllerPos : movingItem.speedControllers) {
            if (level.getBlockEntity(controllerPos) instanceof ItemPumpBlockEntity pump && pump.isRunning()) {
                moveTime = moveTime == 0 ? pump.getMoveTime() : Math.min(moveTime, pump.getMoveTime());
            }
        }

        movingItem.moveTime = moveTime;
        movingItem.lastSpeedCheckGameTime = gameTime;
        return moveTime;
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
        movingItem.speedControllers = offsetPositions(movingItem.speedControllers, offset);
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
        movingTag.put("target", saveBlockPos(movingItem.targetConnector));
        movingTag.putBoolean("spills_at_end", movingItem.spillsAtEnd);
        movingTag.putInt("path_index", movingItem.pathIndex);
        movingTag.putInt("progress", movingItem.progress);
        movingTag.putFloat("segment_progress", movingItem.segmentProgress);
        movingTag.putInt("move_time", movingItem.moveTime);
        movingTag.putLong("animation_id", movingItem.animationId);
        movingTag.putLong("started_at", movingItem.startedAtGameTime);
        movingTag.putBoolean("waiting_for_next", movingItem.waitingForNextTube);
        movingTag.putBoolean("waiting_at_destination", movingItem.waitingAtDestination);
        movingTag.putBoolean("speed_controller_cache", movingItem.hasSpeedControllerCache);

        ListTag controllersTag = new ListTag();
        for (BlockPos controllerPos : movingItem.speedControllers) {
            controllersTag.add(saveBlockPos(controllerPos));
        }
        movingTag.put("speed_controllers", controllersTag);

        ListTag pathTag = new ListTag();

        for (BlockPos pathPos : movingItem.path) {
            pathTag.add(saveBlockPos(pathPos));
        }

        movingTag.put("path", pathTag);
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
        item.spillsAtEnd = movingTag.getBoolean("spills_at_end");
        item.pathIndex = movingTag.getInt("path_index");
        item.progress = movingTag.getInt("progress");
        item.moveTime = movingTag.contains("move_time", Tag.TAG_INT)
                ? Math.max(1, movingTag.getInt("move_time"))
                : BASE_MOVE_TIME;
        item.segmentProgress = movingTag.contains("segment_progress", Tag.TAG_FLOAT)
                ? movingTag.getFloat("segment_progress")
                : Math.min(1.0f, item.progress / (float) item.moveTime);
        item.animationId = movingTag.getLong("animation_id");
        item.startedAtGameTime = movingTag.getLong("started_at");
        item.waitingForNextTube = movingTag.getBoolean("waiting_for_next");
        item.waitingAtDestination = movingTag.getBoolean("waiting_at_destination");
        item.hasSpeedControllerCache = movingTag.getBoolean("speed_controller_cache");

        ListTag controllersTag = movingTag.getList("speed_controllers", Tag.TAG_COMPOUND);
        java.util.List<BlockPos> controllers = new java.util.ArrayList<>();
        for (int i = 0; i < controllersTag.size(); i++) {
            controllers.add(loadBlockPos(controllersTag.getCompound(i)));
        }
        item.speedControllers = controllers;
        return item;
    }

    private int calculateMoveTime(TubePath path) {
        if (level == null) {
            return BASE_MOVE_TIME;
        }

        int moveTime = 0;

        for (BlockPos pathPos : path.tubePositions()) {
            BlockEntity blockEntity = level.getBlockEntity(pathPos);

            if (blockEntity instanceof ItemPumpBlockEntity pump && pump.isRunning()) {
                moveTime = moveTime == 0 ? pump.getMoveTime() : Math.min(moveTime, pump.getMoveTime());
            }
        }

        return moveTime;
    }

    private java.util.List<BlockPos> findSpeedControllers(java.util.List<BlockPos> path) {
        if (level == null) {
            return java.util.List.of();
        }

        java.util.List<BlockPos> controllers = new java.util.ArrayList<>();
        for (BlockPos pathPos : path) {
            if (level.getBlockEntity(pathPos) instanceof ItemPumpBlockEntity) {
                controllers.add(pathPos.immutable());
            }
        }
        return controllers;
    }

    private boolean hasRunningPump(List<BlockPos> controllers) {
        if (level == null) {
            return false;
        }

        for (BlockPos controllerPos : controllers) {
            if (level.getBlockEntity(controllerPos) instanceof ItemPumpBlockEntity pump && pump.isRunning()) {
                return true;
            }
        }

        return false;
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

        if (fromBlockEntity instanceof ItemPumpBlockEntity fromPump
                && !fromPump.canTravelTo(level, direction)) {
            return false;
        }

        if (fromBlockEntity instanceof ItemPumpBlockEntity fromPump) {
            Direction flowDirection = ((ItemPumpBlock) fromPump.getBlockState().getBlock())
                    .getFlowDirection(level, from, fromPump.getBlockState());
            if (flowDirection != null && flowDirection != direction) {
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

            Direction flowDirection = ((ItemPumpBlock) toPump.getBlockState().getBlock())
                    .getFlowDirection(level, to, toPump.getBlockState());
            return flowDirection == null || flowDirection == direction;
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
