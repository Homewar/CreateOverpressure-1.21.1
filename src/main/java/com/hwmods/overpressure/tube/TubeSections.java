package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class TubeSections {
    private static final Map<Level, TubeSectionStorage> CLIENT = new WeakHashMap<>();
    public static TubeSectionStorage get(Level level) {
        return level instanceof ServerLevel server ? TubeSectionStorage.get(server)
                : CLIENT.computeIfAbsent(level, ignored -> new TubeSectionStorage());
    }
    public static void put(Level level, TubeSection section) {
        get(level).put(section);
        changed(level, section);
        if (level instanceof ServerLevel server) PacketDistributor.sendToPlayersInDimension(server,
                new TubeSectionPayload(server.dimension().location(), section.id(), section.save()));
    }
    public static TubeSection remove(Level level, UUID id) {
        if (level instanceof ServerLevel server) SectionTransport.get(server).ejectSection(server, id);
        TubeSection removed = get(level).remove(id);
        if (removed != null) {
            changed(level, removed);
            if (level instanceof ServerLevel server) PacketDistributor.sendToPlayersInDimension(server,
                    new TubeSectionPayload(server.dimension().location(), id, null));
        }
        return removed;
    }
    public static void changed(Level level, TubeSection section) {
        for (boolean start : new boolean[]{true, false}) {
            var port = section.port(start);
            BlockPos pos = BlockPos.containing(port.position().add(port.outward().scale(0.05)));
            if (!level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof PneumaticTubeBlock tube) {
                level.setBlock(pos, tube.getTubeStateForPlacement(level, pos), 3);
            }
            if (state.getBlock() instanceof PneumaticConnectionBlock connector) {
                level.setBlock(pos, connector.refreshSectionConnection(level, pos, state), 3);
            }
            level.updateNeighborsAt(pos, state.getBlock());
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
        }
    }
    public static List<TubeSection.Port> portsAt(Level level, Vec3 position, Vec3 outward) {
        List<TubeSection.Port> result = new ArrayList<>();
        for (TubeSection section : get(level).intersecting(new AABB(position, position).inflate(0.01))) {
            for (boolean start : new boolean[]{true, false}) {
                var port = section.port(start);
                if (position.distanceToSqr(port.position()) < 1.0E-8 && outward.dot(port.outward()) < -0.99) result.add(port);
            }
        }
        return result;
    }
    public static boolean connects(LevelAccessor level, BlockPos pos, Direction face) {
        if (!(level instanceof Level world)) return false;
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        return !portsAt(world, Vec3.atCenterOf(pos).add(normal.scale(0.5)), normal).isEmpty();
    }
    public static List<VoxelShape> collisions(Level level, AABB box) {
        List<VoxelShape> result = new ArrayList<>();
        for (TubeSection section : get(level).intersecting(box)) {
            for (AABB part : section.geometry().boxes()) if (part.intersects(box)) result.add(Shapes.create(part));
        }
        return result;
    }
    public static Hit clip(Level level, Vec3 from, Vec3 to) {
        Hit best = null;
        double distance = from.distanceToSqr(to);
        for (TubeSection section : get(level).intersecting(new AABB(from, to).inflate(0.001))) {
            var point = section.geometry().clip(from, to);
            if (point.isPresent() && from.distanceToSqr(point.get()) <= distance) {
                Vec3 p = point.get();
                Direction face = Direction.getNearest(from.x - p.x, from.y - p.y, from.z - p.z);
                // Use the actual box face, not the direction to the camera.
                for (AABB box : section.geometry().boxes()) {
                    var hit = AABB.clip(List.of(box), from, to, BlockPos.ZERO);
                    if (hit != null && hit.getLocation().distanceToSqr(p) < 1.0E-10) { face = hit.getDirection(); break; }
                }
                best = new Hit(section, p, face);
                distance = from.distanceToSqr(p);
            }
        }
        return best;
    }
    public record Hit(TubeSection section, Vec3 point, Direction face) {}
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { CLIENT.remove(event.getLevel()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { syncPlayer(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { syncPlayer(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { syncPlayer(event); }
    private static void syncPlayer(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (TubeSection section : get(player.level()).sections()) PacketDistributor.sendToPlayer(player,
                    new TubeSectionPayload(player.level().dimension().location(), section.id(), section.save()));
        }
    }
}
