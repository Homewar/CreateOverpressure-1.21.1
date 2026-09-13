package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.tube.TubeSections;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class SectionPlacementCollisionMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void overpressure$protectSections(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        var level = context.getLevel(); var pos = context.getClickedPos();
        for (var local : state.getCollisionShape(level, pos, CollisionContext.empty()).toAabbs()) {
            var box = local.move(pos).deflate(1.0E-5);
            if (!TubeSections.collisions(level, box).isEmpty()) { callback.setReturnValue(false); return; }
        }
    }
}
