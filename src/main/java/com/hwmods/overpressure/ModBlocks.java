package com.hwmods.overpressure;

import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.AllBlocks;

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
    // Создаём Deferred Register для хранения предметов, которые будут зарегистрированы в пространстве имён "overpressure"
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Overpressure.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Overpressure.MODID);
    // Создаём Deferred Register для хранения CreativeModeTabs, которые будут зарегистрированы в пространстве имён "overpressure"
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Overpressure.MODID);
    // Создаёт новый блок с id "overpressure:pneumatic_tube", объединяя пространство имён и путь
    public static final DeferredBlock<PneumaticTubeBlock> PNEUMATIC_TUBE = BLOCKS.register("pneumatic_tube", () -> new PneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion()));
    public static final DeferredBlock<EncasedPneumaticTubeBlock> ANDESITE_ENCASED_PNEUMATIC_TUBE = BLOCKS.register("andesite_encased_pneumatic_tube", () -> new EncasedPneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.8f).sound(SoundType.STONE), () -> AllBlocks.ANDESITE_CASING.get()));
    public static final DeferredBlock<EncasedPneumaticTubeBlock> BRASS_ENCASED_PNEUMATIC_TUBE = BLOCKS.register("brass_encased_pneumatic_tube", () -> new EncasedPneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.8f).sound(SoundType.METAL), () -> AllBlocks.BRASS_CASING.get()));
    public static final DeferredBlock<EncasedPneumaticTubeBlock> COPPER_ENCASED_PNEUMATIC_TUBE = BLOCKS.register("copper_encased_pneumatic_tube", () -> new EncasedPneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.8f).sound(SoundType.COPPER), () -> AllBlocks.COPPER_CASING.get()));
    public static final DeferredBlock<CurvaturePneumaticTubeBlock> CURVATURE_PNEUMATIC_TUBE = BLOCKS.register("curvature_pneumatic_tube", () -> new CurvaturePneumaticTubeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion()));
    public static final DeferredBlock<PneumaticConnectionBlock> PNEUMATIC_CONNECTION = BLOCKS.register("pneumatic_connection", () -> new PneumaticConnectionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.3f).sound(SoundType.AMETHYST).noOcclusion()));
    public static final DeferredBlock<ItemPumpBlock> ITEM_PUMP = BLOCKS.register("item_pump", () -> new ItemPumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<ItemPumpBlock> CREATIVE_ITEM_PUMP = BLOCKS.register("creative_item_pump", () -> new ItemPumpBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<DeviderBlock> DEVIDER = BLOCKS.register("devider", () -> new DeviderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<FilterPipeBlock> FILTER_PIPE = BLOCKS.register("filter_pipe", () -> new FilterPipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<ValveBlock> VALVE = BLOCKS.register("valve", () -> new ValveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<ClogSensorBlock> CLOG_SENSOR = BLOCKS.register("clog_sensor", () -> new ClogSensorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<CapsulePortBlock> CAPSULE_PORT = BLOCKS.register("capsule_port", () -> new CapsulePortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<GrindRailBlock> GRIND_RAIL = BLOCKS.register("grind_rail", () -> new GrindRailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<GrindRailSupportBlock> GRIND_RAIL_SUPPORT = BLOCKS.register("grind_rail_support", () -> new GrindRailSupportBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<Item> PNEUMATIC_TUBE_ITEM = ITEMS.register("pneumatic_tube", () -> new PneumaticTubeBlockItem(PNEUMATIC_TUBE.get(), new Item.Properties()));
    public static final DeferredItem<Item> PNEUMATIC_CONNECTION_ITEM = ITEMS.register("pneumatic_connection", () -> new BlockItem(PNEUMATIC_CONNECTION.get(), new Item.Properties()));
    public static final DeferredItem<Item> ITEM_PUMP_ITEM = ITEMS.register("item_pump", () -> new BlockItem(ITEM_PUMP.get(), new Item.Properties()));
    public static final DeferredItem<Item> CREATIVE_ITEM_PUMP_ITEM = ITEMS.register("creative_item_pump", () -> new BlockItem(CREATIVE_ITEM_PUMP.get(), new Item.Properties()));
    public static final DeferredItem<Item> DEVIDER_ITEM = ITEMS.register("devider", () -> new BlockItem(DEVIDER.get(), new Item.Properties()));
    public static final DeferredItem<Item> FILTER_PIPE_ITEM = ITEMS.register("filter_pipe", () -> new BlockItem(FILTER_PIPE.get(), new Item.Properties()));
    public static final DeferredItem<Item> VALVE_ITEM = ITEMS.register("valve", () -> new BlockItem(VALVE.get(), new Item.Properties()));
    public static final DeferredItem<Item> CLOG_SENSOR_ITEM = ITEMS.register("clog_sensor", () -> new BlockItem(CLOG_SENSOR.get(), new Item.Properties()));
    public static final DeferredItem<Item> CAPSULE_PORT_ITEM = ITEMS.register("capsule_port", () -> new BlockItem(CAPSULE_PORT.get(), new Item.Properties()));
    public static final DeferredItem<Item> GRIND_RAIL_ITEM = ITEMS.register("grind_rail", () -> new GrindRailBlockItem(GRIND_RAIL.get(), new Item.Properties()));
    public static final DeferredItem<Item> GRIND_RAIL_SUPPORT_ITEM = ITEMS.register("grind_rail_support", () -> new BlockItem(GRIND_RAIL_SUPPORT.get(), new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OVERPRESSURE_TAB = CREATIVE_MODE_TABS.register(
            "overpressure",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.overpressure.overpressure"))
                    .icon(() -> new ItemStack(ITEM_PUMP_ITEM.get()))
                    .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey())
                    .displayItems((parameters, output) -> {
                        output.accept(PNEUMATIC_TUBE_ITEM.get());
                        output.accept(PNEUMATIC_CONNECTION_ITEM.get());
                        output.accept(ITEM_PUMP_ITEM.get());
                        output.accept(CREATIVE_ITEM_PUMP_ITEM.get());
                        output.accept(DEVIDER_ITEM.get());
                        output.accept(FILTER_PIPE_ITEM.get());
                        output.accept(VALVE_ITEM.get());
                        output.accept(CLOG_SENSOR_ITEM.get());
                        output.accept(CAPSULE_PORT_ITEM.get());
                        output.accept(GRIND_RAIL_ITEM.get());
                        output.accept(GRIND_RAIL_SUPPORT_ITEM.get());
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
