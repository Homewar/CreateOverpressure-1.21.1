package com.hwmods.overpressure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class GrindRailSoundHandler {
    private static final double SPEED_CEILING = 2.0;
    private static final float SLOW_PITCH_START = 0.25F;
    private static final float NORMAL_PITCH = 1.0F;
    private static final float HIGH_PITCH_MAX = 1.25F;
    private static final float SLOW_VOLUME_START = 0.375F;
    private static final float SLOW_VOLUME_MAX = 0.75F;
    private static final float FAST_VOLUME_MAX = 0.525F;
    private static final float COLLIDE_VOLUME = 0.9F;
    private static final double SPEED_SMOOTH_RATE = 0.08;
    private static final float SLOW_PITCH_EASE_RATE = 0.10F;
    private static final float FAST_VOLUME_EASE_RATE = 0.10F;

    private static final Map<UUID, PlayerSoundState> STATES = new HashMap<>();

    private static double maxSpeed() {
        return GrindRailRidingHandler.maxSpeed();
    }

    private static double slowRampEnd() {
        return maxSpeed() * 0.50;
    }

    private static double fastRampStart() {
        return maxSpeed() * 0.50;
    }

    private static double fastRampEnd() {
        return maxSpeed() * 0.75;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearAll();
            return;
        }

        Set<UUID> ridersInLevel = new HashSet<>();
        for (Player player : minecraft.level.players()) {
            if (!GrindRailRidingHandler.isClientRiding(player)) {
                continue;
            }

            UUID playerId = player.getUUID();
            ridersInLevel.add(playerId);

            PlayerSoundState state = STATES.get(playerId);
            if (state == null) {
                state = new PlayerSoundState();
                STATES.put(playerId, state);
                playCollide(player);
            }
            state.tick(player);
        }

        STATES.entrySet().removeIf(entry -> {
            if (ridersInLevel.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().stopAllLoops();
            return true;
        });
    }

    private static void playCollide(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.level.playLocalSound(
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSoundEvents.GRIND_COLLIDE.get(),
                SoundSource.PLAYERS,
                COLLIDE_VOLUME * userVolume(),
                NORMAL_PITCH,
                false
        );
    }

    private static void clearAll() {
        STATES.values().forEach(PlayerSoundState::stopAllLoops);
        STATES.clear();
    }

    private static float userVolume() {
        return Config.SOUND_VOLUME.get().floatValue();
    }

    private static float computeSlowPitch(double speed) {
        double lowRampEnd = slowRampEnd();
        if (speed < lowRampEnd) {
            float progress = (float) (speed / lowRampEnd);
            return Mth.lerp(progress, SLOW_PITCH_START, NORMAL_PITCH);
        }

        double cruise = maxSpeed();
        if (speed <= cruise) {
            return NORMAL_PITCH;
        }
        if (speed >= SPEED_CEILING) {
            return HIGH_PITCH_MAX;
        }

        float progress = (float) ((speed - cruise) / (SPEED_CEILING - cruise));
        return Mth.lerp(progress, NORMAL_PITCH, HIGH_PITCH_MAX);
    }

    private static float computeSlowVolume(double speed) {
        double rampEnd = slowRampEnd();
        if (speed >= rampEnd) {
            return SLOW_VOLUME_MAX;
        }
        float progress = (float) Mth.clamp(speed / rampEnd, 0.0, 1.0);
        return Mth.lerp(progress, SLOW_VOLUME_START, SLOW_VOLUME_MAX);
    }

    private static float computeFastVolume(double speed) {
        double rampStart = fastRampStart();
        double rampEnd = fastRampEnd();
        if (speed >= rampEnd) {
            return FAST_VOLUME_MAX;
        }
        if (speed <= rampStart) {
            return 0.0F;
        }
        float progress = (float) ((speed - rampStart) / (rampEnd - rampStart));
        return progress * FAST_VOLUME_MAX;
    }

    private static final class PlayerSoundState {
        private GrindLoop slowLoop;
        private GrindLoop fastLoop;
        private double smoothedSpeed;
        private float smoothedSlowPitch = SLOW_PITCH_START;
        private float smoothedFastVolume;
        private boolean needsSeeding = true;

        private void tick(Player player) {
            double rawSpeed = GrindRailRidingHandler.clientSpeed(player);

            if (needsSeeding && rawSpeed > 0.01) {
                smoothedSpeed = rawSpeed;
                smoothedSlowPitch = computeSlowPitch(rawSpeed);
                smoothedFastVolume = computeFastVolume(rawSpeed);
                needsSeeding = false;
            } else {
                double delta = rawSpeed - smoothedSpeed;
                if (Math.abs(delta) > SPEED_SMOOTH_RATE) {
                    smoothedSpeed += Math.signum(delta) * SPEED_SMOOTH_RATE;
                } else {
                    smoothedSpeed = rawSpeed;
                }

                smoothedSlowPitch = Mth.lerp(
                        SLOW_PITCH_EASE_RATE,
                        smoothedSlowPitch,
                        computeSlowPitch(smoothedSpeed)
                );
                smoothedFastVolume = Mth.lerp(
                        FAST_VOLUME_EASE_RATE,
                        smoothedFastVolume,
                        computeFastVolume(smoothedSpeed)
                );
            }

            if (slowLoop == null || slowLoop.isStopped()) {
                slowLoop = new GrindLoop(player, ModSoundEvents.GRIND_SLOW_LOOP.get());
                Minecraft.getInstance().getSoundManager().play(slowLoop);
            }
            if (fastLoop == null || fastLoop.isStopped()) {
                fastLoop = new GrindLoop(player, ModSoundEvents.GRIND_FAST_LOOP.get());
                fastLoop.setPitch(NORMAL_PITCH);
                Minecraft.getInstance().getSoundManager().play(fastLoop);
            }

            slowLoop.setPitch(smoothedSlowPitch);
            slowLoop.setVolume(computeSlowVolume(smoothedSpeed) * userVolume());
            fastLoop.setVolume(smoothedFastVolume * userVolume());
        }

        private void stopAllLoops() {
            if (slowLoop != null) {
                slowLoop.cancel();
                slowLoop = null;
            }
            if (fastLoop != null) {
                fastLoop.cancel();
                fastLoop = null;
            }
            smoothedSpeed = 0.0;
            smoothedSlowPitch = SLOW_PITCH_START;
            smoothedFastVolume = 0.0F;
            needsSeeding = true;
        }
    }

    private static final class GrindLoop extends AbstractTickableSoundInstance {
        private final Player player;

        private GrindLoop(Player player, SoundEvent soundEvent) {
            super(soundEvent, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F;
            this.pitch = SLOW_PITCH_START;
            updatePosition();
        }

        @Override
        public boolean canPlaySound() {
            return !isStopped()
                    && player.isAlive()
                    && GrindRailRidingHandler.isClientRiding(player);
        }

        @Override
        public void tick() {
            if (!player.isAlive() || !GrindRailRidingHandler.isClientRiding(player)) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            x = player.getX();
            y = player.getY() + player.getBbHeight() * 0.5;
            z = player.getZ();
        }

        private void cancel() {
            stop();
        }

        private void setPitch(float pitch) {
            this.pitch = pitch;
        }

        private void setVolume(float volume) {
            this.volume = volume;
        }
    }

    private GrindRailSoundHandler() {
    }
}
