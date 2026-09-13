package com.hwmods.overpressure.tube;

import com.hwmods.overpressure.Overpressure;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class TubeRebuildNotice {
    private static final String SHOWN = "overpressure:standalone_tubes_rebuild_notice";

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag persistent = player.getPersistentData();
        // PlayerPersisted survives death as well as saving and rejoining the world.
        CompoundTag saved = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (saved.getBoolean(SHOWN)) return;
        player.sendSystemMessage(Component.translatable("overpressure.notice.rebuild_curved_tubes")
                .withStyle(ChatFormatting.GOLD));
        saved.putBoolean(SHOWN, true);
        persistent.put(Player.PERSISTED_NBT_TAG, saved);
    }
}
