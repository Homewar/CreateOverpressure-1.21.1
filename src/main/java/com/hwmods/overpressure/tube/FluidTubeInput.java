package com.hwmods.overpressure.tube;

import com.hwmods.overpressure.*;
import com.simibubi.create.AllBlocks;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class FluidTubeInput {
    @SubscribeEvent
    public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var planner = PneumaticTubeBlockItem.planner(event.getItemStack());
        if (planner != null && planner.isFluid()) event.getToolTip().add(net.minecraft.network.chat.Component
                .translatable("overpressure.fluid_tube.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void block(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(AllBlocks.FLUID_PIPE.asItem()) || event.getEntity().isShiftKeyDown()) return;
        var item = PneumaticTubeBlockItem.planner(event.getItemStack());
        if (item == null) return;
        if (item.getSelectedStart(event.getLevel(), event.getEntity()) == null
                && !FluidTubeBridge.canConnect(event.getLevel(), event.getPos(), event.getHitVec().getDirection())) return;
        event.setCancellationResult(item.useOn(new UseOnContext(event.getEntity(), event.getHand(), event.getHitVec())));
        event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void air(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(AllBlocks.FLUID_PIPE.asItem())) return;
        var item = PneumaticTubeBlockItem.planner(event.getItemStack());
        if (item == null) return;
        if (item.getSelectedStart(event.getLevel(), event.getEntity()) == null) return;
        event.setCancellationResult(item.use(event.getLevel(), event.getEntity(), event.getHand()).getResult());
        event.setCanceled(true);
    }
}
