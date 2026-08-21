package com.hwmods.overpressure.transport;

import net.minecraft.world.item.ItemStack;

/** The payload is intentionally unaware of routing and world topology. */
public final class TransportCargo {
    private ItemStack stack;

    public TransportCargo(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack stack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }
}
