package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.tube.FluidTubeFlowPayload;
import com.simibubi.create.content.fluids.*;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidTransportBehaviour.class)
public class FluidTubeFlowMixin {
    @Inject(method = "getFlow", at = @At("RETURN"), cancellable = true, remap = false)
    private void overpressure$visualCurveFlow(Direction side, CallbackInfoReturnable<PipeConnection.Flow> cir) {
        if (cir.getReturnValue() != null) return;
        FluidTransportBehaviour self = (FluidTransportBehaviour) (Object) this;
        var level = self.blockEntity.getLevel();
        if (level == null || !level.isClientSide) return;
        var fluid = FluidTubeFlowPayload.pipeFluid(level, self.blockEntity.getBlockPos(), side);
        if (fluid.isEmpty()) return;
        PipeConnection connection = new PipeConnection(side);
        PipeConnection.Flow flow = connection.new Flow(false, fluid);
        flow.progress.startWithValue(1);
        flow.progress.setValue(1);
        flow.complete = true;
        cir.setReturnValue(flow);
    }
}
