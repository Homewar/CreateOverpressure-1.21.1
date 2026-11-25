package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.PneumaticMultitoolItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultitoolClientMiningMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract void stopDestroyBlock();
    @Unique private int overpressure$lastPressureWarning = -20;

    @Inject(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At("HEAD"), cancellable = true)
    private void overpressure$blockUnpoweredMining(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> result) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        var stack = player.getMainHandItem();
        var tool = PneumaticMultitoolItem.from(stack);
        if (tool == null || tool.canAttackBlock(minecraft.level.getBlockState(pos), minecraft.level, pos, player)) {
            return;
        }
        stopDestroyBlock();
        result.setReturnValue(false);
        if (PneumaticMultitoolItem.selected(stack) != PneumaticMultitoolItem.Attachment.NONE
                && (player.tickCount < overpressure$lastPressureWarning
                || player.tickCount - overpressure$lastPressureWarning >= 20)) {
            overpressure$lastPressureWarning = player.tickCount;
            player.displayClientMessage(Component.translatable("message.overpressure.multitool.low_pressure")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @Redirect(method = {"startDestroyBlock", "continueDestroyBlock"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameType;isCreative()Z"))
    private boolean overpressure$useMiningProgress(GameType mode) {
        return mode.isCreative() && (minecraft.player == null
                || PneumaticMultitoolItem.from(minecraft.player.getMainHandItem()) == null);
    }
}
