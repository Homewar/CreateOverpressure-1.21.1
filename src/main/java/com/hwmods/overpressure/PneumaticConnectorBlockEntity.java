package com.hwmods.overpressure;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PneumaticConnectorBlockEntity extends PneumaticTubeBlockEntity {
    public static final float DEFAULT_ITEM_SPEED = 20.0f / BASE_MOVE_TIME;
    private static final String ITEM_SPEED_TAG = "ItemSpeed";

    private float itemSpeed = DEFAULT_ITEM_SPEED;

    public PneumaticConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PNEUMATIC_CONNECTOR.get(), pos, state);
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            PneumaticConnectorBlockEntity connector
    ) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 5L) == 0L) {
            connector.refreshStoredItemSpeed();
        }
    }

    @Nullable
    public static PneumaticConnectorBlockEntity findNearestActive(Level level, BlockPos origin, int radius) {
        PneumaticConnectorBlockEntity nearest = null;
        double nearestDistance = radius * radius;

        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius)
        )) {
            double distance = origin.distSqr(candidate);
            if (distance > nearestDistance
                    || !(level.getBlockEntity(candidate) instanceof PneumaticConnectorBlockEntity connector)
                    || connector.getItemSpeed() <= 0.0f) {
                continue;
            }
            nearest = connector;
            nearestDistance = distance;
        }
        return nearest;
    }

    public float getItemSpeed() {
        return itemSpeed;
    }

    public void setItemSpeed(float itemSpeed) {
        float sanitizedSpeed = Float.isFinite(itemSpeed) ? Math.max(0.0f, itemSpeed) : DEFAULT_ITEM_SPEED;
        if (Float.compare(this.itemSpeed, sanitizedSpeed) == 0) {
            return;
        }
        this.itemSpeed = sanitizedSpeed;
        setChanged();
        notifyUpdate();
    }

    private void refreshStoredItemSpeed() {
        // Do not call getDisplayedMoveTime(): that method intentionally uses
        // the currently rendered cargo first, which made tool power flicker.
        // This walks the route to pumps even when the line is empty.
        int moveTime = calculateConnectedLineMoveTime();
        int effectiveMoveTime = moveTime <= 0 ? BASE_MOVE_TIME : moveTime;
        setItemSpeed(20.0f / effectiveMoveTime);
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return getBlockState().getValue(PneumaticTubeBlock.getConnectionProperty(direction));
    }

    @Override
    public int getDisplayedMoveTime() {
        return itemSpeed <= 0.0f ? 0 : Math.max(1, Math.round(20.0f / itemSpeed));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat(ITEM_SPEED_TAG, itemSpeed);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        itemSpeed = tag.contains(ITEM_SPEED_TAG) && Float.isFinite(tag.getFloat(ITEM_SPEED_TAG))
                ? Math.max(0.0f, tag.getFloat(ITEM_SPEED_TAG))
                : DEFAULT_ITEM_SPEED;
    }
}
