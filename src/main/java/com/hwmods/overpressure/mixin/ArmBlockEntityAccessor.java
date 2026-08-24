package com.hwmods.overpressure.mixin;

import java.util.List;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ArmBlockEntity.class, remap = false)
public interface ArmBlockEntityAccessor {
    @Accessor("outputs")
    List<ArmInteractionPoint> overpressure$getOutputs();

    @Accessor("heldItem")
    void overpressure$setHeldItem(ItemStack stack);

    @Invoker("read")
    void overpressure$readPonderState(
            CompoundTag tag,
            HolderLookup.Provider registries,
            boolean clientPacket
    );
}
