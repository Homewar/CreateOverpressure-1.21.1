package com.hwmods.overpressure;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    // Создаём Deferred Register для хранения сущностей блоков, которые будут зарегистрированы в пространстве имён "overpressure"
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Overpressure.MODID);

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PneumaticTubeBlockEntity>> PNEUMATIC_TUBE =
            BLOCK_ENTITIES.register(
                    "pneumatic_tube",
                    () -> new BlockEntityType<>(
                            PneumaticTubeBlockEntity::new,
                            Set.of(
                                    ModBlocks.PNEUMATIC_TUBE.get(),
                                    ModBlocks.ANDESITE_ENCASED_PNEUMATIC_TUBE.get(),
                                    ModBlocks.BRASS_ENCASED_PNEUMATIC_TUBE.get(),
                                    ModBlocks.COPPER_ENCASED_PNEUMATIC_TUBE.get()
                            ),
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
                            Set.of(ModBlocks.ITEM_PUMP.get(), ModBlocks.CREATIVE_ITEM_PUMP.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeviderBlockEntity>> DEVIDER =
            BLOCK_ENTITIES.register(
                    "devider",
                    () -> new BlockEntityType<>(
                            DeviderBlockEntity::new,
                            Set.of(ModBlocks.DEVIDER.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ValveBlockEntity>> VALVE =
            BLOCK_ENTITIES.register(
                    "valve",
                    () -> new BlockEntityType<>(
                            ValveBlockEntity::new,
                            Set.of(ModBlocks.VALVE.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClogSensorBlockEntity>> CLOG_SENSOR =
            BLOCK_ENTITIES.register(
                    "clog_sensor",
                    () -> new BlockEntityType<>(
                            ClogSensorBlockEntity::new,
                            Set.of(ModBlocks.CLOG_SENSOR.get()),
                            null
                    )
            );

        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrindRailBlockEntity>> GRIND_RAIL =
            BLOCK_ENTITIES.register(
                    "grind_rail",
                    () -> new BlockEntityType<>(
                            GrindRailBlockEntity::new,
                            Set.of(ModBlocks.GRIND_RAIL.get()),
                            null
                    )
            );
            



    private ModBlockEntities() {
    }
}
