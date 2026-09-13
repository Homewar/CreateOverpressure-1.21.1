package com.hwmods.overpressure.tube;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/** Dimension-owned sections. Chunk coordinates are only a spatial index, never owners. */
public final class TubeSectionStorage extends SavedData {
    private static final String FILE_NAME = "overpressure_tube_sections";
    private static final Factory<TubeSectionStorage> FACTORY = new Factory<>(TubeSectionStorage::new,
            TubeSectionStorage::load, null);
    private final Map<UUID, TubeSection> sections = new HashMap<>();
    private final Map<Long, Set<UUID>> chunks = new HashMap<>();

    public static TubeSectionStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    public Collection<TubeSection> sections() { return Collections.unmodifiableCollection(sections.values()); }
    public TubeSection get(UUID id) { return sections.get(id); }

    public void put(TubeSection section) {
        TubeSection previous = sections.put(section.id(), section);
        if (previous != null) unindex(previous);
        forChunks(section.geometry().bounds(), key -> chunks.computeIfAbsent(key, ignored -> new HashSet<>()).add(section.id()));
        setDirty();
    }

    public TubeSection remove(UUID id) {
        TubeSection removed = sections.remove(id);
        if (removed != null) {
            unindex(removed);
            setDirty();
        }
        return removed;
    }

    public Collection<TubeSection> intersecting(AABB bounds) {
        Set<UUID> ids = new HashSet<>();
        forChunks(bounds, key -> ids.addAll(chunks.getOrDefault(key, Set.of())));
        return ids.stream().map(sections::get).filter(section -> section.geometry().bounds().intersects(bounds)).toList();
    }

    private void unindex(TubeSection section) {
        forChunks(section.geometry().bounds(), key -> {
            Set<UUID> ids = chunks.get(key);
            if (ids != null) {
                ids.remove(section.id());
                if (ids.isEmpty()) chunks.remove(key);
            }
        });
    }

    private static void forChunks(AABB bounds, java.util.function.LongConsumer operation) {
        int minX = ((int) Math.floor(bounds.minX)) >> 4, maxX = ((int) Math.floor(bounds.maxX)) >> 4;
        int minZ = ((int) Math.floor(bounds.minZ)) >> 4, maxZ = ((int) Math.floor(bounds.maxZ)) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) operation.accept(ChunkPos.asLong(x, z));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", 1);
        ListTag entries = new ListTag();
        for (TubeSection section : sections.values()) entries.add(section.save());
        tag.put("Sections", entries);
        return tag;
    }

    public static TubeSectionStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        TubeSectionStorage storage = new TubeSectionStorage();
        ListTag entries = tag.getList("Sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) storage.put(TubeSection.load(entries.getCompound(i)));
        storage.setDirty(false);
        return storage;
    }
}
