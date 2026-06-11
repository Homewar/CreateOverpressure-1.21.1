package com.hwmods.boilingpoint;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;

public record InventorySide(BlockPos pos, IItemHandler handler) {
}
