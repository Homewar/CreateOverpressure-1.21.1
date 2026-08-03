package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class PneumaticLine {
    public static boolean isPathNode(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PneumaticTubeBlock
                || state.getBlock() instanceof ItemPumpBlock
                || state.getBlock() instanceof DeviderBlock;
    }

    public static List<BlockPos> getForwardNeighbors(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DeviderBlockEntity devider) {
            return devider.getOrderedOutputPositions();
        }

        List<BlockPos> neighbors = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            neighbors.add(pos.relative(direction));
        }
        return neighbors;
    }

    public static boolean isTravelAllowed(Level level, BlockPos from, BlockPos to) {
        BlockEntity fromBlockEntity = level.getBlockEntity(from);
        BlockEntity toBlockEntity = level.getBlockEntity(to);

        if (fromBlockEntity instanceof DeviderBlockEntity devider) {
            return devider.isOutputPosition(to)
                    && level.getBlockState(to).getBlock() instanceof CurvaturePneumaticTubeBlock
                    && toBlockEntity instanceof PneumaticTubeBlockEntity;
        }

        if (toBlockEntity instanceof DeviderBlockEntity) {
            return from.equals(to.below())
                    && (fromBlockEntity instanceof PneumaticTubeBlockEntity
                    || fromBlockEntity instanceof ItemPumpBlockEntity);
        }

        Direction direction = getDirectionBetween(from, to);
        return direction != null && isTravelAllowed(level, from, to, direction);
    }

    public static boolean isTravelAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        if (!isPathNode(level, to)) {
            return false;
        }

        return strictTravelAllowed(level, from, to, direction);
    }

    private static Direction getDirectionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
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
