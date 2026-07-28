package com.hwmods.boilingpoint;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only state for the "thinking mode" (planning preview).
 * When active, holding the pneumatic tube item shows a translucent ghost of the
 * tube section that would be built, so the player can plan routes before placing.
 */
@EventBusSubscriber(modid = BoilingPoint.MODID, value = Dist.CLIENT)
public class PlanningMode {
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.boilingpoint.planning_mode",
            GLFW.GLFW_KEY_G,
            "key.categories.boilingpoint"
    );

    private static boolean active = false;

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        while (TOGGLE.consumeClick()) {
            active = !active;
        }
    }
}
