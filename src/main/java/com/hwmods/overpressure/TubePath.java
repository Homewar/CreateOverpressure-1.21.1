package com.hwmods.overpressure;

import java.util.List;

import net.minecraft.core.BlockPos;

public record TubePath(List<BlockPos> tubePositions, BlockPos targetConnector, boolean spillsAtEnd) {
    public TubePath(List<BlockPos> tubePositions, BlockPos targetConnector) {
        this(tubePositions, targetConnector, false);
    }

    public boolean isEmpty() {
        return tubePositions.isEmpty();
    }
}
