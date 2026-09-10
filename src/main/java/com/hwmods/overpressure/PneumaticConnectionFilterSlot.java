package com.hwmods.overpressure;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PneumaticConnectionFilterSlot extends ValueBoxTransform.Sided {
    private static final double SLOT_DEPTH = 13.35 / 16.0;

    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, SLOT_DEPTH);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        Direction face = PneumaticConnectionBlock.getFilterSlotFace(state);
        // Both faces use the same FilteringBehaviour and therefore the same filter.
        return side == face || side == face.getOpposite();
    }
}