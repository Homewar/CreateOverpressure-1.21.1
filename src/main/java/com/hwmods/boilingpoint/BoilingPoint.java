package com.hwmods.boilingpoint;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Значение здесь должно совпадать с записью в файле META-INF/neoforge.mods.toml
@Mod(BoilingPoint.MODID)
public class BoilingPoint {

    // Определяем id мода в одном общем месте, чтобы на него можно было ссылаться отовсюду
    public static final String MODID = "boilingpoint";
    // Прямо ссылаемся на slf4j-логгер
    public static final Logger LOGGER = LogUtils.getLogger();

    public BoilingPoint(IEventBus modBus, ModContainer modContainer) {
        // Регистрируем метод commonSetup для вызова на этапе FMLCommonSetupEvent
        modBus.addListener(this::commonSetup);
        // Регистрируем текущий класс для получения событий, которые он может обрабатывать
        NeoForge.EVENT_BUS.register(this);
        // Регистрируем наши блоки и предметы
        ModBlocks.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
    
    private void commonSetup(FMLCommonSetupEvent event) {
        // Некоторый общий код настройки
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Можно использовать SubscribeEvent и позволить Event Bus самому находить методы для вызова
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Выполнить что-то при запуске сервера
        LOGGER.info("HELLO from server starting");
    }
}
