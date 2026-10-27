package com.hwmods.overpressure;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record MultitoolOpenPayload(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<MultitoolOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "multitool_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultitoolOpenPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeEnum(payload.hand),
            buffer -> new MultitoolOpenPayload(buffer.readEnum(InteractionHand.class)));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC,
                (payload, context) -> context.enqueueWork(() -> MultitoolClient.open(payload.hand)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
