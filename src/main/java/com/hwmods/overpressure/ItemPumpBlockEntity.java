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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ItemPumpBlockEntity extends KineticBlockEntity
        implements IHaveGoggleInformation, TransportFlowSource, TransportGate {
    private static final float RPM_TICK_SCALE = 1024.0f;

    public ItemPumpBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ITEM_PUMP.get(), pos, state);
    }

    public ItemPumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
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
        if (isCreative()) {
            return Config.applyTubeSpeed(PneumaticTubeBlockEntity.MIN_PUMPED_MOVE_TIME);
        }

        float speed = Math.abs(getSpeed());

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
