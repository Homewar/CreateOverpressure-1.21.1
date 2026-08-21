package com.hwmods.overpressure.mixin;

import java.util.List;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ArmBlockEntity.class, remap = false)
public interface ArmBlockEntityAccessor {
    @Accessor("outputs")
    List<ArmInteractionPoint> overpressure$getOutputs();
}
