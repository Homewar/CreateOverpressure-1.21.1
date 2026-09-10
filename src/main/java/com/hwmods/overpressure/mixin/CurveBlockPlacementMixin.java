package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.CurvaturePneumaticTubeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockPlaceContext.class)
public abstract class CurveBlockPlacementMixin {
    @Shadow @Final @Mutable private BlockPos relativePos;
    @Shadow protected boolean replaceClicked;
    @Unique private boolean overpressure$curvePlacement;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/BlockHitResult;)V",
            at = @At("RETURN"))
    private void overpressure$placeAtVisibleSurface(Level level, Player player, InteractionHand hand,
            ItemStack stack, BlockHitResult hit, CallbackInfo callback) {
        if (player == null || !(level.getBlockEntity(hit.getBlockPos()) instanceof CurvaturePneumaticTubeEntity)) return;
        // Choose the cell just inside the hit face, then place against that face.
        // Keep the original hit/owner for interactions; only placement moves to the visible curve.
        Vec3 inward = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(-1.0E-4);
        relativePos = BlockPos.containing(hit.getLocation().add(inward)).relative(hit.getDirection());
        replaceClicked = false;
        overpressure$curvePlacement = true;
    }

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void overpressure$checkPlacementPermissions(CallbackInfoReturnable<Boolean> callback) {
        if (!overpressure$curvePlacement) return;
        BlockPlaceContext context = (BlockPlaceContext) (Object) this;
        Level level = context.getLevel();
        if (!level.isLoaded(relativePos) || level.isOutsideBuildHeight(relativePos)
                || !level.getWorldBorder().isWithinBounds(relativePos)
                || !level.mayInteract(context.getPlayer(), relativePos)) callback.setReturnValue(false);
    }
}
