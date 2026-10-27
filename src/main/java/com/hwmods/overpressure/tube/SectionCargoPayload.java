package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.Overpressure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SectionCargoPayload(ResourceLocation dimension, CompoundTag data) implements CustomPacketPayload {
    public static final Type<SectionCargoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "section_cargo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SectionCargoPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeResourceLocation(p.dimension); b.writeNbt(p.data); },
            b -> new SectionCargoPayload(b.readResourceLocation(), b.readNbt()));
    private static final Map<Level, Map<UUID, Visual>> CLIENT = new WeakHashMap<>();
    public record Visual(ItemStack stack, CargoAnimation animation) {}
    public static Collection<Visual> visuals(Level level) { return CLIENT.getOrDefault(level, Map.of()).values(); }
    public static void send(ServerLevel level, Collection<SectionTransport.Cargo> cargo) {
        if (level.players().isEmpty()) return;
        CompoundTag tag = new CompoundTag(); tag.putLong("Tick", level.getGameTime()); ListTag entries = new ListTag();
        for (var c : cargo) entries.add(snapshot(level, c, c.stack,
                SectionTransport.position(level, c.route.get(c.index), c.distance)));
        tag.put("Cargo", entries);
        PacketDistributor.sendToPlayersInDimension(level, new SectionCargoPayload(level.dimension().location(), tag));
    }
    public static void start(ServerLevel level, SectionTransport.Cargo cargo) {
        if (level.players().isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        // The renderer intentionally runs a few ticks behind for smooth packet
        // interpolation. Backdate the zero-distance sample by the same amount so
        // a newly dispatched capsule starts moving immediately at the route entry.
        tag.putLong("Tick", level.getGameTime() - CargoAnimation.RENDER_DELAY_TICKS);
        tag.putBoolean("Partial", true);
        ListTag entries = new ListTag();
        entries.add(snapshot(level, cargo, cargo.stack,
                SectionTransport.position(level, cargo.route.get(cargo.index), cargo.distance)));
        tag.put("Cargo", entries);
        PacketDistributor.sendToPlayersInDimension(level,
                new SectionCargoPayload(level.dimension().location(), tag));
    }
    private static CompoundTag snapshot(ServerLevel level, SectionTransport.Cargo c, ItemStack stack, Vec3 position) {
        CompoundTag entry = new CompoundTag();
        SectionTransport.Hop hop = c.route.get(c.index);
        double pathLength = SectionTransport.length(level, hop);
        entry.putDouble("PathDistance", c.distance);
        entry.putDouble("PathLength", pathLength);
        SectionTransport.point(entry, "PathEntry", hop.entry());
        SectionTransport.point(entry, "PathExit", hop.exit());
        if (hop.location() instanceof com.hwmods.overpressure.transport.TransportLocation.Section ref) {
            var section = TubeSections.get(level).get(ref.id());
            if (section != null) {
                entry.putUUID("Section", ref.id());
                entry.putBoolean("SectionStart", ref.start());
                entry.putDouble("Distance", ref.start() ? c.distance : section.geometry().length() - c.distance);
            }
        } else if (hop.location() instanceof com.hwmods.overpressure.transport.TransportLocation.Block block) {
            entry.putLong("Block", block.pos().asLong());
        }
        entry.putUUID("Id", c.id); entry.put("Stack", stack.save(level.registryAccess()));
        SectionTransport.point(entry, "Position", position);
        SectionTransport.point(entry, "Direction", SectionTransport.position(level, c.route.get(c.index), c.distance + 0.01)
                .subtract(SectionTransport.position(level, c.route.get(c.index), Math.max(0, c.distance - 0.01))).normalize());
        return entry;
    }
    public static void finish(ServerLevel level, SectionTransport.Cargo cargo, ItemStack stack, Vec3 position) {
        if (level.players().isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        tag.putLong("Tick", level.getGameTime()); tag.putBoolean("Partial", true);
        CompoundTag entry = snapshot(level, cargo, stack, position);
        entry.putBoolean("Finished", true);
        ListTag entries = new ListTag(); entries.add(entry); tag.put("Cargo", entries);
        PacketDistributor.sendToPlayersInDimension(level, new SectionCargoPayload(level.dimension().location(), tag));
    }

    public static Map<UUID, Visual> updateVisuals(Level level, Map<UUID, Visual> old, CompoundTag data) {
        boolean partial = data.getBoolean("Partial");
        Map<UUID, Visual> next = partial ? new HashMap<>(old) : new HashMap<>();
        long tick = data.getLong("Tick");
        ListTag entries = data.getList("Cargo", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i); UUID id = entry.getUUID("Id");
            Vec3 point = SectionTransport.point(entry, "Position");
            CargoAnimation.Path path = null;
            if (entry.hasUUID("Section")) {
                path = new CargoAnimation.Path(entry.getUUID("Section"), entry.getBoolean("SectionStart"), null,
                        SectionTransport.point(entry, "PathEntry"), SectionTransport.point(entry, "PathExit"),
                        entry.getDouble("PathLength"));
            } else if (entry.contains("Block", Tag.TAG_LONG)) {
                path = new CargoAnimation.Path(null, false, BlockPos.of(entry.getLong("Block")),
                        SectionTransport.point(entry, "PathEntry"), SectionTransport.point(entry, "PathExit"),
                        entry.getDouble("PathLength"));
            }
            CargoAnimation animation = old.containsKey(id) ? old.get(id).animation() : new CargoAnimation();
            animation.add(new CargoAnimation.Sample(tick, point, SectionTransport.point(entry, "Direction"),
                    entry.hasUUID("Section") ? entry.getUUID("Section") : null, entry.getDouble("Distance"),
                    path, entry.getDouble("PathDistance")));
            if (entry.getBoolean("Finished")) animation.removed(tick);
            next.put(id, new Visual(ItemStack.parseOptional(level.registryAccess(), entry.getCompound("Stack")), animation));
        }
        if (!partial) {
            // Keep the final segment until the delayed render clock reaches completion.
            old.forEach((id, visual) -> {
                if (!next.containsKey(id)) {
                    visual.animation().removed(tick);
                    if (!visual.animation().expired(tick - CargoAnimation.RENDER_DELAY_TICKS - 2)) next.put(id, visual);
                }
            });
        }
        return next;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> c.enqueueWork(() -> {
            Level level = c.player().level(); if (!level.dimension().location().equals(p.dimension)) return;
            CLIENT.put(level, updateVisuals(level, CLIENT.getOrDefault(level, Map.of()), p.data));
        }));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
