package com.hwmods.overpressure.mixin;

import com.hwmods.overpressure.ModBlocks;
import com.hwmods.overpressure.tube.TubeSectionInteractions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SectionPickItemMixin {
    @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    private void overpressure$pickSection(CallbackInfo callback) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || mc.gameMode == null
                || TubeSectionInteractions.hit(mc.player, mc.player.blockInteractionRange()) == null) return;
        callback.cancel();
        ItemStack stack = new ItemStack(ModBlocks.PNEUMATIC_TUBE_ITEM.get());
        Inventory inventory = mc.player.getInventory();
        if (mc.player.getAbilities().instabuild) {
            inventory.setPickedItem(stack);
            mc.gameMode.handleCreativeModeItemAdd(mc.player.getItemInHand(InteractionHand.MAIN_HAND), 36 + inventory.selected);
        } else {
            int slot = inventory.findSlotMatchingItem(stack);
            if (Inventory.isHotbarSlot(slot)) inventory.selected = slot;
            else if (slot >= 0) mc.gameMode.handlePickItem(slot);
        }
    }
}
