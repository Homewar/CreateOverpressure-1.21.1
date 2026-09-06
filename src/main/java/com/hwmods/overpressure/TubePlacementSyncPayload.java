package com.hwmods.overpressure;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server acknowledgement of the selected tube anchor, including failed builds. */
public record TubePlacementSyncPayload(
        ResourceLocation dimension,
        @Nullable PneumaticTubeBlockItem.CurveStart start
) implements CustomPacketPayload {
    public static final Type<TubePlacementSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "tube_placement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TubePlacementSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeResourceLocation(payload.dimension());
                buffer.writeBoolean(payload.start() != null);
                if (payload.start() != null) {
                    buffer.writeBlockPos(payload.start().pos());
                    buffer.writeEnum(payload.start().direction());
                    buffer.writeBoolean(payload.start().deviderSide() != null);
                    if (payload.start().deviderSide() != null) {
                        buffer.writeEnum(payload.start().deviderSide());
                    }
                }
            },
            buffer -> {
                ResourceLocation dimension = buffer.readResourceLocation();
                PneumaticTubeBlockItem.CurveStart start = null;
                if (buffer.readBoolean()) {
                    var pos = buffer.readBlockPos();
                    Direction direction = buffer.readEnum(Direction.class);
                    Direction branch = buffer.readBoolean() ? buffer.readEnum(Direction.class) : null;
                    start = new PneumaticTubeBlockItem.CurveStart(pos, direction, branch);
                }
                return new TubePlacementSyncPayload(dimension, start);
            });

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player().level().dimension().location().equals(payload.dimension())) {
                        PneumaticTubeBlockItem.applyClientSelection(context.player(), payload.start());
                    }
                }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
