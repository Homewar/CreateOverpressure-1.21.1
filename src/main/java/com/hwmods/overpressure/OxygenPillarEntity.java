package com.hwmods.overpressure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A visual, non-colliding relay for the multitool pneumatic network. */
public class OxygenPillarEntity extends Entity {
    public OxygenPillarEntity(EntityType<? extends OxygenPillarEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!level().isClientSide) {
            spawnAtLocation(ModBlocks.OXYGEN_PILLAR_ITEM.get());
            discard();
        }
        return true;
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
}
