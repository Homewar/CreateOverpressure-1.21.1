package com.hwmods.overpressure.mixin;
import com.hwmods.overpressure.tube.FluidTubeBridge;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PumpBlockEntity.class)
public class FluidTubePumpMixin {
    @Inject(method = "hasReachedValidEndpoint", at = @At("HEAD"), cancellable = true, remap = false)
    private void overpressure$curveEndpoint(LevelAccessor world, BlockFace face, boolean pull, CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof Level level && FluidTubeBridge.at(level, face.getConnectedPos(), face.getOppositeFace()) != null)
            cir.setReturnValue(true);
    }
}
