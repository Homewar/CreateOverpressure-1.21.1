package com.hwmods.overpressure;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public class MovingTubeItem {
    public record SpeedController(BlockPos pos) {
    }

    public ItemStack stack;
    public List<BlockPos> path;
    public List<BlockPos> reservedMergerPassages;
    public List<SpeedController> speedControllers;
    public BlockPos sourceConnector;
    public BlockPos targetConnector;
    public boolean spillsAtEnd;
    public int startPathIndex;
    public int pathIndex;
    public int progress;
    public float segmentProgress;
    public int moveTime;
    public int segmentDuration;
    public long animationId;
    public long startedAtGameTime;
    public boolean waitingForNextTube;
    public boolean waitingAtDestination;
    public boolean startsAtTubeOpenEnd;
    public boolean endsAtTubeOpenEnd;
    public boolean protectedFromJunctionSpill;
    public boolean hasSpeedControllerCache;
    public long transportTopologyVersion;
    public long lastSpeedCheckGameTime;
    public long lastTickedGameTime;

    public MovingTubeItem(ItemStack stack, List<BlockPos> path, BlockPos targetConnector) {
        this.stack = stack;
        this.path = path;
        this.reservedMergerPassages = List.of();
        this.speedControllers = List.of();
        this.sourceConnector = null;
        this.targetConnector = targetConnector;
        this.spillsAtEnd = false;
        this.startPathIndex = 0;
        this.pathIndex = 0;
        this.progress = 0;
        this.segmentProgress = 0.0f;
        this.moveTime = PneumaticTubeBlockEntity.BASE_MOVE_TIME;
        this.segmentDuration = PneumaticTubeBlockEntity.BASE_MOVE_TIME;
        this.animationId = 0L;
        this.startedAtGameTime = 0L;
        this.waitingForNextTube = false;
        this.waitingAtDestination = false;
        this.startsAtTubeOpenEnd = false;
        this.endsAtTubeOpenEnd = false;
        this.protectedFromJunctionSpill = false;
        this.hasSpeedControllerCache = false;
        this.transportTopologyVersion = Long.MIN_VALUE;
        this.lastSpeedCheckGameTime = Long.MIN_VALUE;
        this.lastTickedGameTime = -1L;
    }
}
