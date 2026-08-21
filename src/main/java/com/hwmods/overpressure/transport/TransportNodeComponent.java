package com.hwmods.overpressure.transport;

/** Implemented by functional graph nodes; passive tubes deliberately do not implement it. */
public interface TransportNodeComponent {
    TubeGraphRoute.NodeKind graphNodeKind();

    default boolean participatesInPath() {
        return true;
    }

    default boolean hasCargoSlot() {
        return true;
    }
}
