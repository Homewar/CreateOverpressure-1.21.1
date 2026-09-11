package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.*;
import com.hwmods.overpressure.math.CubicBezier;
import com.hwmods.overpressure.transport.TubeTransportManager;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class LegacyTubeMigration {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new IdentityHashMap<>();
    private static final Set<Level> MIGRATING = Collections.newSetFromMap(new IdentityHashMap<>());
    public static boolean isMigrating(Level level) { return MIGRATING.contains(level); }
    public static void enqueue(ServerLevel level, BlockPos pos) { PENDING.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable()); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { PENDING.remove(event.getLevel()); MIGRATING.remove(event.getLevel()); }
    @SubscribeEvent public static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        var pending = PENDING.get(level); if (pending == null) return;
        for (BlockPos pos : List.copyOf(pending)) if (migrate(level, pos)) pending.remove(pos);
    }
    public static boolean migrate(ServerLevel level, BlockPos origin) {
        if (!level.isLoaded(origin)) return false;
        if (!(level.getBlockEntity(origin) instanceof CurvaturePneumaticTubeEntity first)) return true;
        UUID id = first.getSectionId();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(); queue.add(origin);
        Map<BlockPos, CurvaturePneumaticTubeEntity> blocks = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove(); if (blocks.containsKey(pos)) continue;
            if (!level.isLoaded(pos)) return false;
            if (!(level.getBlockEntity(pos) instanceof CurvaturePneumaticTubeEntity tube) || !tube.getSectionId().equals(id)) continue;
            blocks.put(pos.immutable(), tube);
            if (blocks.size() > 512) return false;
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
        Map<BlockPos, CubicBezier> remaining = new LinkedHashMap<>();
        for (var entry : blocks.entrySet()) {
            Vec3 offset = Vec3.atLowerCornerOf(entry.getKey()); var tube = entry.getValue();
            remaining.put(entry.getKey(), new CubicBezier(offset.add(tube.getP0()), offset.add(tube.getP1()), offset.add(tube.getP2()), offset.add(tube.getP3())));
        }
        List<TubeSection> sections = new ArrayList<>();
        Map<BlockPos, TubeSection> ownerToSection = new HashMap<>();
        Map<BlockPos, Double> offsets = new HashMap<>();
        while (!remaining.isEmpty()) {
            BlockPos beginning = remaining.keySet().stream().filter(pos -> remaining.entrySet().stream()
                    .noneMatch(e -> !e.getKey().equals(pos) && e.getValue().p3().distanceToSqr(remaining.get(pos).p0()) < 1.0E-8))
                    .findFirst().orElse(remaining.keySet().iterator().next());
            List<BlockPos> ordered = new ArrayList<>(); List<TubeSection.Span> spans = new ArrayList<>();
            BlockPos current = beginning; double distance = 0;
            while (current != null) {
                CubicBezier curve = remaining.remove(current); var block = blocks.get(current);
                ordered.add(current); offsets.put(current, distance);
                var span = new TubeSection.Span(curve, block.getTubeColor(), block.isTubeGlowing()); spans.add(span);
                distance += new TubeSectionGeometry(List.of(span)).length();
                current = remaining.entrySet().stream().filter(e -> curve.p3().distanceToSqr(e.getValue().p0()) < 1.0E-8)
                        .map(Map.Entry::getKey).findFirst().orElse(null);
            }
            UUID sectionId = sections.isEmpty() ? id : UUID.randomUUID();
            var section = new TubeSection(sectionId, spans, spans.size()); sections.add(section);
            for (BlockPos pos : ordered) ownerToSection.put(pos, section);
        }
        Map<BlockPos, MovingTubeItem> cargo = new HashMap<>();
        for (BlockPos pos : blocks.keySet()) {
            var snapshot = TubeTransportManager.get(level).takeForMigration(pos);
            if (snapshot != null) cargo.put(pos, snapshot);
        }
        MIGRATING.add(level);
        try {
            for (TubeSection section : sections) TubeSections.put(level, section);
            for (var entry : blocks.entrySet()) {
                boolean water = entry.getValue().getBlockState().getValue(PneumaticTubeBlock.WATERLOGGED);
                level.setBlock(entry.getKey(), water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            for (TubeSection section : sections) TubeSections.changed(level, section);
        } finally { MIGRATING.remove(level); }
        for (var entry : cargo.entrySet()) {
            var section = ownerToSection.get(entry.getKey());
            var snapshot = entry.getValue();
            double offset = offsets.get(entry.getKey());
            boolean forward = true;
            if (snapshot.pathIndex + 1 < snapshot.path.size() && offsets.containsKey(snapshot.path.get(snapshot.pathIndex + 1))) {
                forward = offsets.get(snapshot.path.get(snapshot.pathIndex + 1)) > offset;
            } else if (snapshot.pathIndex > 0 && offsets.containsKey(snapshot.path.get(snapshot.pathIndex - 1))) {
                forward = offset > offsets.get(snapshot.path.get(snapshot.pathIndex - 1));
            }
            var old = blocks.get(entry.getKey());
            var part = new TubeSection.Span(new CubicBezier(old.getP0(), old.getP1(), old.getP2(), old.getP3()), old.getTubeColor(), old.isTubeGlowing());
            double progress = Math.max(0, Math.min(1, snapshot.segmentProgress));
            double along = offset + new TubeSectionGeometry(List.of(part)).length() * (forward ? progress : 1 - progress);
            SectionTransport.get(level).restoreLegacy(level, section, snapshot, along, forward);
        }
        return true;
    }
}
