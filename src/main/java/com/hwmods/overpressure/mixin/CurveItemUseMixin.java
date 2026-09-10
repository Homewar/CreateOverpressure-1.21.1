package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.CurvaturePneumaticTubeEntity;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CurveItemUseMixin {
    @Shadow public ServerPlayer player;

    @Redirect(method = "handleUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 overpressure$validateCurveSurface(Vec3 location, Vec3 blockCenter, ServerboundUseItemOnPacket packet) {
        var stack = player.getItemInHand(packet.getHand());
        var pos = packet.getHitResult().getBlockPos();
        if ((stack.getItem() instanceof DyeItem || stack.is(Items.GLOW_INK_SAC) || stack.getItem() instanceof BlockItem)
                && player.level().isLoaded(pos)
                && player.level().getBlockEntity(pos) instanceof CurvaturePneumaticTubeEntity) {
            Vec3 eye = player.getEyePosition();
            var actual = player.level().clip(new ClipContext(eye,
                    eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (actual.getType() == HitResult.Type.BLOCK && actual.getBlockPos().equals(pos)
                    && actual.getLocation().distanceToSqr(location) < 0.0001) {
                // This verified point belongs to the curve, not necessarily its owner's cell.
                // Keep all remaining vanilla permission, game mode and item-use checks intact.
                return Vec3.ZERO;
            }
        }
        return location.subtract(blockCenter);
    }
}
