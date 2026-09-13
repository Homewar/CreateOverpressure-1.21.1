package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.CurveCollisionIndex;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockGetter.class)
public interface CurveRaycastMixin {
    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
            at = @At("RETURN"), cancellable = true)
    private void overpressure$pickVisibleCurve(ClipContext context, CallbackInfoReturnable<BlockHitResult> callback) {
        if ((Object) this instanceof Level level) {
            callback.setReturnValue(CurveCollisionIndex.clip(level, context, callback.getReturnValue()));
        }
    }
}
