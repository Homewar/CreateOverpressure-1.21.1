package com.hwmods.overpressure;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Overpressure.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WHITE_SPLASH =
            PARTICLE_TYPES.register("white_splash", () -> new SimpleParticleType(false));

    private ModParticleTypes() {
    }
}
