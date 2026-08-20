package com.hwmods.overpressure.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hwmods.overpressure.GrindRailRidingHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Replaces the hand-relative wrench with the rail-locked renderer. */
@Mixin(ItemInHandLayer.class)
public abstract class GrindRailHeldItemMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void overpressure$hideMovingWrench(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo callback
    ) {
        if (entity instanceof Player player
                && GrindRailRidingHandler.isClientRiding(player)
                && AllItems.WRENCH.isIn(stack)) {
            callback.cancel();
        }
    }
}
