package com.hwmods.overpressure.transport;

import net.minecraft.world.level.Level;

public interface TransportQueueObserver extends TransportNodeComponent {
    void onQueueStateChanged(Level level, boolean clogged);
}
