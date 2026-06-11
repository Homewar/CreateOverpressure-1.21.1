package com.hwmods.boilingpoint;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class MovingTubeItem {
    public ItemStack stack;
    public List<BlockPos> path;
    public BlockPos targetConnector;
    public int pathIndex;
    public int progress;
    public int moveTime;
    public long animationId;
    public long lastTickedGameTime;

    public MovingTubeItem(ItemStack stack, List<BlockPos> path, BlockPos targetConnector) {
        this.stack = stack;
        this.path = path;
        this.targetConnector = targetConnector;
        this.pathIndex = 0;
        this.progress = 0;
        this.moveTime = PneumaticTubeBlockEntity.BASE_MOVE_TIME;
        this.animationId = 0L;
        this.lastTickedGameTime = -1L;
    }
}
