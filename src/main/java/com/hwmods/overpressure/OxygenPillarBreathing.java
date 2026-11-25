package com.hwmods.overpressure;

import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.DivingHelmetItem;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class OxygenPillarBreathing {
    private OxygenPillarBreathing() {}

    // Supply air before Create's default-priority helmet handler spends tank air.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void supplyAir(LivingBreatheEvent event) {
        if (event.canBreathe() || !(event.getEntity() instanceof Player player) || !canUsePillarAir(player)) {
            return;
        }

        var supply = PneumaticAirNetwork.findPillarSupply(player.level(), player.position());
        if (supply != null && supply.connector().getItemSpeed() > 0.0F) {
            event.setCanBreathe(true);
            event.setRefillAirAmount(player.getMaxAirSupply());
        }
    }

    static boolean canUsePillarAir(Player player) {
        return player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()) && !player.isInLava()
                && DivingHelmetItem.isWornBy(player) && BacktankItem.getWornBy(player) != null;
    }
}
