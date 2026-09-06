package com.hwmods.overpressure;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only state for the "thinking mode" (planning preview).
 * When active, holding the pneumatic tube item shows a translucent ghost of the
 * tube section that would be built, so the player can plan routes before placing.
 */
@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public class PlanningMode {
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.overpressure.planning_mode",
            GLFW.GLFW_KEY_G,
            "key.categories.overpressure"
    );

    private static boolean active = false;
    private static double scrollRemainder;
    private static PneumaticTubeBlockItem.CurveStart scrollStart;

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.screen != null || event.getScrollDeltaY() == 0.0) {
            return;
        }
        var stack = player.getMainHandItem().getItem() instanceof PneumaticTubeBlockItem
                ? player.getMainHandItem() : player.getOffhandItem();
        if (!(stack.getItem() instanceof PneumaticTubeBlockItem item)) {
            scrollRemainder = 0.0;
            scrollStart = null;
            return;
        }
        var start = item.getSelectedStart(player.level(), player);
        if (start == null) {
            scrollRemainder = 0.0;
            scrollStart = null;
            return;
        }
        // Consume even fractional scrolling to keep the selected hotbar slot.
        event.setCanceled(true);
        if (!start.equals(scrollStart)) {
            scrollRemainder = 0.0;
            scrollStart = start;
        }
        scrollRemainder += event.getScrollDeltaY();
        int steps = (int) scrollRemainder;
        if (steps == 0) {
            return;
        }
        scrollRemainder -= steps;
        int previousReach = item.getPlacementReach(player, start);
        item.setPlacementReach(player, previousReach + steps);
        int reach = item.getPlacementReach(player, start);
        PlanningModeRenderer.invalidatePreview();
        PacketDistributor.sendToServer(new TubePlacementReachPayload(player.level().dimension().location(), start.pos(), reach));
    }

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
