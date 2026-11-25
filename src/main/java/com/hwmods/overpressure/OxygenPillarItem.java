package com.hwmods.overpressure;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places a relay entity instead of a colliding world block. */
public class OxygenPillarItem extends Item {
    private final boolean glowing;

    public OxygenPillarItem(Properties properties, boolean glowing) {
        super(properties);
        this.glowing = glowing;
    }

    boolean isGlowingVariant() {
        return glowing;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        var pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!OxygenPillarEntity.place(level, pos, context.getClickedFace(), glowing)) return InteractionResult.FAIL;
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
