package com.hwmods.overpressure;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class MultitoolClient {
    public static final KeyMapping CYCLE = new KeyMapping(
            "key.overpressure.multitool_cycle",
            GLFW.GLFW_KEY_R,
            "key.categories.overpressure"
    );

    private MultitoolClient() {
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new MultitoolRadialScreen(hand));
    }

    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CYCLE);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (CYCLE.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            InteractionHand hand = PneumaticMultitoolItem.from(minecraft.player.getMainHandItem()) != null
                    ? InteractionHand.MAIN_HAND
                    : PneumaticMultitoolItem.from(minecraft.player.getOffhandItem()) != null
                    ? InteractionHand.OFF_HAND : null;
            if (hand != null) {
                PacketDistributor.sendToServer(new MultitoolActionPayload(hand, MultitoolActionPayload.Action.CYCLE));
            }
        }
    }
}
