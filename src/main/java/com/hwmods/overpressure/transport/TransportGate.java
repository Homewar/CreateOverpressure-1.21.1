package com.hwmods.overpressure.transport;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Separates structural route connectivity from a gate's current runtime state. */
public interface TransportGate extends TransportNodeComponent {
    boolean allowsRoute(Level level, Direction travelDirection);

    boolean allowsTravelNow(Level level, Direction travelDirection);
}
