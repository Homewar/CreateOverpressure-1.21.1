package com.hwmods.overpressure;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SplashParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/** Splash physics with a neutral sprite that can be tinted like a hot spark. */
public class WhiteSplashParticle extends SplashParticle {
    protected WhiteSplashParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed
        ) {
            WhiteSplashParticle particle = new WhiteSplashParticle(
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed
            );
            float colorRoll = level.random.nextFloat();
            if (colorRoll < 0.40F) {
                particle.setColor(1.0F, 0.78F, 0.03F);
            } else if (colorRoll < 0.70F) {
                particle.setColor(1.0F, 0.36F, 0.015F);
            } else if (colorRoll < 0.90F) {
                particle.setColor(1.0F, 0.08F, 0.015F);
            } else {
                particle.setColor(1.0F, 1.0F, 1.0F);
            }
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
