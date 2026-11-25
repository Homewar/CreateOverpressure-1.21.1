package com.hwmods.overpressure.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hwmods.overpressure.MirroredMultitoolModel;
import com.hwmods.overpressure.PneumaticMultitoolCableRenderer;
import com.hwmods.overpressure.PneumaticMultitoolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Captures the exact transformed position of the multitool's 2x2 air socket. */
@Mixin(ItemRenderer.class)
public abstract class MultitoolPortCaptureMixin {
    @Unique
    private static final String OVERPRESSURE_ITEM_RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "IILnet/minecraft/client/resources/model/BakedModel;)V";

    @Inject(
            method = OVERPRESSURE_ITEM_RENDER,
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
        PneumaticMultitoolCableRenderer.captureToolPort(stack, displayContext, poseStack, overpressure$mirrorHand(displayContext, leftHand));
    }

    @Redirect(method = OVERPRESSURE_ITEM_RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void overpressure$renderHandModel(ItemRenderer renderer, BakedModel pass, ItemStack stack,
            int light, int overlay, PoseStack pose, VertexConsumer buffer, ItemStack renderedStack,
            ItemDisplayContext context, boolean leftHand, PoseStack renderPose, MultiBufferSource buffers,
            int packedLight, int packedOverlay, BakedModel model) {
        if (PneumaticMultitoolItem.from(stack) != null && overpressure$mirrorHand(context, leftHand)) {
            pass = new MirroredMultitoolModel(pass);
        }
        renderer.renderModelLists(pass, stack, light, overlay, pose, buffer);
    }

    @Unique
    private static boolean overpressure$mirrorHand(ItemDisplayContext context, boolean leftHand) {
        // The item's existing 180-degree display transform puts the unmirrored socket on the left.
        return !leftHand && (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
