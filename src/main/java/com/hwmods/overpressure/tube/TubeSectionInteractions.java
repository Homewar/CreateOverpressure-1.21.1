package com.hwmods.overpressure.tube;

import com.hwmods.overpressure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class TubeSectionInteractions {
    public static TubeSections.Hit hit(Player player, double reach) {
        Vec3 from = player.getEyePosition(), to = from.add(player.getLookAngle().scale(reach));
        var hit = TubeSections.clip(player.level(), from, to);
        if (hit == null) return null;
        var block = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.BLOCK && from.distanceToSqr(block.getLocation()) + 1.0E-8 < from.distanceToSqr(hit.point()) ? null : hit;
    }
    public static void interact(Player player, java.util.UUID id, boolean attack, InteractionHand hand) {
        var hit = hit(player, player.blockInteractionRange());
        if (hit == null || !hit.section().id().equals(id) || player.isSpectator() || !player.getAbilities().mayBuild) return;
        var level = player.level();
        for (BlockPos pos : TubeSectionPlacement.cells(hit.section())) if (!level.mayInteract(player, pos)) return;
        ItemStack held = player.getItemInHand(hand);
        if (attack || player.isShiftKeyDown() && com.simibubi.create.AllItems.WRENCH.isIn(held)) {
            if (level.isClientSide) return;
            TubeSection removed = TubeSections.remove(level, id);
            if (removed == null) return;
            var state = ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState();
            level.levelEvent(null, 2001, BlockPos.containing(hit.point()), net.minecraft.world.level.block.Block.getId(state));
            level.gameEvent(player, net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, hit.point());
            if (!player.getAbilities().instabuild && (!attack
                    || level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS))) {
                int count = removed.materialCost();
                while (count > 0) {
                    ItemStack refund = new ItemStack(ModBlocks.PNEUMATIC_TUBE_ITEM.get());
                    int amount = Math.min(refund.getMaxStackSize(), count);
                    refund.setCount(amount);
                    if (!attack) player.getInventory().placeItemBackInInventory(refund);
                    else {
                        Vec3 drop = hit.point().add(Vec3.atLowerCornerOf(hit.face().getNormal()).scale(0.15));
                        ItemEntity item = new ItemEntity(level, drop.x, drop.y, drop.z, refund);
                        item.setDefaultPickUpDelay();
                        level.addFreshEntity(item);
                    }
                    count -= amount;
                }
            }
            return;
        }
        if (held.getItem() instanceof DyeItem dye || held.is(Items.GLOW_INK_SAC)) {
            int color = held.getItem() instanceof DyeItem item
                    ? item.getDyeColor() == net.minecraft.world.item.DyeColor.WHITE ? 0xFFFFFF : item.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF
                    : -1;
            if (paint(player, id, color, held.is(Items.GLOW_INK_SAC)) && !player.getAbilities().instabuild) held.shrink(1);
            return;
        }
        if (held.getItem() instanceof PneumaticTubeBlockItem tube && !player.isShiftKeyDown()) {
            if (tube.getSelectedStart(level, player) != null) { tube.use(level, player, hand); return; }
            boolean start = hit.point().distanceToSqr(hit.section().port(true).position())
                    < hit.point().distanceToSqr(hit.section().port(false).position());
            var port = hit.section().port(start);
            if (hit.point().distanceTo(port.position()) <= 0.8) tube.selectSectionEnd(level, player, port);
            return;
        }
        if (held.getItem() instanceof BlockItem block) {
            Vec3 normal = Vec3.atLowerCornerOf(hit.face().getNormal());
            BlockPos inside = BlockPos.containing(hit.point().subtract(normal.scale(1.0E-4)));
            BlockPos target = inside.relative(hit.face());
            if (!level.isLoaded(target) || level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)
                    || !level.mayInteract(player, target) || !player.mayUseItemAt(target, hit.face(), held)) return;
            BlockHitResult surface = new BlockHitResult(hit.point(), hit.face(), inside, false);
            BlockPlaceContext context = new BlockPlaceContext(player, hand, held, surface) {
                @Override public BlockPos getClickedPos() { return target; }
                @Override public boolean replacingClickedOnBlock() { return false; }
                @Override public boolean canPlace() { return level.getBlockState(target).canBeReplaced(this); }
            };
            block.place(context);
        }
    }
    public static boolean paint(Player player, java.util.UUID id, int color, boolean glow) {
        var section = TubeSections.get(player.level()).get(id);
        if (section == null) return false;
        var hit = hit(player, player.blockInteractionRange());
        int index = 0;
        if (hit != null && hit.section().id().equals(id)) {
            double best = Double.POSITIVE_INFINITY;
            for (var frame : section.geometry().frames()) {
                double distance = frame.center().distanceToSqr(hit.point());
                if (distance < best) { best = distance; index = frame.span(); }
            }
        }
        return TubeSectionPainting.apply(player.level(), new TubeSectionPainting.SpanKey(id, index), player, color, glow) > 0;
    }
}
