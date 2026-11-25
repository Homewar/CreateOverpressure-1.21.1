package com.hwmods.overpressure;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.hwmods.overpressure.transport.TransportFlowSource;
import com.hwmods.overpressure.transport.TransportGate;
import com.hwmods.overpressure.transport.TubeGraphRoute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ItemPumpBlockEntity extends KineticBlockEntity
        implements IHaveGoggleInformation, TransportFlowSource, TransportGate {
    private static final float RPM_TICK_SCALE = 512.0f;
    private static final float MIN_MULTITOOL_RPM = 128.0F;
    static final int TOOL_LOAD_GRACE_TICKS = 4;
    private CreativePumpSpeedBehaviour creativeSpeed;
    private long toolLoadUntil;
    private boolean toolLoadActive;

    public ItemPumpBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ITEM_PUMP.get(), pos, state);
    }

    public ItemPumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        if (isCreative()) {
            creativeSpeed = new CreativePumpSpeedBehaviour(this);
            behaviours.add(creativeSpeed);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState blockState) {
        BlockState previousState = getBlockState();
        super.setBlockState(blockState);
        if (level != null && !previousState.equals(blockState)) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!isCreative()) {
            super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        }
        PneumaticTubeBlockEntity.addTransportSpeedTooltip(tooltip, getMoveTime());
        return true;
    }

    public int getMoveTime() {
        float speed = operatingRpm();

        if (speed <= 0.0f) {
            return 0;
        }

        int moveTime = (int) Math.ceil(RPM_TICK_SCALE / speed);
        int baseMoveTime = Math.max(
                PneumaticTubeBlockEntity.MIN_PUMPED_MOVE_TIME,
                Math.min(PneumaticTubeBlockEntity.BASE_MOVE_TIME, moveTime)
        );
        return Config.applyTubeSpeed(baseMoveTime);
    }

    private float operatingRpm() {
        return isCreative() ? (creativeSpeed == null ? 256 : creativeSpeed.getValue()) : Math.abs(getSpeed());
    }

    float getMultitoolFlowSpeed() {
        return multitoolFlowAt(operatingRpm());
    }

    float getPotentialMultitoolFlowSpeed() {
        // Create retains theoretical RPM during overload. Keep the tool's
        // load on the same pump so an overloaded network cannot oscillate.
        return multitoolFlowAt(isCreative() ? operatingRpm() : Math.abs(getTheoreticalSpeed()));
    }

    private static float multitoolFlowAt(float rpm) {
        if (rpm < MIN_MULTITOOL_RPM) {
            return 0.0F;
        }
        float speed = PneumaticConnectorBlockEntity.MIN_FLOW_SPEED * rpm / MIN_MULTITOOL_RPM;
        return Math.clamp(speed, PneumaticConnectorBlockEntity.MIN_FLOW_SPEED,
                PneumaticConnectorBlockEntity.MAX_FLOW_SPEED);
    }

    void requestToolLoad() {
        if (level == null || level.isClientSide || isCreative()) {
            return;
        }
        toolLoadUntil = level.getGameTime() + TOOL_LOAD_GRACE_TICKS;
        setToolLoadActive(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && toolLoadActive && level.getGameTime() >= toolLoadUntil) {
            setToolLoadActive(false);
        }
    }

    private void setToolLoadActive(boolean active) {
        if (toolLoadActive == active) {
            return;
        }
        toolLoadActive = active;
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        }
        sendData();
    }

    @Override
    public float calculateStressApplied() {
        float baseStress = super.calculateStressApplied();
        float extraStress = toolLoadActive && !isCreative() ? Config.MULTITOOL_PUMP_EXTRA_STRESS.get().floatValue() : 0.0F;
        return lastStressApplied = baseStress + extraStress;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putBoolean("ToolLoadActive", toolLoadActive);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            toolLoadActive = tag.getBoolean("ToolLoadActive");
        }
    }

    public boolean isRunning() {
        return isCreative() || Math.abs(getSpeed()) > 0.0f;
    }

    public boolean isCreative() {
        return getBlockState().is(ModBlocks.CREATIVE_ITEM_PUMP.get());
    }

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.PUMP;
    }

    public boolean canTravelTo(Level level, Direction direction) {
        if (!(getBlockState().getBlock() instanceof ItemPumpBlock pump)) {
            return false;
        }

        return pump.canTravelTo(getBlockState(), direction);
    }

    @Override
    public boolean allowsRoute(Level level, Direction travelDirection) {
        return getBlockState().getBlock() instanceof ItemPumpBlock pump
                && pump.allowsTravel(level, worldPosition, getBlockState(), travelDirection);
    }

    @Override
    public boolean allowsTravelNow(Level level, Direction travelDirection) {
        return allowsRoute(level, travelDirection);
    }
}
