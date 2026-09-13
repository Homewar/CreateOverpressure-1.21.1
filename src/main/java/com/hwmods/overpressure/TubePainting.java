package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
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
        if (rgb == (net.minecraft.world.item.DyeColor.WHITE.getTextureDiffuseColor() & 0xFFFFFF)) rgb = 0xFFFFFF;
        return com.hwmods.overpressure.tube.TubeSectionPainting.apply(level, start, player, rgb, false);
    }

    public static int glow(Level level, BlockPos start, Player player) {
        return com.hwmods.overpressure.tube.TubeSectionPainting.apply(level, start, player, -1, true);
    }
}
