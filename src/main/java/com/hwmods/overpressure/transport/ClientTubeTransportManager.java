package com.hwmods.overpressure.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.hwmods.overpressure.MovingTubeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Client-side registry of immutable-ish render snapshots received from the server. */
public final class ClientTubeTransportManager {
    private static final Map<Level, ClientTubeTransportManager> BY_LEVEL = new WeakHashMap<>();

    private final Map<BlockPos, MovingTubeItem> snapshots = new HashMap<>();

    public static ClientTubeTransportManager get(Level level) {
        synchronized (BY_LEVEL) {
            return BY_LEVEL.computeIfAbsent(level, ignored -> new ClientTubeTransportManager());
        }
    }

    public void update(BlockPos owner, @Nullable MovingTubeItem snapshot) {
        if (snapshot == null) {
            snapshots.remove(owner);
        } else {
            snapshots.put(owner.immutable(), snapshot);
        }
    }

    @Nullable
    public MovingTubeItem snapshot(BlockPos owner) {
        return snapshots.get(owner);
    }

    private ClientTubeTransportManager() {
    }
}
