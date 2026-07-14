package com.hwmods.boilingpoint;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(BoilingPoint.MODID)
public class BoilingPoint {
    public static final String MODID = "boilingpoint";

    public BoilingPoint(IEventBus modBus, ModContainer modContainer) {
        ModBlocks.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
