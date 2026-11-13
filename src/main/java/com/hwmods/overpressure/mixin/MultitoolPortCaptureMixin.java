package com.hwmods.overpressure.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hwmods.overpressure.PneumaticMultitoolCableRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Captures the exact transformed position of the multitool's 2x2 air socket. */
@Mixin(ItemRenderer.class)
public abstract class MultitoolPortCaptureMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void overpressure$captureMultitoolSocket(
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            BakedModel model,
            CallbackInfo callback
    ) {
        PneumaticMultitoolCableRenderer.captureToolPort(stack, displayContext, poseStack);
    }
}
