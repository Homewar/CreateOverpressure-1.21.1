package com.hwmods.overpressure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Overpressure.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<OxygenPillarEntity>> OXYGEN_PILLAR = ENTITY_TYPES.register("oxygen_pillar", () ->
            EntityType.Builder.of(OxygenPillarEntity::new, MobCategory.MISC).sized(.12F, 1.0F).clientTrackingRange(64).updateInterval(20).build("oxygen_pillar"));
    private ModEntities() { }
}
