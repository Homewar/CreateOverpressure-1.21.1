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
                || state.getBlock() instanceof ValveBlock
                || state.getBlock() instanceof ClogSensorBlock
                || state.getBlock() instanceof DeviderBlock;
    }

    public static List<BlockPos> getForwardNeighbors(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DeviderBlockEntity devider) {
            return devider.getConnectedPortPositions();
        }

        List<BlockPos> neighbors = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            neighbors.add(pos.relative(direction));
        }
        return neighbors;
    }

    public static List<BlockPos> getForwardNeighbors(Level level, BlockPos pos, BlockPos previousPos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DeviderBlockEntity devider) {
            return devider.getForwardPositions(previousPos);
        }
        return getForwardNeighbors(level, pos);
    }

    public static boolean isTravelAllowed(Level level, BlockPos from, BlockPos to) {
        return isTravelAllowed(level, from, to, true);
    }

    /**
     * Checks whether two components form a valid route without treating a
     * powered valve as a permanent break in that route. Runtime movement still
     * uses {@link #isTravelAllowed(Level, BlockPos, BlockPos)} and stops at the
     * closed valve, allowing items behind it to form a queue.
     */
    public static boolean isRouteAllowed(Level level, BlockPos from, BlockPos to) {
        return isTravelAllowed(level, from, to, false);
    }

    private static boolean isTravelAllowed(
            Level level,
            BlockPos from,
            BlockPos to,
            boolean respectValvePower
    ) {
        BlockEntity fromBlockEntity = level.getBlockEntity(from);
        BlockEntity toBlockEntity = level.getBlockEntity(to);

        if (fromBlockEntity instanceof DeviderBlockEntity devider) {
            Direction movementDirection = getDirectionBetween(from, to);
            if (movementDirection == null
                    || !componentAllowsMovement(level, to, movementDirection, respectValvePower)) {
                return false;
            }
            if (devider.isStraightPosition(to)) {
                return devider.getJunctionRole() == DeviderBlockEntity.JunctionRole.MERGER
                        && (toBlockEntity instanceof PneumaticTubeBlockEntity
                        || toBlockEntity instanceof ItemPumpBlockEntity);
            }
            return devider.isBranchOutputEnabled(to)
                    && level.getBlockState(to).getBlock() instanceof CurvaturePneumaticTubeBlock
                    && toBlockEntity instanceof PneumaticTubeBlockEntity;
        }

        if (toBlockEntity instanceof DeviderBlockEntity devider) {
            Direction movementDirection = getDirectionBetween(from, to);
            if (movementDirection == null
                    || !componentAllowsMovement(level, from, movementDirection, respectValvePower)) {
                return false;
            }
            if (devider.isStraightPosition(from)) {
                return devider.getJunctionRole() == DeviderBlockEntity.JunctionRole.DIVIDER
                        && (fromBlockEntity instanceof PneumaticTubeBlockEntity
                        || fromBlockEntity instanceof ItemPumpBlockEntity);
            }
            return devider.isBranchInputEnabled(from)
                    && level.getBlockState(from).getBlock() instanceof CurvaturePneumaticTubeBlock
                    && fromBlockEntity instanceof PneumaticTubeBlockEntity;
        }

        Direction direction = getDirectionBetween(from, to);
        return direction != null && strictTravelAllowed(
                level,
                from,
                to,
                direction,
                respectValvePower
        );
    }

    private static boolean componentAllowsMovement(
            Level level,
            BlockPos pos,
            Direction movementDirection,
            boolean respectValvePower
    ) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ItemPumpBlock pump) {
            return pump.allowsTravel(level, pos, state, movementDirection);
        }
        if (state.getBlock() instanceof ValveBlock valve) {
            return respectValvePower
                    ? valve.allowsTravel(state, movementDirection)
                    : valve.canTravelTo(state, movementDirection);
        }
        return true;
    }

    public static boolean isTravelAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        if (!isPathNode(level, to)) {
            return false;
        }

        return strictTravelAllowed(level, from, to, direction, true);
    }

    public static boolean isRouteAllowed(Level level, BlockPos from, BlockPos to, Direction direction) {
        if (!isPathNode(level, to)) {
            return false;
        }

        return strictTravelAllowed(level, from, to, direction, false);
    }

    private static Direction getDirectionBetween(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.values()) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
    }

    private static boolean strictTravelAllowed(
            Level level,
            BlockPos from,
            BlockPos to,
            Direction direction,
            boolean respectValvePower
    ) {
        BlockEntity fromBlockEntity = level.getBlockEntity(from);
        BlockEntity toBlockEntity = level.getBlockEntity(to);

        if (fromBlockEntity instanceof ItemPumpBlockEntity fromPump
                && !fromPump.canTravelTo(level, direction)) {
            return false;
        }

        if (!componentAllowsMovement(level, from, direction, respectValvePower)) {
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

        if (!componentAllowsMovement(level, to, direction, respectValvePower)) {
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
