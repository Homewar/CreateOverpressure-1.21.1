package com.hwmods.overpressure;

import com.mojang.logging.LogUtils;
import com.hwmods.overpressure.compat.create.PneumaticConnectionArmInteractionPoint;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(Overpressure.MODID)
public class Overpressure {
    public static final String MODID = "overpressure";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final double ITEM_PUMP_STRESS_IMPACT = 1.0;

    public Overpressure(IEventBus modBus, ModContainer modContainer) {
        LegacyRegistryAliases.register();
        ModBlocks.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModSoundEvents.SOUND_EVENTS.register(modBus);
        ModParticleTypes.PARTICLE_TYPES.register(modBus);
        modBus.addListener(PneumaticConnectionArmInteractionPoint::register);
        modBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BlockStressValues.IMPACTS.register(ModBlocks.ITEM_PUMP.get(), () -> ITEM_PUMP_STRESS_IMPACT);
            EncasingRegistry.addVariant(ModBlocks.PNEUMATIC_TUBE.get(), ModBlocks.ANDESITE_ENCASED_PNEUMATIC_TUBE.get());
            EncasingRegistry.addVariant(ModBlocks.PNEUMATIC_TUBE.get(), ModBlocks.BRASS_ENCASED_PNEUMATIC_TUBE.get());
            EncasingRegistry.addVariant(ModBlocks.PNEUMATIC_TUBE.get(), ModBlocks.COPPER_ENCASED_PNEUMATIC_TUBE.get());
            SimulatedCompat.register();
        });
    }
}
