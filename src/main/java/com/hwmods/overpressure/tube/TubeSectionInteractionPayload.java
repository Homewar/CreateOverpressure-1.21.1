package com.hwmods.overpressure.tube;

import java.util.UUID;
import com.hwmods.overpressure.Overpressure;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record TubeSectionInteractionPayload(UUID id, boolean attack, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<TubeSectionInteractionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "tube_section_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TubeSectionInteractionPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.id); b.writeBoolean(p.attack); b.writeEnum(p.hand); },
            b -> new TubeSectionInteractionPayload(b.readUUID(), b.readBoolean(), b.readEnum(InteractionHand.class)));
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (p, c) -> c.enqueueWork(() -> TubeSectionInteractions.interact(c.player(), p.id, p.attack, p.hand)));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
