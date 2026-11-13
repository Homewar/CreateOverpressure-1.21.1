package com.hwmods.overpressure;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places a relay entity instead of a colliding world block. */
public class OxygenPillarItem extends Item {
    public OxygenPillarItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        var pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            OxygenPillarEntity pillar = new OxygenPillarEntity(ModEntities.OXYGEN_PILLAR.get(), level);
            pillar.setPos(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
            level.addFreshEntity(pillar);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
