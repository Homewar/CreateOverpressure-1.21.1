package com.hwmods.overpressure;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record MultitoolSpeedPayload(InteractionHand hand, int speed) implements CustomPacketPayload {
    public static final Type<MultitoolSpeedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "multitool_speed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultitoolSpeedPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeEnum(payload.hand);
                buffer.writeVarInt(payload.speed);
            },
            buffer -> new MultitoolSpeedPayload(
                    buffer.readEnum(InteractionHand.class),
                    buffer.readVarInt()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.hand);
            if (PneumaticMultitoolItem.from(stack) != null) {
                PneumaticMultitoolItem.setSpeedSetting(stack, payload.speed);
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
