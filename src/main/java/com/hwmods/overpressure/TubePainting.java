package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class TubePainting {
    private TubePainting() {}

    public static boolean isTube(BlockState state) {
        return state.is(ModBlocks.PNEUMATIC_TUBE.get())
                || state.is(ModBlocks.CURVATURE_PNEUMATIC_TUBE.get())
                || state.getBlock() instanceof EncasedPneumaticTubeBlock;
    }

    public static int paint(Level level, BlockPos start, Player player, int color) {
        int rgb = color & 0xFFFFFF;
        return apply(level, start, player, tube -> {
            if (tube.getTubeColor() == rgb) return false;
            tube.setTubeColor(rgb);
            return true;
        });
    }

    public static int glow(Level level, BlockPos start, Player player) {
        return apply(level, start, player, tube -> {
            if (tube.isTubeGlowing()) return false;
            tube.setTubeGlowing(true);
            return true;
        });
    }

    private static int apply(Level level, BlockPos start, Player player,
                             java.util.function.Predicate<PneumaticTubeBlockEntity> operation) {
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        queue.add(start.immutable());
        visited.add(start.immutable());
        int traversed = 0;
        int changed = 0;
        while (!queue.isEmpty() && traversed < 32) {
            BlockPos pos = queue.remove();
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos) || !player.getAbilities().mayBuild) continue;
            BlockState state = level.getBlockState(pos);
            if (!isTube(state) || !(level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube)) continue;
            traversed++;
            if (operation.test(tube)) changed++;
            for (Direction direction : Direction.values()) {
                if (!state.getValue(PneumaticTubeBlock.getConnectionProperty(direction))) continue;
                BlockPos next = pos.relative(direction);
                if (!level.isLoaded(next)) continue;
                BlockState neighbor = level.getBlockState(next);
                if (isTube(neighbor) && neighbor.getValue(PneumaticTubeBlock.getConnectionProperty(direction.getOpposite()))
                        && visited.add(next)) queue.add(next);
            }
        }
        return changed;
    }
}
