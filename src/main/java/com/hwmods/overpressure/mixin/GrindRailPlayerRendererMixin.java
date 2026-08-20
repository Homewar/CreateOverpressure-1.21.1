package com.hwmods.overpressure.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hwmods.overpressure.GrindRailRidingHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;

/** Rotates the rendered player around the hand attached to the rail. */
@Mixin(PlayerRenderer.class)
public abstract class GrindRailPlayerRendererMixin {
    // PlayerRenderer applies a 15/16 scale after setupRotations. ModelPart.x is
    // the shoulder joint; the visible palm center is another 1 px (wide skin)
    // or 0.5 px (slim skin) farther out.
    private static final double MODEL_SCALE = 15.0 / 16.0;

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void overpressure$placeHandOnGrindPivot(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float bob,
            float bodyYaw,
            float partialTick,
            float scale,
            CallbackInfo callback
    ) {
        double pivot = overpressure$getPivot(player);
        if (pivot != 0.0) {
            // Move the raised hand onto the entity origin after body rotation.
            // The body consequently orbits that fixed hand instead of leaving
            // the hand one shoulder-width away from the rail/wrench.
            poseStack.translate(-pivot, 0.0, 0.0);
        }
    }

    private static double overpressure$getPivot(AbstractClientPlayer player) {
        if (!GrindRailRidingHandler.isClientRiding(player)) {
            return 0.0;
        }

        HumanoidArm wrenchArm = AllItems.WRENCH.isIn(player.getMainHandItem())
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        double handCenterPixels = player.getSkin().model() == PlayerSkin.Model.SLIM ? 5.5 : 6.0;
        double handPivot = handCenterPixels / 16.0 * MODEL_SCALE;
        return wrenchArm == HumanoidArm.RIGHT ? handPivot : -handPivot;
    }
}
