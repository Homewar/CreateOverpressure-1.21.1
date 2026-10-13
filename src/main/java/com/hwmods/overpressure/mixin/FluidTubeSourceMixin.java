package com.hwmods.overpressure.mixin;
import com.hwmods.overpressure.tube.FluidTubeBridge;
import com.simibubi.create.content.fluids.FlowSource;
import com.simibubi.create.foundation.ICapabilityProvider;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowSource.FluidHandler.class)
public class FluidTubeSourceMixin {
    @Shadow(remap = false) ICapabilityProvider<IFluidHandler> fluidHandlerCache;
    @Unique private BlockFace overpressure$location;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void overpressure$rememberPort(BlockFace location, CallbackInfo ci) { overpressure$location = location; }
    @Inject(method = "manageSource", at = @At("HEAD"), cancellable = true, remap = false)
    private void overpressure$virtualSource(Level level, BlockEntity networkBE, CallbackInfo ci) {
        var location = overpressure$location;
        if (location == null) return;
        if (FluidTubeBridge.at(level, location.getConnectedPos(), location.getOppositeFace()) == null) return;
        fluidHandlerCache = ICapabilityProvider.of(() -> FluidTubeBridge.at(level, location.getConnectedPos(), location.getOppositeFace()));
        ci.cancel();
    }
}
