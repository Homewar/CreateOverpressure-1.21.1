package com.hwmods.overpressure;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PneumaticConnectorBlockEntity extends PneumaticTubeBlockEntity {
    public static final float MIN_FLOW_SPEED = 10.0F;
    public static final float MAX_FLOW_SPEED = 20.0F;
    private static final String ITEM_SPEED_TAG = "ItemSpeed";

    private float itemSpeed;
    private float potentialItemSpeed;
    private ItemPumpBlockEntity supplyingPump;

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
        if (level == null || level.isClientSide) {
            return itemSpeed;
        }
        return supplyingPump == null || supplyingPump.isRemoved() ? 0.0F : supplyingPump.getMultitoolFlowSpeed();
    }

    float getPotentialItemSpeed() {
        if (level == null || level.isClientSide) {
            return potentialItemSpeed;
        }
        return supplyingPump == null || supplyingPump.isRemoved() ? 0.0F : supplyingPump.getPotentialMultitoolFlowSpeed();
    }

    void requestToolLoad() {
        if (supplyingPump != null && !supplyingPump.isRemoved()) {
            supplyingPump.requestToolLoad();
        }
    }

    private void refreshStoredItemSpeed() {
        supplyingPump = findConnectedPump(ItemPumpBlockEntity::getPotentialMultitoolFlowSpeed);
        float speed = getItemSpeed();
        float potentialSpeed = getPotentialItemSpeed();
        if (this.itemSpeed != speed || this.potentialItemSpeed != potentialSpeed) {
            this.itemSpeed = speed;
            this.potentialItemSpeed = potentialSpeed;
            setChanged();
            notifyUpdate();
        }
    }

    private static float sanitizeSpeed(float speed) {
        return Float.isFinite(speed) && speed >= MIN_FLOW_SPEED ? Math.min(MAX_FLOW_SPEED, speed) : 0.0F;
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return getBlockState().getValue(PneumaticTubeBlock.getConnectionProperty(direction));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        addTransportSpeedTooltip(tooltip, itemSpeed, MAX_FLOW_SPEED);
        if (itemSpeed <= 0.0F) {
            tooltip.add(Component.translatable("message.overpressure.multitool.low_pressure").withStyle(ChatFormatting.RED));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat(ITEM_SPEED_TAG, itemSpeed);
        if (clientPacket) {
            tag.putFloat("PotentialItemSpeed", potentialItemSpeed);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        itemSpeed = sanitizeSpeed(tag.getFloat(ITEM_SPEED_TAG));
        potentialItemSpeed = sanitizeSpeed(tag.getFloat("PotentialItemSpeed"));
    }
}
