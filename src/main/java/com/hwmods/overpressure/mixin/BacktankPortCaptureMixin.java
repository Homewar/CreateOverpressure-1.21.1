package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.PneumaticMultitoolCableRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.BacktankArmorLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BacktankArmorLayer.class)
public abstract class BacktankPortCaptureMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER))
    private void overpressure$capturePort(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch,
            CallbackInfo callback) {
        if (entity instanceof Player player) {
            PneumaticMultitoolCableRenderer.captureBacktankPort(player, pose);
        }
    }
}
