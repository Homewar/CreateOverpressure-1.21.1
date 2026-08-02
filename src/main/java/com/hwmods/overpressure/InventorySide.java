package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;

public record InventorySide(BlockPos pos, IItemHandler handler) {
}
