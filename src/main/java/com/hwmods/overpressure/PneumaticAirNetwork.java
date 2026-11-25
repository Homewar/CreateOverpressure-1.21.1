package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PneumaticAirNetwork {
    private static final int LINK_RANGE = 8;
    private static final int LINK_RANGE_SQUARED = LINK_RANGE * LINK_RANGE;

    private PneumaticAirNetwork() {}

    static PneumaticConnectorBlockEntity findConnector(Level level, Vec3 origin) {
        Supply supply = findSupply(level, origin, true);
        return supply == null ? null : supply.connector();
    }

    static Supply findPillarSupply(Level level, Vec3 origin) {
        return findSupply(level, origin, false);
    }

    private static Supply findSupply(Level level, Vec3 origin, boolean allowDirectConnection) {
        Queue<Node> queue = new ArrayDeque<>();
        Set<BlockPos> discovered = new HashSet<>();
        var nearbyPillars = new ArrayList<>(level.getEntitiesOfClass(OxygenPillarEntity.class,
                new AABB(origin, origin).inflate(LINK_RANGE),
                relay -> relay.isAlive() && relay.distanceToSqr(origin) <= LINK_RANGE_SQUARED));
        nearbyPillars.sort(Comparator.comparingDouble(pillar -> pillar.distanceToSqr(origin)));
        for (OxygenPillarEntity pillar : nearbyPillars) {
            enqueue(queue, discovered, pillar.blockPosition(), pillar);
        }
        if (allowDirectConnection) {
            enqueueBlockLinks(level, BlockPos.containing(origin), false, null, queue, discovered);
        }

        float bestPotential = 0.0F;
        Supply supplier = null;
        while (!queue.isEmpty()) {
            Node link = queue.remove();
            BlockPos node = link.pos();
            if (level.getBlockEntity(node) instanceof PneumaticConnectorBlockEntity connector) {
                float potential = connector.getPotentialItemSpeed();
                if (potential > bestPotential) {
                    bestPotential = potential;
                    supplier = new Supply(connector, link.entryPillar());
                }
            }
            for (OxygenPillarEntity relay : level.getEntitiesOfClass(OxygenPillarEntity.class,
                    new AABB(node).inflate(LINK_RANGE),
                    pillar -> pillar.isAlive() && node.distSqr(pillar.blockPosition()) <= LINK_RANGE_SQUARED)) {
                enqueue(queue, discovered, relay.blockPosition(), link.entryPillar());
            }
            enqueueBlockLinks(level, node, true, link.entryPillar(), queue, discovered);
        }
        return supplier;
    }

    private static void enqueueBlockLinks(Level level, BlockPos node, boolean includeLegacyPillars,
            OxygenPillarEntity entryPillar, Queue<Node> queue, Set<BlockPos> discovered) {
        BlockPos min = node.offset(-LINK_RANGE, -LINK_RANGE, -LINK_RANGE);
        BlockPos max = node.offset(LINK_RANGE, LINK_RANGE, LINK_RANGE);
        for (int cx = min.getX() >> 4; cx <= (max.getX() >> 4); cx++) {
            for (int cz = min.getZ() >> 4; cz <= (max.getZ() >> 4); cz++) {
                // Breathing checks run every tick, so never load chunks during the walk.
                var chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos neighbor : BlockPos.betweenClosed(
                        Math.max(min.getX(), cx << 4), min.getY(), Math.max(min.getZ(), cz << 4),
                        Math.min(max.getX(), (cx << 4) + 15), max.getY(), Math.min(max.getZ(), (cz << 4) + 15))) {
                    if (node.distSqr(neighbor) > LINK_RANGE_SQUARED || discovered.contains(neighbor)) {
                        continue;
                    }
                    var state = chunk.getBlockState(neighbor);
                    if (state.is(ModBlocks.PNEUMATIC_CONNECTOR.get())
                            || includeLegacyPillars && state.is(ModBlocks.OXYGEN_PILLAR.get())) {
                        enqueue(queue, discovered, neighbor, entryPillar);
                    }
                }
            }
        }
    }

    private static void enqueue(Queue<Node> queue, Set<BlockPos> discovered, BlockPos pos, OxygenPillarEntity entryPillar) {
        BlockPos key = pos.immutable();
        if (discovered.add(key)) {
            queue.add(new Node(key, entryPillar));
        }
    }

    record Supply(PneumaticConnectorBlockEntity connector, OxygenPillarEntity entryPillar) {}

    private record Node(BlockPos pos, OxygenPillarEntity entryPillar) {}
}
