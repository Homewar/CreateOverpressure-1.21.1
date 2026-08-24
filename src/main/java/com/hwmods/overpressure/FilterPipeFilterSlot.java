package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FilterPipeFilterSlot extends ValueBoxTransform {
    private static final double SLOT_DEPTH = 13.02 / 16.0;

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction face = getFilterFace(state);
        return new Vec3(0.5, 0.5, 0.5)
                .add(Vec3.atLowerCornerOf(face.getNormal()).scale(SLOT_DEPTH - 0.5));
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
        Direction main = state.getValue(FilterPipeBlock.INPUT).getOpposite();
        Direction front = FilterPipeBlock.getFrontDirection(state);

        DeviderRenderer.applyMainRotation(poseStack, main);
        poseStack.mulPose(Axis.YP.rotationDegrees(DeviderRenderer.findRoll(main, front)));
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        Direction face = getFilterFace(state);
        Vec3 delta = localHit.subtract(getLocalOffset(level, pos, state));

        double normalDistance = Math.abs(delta.dot(Vec3.atLowerCornerOf(face.getNormal())));
        if (normalDistance > 1.0 / 16.0) {
            return false;
        }

        return switch (face.getAxis()) {
            case X -> Math.abs(delta.y) <= 5.0 / 16.0 && Math.abs(delta.z) <= 5.0 / 16.0;
            case Y -> Math.abs(delta.x) <= 5.0 / 16.0 && Math.abs(delta.z) <= 5.0 / 16.0;
            case Z -> Math.abs(delta.x) <= 5.0 / 16.0 && Math.abs(delta.y) <= 5.0 / 16.0;
        };
    }

    private static Direction getFilterFace(BlockState state) {
        return FilterPipeBlock.getBranchDirection(state).getOpposite();
    }
}
