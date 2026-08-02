package com.hwmods.overpressure;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

final class LegacyRegistryAliases {
    private static final String LEGACY_MOD_ID = "boilingpoint";

    private static final List<String> BLOCKS = List.of(
            "pneumatic_tube",
            "andesite_encased_pneumatic_tube",
            "brass_encased_pneumatic_tube",
            "copper_encased_pneumatic_tube",
            "curvature_pneumatic_tube",
            "pneumatic_connection",
            "item_pump",
            "creative_item_pump"
    );

    private static final List<String> ITEMS = List.of(
            "pneumatic_tube",
            "pneumatic_connection",
            "item_pump",
            "creative_item_pump"
    );

    private static final List<String> BLOCK_ENTITY_TYPES = List.of(
            "pneumatic_tube",
            "pneumatic_connection",
            "curvature_pneumatic_tube",
            "item_pump"
    );

    static void register() {
        addSamePathAliases(ModBlocks.BLOCKS, BLOCKS);
        addSamePathAliases(ModBlocks.ITEMS, ITEMS);
        addSamePathAliases(ModBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITY_TYPES);
        ModBlocks.CREATIVE_MODE_TABS.addAlias(
                id(LEGACY_MOD_ID, "boiling_point"),
                id(Overpressure.MODID, "overpressure")
        );
    }

    private static void addSamePathAliases(DeferredRegister<?> registry, List<String> paths) {
        for (String path : paths) {
            registry.addAlias(id(LEGACY_MOD_ID, path), id(Overpressure.MODID, path));
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private LegacyRegistryAliases() {
    }
}
