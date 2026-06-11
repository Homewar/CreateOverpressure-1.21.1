package com.hwmods.boilingpoint;

import java.util.List;

import net.minecraft.core.BlockPos;

public record TubePath(List<BlockPos> tubePositions, BlockPos targetConnector) {
    public boolean isEmpty() {
        return tubePositions.isEmpty();
    }
}
