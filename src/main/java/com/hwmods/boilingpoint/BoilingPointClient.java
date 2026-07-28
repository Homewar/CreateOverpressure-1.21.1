package com.hwmods.boilingpoint;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BoilingPoint.MODID, dist = Dist.CLIENT)
public class BoilingPointClient {
    public BoilingPointClient(IEventBus modBus, ModContainer container) {
        ItemPumpRenderer.init();
        modBus.addListener(BoilingPointClient::onClientSetup);
        modBus.addListener(BoilingPointClient::registerRenderers);
        modBus.addListener(BoilingPointClient::modifyBakedModels);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        // Register our Ponder scenes before Ponder compiles them at mod load complete.
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new BoilingPointPonderPlugin());

        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.PNEUMATIC_CONNECTION.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ITEM_PUMP.get(), RenderType.cutout());
        });
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
    }

    static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> isPneumaticTubeModel(location)
                ? new PneumaticTubeBracketedModel(model)
                : model);
    }

    private static boolean isPneumaticTubeModel(ModelResourceLocation location) {
        return location.id().getNamespace().equals(BoilingPoint.MODID)
                && location.id().getPath().equals("pneumatic_tube");
    }
}
