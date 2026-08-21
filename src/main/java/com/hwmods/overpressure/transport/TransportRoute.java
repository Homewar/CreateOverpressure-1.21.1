package com.hwmods.overpressure.transport;

import java.util.List;

import net.minecraft.core.BlockPos;

public final class TransportRoute {
    private List<BlockPos> blockPath;
    private TubeGraphRoute graphRoute;
    private BlockPos sourceConnector;
    private BlockPos targetConnector;
    private boolean spillsAtEnd;
    private boolean protectedFromJunctionSpill;
    private List<BlockPos> reservedMergerPassages = List.of();
    private List<BlockPos> speedControllers = List.of();
    private boolean speedControllerCacheValid;
    private long topologyVersion = Long.MIN_VALUE;

    public TransportRoute(
            List<BlockPos> blockPath,
            TubeGraphRoute graphRoute,
            BlockPos sourceConnector,
            BlockPos targetConnector,
            boolean spillsAtEnd
    ) {
        this.blockPath = List.copyOf(blockPath);
        this.graphRoute = graphRoute;
        this.sourceConnector = sourceConnector == null ? null : sourceConnector.immutable();
        this.targetConnector = targetConnector == null ? null : targetConnector.immutable();
        this.spillsAtEnd = spillsAtEnd;
    }

    public List<BlockPos> blockPath() { return blockPath; }
    public TubeGraphRoute graphRoute() { return graphRoute; }
    public BlockPos sourceConnector() { return sourceConnector; }
    public BlockPos targetConnector() { return targetConnector; }
    public boolean spillsAtEnd() { return spillsAtEnd; }
    public boolean protectedFromJunctionSpill() { return protectedFromJunctionSpill; }
    public List<BlockPos> reservedMergerPassages() { return reservedMergerPassages; }
    public List<BlockPos> speedControllers() { return speedControllers; }
    public boolean speedControllerCacheValid() { return speedControllerCacheValid; }
    public long topologyVersion() { return topologyVersion; }

    public void replacePath(List<BlockPos> path, TubeGraphRoute graphRoute, BlockPos target, boolean spills) {
        this.blockPath = List.copyOf(path);
        this.graphRoute = graphRoute;
        this.targetConnector = target == null ? null : target.immutable();
        this.spillsAtEnd = spills;
    }

    public void setSpillsAtEnd(boolean value) { spillsAtEnd = value; }
    public void setProtectedFromJunctionSpill(boolean value) { protectedFromJunctionSpill = value; }
    public void setReservedMergerPassages(List<BlockPos> value) { reservedMergerPassages = List.copyOf(value); }
    public void setSpeedControllers(List<BlockPos> value) { speedControllers = List.copyOf(value); }
    public void setSpeedControllerCacheValid(boolean value) { speedControllerCacheValid = value; }
    public void setTopologyVersion(long value) { topologyVersion = value; }
}
