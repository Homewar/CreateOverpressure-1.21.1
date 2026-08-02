package com.hwmods.overpressure;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ItemPumpBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!isCreative()) {
            super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        }
        PneumaticTubeBlockEntity.addTransportSpeedTooltip(tooltip, getMoveTime());
        return true;
    }

    public int getMoveTime() {
        if (isCreative()) {
            return PneumaticTubeBlockEntity.MIN_PUMPED_MOVE_TIME;
        }

        float speed = Math.abs(getSpeed());

        if (speed <= 0.0f) {
            return 0;
        }

        return Math.max(PneumaticTubeBlockEntity.MIN_PUMPED_MOVE_TIME,
                PneumaticTubeBlockEntity.BASE_MOVE_TIME - Math.round(speed / 8.0f));
    }

    public boolean isRunning() {
        return isCreative() || Math.abs(getSpeed()) > 0.0f;
    }

    public boolean isCreative() {
        return getBlockState().is(ModBlocks.CREATIVE_ITEM_PUMP.get());
    }

    public boolean canTravelTo(Level level, Direction direction) {
        if (!(getBlockState().getBlock() instanceof ItemPumpBlock pump)) {
            return false;
        }

        return pump.canTravelTo(getBlockState(), direction);
    }
}
