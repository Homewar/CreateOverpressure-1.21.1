package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class PneumaticConnectionFilterSlot extends ValueBoxTransform {
    private static final double SLOT_DEPTH = 13.35 / 16.0;

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction face = PneumaticConnectionBlock.getFilterSlotFace(state);
        return new Vec3(0.5 + face.getStepX() * (SLOT_DEPTH - 0.5),
                0.5 + face.getStepY() * (SLOT_DEPTH - 0.5),
                0.5 + face.getStepZ() * (SLOT_DEPTH - 0.5));
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
        Direction face = PneumaticConnectionBlock.getFilterSlotFace(state);
        // Match Create's ValueBoxTransform.Sided orientation, including its local face normal.
        poseStack.mulPose(Axis.YP.rotationDegrees(net.createmod.catnip.math.AngleHelper.horizontalAngle(face) + 180));
        if (face == Direction.UP) poseStack.mulPose(Axis.XP.rotationDegrees(90));
        if (face == Direction.DOWN) poseStack.mulPose(Axis.XP.rotationDegrees(270));
    }
}