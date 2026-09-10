package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.CurveCollisionIndex;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CollisionGetter.class)
public interface CurveCollisionMixin {
    @Inject(method = "getBlockCollisions", at = @At("RETURN"), cancellable = true)
    private void overpressure$includeDisplacedCurves(Entity entity, AABB box,
            CallbackInfoReturnable<Iterable<VoxelShape>> callback) {
        if ((Object) this instanceof Level level) {
            var curves = CurveCollisionIndex.collisions(level, box);
            if (!curves.isEmpty()) {
                callback.setReturnValue(com.google.common.collect.Iterables.concat(callback.getReturnValue(), curves));
            }
        }
    }
}
