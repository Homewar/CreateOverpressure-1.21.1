package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.PneumaticMultitoolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MultitoolDestroyProgressMixin {
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void overpressure$limitMultitoolProgress(Player player, BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<Float> result) {
        var tool = player.getMainHandItem();
        if (PneumaticMultitoolItem.from(tool) != null) {
            result.setReturnValue(PneumaticMultitoolItem.limitDestroyProgress(
                    tool, (BlockState) (Object) this, result.getReturnValueF()));
        }
    }
}
