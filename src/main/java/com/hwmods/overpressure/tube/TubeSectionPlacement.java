package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.PneumaticTubeBlockItem.PlanTube;
import com.hwmods.overpressure.math.CubicBezier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class TubeSectionPlacement {
    public static TubeSection fromPlan(List<PlanTube> tubes) {
        List<TubeSection.Span> spans = new ArrayList<>();
        for (PlanTube tube : tubes) {
            if (tube.curveP0() == null) continue;
            Vec3 origin = Vec3.atLowerCornerOf(tube.pos());
            spans.add(new TubeSection.Span(new CubicBezier(origin.add(tube.curveP0()), origin.add(tube.curveP1()),
                    origin.add(tube.curveP2()), origin.add(tube.curveP3())), 0xFFFFFF, false));
        }
        return spans.isEmpty() ? null : new TubeSection(UUID.randomUUID(), spans, spans.size());
    }
    public static Set<BlockPos> cells(TubeSection section) {
        Set<BlockPos> result = new HashSet<>();
        for (AABB raw : section.geometry().boxes()) {
            AABB box = raw.deflate(1.0E-5);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) result.add(pos.immutable());
        }
        return result;
    }
    public static boolean canPlace(Level level, TubeSection section, Player player) {
        for (BlockPos pos : cells(section)) {
            if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || player != null && (!player.getAbilities().mayBuild || !level.mayInteract(player, pos))) return false;
            var state = level.getBlockState(pos);
            if (state.canBeReplaced()) continue;
            var shape = state.getCollisionShape(level, pos, CollisionContext.empty()).move(pos.getX(), pos.getY(), pos.getZ());
            for (AABB block : shape.toAabbs()) {
                for (AABB tube : section.geometry().boxes()) if (block.intersects(tube.deflate(1.0E-5))) {
                    if (!isJunctionJoin(level, pos, section, tube)) return false;
                }
            }
        }
        for (TubeSection other : TubeSections.get(level).intersecting(section.geometry().bounds())) {
            for (AABB a : section.geometry().boxes()) for (AABB b : other.geometry().boxes()) {
                if (!a.deflate(0.002).intersects(b.deflate(0.002))) continue;
                // Collars of two joining sections share a small region at their seam.
                boolean seam = false;
                for (boolean first : new boolean[]{true, false}) for (boolean second : new boolean[]{true, false}) {
                    if (section.port(first).connectsTo(other.port(second))
                            && a.getCenter().distanceTo(section.port(first).position()) < 0.55
                            && b.getCenter().distanceTo(section.port(first).position()) < 0.55) seam = true;
                }
                if (!seam) return false;
            }
        }
        return true;
    }
    private static boolean isJunctionJoin(Level level, BlockPos pos, TubeSection section, AABB tube) {
        if (!(level.getBlockEntity(pos) instanceof com.hwmods.overpressure.transport.TransportJunction)) return false;
        for (boolean first : new boolean[]{true, false}) {
            var port = section.port(first);
            if (tube.getCenter().distanceTo(port.position()) > 0.7) continue;
            for (var face : net.minecraft.core.Direction.values()) {
                if (SectionTransport.portPoint(level, pos, face).distanceToSqr(port.position()) < 1.0E-8
                        && SectionTransport.portVector(level, pos, face).dot(port.outward()) < -0.99) return true;
            }
        }
        return false;
    }
}
