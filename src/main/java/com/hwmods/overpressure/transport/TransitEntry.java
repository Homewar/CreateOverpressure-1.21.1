package com.hwmods.overpressure.transport;

import java.util.List;

import com.hwmods.overpressure.MovingTubeItem;

import net.minecraft.core.BlockPos;

/** Server-owned transport state. MovingTubeItem is now only its wire/save view. */
public final class TransitEntry {
    private final TransportCargo cargo;
    private TransportRoute route;
    private final TransportRuntimeState state;

    public TransitEntry(TransportCargo cargo, TransportRoute route, TransportRuntimeState state) {
        this.cargo = cargo;
        this.route = route;
        this.state = state;
    }

    public TransportCargo cargo() { return cargo; }
    public TransportRoute route() { return route; }
    public TransportRuntimeState state() { return state; }
    public void setRoute(TransportRoute route) { this.route = route; }

    public MovingTubeItem snapshot() {
        MovingTubeItem item = new MovingTubeItem(
                cargo.stack().copy(),
                route.blockPath(),
                route.targetConnector()
        );
        item.sourceConnector = route.sourceConnector();
        item.spillsAtEnd = route.spillsAtEnd();
        item.protectedFromJunctionSpill = route.protectedFromJunctionSpill();
        item.reservedMergerPassages = route.reservedMergerPassages();
        item.speedControllers = route.speedControllers().stream()
                .map(MovingTubeItem.SpeedController::new)
                .toList();
        item.hasSpeedControllerCache = route.speedControllerCacheValid();
        item.transportTopologyVersion = route.topologyVersion();
        item.startPathIndex = state.startPathIndex;
        item.pathIndex = state.pathIndex;
        item.progress = state.progress;
        item.segmentProgress = state.segmentProgress;
        item.moveTime = state.moveTime;
        item.segmentDuration = state.segmentDuration;
        item.animationId = state.animationId;
        item.startedAtGameTime = state.startedAtGameTime;
        item.lastSpeedCheckGameTime = state.lastSpeedCheckGameTime;
        item.lastTickedGameTime = state.lastTickedGameTime;
        item.waitingForNextTube = state.status == TransitStatus.WAITING_FOR_SPACE;
        item.waitingAtDestination = state.status == TransitStatus.WAITING_AT_DESTINATION
                || state.status == TransitStatus.SPILLING;
        return item;
    }

    public static TransitEntry restore(MovingTubeItem item, BlockPos owner, TubeGraphRoute graphRoute) {
        TransportRoute route = new TransportRoute(
                item.path,
                graphRoute,
                item.sourceConnector,
                item.targetConnector,
                item.spillsAtEnd
        );
        route.setProtectedFromJunctionSpill(item.protectedFromJunctionSpill);
        route.setReservedMergerPassages(item.reservedMergerPassages);
        route.setSpeedControllers(item.speedControllers.stream()
                .map(MovingTubeItem.SpeedController::pos)
                .toList());
        route.setSpeedControllerCacheValid(item.hasSpeedControllerCache);
        route.setTopologyVersion(item.transportTopologyVersion);

        TransportRuntimeState state = new TransportRuntimeState(owner);
        state.startPathIndex = item.startPathIndex;
        state.pathIndex = item.pathIndex;
        state.progress = item.progress;
        state.segmentProgress = item.segmentProgress;
        state.moveTime = item.moveTime;
        state.segmentDuration = item.segmentDuration;
        state.animationId = item.animationId;
        state.startedAtGameTime = item.startedAtGameTime;
        state.lastSpeedCheckGameTime = item.lastSpeedCheckGameTime;
        state.lastTickedGameTime = item.lastTickedGameTime;
        state.status = item.waitingAtDestination
                ? (item.spillsAtEnd ? TransitStatus.SPILLING : TransitStatus.WAITING_AT_DESTINATION)
                : item.waitingForNextTube ? TransitStatus.WAITING_FOR_SPACE : TransitStatus.MOVING;
        return new TransitEntry(new TransportCargo(item.stack), route, state);
    }
}
