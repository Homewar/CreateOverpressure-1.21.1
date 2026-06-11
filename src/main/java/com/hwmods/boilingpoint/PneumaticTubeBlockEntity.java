package com.hwmods.boilingpoint;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PneumaticTubeBlockEntity extends BlockEntity {
    public static final int BASE_MOVE_TIME = 24;
    public static final int MIN_PUMPED_MOVE_TIME = 4;
    private static final Map<Long, Long> CLIENT_ANIMATION_STARTS = new HashMap<>();

    private MovingTubeItem movingItem;
    private MovingTubeItem clientVisualItem;

    public PneumaticTubeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PNEUMATIC_TUBE.get(), pos, blockState);
    }

    protected PneumaticTubeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PneumaticTubeBlockEntity tube) {
        if (tube.movingItem != null) {
            tube.tickMovingItem(level);
        }
    }

    public boolean acceptItem(ItemStack stack, TubePath path) {
        if (movingItem != null || stack.isEmpty() || path.isEmpty()) {
            return false;
        }

        int pathIndex = path.tubePositions().indexOf(worldPosition);

        if (pathIndex < 0) {
            return false;
        }

        movingItem = new MovingTubeItem(stack, path.tubePositions(), path.targetConnector());
        movingItem.pathIndex = pathIndex;
        movingItem.moveTime = calculateMoveTime(path);
        movingItem.animationId = createAnimationId(level);
        movingItem.lastTickedGameTime = level.getGameTime();
        setChanged();
        syncMovingItem(level);
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
        movingItem.progress++;

        if (movingItem.progress < movingItem.moveTime) {
            setChanged();
            return;
        }

        movingItem.progress = 0;
        movingItem.pathIndex++;

        if (movingItem.pathIndex >= movingItem.path.size()) {
            tryInsertIntoTargetConnector(level);
            return;
        }

        BlockPos nextPos = movingItem.path.get(movingItem.pathIndex);
        BlockEntity nextBlockEntity = level.getBlockEntity(nextPos);

        if (nextBlockEntity instanceof PneumaticTubeBlockEntity nextTube && nextTube.movingItem == null) {
            nextTube.movingItem = movingItem;
            movingItem = null;
            nextTube.setChanged();
            setChanged();
            nextTube.syncMovingItem(level);
            syncMovingItem(level);
        } else if (nextBlockEntity instanceof ItemPumpBlockEntity) {
            movingItem.pathIndex++;
            if (movingItem.pathIndex >= movingItem.path.size()) {
                tryInsertIntoTargetConnector(level);
                return;
            }

            BlockEntity afterPumpBlockEntity = level.getBlockEntity(movingItem.path.get(movingItem.pathIndex));

            if (afterPumpBlockEntity instanceof PneumaticTubeBlockEntity afterPumpTube && afterPumpTube.movingItem == null) {
                afterPumpTube.movingItem = movingItem;
                movingItem = null;
                afterPumpTube.setChanged();
                setChanged();
                afterPumpTube.syncMovingItem(level);
                syncMovingItem(level);
            } else {
                movingItem.pathIndex--;
                movingItem.progress = movingItem.moveTime - 1;
                setChanged();
                syncMovingItem(level);
            }
        } else if (!(nextBlockEntity instanceof PneumaticTubeBlockEntity)) {
            movingItem = null;
            setChanged();
            syncMovingItem(level);
        }
    }

    private void tryInsertIntoTargetConnector(Level level) {
        BlockEntity targetBlockEntity = level.getBlockEntity(movingItem.targetConnector);

        if (!(targetBlockEntity instanceof PneumaticConnectionBlockEntity connector)) {
            movingItem = null;
            setChanged();
            syncMovingItem(level);
            return;
        }

        ItemStack remaining = connector.insertIntoAttachedInventory(level, movingItem.stack);

        if (remaining.isEmpty()) {
            movingItem = null;
        } else {
            movingItem.stack = remaining;
        }

        setChanged();
        syncMovingItem(level);
    }

    public MovingTubeItem getMovingItem() {
        return movingItem;
    }

    public MovingTubeItem getRenderMovingItem() {
        if (level != null && level.isClientSide && clientVisualItem != null) {
            return clientVisualItem;
        }

        return movingItem;
    }

    public boolean shouldRenderMovingItem(float partialTick) {
        MovingTubeItem item = getRenderMovingItem();

        if (item == null) {
            return false;
        }

        if (level == null || !level.isClientSide) {
            return true;
        }

        if (!isClientRenderOwner(item)) {
            return false;
        }

        if (getMovingProgressSegments(item, partialTick) < item.path.size()) {
            return true;
        }

        if (clientVisualItem == item) {
            clientVisualItem = null;
        }

        return false;
    }

    private boolean isClientRenderOwner(MovingTubeItem item) {
        if (level == null) {
            return false;
        }

        int currentPathIndex = item.path.indexOf(worldPosition);

        if (currentPathIndex < 0) {
            return false;
        }

        for (int i = 0; i < currentPathIndex; i++) {
            BlockEntity blockEntity = level.getBlockEntity(item.path.get(i));

            if (blockEntity instanceof PneumaticTubeBlockEntity tube
                    && tube.hasMovingItemAnimation(item.animationId)) {
                return false;
            }
        }

        return true;
    }

    public float getMovingProgress(float partialTick) {
        MovingTubeItem item = getRenderMovingItem();

        if (item == null) {
            return 0.0f;
        }

        if (level != null && level.isClientSide) {
            return Math.min(1.0f, getClientMovingTicks(item, partialTick) / item.moveTime);
        }

        return Math.min(1.0f, (item.progress + partialTick) / item.moveTime);
    }

    public float getMovingProgressSegments(float partialTick) {
        return getMovingProgressSegments(getRenderMovingItem(), partialTick);
    }

    private float getMovingProgressSegments(MovingTubeItem item, float partialTick) {
        if (item == null) {
            return 0.0f;
        }

        if (level != null && level.isClientSide) {
            return getClientMovingTicks(item, partialTick) / item.moveTime;
        }

        return (item.progress + partialTick) / item.moveTime;
    }

    public boolean hasMovingItemAnimation(long animationId) {
        MovingTubeItem item = getRenderMovingItem();
        return item != null && item.animationId == animationId;
    }

    private float getClientMovingTicks(MovingTubeItem item, float partialTick) {
        long startedAt = CLIENT_ANIMATION_STARTS.computeIfAbsent(
                item.animationId,
                id -> getClientGameTime() - item.progress
        );

        return getClientGameTime() - startedAt + partialTick;
    }

    private long getClientGameTime() {
        return level == null ? 0L : level.getGameTime();
    }

    private long createAnimationId(Level level) {
        return level.getGameTime() ^ worldPosition.asLong();
    }

    private void syncMovingItem(Level level) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveMovingItem(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        MovingTubeItem previousItem = movingItem;
        movingItem = loadMovingItem(tag, registries);
        updateClientVisualItem(previousItem);
    }

    private void updateClientVisualItem(MovingTubeItem previousItem) {
        if (level == null || !level.isClientSide) {
            return;
        }

        if (movingItem != null) {
            clientVisualItem = movingItem;
        } else if (previousItem != null && clientVisualItem == null) {
            clientVisualItem = previousItem;
        }

        if (clientVisualItem != null && (previousItem == null
                || previousItem.animationId != clientVisualItem.animationId
                || previousItem.pathIndex != clientVisualItem.pathIndex
                || !previousItem.path.equals(clientVisualItem.path))) {
            CLIENT_ANIMATION_STARTS.computeIfAbsent(
                    clientVisualItem.animationId,
                    id -> getClientGameTime() - clientVisualItem.progress
            );
        }

        if (movingItem != null && (previousItem == null
                || previousItem.animationId != movingItem.animationId
                || previousItem.pathIndex != movingItem.pathIndex
                || !previousItem.path.equals(movingItem.path))) {
            CLIENT_ANIMATION_STARTS.computeIfAbsent(
                    movingItem.animationId,
                    id -> getClientGameTime() - movingItem.progress
            );
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveMovingItem(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void saveMovingItem(CompoundTag tag, HolderLookup.Provider registries) {
        if (movingItem == null) {
            tag.remove("moving_item");
            return;
        }

        CompoundTag movingTag = new CompoundTag();
        movingTag.put("stack", movingItem.stack.save(registries));
        movingTag.put("target", saveBlockPos(movingItem.targetConnector));
        movingTag.putInt("path_index", movingItem.pathIndex);
        movingTag.putInt("progress", movingItem.progress);
        movingTag.putInt("move_time", movingItem.moveTime);
        movingTag.putLong("animation_id", movingItem.animationId);

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
        item.pathIndex = movingTag.getInt("path_index");
        item.progress = movingTag.getInt("progress");
        item.moveTime = movingTag.contains("move_time", Tag.TAG_INT)
                ? Math.max(1, movingTag.getInt("move_time"))
                : BASE_MOVE_TIME;
        item.animationId = movingTag.getLong("animation_id");
        return item;
    }

    private int calculateMoveTime(TubePath path) {
        if (level == null) {
            return BASE_MOVE_TIME;
        }

        int moveTime = BASE_MOVE_TIME;

        for (BlockPos pathPos : path.tubePositions()) {
            BlockEntity blockEntity = level.getBlockEntity(pathPos);

            if (blockEntity instanceof ItemPumpBlockEntity pump) {
                moveTime = Math.min(moveTime, pump.getMoveTime());
            }
        }

        return moveTime;
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
