package com.hwmods.overpressure;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record MultitoolActionPayload(InteractionHand hand, Action action) implements CustomPacketPayload {
    public enum Action {
        TOGGLE_DRILL,
        TOGGLE_SAW,
        CYCLE
    }

    public static final Type<MultitoolActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "multitool_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultitoolActionPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeEnum(payload.hand);
                buffer.writeEnum(payload.action);
            },
            buffer -> new MultitoolActionPayload(
                    buffer.readEnum(InteractionHand.class),
                    buffer.readEnum(Action.class)));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            switch (payload.action) {
                case CYCLE -> PneumaticMultitoolItem.cycle(player, payload.hand);
                case TOGGLE_DRILL -> PneumaticMultitoolItem.toggle(
                        player, payload.hand, PneumaticMultitoolItem.Attachment.DRILL);
                case TOGGLE_SAW -> PneumaticMultitoolItem.toggle(
                        player, payload.hand, PneumaticMultitoolItem.Attachment.SAW);
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
