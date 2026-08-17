package com.hwmods.overpressure.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hwmods.overpressure.GrindRailRidingHandler;
import com.simibubi.create.AllItems;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Applies a stable one-handed hanging pose while a player grinds on a rail. */
@Mixin(HumanoidModel.class)
public abstract class GrindRailPlayerPoseMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void overpressure$applyGrindRailPose(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callback
    ) {
        if (!(entity instanceof Player player) || !GrindRailRidingHandler.isClientRiding(player)) {
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        HumanoidArm wrenchArm = AllItems.WRENCH.isIn(player.getMainHandItem())
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        // The wrench arm reaches almost straight above the shoulder. A tiny
        // inward roll places the wrench over the middle of the player's head.
        if (wrenchArm == HumanoidArm.RIGHT) {
            model.rightArm.xRot = -2.92F;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.10F;

            model.leftArm.xRot = -0.42F;
            model.leftArm.yRot = 0.12F;
            model.leftArm.zRot = -0.24F;
        } else {
            model.leftArm.xRot = -2.92F;
            model.leftArm.yRot = 0.0F;
            model.leftArm.zRot = -0.10F;

            model.rightArm.xRot = -0.42F;
            model.rightArm.yRot = -0.12F;
            model.rightArm.zRot = 0.24F;
        }

        // Stop airborne walking and give the lower body a restrained balancing
        // pose. Armor models receive the same rotations through this mixin.
        model.body.xRot = -0.10F;
        model.body.yRot = 0.0F;
        model.body.zRot = 0.0F;
        model.rightLeg.xRot = 0.24F;
        model.rightLeg.yRot = 0.04F;
        model.rightLeg.zRot = 0.05F;
        model.leftLeg.xRot = -0.16F;
        model.leftLeg.yRot = -0.04F;
        model.leftLeg.zRot = -0.05F;
    }
}
