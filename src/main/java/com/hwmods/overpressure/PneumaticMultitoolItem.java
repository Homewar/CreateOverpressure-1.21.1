package com.hwmods.overpressure;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** An unbreakable pneumatic tool whose mining type is supplied by installed attachments. */
public class PneumaticMultitoolItem extends Item {
    private static final String DATA_ROOT = "OverpressureMultitool";
    private static final String DRILL_INSTALLED = "DrillInstalled";
    private static final String SAW_INSTALLED = "SawInstalled";
    private static final String SELECTED = "Selected";
    private static final float NETHERITE_HASTE_TWO_SPEED = 9.0F * 1.4F;

    public PneumaticMultitoolItem(Properties properties) {
        super(properties.stacksTo(1).setNoRepair());
    }

    public enum Attachment {
        NONE("none"),
        DRILL("drill"),
        SAW("saw");

        private final String id;

        Attachment(String id) {
            this.id = id;
        }

        static Attachment byId(String id) {
            for (Attachment attachment : values()) {
                if (attachment.id.equals(id)) {
                    return attachment;
                }
            }
            return NONE;
        }
    }

    public static PneumaticMultitoolItem from(ItemStack stack) {
        return stack.getItem() instanceof PneumaticMultitoolItem item ? item : null;
    }

    public static boolean isInstalled(ItemStack stack, Attachment attachment) {
        CompoundTag tag = data(stack);
        return switch (attachment) {
            case DRILL -> tag.getBoolean(DRILL_INSTALLED);
            case SAW -> tag.getBoolean(SAW_INSTALLED);
            case NONE -> false;
        };
    }

    public static Attachment selected(ItemStack stack) {
        Attachment selected = Attachment.byId(data(stack).getString(SELECTED));
        return isInstalled(stack, selected) ? selected : firstInstalled(stack);
    }

    public static void cycle(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (from(stack) == null) {
            return;
        }
        boolean drill = isInstalled(stack, Attachment.DRILL);
        boolean saw = isInstalled(stack, Attachment.SAW);
        Attachment current = selected(stack);
        Attachment next;
        if (drill && saw) {
            next = current == Attachment.DRILL ? Attachment.SAW : Attachment.DRILL;
        } else if (drill) {
            next = Attachment.DRILL;
        } else if (saw) {
            next = Attachment.SAW;
        } else {
            next = Attachment.NONE;
        }
        setSelected(stack, next);
        player.displayClientMessage(Component.translatable("message.overpressure.multitool.selected." + next.id), true);
    }

    public static void toggle(ServerPlayer player, InteractionHand hand, Attachment attachment) {
        if (attachment == Attachment.NONE) {
            return;
        }
        ItemStack tool = player.getItemInHand(hand);
        if (from(tool) == null) {
            return;
        }
        if (isInstalled(tool, attachment)) {
            boolean wasSelected = selected(tool) == attachment;
            setInstalled(tool, attachment, false);
            ItemStack returned = new ItemStack(itemFor(attachment));
            if (!player.getInventory().add(returned)) {
                player.drop(returned, false);
            }
            if (wasSelected) {
                setSelected(tool, firstInstalled(tool));
            }
            return;
        }

        Inventory inventory = player.getInventory();
        Item attachmentItem = itemFor(attachment);
        boolean hadSelectedAttachment = selected(tool) != Attachment.NONE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate.is(attachmentItem)) {
                candidate.shrink(1);
                setInstalled(tool, attachment, true);
                if (!hadSelectedAttachment) {
                    setSelected(tool, attachment);
                }
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.overpressure.multitool.missing_attachment"), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new MultitoolOpenPayload(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return selected(player.getMainHandItem()) != Attachment.NONE;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return supports(selected(stack), state) ? NETHERITE_HASTE_TWO_SPEED : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return supports(selected(stack), state) && !state.is(BlockTags.INCORRECT_FOR_NETHERITE_TOOL);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        return selected(stack) != Attachment.NONE;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            int expected = modelData(selected(stack));
            int current = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value();
            if (current != expected) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(expected));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Attachment active = selected(stack);
        tooltip.add(Component.translatable("tooltip.overpressure.multitool.active", Component.translatable(
                "message.overpressure.multitool.selected." + active.id)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.overpressure.multitool.controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static boolean supports(Attachment attachment, BlockState state) {
        return switch (attachment) {
            case DRILL -> state.is(BlockTags.MINEABLE_WITH_PICKAXE);
            case SAW -> state.is(BlockTags.MINEABLE_WITH_AXE);
            case NONE -> false;
        };
    }

    private static Item itemFor(Attachment attachment) {
        return switch (attachment) {
            case DRILL -> ModBlocks.PNEUMATIC_DRILL_ATTACHMENT.get();
            case SAW -> ModBlocks.PNEUMATIC_CHAINSAW_ATTACHMENT.get();
            case NONE -> throw new IllegalArgumentException("NONE has no item");
        };
    }

    private static Attachment firstInstalled(ItemStack stack) {
        if (isInstalled(stack, Attachment.DRILL)) {
            return Attachment.DRILL;
        }
        if (isInstalled(stack, Attachment.SAW)) {
            return Attachment.SAW;
        }
        return Attachment.NONE;
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.getCompound(DATA_ROOT);
    }

    private static void setInstalled(ItemStack stack, Attachment attachment, boolean installed) {
        updateData(stack, tag -> tag.putBoolean(
                attachment == Attachment.DRILL ? DRILL_INSTALLED : SAW_INSTALLED, installed));
    }

    private static void setSelected(ItemStack stack, Attachment attachment) {
        updateData(stack, tag -> tag.putString(SELECTED, attachment.id));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData(attachment)));
    }

    private static int modelData(Attachment attachment) {
        return switch (attachment) {
            case NONE -> 0;
            case DRILL -> 1;
            case SAW -> 2;
        };
    }

    private static void updateData(ItemStack stack, java.util.function.Consumer<CompoundTag> updater) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag tag = root.getCompound(DATA_ROOT);
            updater.accept(tag);
            root.put(DATA_ROOT, tag);
        });
    }
}
