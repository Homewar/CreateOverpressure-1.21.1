package com.hwmods.overpressure.transport;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Routing and merge arbitration contract used by the graph instead of a concrete block class. */
public interface TransportJunction extends TransportNodeComponent {
    List<BlockPos> connectedPorts();

    List<BlockPos> forwardPorts(BlockPos previous);

    List<BlockPos> orderedBranchPorts();

    BlockPos straightPort();

    boolean isStraightPort(BlockPos pos);

    boolean isBranchPort(BlockPos pos);

    boolean isBranchOutputEnabled(BlockPos pos);

    boolean isBranchInputEnabled(BlockPos pos);

    boolean isMerger();

    boolean isMergerPassage(BlockPos branchPos, BlockPos straightPos);

    boolean canMergeFrom(Level level, BlockPos branchPos);

    void markBranchUsed(BlockPos branchPos);

    void markMergeInputUsed(BlockPos branchPos);
}
