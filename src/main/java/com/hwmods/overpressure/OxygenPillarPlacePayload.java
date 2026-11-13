package com.hwmods.overpressure;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

public record OxygenPillarPlacePayload(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<OxygenPillarPlacePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "oxygen_pillar_place"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OxygenPillarPlacePayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeEnum(value.hand), buf -> new OxygenPillarPlacePayload(buf.readEnum(InteractionHand.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) place(player, payload.hand);
        }));
    }
    public static void place(ServerPlayer player, InteractionHand hand) {
        if (player == null) return;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof PneumaticMultitoolItem)) return;
        var hit = player.pick(8.0D, 0.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return;
        var pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!player.level().getBlockState(pos).canBeReplaced()) return;
        int slot = player.getInventory().findSlotMatchingItem(new ItemStack(ModBlocks.OXYGEN_PILLAR_ITEM.get()));
        if (slot < 0) return;
        OxygenPillarEntity pillar = new OxygenPillarEntity(ModEntities.OXYGEN_PILLAR.get(), player.level());
        pillar.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
        player.level().addFreshEntity(pillar);
        player.getInventory().getItem(slot).shrink(1);
    }
}
