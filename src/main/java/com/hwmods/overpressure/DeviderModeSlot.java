package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DeviderModeSlot extends ValueBoxTransform {
    private static final double SLOT_HEIGHT = 13.35 / 16.0;

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        net.minecraft.core.Direction face = state.getValue(DeviderBlock.INPUT).getOpposite();
        return new Vec3(0.5, 0.5, 0.5).add(
                Vec3.atLowerCornerOf(face.getNormal()).scale(SLOT_HEIGHT - 0.5)
        );
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
        switch (state.getValue(DeviderBlock.INPUT).getOpposite()) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case NORTH -> {
            }
        }
    }
}
