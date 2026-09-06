package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Adjusts the active preview distance; world changes still require a placement click. */
public record TubePlacementReachPayload(ResourceLocation dimension, BlockPos anchor, int reach)
        implements CustomPacketPayload {
    public static final Type<TubePlacementReachPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "tube_placement_reach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TubePlacementReachPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeResourceLocation(payload.dimension());
                buffer.writeBlockPos(payload.anchor());
                buffer.writeVarInt(payload.reach());
            },
            buffer -> new TubePlacementReachPayload(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readVarInt()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (!player.level().dimension().location().equals(payload.dimension())) {
                return;
            }
            var stack = player.getMainHandItem().getItem() instanceof PneumaticTubeBlockItem
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (stack.getItem() instanceof PneumaticTubeBlockItem item) {
                var start = item.getSelectedStart(player.level(), player);
                if (start != null && start.pos().equals(payload.anchor())) {
                    item.setPlacementReach(player, payload.reach());
                }
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
