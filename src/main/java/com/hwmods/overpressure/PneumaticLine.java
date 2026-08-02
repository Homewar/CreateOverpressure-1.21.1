package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PneumaticLine {
    public static boolean isPathNode(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PneumaticTubeBlock
                || state.getBlock() instanceof ItemPumpBlock;
    }

    public static boolean isTravelAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        if (!isPathNode(level, to)) {
            return false;
        }

        return strictTravelAllowed(level, from, to, direction);
    }

    private static boolean strictTravelAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        BlockEntity fromBlockEntity = level.getBlockEntity(from);
        BlockEntity toBlockEntity = level.getBlockEntity(to);

        if (fromBlockEntity instanceof ItemPumpBlockEntity fromPump
                && !fromPump.canTravelTo(level, direction)) {
            return false;
        }

        if (fromBlockEntity instanceof PneumaticTubeBlockEntity fromTube
                && !fromTube.canTravelTo(level, direction)) {
            return false;
        }

        if (toBlockEntity instanceof ItemPumpBlockEntity toPump
                && !toPump.canTravelTo(level, direction.getOpposite())) {
            return false;
        }

        if (toBlockEntity instanceof PneumaticTubeBlockEntity toTube
                && !toTube.canTravelTo(level, direction.getOpposite())) {
            return false;
        }

        return fromBlockEntity instanceof PneumaticTubeBlockEntity
                || fromBlockEntity instanceof ItemPumpBlockEntity
                || level.getBlockState(from).getBlock() instanceof PneumaticConnectionBlock;
    }

    private PneumaticLine() {
    }
}
