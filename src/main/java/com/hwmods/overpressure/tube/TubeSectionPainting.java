package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TubeSectionPainting {
    public record SpanKey(UUID section, int index) {}
    public static int apply(Level level, Object start, Player player, int color, boolean glow) {
        if (!player.getAbilities().mayBuild) return 0;
        ArrayDeque<Object> queue = new ArrayDeque<>(); Set<Object> visited = new HashSet<>();
        Map<UUID, List<TubeSection.Span>> changedSections = new HashMap<>();
        queue.add(start); int traversed = 0, changed = 0;
        while (!queue.isEmpty() && traversed < 32) {
            Object key = queue.remove(); if (!visited.add(key)) continue;
            if (key instanceof BlockPos pos) {
                if (!level.isLoaded(pos) || !level.mayInteract(player, pos) || !TubePainting.isTube(level.getBlockState(pos))
                        || !(level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube)) continue;
                traversed++;
                if (color >= 0 && tube.getTubeColor() != color) { tube.setTubeColor(color); changed++; }
                if (glow && !tube.isTubeGlowing()) { tube.setTubeGlowing(true); changed++; }
                for (Direction direction : Direction.values()) {
                    if (!tube.getBlockState().getValue(PneumaticTubeBlock.getConnectionProperty(direction))) continue;
                    BlockPos next = pos.relative(direction);
                    var state = level.getBlockState(next);
                    if (TubePainting.isTube(state) && state.getValue(PneumaticTubeBlock.getConnectionProperty(direction.getOpposite()))) queue.add(next);
                    Vec3 outward = Vec3.atLowerCornerOf(direction.getNormal());
                    for (var port : TubeSections.portsAt(level, Vec3.atCenterOf(pos).add(outward.scale(0.5)), outward)) addPort(level, queue, port);
                }
            } else if (key instanceof SpanKey part) {
                TubeSection section = TubeSections.get(level).get(part.section);
                if (section == null || part.index < 0 || part.index >= section.spans().size()) continue;
                var span = section.spans().get(part.index);
                BlockPos pos = BlockPos.containing(span.curve().pointAt(0.5));
                if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) continue;
                traversed++;
                int rgb = color < 0 ? span.color() : color;
                boolean bright = glow || span.glowing();
                if (rgb != span.color() || bright != span.glowing()) {
                    changedSections.computeIfAbsent(section.id(), ignored -> new ArrayList<>(section.spans()))
                            .set(part.index, new TubeSection.Span(span.curve(), rgb, bright));
                    changed++;
                }
                if (part.index > 0) queue.add(new SpanKey(part.section, part.index - 1));
                if (part.index + 1 < section.spans().size()) queue.add(new SpanKey(part.section, part.index + 1));
                for (boolean first : new boolean[]{true, false}) {
                    if (first ? part.index != 0 : part.index != section.spans().size() - 1) continue;
                    var port = section.port(first);
                    for (var next : TubeSections.portsAt(level, port.position(), port.outward())) addPort(level, queue, next);
                    queue.add(BlockPos.containing(port.position().add(port.outward().scale(0.05))));
                }
            }
        }
        changedSections.forEach((id, spans) -> TubeSections.put(level, new TubeSection(id, spans, TubeSections.get(level).get(id).materialCost())));
        return changed;
    }
    private static void addPort(Level level, ArrayDeque<Object> queue, TubeSection.Port port) {
        var section = TubeSections.get(level).get(port.section());
        if (section != null) queue.add(new SpanKey(port.section(), port.start() ? 0 : section.spans().size() - 1));
    }
}
