package com.hwmods.overpressure;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

public final class SimulatedCompat {
    private static final String SIMULATED_MOD_ID = "simulated";
    private static final String MOVEMENT_CHECKS_CLASS =
            "dev.simulated_team.simulated.index.SimBlockMovementChecks";
    private static final String ADDITIONAL_BLOCKS_CLASS = MOVEMENT_CHECKS_CLASS + "$AdditionalBlocks";

    public static void register() {
        if (!ModList.get().isLoaded(SIMULATED_MOD_ID)) {
            return;
        }

        try {
            Class<?> movementChecks = Class.forName(MOVEMENT_CHECKS_CLASS);
            Class<?> additionalBlocks = Class.forName(ADDITIONAL_BLOCKS_CLASS);
            Object callback = Proxy.newProxyInstance(
                    additionalBlocks.getClassLoader(),
                    new Class<?>[]{additionalBlocks},
                    SimulatedCompat::invokeAdditionalBlocks
            );

            Method register = movementChecks.getMethod("registerAdditionalBlocks", additionalBlocks);
            register.invoke(null, callback);
            Overpressure.LOGGER.info("Registered Create Simulated assembly support for pneumatic lines");
        } catch (ReflectiveOperationException | LinkageError exception) {
            Overpressure.LOGGER.error("Could not register Create Simulated assembly support", exception);
        }
    }

    private static Object invokeAdditionalBlocks(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }

        if (!method.getName().equals("addAdditionalBlocks") || args == null || args.length != 4) {
            throw new UnsupportedOperationException(method.toString());
        }

        BlockState state = (BlockState) args[0];
        Level level = (Level) args[1];
        BlockPos pos = (BlockPos) args[2];
        @SuppressWarnings("unchecked")
        Set<BlockPos> visited = (Set<BlockPos>) args[3];
        return findConnectedBlocks(state, level, pos, visited);
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "Overpressure pneumatic line assembly callback";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    static Iterable<BlockPos> findConnectedBlocks(
            BlockState state,
            Level level,
            BlockPos pos,
            Set<BlockPos> visited
    ) {
        if (!isLineComponent(state)) {
            return List.of();
        }

        List<BlockPos> connected = new ArrayList<>(2);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if (visited.contains(neighborPos)) {
                continue;
            }

            BlockState neighborState = level.getBlockState(neighborPos);
            if (opensToward(state, direction)
                    && opensToward(neighborState, direction.getOpposite())) {
                connected.add(neighborPos);
            }
        }

        return connected;
    }

    private static boolean isLineComponent(BlockState state) {
        return state.getBlock() instanceof PneumaticTubeBlock
                || state.getBlock() instanceof ItemPumpBlock
                || state.getBlock() instanceof PneumaticConnectionBlock;
    }

    private static boolean opensToward(BlockState state, Direction direction) {
        if (state.getBlock() instanceof PneumaticTubeBlock) {
            return state.getValue(PneumaticTubeBlock.getConnectionProperty(direction));
        }

        if (state.getBlock() instanceof ItemPumpBlock pump) {
            return pump.canTravelTo(state, direction);
        }

        if (!(state.getBlock() instanceof PneumaticConnectionBlock)) {
            return false;
        }

        Direction facing = state.getValue(PneumaticConnectionBlock.FACING);
        return switch (state.getValue(PneumaticConnectionBlock.MODE)) {
            case EXTRACT -> direction == facing;
            case INSERT -> direction == facing.getOpposite();
            case DISABLED -> false;
        };
    }

    private SimulatedCompat() {
    }
}
