package com.hwmods.overpressure.transport;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Graph identity is independent of world cells for standalone tube sections. */
public sealed interface TransportLocation permits TransportLocation.Block, TransportLocation.Section {
    CompoundTag save();

    static TransportLocation load(CompoundTag tag) {
        return switch (tag.getString("Kind")) {
            case "Block" -> new Block(BlockPos.of(tag.getLong("Position")));
            case "Section" -> new Section(tag.getUUID("Section"), tag.getBoolean("Start"));
            default -> throw new IllegalArgumentException("Unknown transport location");
        };
    }

    record Block(BlockPos pos) implements TransportLocation {
        public Block { pos = pos.immutable(); }
        @Override public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Kind", "Block");
            tag.putLong("Position", pos.asLong());
            return tag;
        }
    }

    /** The boolean identifies the entrance; traversal always exits through the other end. */
    record Section(UUID id, boolean start) implements TransportLocation {
        @Override public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Kind", "Section");
            tag.putUUID("Section", id);
            tag.putBoolean("Start", start);
            return tag;
        }
    }
}
