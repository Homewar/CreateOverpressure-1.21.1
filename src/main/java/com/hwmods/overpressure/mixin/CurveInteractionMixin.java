package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.CurvaturePneumaticTubeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class CurveInteractionMixin {
    @Inject(method = "canInteractWithBlock", at = @At("RETURN"), cancellable = true)
    private void overpressure$checkVisibleCurveReach(BlockPos pos, double extraRange, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) return;
        Player player = (Player) (Object) this;
        if (!player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof CurvaturePneumaticTubeEntity)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + extraRange));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        // Only the visible, unobstructed part within normal reach can authorize a distant owner.
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) callback.setReturnValue(true);
    }
}
