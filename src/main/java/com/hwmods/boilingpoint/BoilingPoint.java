package com.hwmods.boilingpoint;

import com.simibubi.create.api.stress.BlockStressValues;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(BoilingPoint.MODID)
public class BoilingPoint {
    public static final String MODID = "boilingpoint";
    private static final double ITEM_PUMP_STRESS_IMPACT = 1.0;

    public BoilingPoint(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(
                ModBlocks.ITEM_PUMP.get(),
                () -> ITEM_PUMP_STRESS_IMPACT
        ));
    }
}
