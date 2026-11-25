package com.hwmods.overpressure;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightBlock;

/** A visual, non-colliding relay for the multitool pneumatic network. */
public class OxygenPillarEntity extends Entity {
    private static final EntityDataAccessor<Boolean> GLOWING =
            SynchedEntityData.defineId(OxygenPillarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Direction> MOUNT_FACE =
            SynchedEntityData.defineId(OxygenPillarEntity.class, EntityDataSerializers.DIRECTION);

    public OxygenPillarEntity(EntityType<? extends OxygenPillarEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    static boolean place(Level level, BlockPos pos, Direction mountFace, boolean glowing) {
        OxygenPillarEntity pillar = new OxygenPillarEntity(ModEntities.OXYGEN_PILLAR.get(), level);
        pillar.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (!level.getBlockState(pos).canBeReplaced()
                || !level.getEntitiesOfClass(OxygenPillarEntity.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) {
            return false;
        }
        pillar.entityData.set(GLOWING, glowing);
        pillar.entityData.set(MOUNT_FACE, mountFace);
        if (!level.addFreshEntity(pillar)) {
            return false;
        }
        if (glowing) {
            level.setBlockAndUpdate(pos, ModBlocks.OXYGEN_PILLAR_LIGHT.get().defaultBlockState()
                    .setValue(LightBlock.LEVEL, 12).setValue(LightBlock.WATERLOGGED, level.isWaterAt(pos)));
        }
        return true;
    }

    public boolean hasGlowingSocket() {
        return entityData.get(GLOWING);
    }

    public Direction mountFace() {
        return entityData.get(MOUNT_FACE);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GLOWING, false);
        builder.define(MOUNT_FACE, Direction.UP);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(GLOWING, tag.getBoolean("GlowingSocket"));
        Direction face = Direction.byName(tag.getString("MountFace"));
        entityData.set(MOUNT_FACE, face == null ? Direction.UP : face);
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("GlowingSocket", hasGlowingSocket());
        tag.putString("MountFace", mountFace().getSerializedName());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && hasGlowingSocket()) {
            var state = level().getBlockState(blockPosition());
            if (state.is(ModBlocks.OXYGEN_PILLAR_LIGHT.get())) {
                level().setBlockAndUpdate(blockPosition(), state.getFluidState().createLegacyBlock());
            }
        }
        super.remove(reason);
    }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!level().isClientSide) {
            spawnAtLocation(hasGlowingSocket() ? ModBlocks.GLOWING_OXYGEN_PILLAR_ITEM.get() : ModBlocks.OXYGEN_PILLAR_ITEM.get());
            discard();
        }
        return true;
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
}
