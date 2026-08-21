package com.hwmods.overpressure.transport;

/** A node capable of providing flow to a selected graph route. */
public interface TransportFlowSource extends TransportNodeComponent {
    boolean isRunning();

    int getMoveTime();

    @Override
    default boolean hasCargoSlot() {
        return false;
    }
}
