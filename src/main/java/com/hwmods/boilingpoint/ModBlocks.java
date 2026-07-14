package com.hwmods.boilingpoint;

import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    // Создаём Deferred Register для хранения предметов, которые будут зарегистрированы в пространстве имён "boilingpoint"
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BoilingPoint.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BoilingPoint.MODID);
    // Создаём Deferred Register для хранения CreativeModeTabs, которые будут зарегистрированы в пространстве имён "boilingpoint"
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BoilingPoint.MODID);
    // Создаёт новый блок с id "boilingpoint:pneumatic_tube", объединяя пространство имён и путь
    public static final DeferredBlock<PneumaticTubeBlock> PNEUMATIC_TUBE = BLOCKS.register("pneumatic_tube", () -> new PneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion()));
    public static final DeferredBlock<CurvaturePneumaticTubeBlock> CURVATURE_PNEUMATIC_TUBE = BLOCKS.register("curvature_pneumatic_tube", () -> new CurvaturePneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion()));
    public static final DeferredBlock<PneumaticConnectionBlock> PNEUMATIC_CONNECTION = BLOCKS.register("pneumatic_connection", () -> new PneumaticConnectionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion()));
    public static final DeferredBlock<ItemPumpBlock> ITEM_PUMP = BLOCKS.register("item_pump", () -> new ItemPumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<Item> PNEUMATIC_TUBE_ITEM = ITEMS.register("pneumatic_tube", () -> new PneumaticTubeBlockItem(PNEUMATIC_TUBE.get(), new Item.Properties()));
    public static final DeferredItem<Item> PNEUMATIC_CONNECTION_ITEM = ITEMS.register("pneumatic_connection", () -> new BlockItem(PNEUMATIC_CONNECTION.get(), new Item.Properties()));
    public static final DeferredItem<Item> ITEM_PUMP_ITEM = ITEMS.register("item_pump", () -> new BlockItem(ITEM_PUMP.get(), new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BOILING_POINT_TAB = CREATIVE_MODE_TABS.register(
            "boiling_point",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.boilingpoint.boiling_point"))
                    .icon(() -> new ItemStack(PNEUMATIC_TUBE_ITEM.get()))
                    .withTabsAfter(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
                    .displayItems((parameters, output) -> {
                        output.accept(PNEUMATIC_TUBE_ITEM.get());
                        output.accept(PNEUMATIC_CONNECTION_ITEM.get());
                        output.accept(ITEM_PUMP_ITEM.get());
                    })
                    .build()
    );
    
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
    }

    private ModBlocks() {
    }
}
