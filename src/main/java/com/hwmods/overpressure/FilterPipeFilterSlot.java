package com.hwmods.overpressure;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FilterPipeFilterSlot extends ValueBoxTransform.Sided {
    private static final double SLOT_DEPTH = 13.02 / 16.0;

    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, SLOT_DEPTH);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        Direction front = FilterPipeBlock.getFrontDirection(state);
        return side == front || side == front.getOpposite();
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        Direction face = getSide();
        if (!isSideActive(state, face)) return false;
        Vec3 delta = localHit.subtract(getLocalOffset(level, pos, state));
        double normalDistance = Math.abs(delta.dot(Vec3.atLowerCornerOf(face.getNormal())));
        if (normalDistance > 1.0 / 16.0) return false;
        return switch (face.getAxis()) {
            case X -> Math.abs(delta.y) <= 5.0 / 16.0 && Math.abs(delta.z) <= 5.0 / 16.0;
            case Y -> Math.abs(delta.x) <= 5.0 / 16.0 && Math.abs(delta.z) <= 5.0 / 16.0;
            case Z -> Math.abs(delta.x) <= 5.0 / 16.0 && Math.abs(delta.y) <= 5.0 / 16.0;
        };
    }
}