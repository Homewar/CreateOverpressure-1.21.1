package com.hwmods.boilingpoint;

import java.util.Set;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
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

public class ModBlockEntities {
    // Создаём Deferred Register для хранения сущностей блоков, которые будут зарегистрированы в пространстве имён "boilingpoint"
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BoilingPoint.MODID);

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PneumaticTubeBlockEntity>> PNEUMATIC_TUBE =
            BLOCK_ENTITIES.register(
                    "pneumatic_tube",
                    () -> new BlockEntityType<>(
                            PneumaticTubeBlockEntity::new,
                            Set.of(ModBlocks.PNEUMATIC_TUBE.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PneumaticConnectionBlockEntity>> PNEUMATIC_CONNECTION =
            BLOCK_ENTITIES.register(
                    "pneumatic_connection",
                    () -> new BlockEntityType<>(
                            PneumaticConnectionBlockEntity::new,
                            Set.of(ModBlocks.PNEUMATIC_CONNECTION.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CurvaturePneumaticTubeEntity>> CURVATURE_PNEUMATIC_TUBE =
            BLOCK_ENTITIES.register(
                    "curvature_pneumatic_tube",
                    () -> new BlockEntityType<>(
                            CurvaturePneumaticTubeEntity::new,
                            Set.of(ModBlocks.CURVATURE_PNEUMATIC_TUBE.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPumpBlockEntity>> ITEM_PUMP =
            BLOCK_ENTITIES.register(
                    "item_pump",
                    () -> new BlockEntityType<>(
                            ItemPumpBlockEntity::new,
                            Set.of(ModBlocks.ITEM_PUMP.get()),
                            null
                    )
            );
            



    private ModBlockEntities() {
    }
}
