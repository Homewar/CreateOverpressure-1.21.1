package com.hwmods.overpressure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Overpressure.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GRIND_COLLIDE = register("grind_collide");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRIND_FAST_LOOP = register("grind_fast_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRIND_SLOW_LOOP = register("grind_slow_loop");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, name)
        ));
    }

    private ModSoundEvents() {
    }
}
