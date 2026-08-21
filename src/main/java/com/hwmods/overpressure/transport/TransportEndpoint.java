package com.hwmods.overpressure.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Inventory boundary of the graph. Extraction scheduling remains endpoint-local. */
public interface TransportEndpoint extends TransportNodeComponent {
    boolean canReceiveFrom(Level level, BlockPos sourcePos);

    boolean allowsRoute(Level level, Direction travelDirection);

    ItemStack insertCargo(Level level, ItemStack stack);

    @Override
    default boolean participatesInPath() {
        return false;
    }
}
