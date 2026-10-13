package com.hwmods.overpressure.mixin;
import com.hwmods.overpressure.tube.FluidTubeBridge;
import com.simibubi.create.content.fluids.FluidPropagator;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidPropagator.class)
public class FluidTubeConnectionMixin {
    @Inject(method = "hasFluidCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private static void overpressure$endpoint(BlockGetter world, BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof Level level && FluidTubeBridge.at(level, pos, side) != null) cir.setReturnValue(true);
    }
    @Inject(method = "isOpenEnd", at = @At("HEAD"), cancellable = true, remap = false)
    private static void overpressure$closedEnd(BlockGetter world, BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof Level level && FluidTubeBridge.at(level, pos.relative(side), side.getOpposite()) != null) cir.setReturnValue(false);
    }
}
