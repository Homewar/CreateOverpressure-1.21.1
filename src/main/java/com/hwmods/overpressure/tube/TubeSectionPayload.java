package com.hwmods.overpressure.tube;

import java.util.UUID;
import com.hwmods.overpressure.Overpressure;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record TubeSectionPayload(ResourceLocation dimension, UUID id, CompoundTag data) implements CustomPacketPayload {
    public static final Type<TubeSectionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "tube_section"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TubeSectionPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeResourceLocation(p.dimension); b.writeUUID(p.id); b.writeNbt(p.data); },
            b -> new TubeSectionPayload(b.readResourceLocation(), b.readUUID(), b.readNbt()));
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (p, c) -> c.enqueueWork(() -> {
            var level = c.player().level();
            if (!level.dimension().location().equals(p.dimension)) return;
            if (p.data == null) TubeSections.remove(level, p.id);
            else TubeSections.put(level, TubeSection.load(p.data));
        }));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
