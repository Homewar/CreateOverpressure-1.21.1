package com.hwmods.boilingpoint;

import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ItemPumpBlockEntity extends KineticBlockEntity {
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

    public int getMoveTime() {
        float speed = Math.abs(getSpeed());

        if (speed <= 0.0f) {
            return PneumaticTubeBlockEntity.BASE_MOVE_TIME;
        }

        return Math.max(PneumaticTubeBlockEntity.MIN_PUMPED_MOVE_TIME,
                PneumaticTubeBlockEntity.BASE_MOVE_TIME - Math.round(speed / 8.0f));
    }
}
