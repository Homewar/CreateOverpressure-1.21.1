package com.hwmods.overpressure.transport;

import net.minecraft.core.BlockPos;

public final class TransportRuntimeState {
    private BlockPos owner;
    public int startPathIndex;
    public int pathIndex;
    public int progress;
    public float segmentProgress;
    public int moveTime;
    public int segmentDuration;
    public long animationId;
    public long startedAtGameTime;
    public long lastSpeedCheckGameTime = Long.MIN_VALUE;
    public long lastTickedGameTime = -1L;
    public TransitStatus status = TransitStatus.MOVING;

    public TransportRuntimeState(BlockPos owner) {
        this.owner = owner.immutable();
    }

    public BlockPos owner() {
        return owner;
    }

    public void setOwner(BlockPos owner) {
        this.owner = owner.immutable();
    }
}
