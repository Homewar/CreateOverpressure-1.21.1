package com.hwmods.overpressure;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Overpressure.MODID, dist = Dist.CLIENT)
public class OverpressureClient {
    public static final ModelResourceLocation PNEUMATIC_CAPSULE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "entity/capsule/pneumo_capsule")
    );
    public static final ModelResourceLocation DEVIDER_BLOCK_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "block/devider/divider")
    );
    public static final ModelResourceLocation DEVIDER_MERGE_BLOCK_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "block/devider/divider_merge")
    );
    public static final ModelResourceLocation FILTER_PIPE_BLOCK_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "block/filter_pipe/filter_pipe")
    );
    public OverpressureClient(IEventBus modBus, ModContainer container) {
        ItemPumpRenderer.init();
        modBus.addListener(OverpressureClient::onClientSetup);
        modBus.addListener(OverpressureClient::registerRenderers);
        modBus.addListener(OverpressureClient::registerAdditionalModels);
        modBus.addListener(OverpressureClient::modifyBakedModels);
        modBus.addListener(OverpressureClient::registerParticleProviders);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        // Register our Ponder scenes before Ponder compiles them at mod load complete.
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new OverpressurePonderPlugin());

    }

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.PNEUMATIC_TUBE.get(),
                PneumaticTubeRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.CURVATURE_PNEUMATIC_TUBE.get(),
                CurvaturePneumaticTubeRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.PNEUMATIC_CONNECTION.get(),
                PneumaticConnectionRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.ITEM_PUMP.get(),
                ItemPumpRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.DEVIDER.get(),
                DeviderRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.FILTER_PIPE.get(),
                FilterPipeRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.VALVE.get(),
                PneumaticTubeRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.CLOG_SENSOR.get(),
                PneumaticTubeRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.CAPSULE_PORT.get(),
                CapsulePortRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.GRIND_RAIL.get(),
                GrindRailRenderer::new
        );
    }

    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(PNEUMATIC_CAPSULE_MODEL);
        event.register(DEVIDER_BLOCK_MODEL);
        event.register(DEVIDER_MERGE_BLOCK_MODEL);
        event.register(FILTER_PIPE_BLOCK_MODEL);
    }

    static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticleTypes.WHITE_SPLASH.get(), WhiteSplashParticle.Provider::new);
    }

    static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> {
            if (isPneumaticTubeModel(location)) {
                return new PneumaticTubeBracketedModel(model);
            }
            if (isCapsulePortModel(location)) {
                return new CapsulePortBakedModel(model);
            }
            return model;
        });
    }

    private static boolean isPneumaticTubeModel(ModelResourceLocation location) {
        return location.id().getNamespace().equals(Overpressure.MODID)
                && location.id().getPath().equals("pneumatic_tube");
    }

    private static boolean isCapsulePortModel(ModelResourceLocation location) {
        return location.id().getNamespace().equals(Overpressure.MODID)
                && location.id().getPath().equals("capsule_port");
    }
}
