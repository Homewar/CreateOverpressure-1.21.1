package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.PneumaticMultitoolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class MultitoolServerMiningMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void overpressure$blockUnpoweredMining(BlockPos pos, ServerboundPlayerActionPacket.Action action,
            Direction face, int maxBuildHeight, int sequence, CallbackInfo callback) {
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            return;
        }
        var stack = player.getMainHandItem();
        var tool = PneumaticMultitoolItem.from(stack);
        var level = player.serverLevel();
        if (tool == null || !player.canInteractWithBlock(pos, 1.0)
                || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        PneumaticMultitoolItem.updateHeldPower(stack, player);
        if (!tool.canAttackBlock(level.getBlockState(pos), level, pos, player)) {
            callback.cancel();
        }
    }
    @Redirect(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;isCreative()Z"))
    private boolean overpressure$useMiningProgress(ServerPlayerGameMode mode) {
        return mode.isCreative() && PneumaticMultitoolItem.from(player.getMainHandItem()) == null;
    }
}
