package com.hwmods.overpressure;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class MovingTubeItem {
    public ItemStack stack;
    public List<BlockPos> path;
    public List<BlockPos> speedControllers;
    public BlockPos targetConnector;
    public boolean spillsAtEnd;
    public int pathIndex;
    public int progress;
    public float segmentProgress;
    public int moveTime;
    public long animationId;
    public long startedAtGameTime;
    public boolean waitingForNextTube;
    public boolean waitingAtDestination;
    public boolean hasSpeedControllerCache;
    public long lastSpeedCheckGameTime;
    public long lastTickedGameTime;

    public MovingTubeItem(ItemStack stack, List<BlockPos> path, BlockPos targetConnector) {
        this.stack = stack;
        this.path = path;
        this.speedControllers = List.of();
        this.targetConnector = targetConnector;
        this.spillsAtEnd = false;
        this.pathIndex = 0;
        this.progress = 0;
        this.segmentProgress = 0.0f;
        this.moveTime = PneumaticTubeBlockEntity.BASE_MOVE_TIME;
        this.animationId = 0L;
        this.startedAtGameTime = 0L;
        this.waitingForNextTube = false;
        this.waitingAtDestination = false;
        this.hasSpeedControllerCache = false;
        this.lastSpeedCheckGameTime = Long.MIN_VALUE;
        this.lastTickedGameTime = -1L;
    }
}
