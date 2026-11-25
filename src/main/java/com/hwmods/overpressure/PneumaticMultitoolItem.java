package com.hwmods.overpressure;

import java.util.List;
import java.util.Locale;

import com.simibubi.create.content.equipment.armor.BacktankUtil;

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
import net.minecraft.world.item.context.UseOnContext;
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
    private static final String CONNECTOR_SPEED = "ConnectorSpeed";
    private static final String IN_CONNECTOR_RANGE = "InConnectorRange";
    private static final String RESERVE_BLOCKS = "ReserveBlocks";
    private static final String SPEED_SETTING = "SpeedSetting";
    private static final String AIR_REMAINDER = "AirRemainder";
    private static final String POWERED = "Powered";
    private static final float NETHERITE_HASTE_TWO_SPEED = 9.0F * 1.4F;
    private static final float REFERENCE_TUBE_SPEED = 20.0F / PneumaticTubeBlockEntity.BASE_MOVE_TIME;
    private static final float MAX_DESTROY_SPEED = 64.0F;
    private static final float MAX_BREAK_PROGRESS_PER_TICK = 0.32F;
    /** Autonomous compressed-air sources run the tool at 20 blocks per second. */
    private static final float AUTONOMOUS_AIR_SPEED = 20.0F;
    private static final float TUBE_SPEED_AT_128_RPM = 5.0F;
    private static final float BACKTANK_REFERENCE_DESTROY_SPEED = NETHERITE_HASTE_TWO_SPEED
            * TUBE_SPEED_AT_128_RPM / REFERENCE_TUBE_SPEED;
    public static final int MAX_RESERVE_BLOCKS = 16;
    public static final int MIN_SPEED_SETTING = 10;
    public static final int MAX_SPEED_SETTING = 100;
    private static final int BACKTANK_BLOCKS_AT_MAX_SPEED = 64;

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

    public static int speedSetting(ItemStack stack) {
        int value = data(stack).getInt(SPEED_SETTING);
        return value == 0 ? MAX_SPEED_SETTING : Math.clamp(value, MIN_SPEED_SETTING, MAX_SPEED_SETTING);
    }

    public static void setSpeedSetting(ItemStack stack, int value) {
        int clamped = Math.clamp(value, MIN_SPEED_SETTING, MAX_SPEED_SETTING);
        updateData(stack, tag -> tag.putInt(SPEED_SETTING, clamped));
    }

    public static int reserveBlocks(ItemStack stack) {
        return Math.clamp(data(stack).getInt(RESERVE_BLOCKS), 0, MAX_RESERVE_BLOCKS);
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

    /** Normal right-click deploys a relay; Shift + right-click remains the tool menu. */
    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            var pos = context.getClickedPos().relative(context.getClickedFace());
            var inventory = player.getInventory();
            ItemStack stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof OxygenPillarItem)) {
                for (ItemStack candidate : inventory.items) {
                    if (!candidate.isEmpty() && candidate.getItem() instanceof OxygenPillarItem) {
                        stack = candidate;
                        break;
                    }
                }
            }
            if (!(stack.getItem() instanceof OxygenPillarItem pillar)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            if (!OxygenPillarEntity.place(player.level(), pos, context.getClickedFace(), pillar.isGlowingVariant())) {
                return net.minecraft.world.InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        ItemStack stack = player.getMainHandItem();
        return selected(stack) != Attachment.NONE && hasMiningPower(stack, player);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!supports(selected(stack), state)) {
            return 1.0F;
        }

        return !data(stack).getBoolean(POWERED) ? 1.0F : configuredDestroySpeed(stack);
    }

    public static float limitDestroyProgress(ItemStack stack, BlockState state, float progress) {
        CompoundTag power = data(stack);
        Attachment attachment = selected(stack);
        if (attachment == Attachment.NONE || !power.getBoolean(POWERED)) {
            return 0.0F;
        }
        float limit = MAX_BREAK_PROGRESS_PER_TICK;
        if (supports(attachment, state)) {
            if (progress > 0.0F && power.getBoolean(IN_CONNECTOR_RANGE)
                    && power.getFloat(CONNECTOR_SPEED) >= PneumaticConnectorBlockEntity.MAX_FLOW_SPEED
                    && speedSetting(stack) == MAX_SPEED_SETTING) {
                return 1.0F;
            }
            limit *= configuredDestroySpeed(stack) / MAX_DESTROY_SPEED;
        }
        return Math.min(progress, limit);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return supports(selected(stack), state) && !state.is(BlockTags.INCORRECT_FOR_NETHERITE_TOOL);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && miner instanceof Player player && supports(selected(stack), state)) {
            consumeMiningPower(stack, player);
        }
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

        if (isSelected && entity instanceof Player player) {
            updateHeldPower(stack, player);
        }
    }

    public static void updateHeldPower(ItemStack stack, Player player) {
        Level level = player.level();
        PneumaticConnectorBlockEntity connector = PneumaticAirNetwork.findConnector(level, player.position());
        if (!level.isClientSide && connector != null && selected(stack) != Attachment.NONE) {
            connector.requestToolLoad();
        }
        float detectedSpeed = connector == null ? 0.0F : connector.getItemSpeed();
        boolean inRange = detectedSpeed > 0.0F;
        boolean reservePower = reserveBlocks(stack) > 0;
        boolean backtankPower = hasEnoughBacktankAir(stack, player);
        boolean powered = inRange || reservePower || backtankPower;
        float activeSpeed = inRange ? detectedSpeed
                : (reservePower || backtankPower ? AUTONOMOUS_AIR_SPEED : 0.0F);
        CompoundTag current = data(stack);
        boolean changed = current.getBoolean(IN_CONNECTOR_RANGE) != inRange
                || current.getBoolean(POWERED) != powered
                || Math.abs(current.getFloat(CONNECTOR_SPEED) - activeSpeed) > 1.0E-4F
                || inRange && current.getInt(RESERVE_BLOCKS) != MAX_RESERVE_BLOCKS;
        if (changed) {
            updateData(stack, tag -> {
                tag.putBoolean(IN_CONNECTOR_RANGE, inRange);
                tag.putBoolean(POWERED, powered);
                tag.putFloat(CONNECTOR_SPEED, activeSpeed);
                if (inRange) {
                    tag.putInt(RESERVE_BLOCKS, MAX_RESERVE_BLOCKS);
                }
            });
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Attachment active = selected(stack);
        tooltip.add(Component.translatable("tooltip.overpressure.multitool.active", Component.translatable(
                "message.overpressure.multitool.selected." + active.id)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.overpressure.multitool.controls").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.overpressure.multitool.connector_range").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.overpressure.multitool.tube_speed").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                "tooltip.overpressure.multitool.current_speed",
                String.format(Locale.ROOT, "%.2f", cachedConnectorSpeed(stack))
        ).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
                "tooltip.overpressure.multitool.speed_setting", speedSetting(stack)
        ).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                "tooltip.overpressure.multitool.reserve", reserveBlocks(stack), MAX_RESERVE_BLOCKS
        ).withStyle(ChatFormatting.YELLOW));
    }

    private static boolean supports(Attachment attachment, BlockState state) {
        return switch (attachment) {
            case DRILL -> state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                    || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            case SAW -> state.is(BlockTags.MINEABLE_WITH_AXE);
            case NONE -> false;
        };
    }

    private static float connectorSpeed(Player player) {
        PneumaticConnectorBlockEntity connector = PneumaticAirNetwork.findConnector(player.level(), player.position());
        return connector == null ? 0.0F : connector.getItemSpeed();
    }

    private static float cachedConnectorSpeed(ItemStack stack) {
        float speed = data(stack).getFloat(CONNECTOR_SPEED);
        return Float.isFinite(speed) ? Math.max(0.0F, speed) : 0.0F;
    }


    private static boolean hasMiningPower(ItemStack stack, Player player) {
        return reserveBlocks(stack) > 0
                || hasEnoughBacktankAir(stack, player)
                || connectorSpeed(player) > 0.0F;
    }

    private static boolean hasEnoughBacktankAir(ItemStack stack, LivingEntity miner) {
        int required = airRequiredForNextBlock(stack);
        int available = BacktankUtil.getAllWithAir(miner).stream()
                .mapToInt(BacktankUtil::getAir)
                .sum();
        return available > 0 && available >= required;
    }

    private static int airRequiredForNextBlock(ItemStack stack) {
        int remainder = Math.clamp(data(stack).getInt(AIR_REMAINDER), 0, 999);
        int cost = airCostPerBlockMilli(stack);
        return (remainder + cost) / 1_000;
    }

    private static float configuredDestroySpeed(ItemStack stack) {
        float fullSpeed = fullDestroySpeed(stack);
        return Math.max(1.0F, fullSpeed * speedSetting(stack) / 100.0F);
    }

    private static float fullDestroySpeed(ItemStack stack) {
        float tubeSpeed = cachedConnectorSpeed(stack);
        float minFlow = PneumaticConnectorBlockEntity.MIN_FLOW_SPEED;
        float maxFlow = PneumaticConnectorBlockEntity.MAX_FLOW_SPEED;
        float fraction = Math.clamp((tubeSpeed - minFlow) / (maxFlow - minFlow), 0.0F, 1.0F);
        return NETHERITE_HASTE_TWO_SPEED + fraction * (MAX_DESTROY_SPEED - NETHERITE_HASTE_TWO_SPEED);
    }

    private static int airCostPerBlockMilli(ItemStack stack) {
        float maxBacktankCost = BacktankUtil.maxAirWithoutEnchants()
                * 1_000.0F / BACKTANK_BLOCKS_AT_MAX_SPEED;
        float tubeSpeedFactor = Math.clamp(cachedConnectorSpeed(stack) / TUBE_SPEED_AT_128_RPM,
                1.0F / BACKTANK_REFERENCE_DESTROY_SPEED, 1.0F);
        float selectedSpeedFactor = speedSetting(stack) / 100.0F;
        return Math.max(1, Math.round(maxBacktankCost * tubeSpeedFactor * selectedSpeedFactor));
    }

    private static void consumeMiningPower(ItemStack stack, Player player) {
        if (player.getAbilities().instabuild) {
            return;
        }

        float detectedSpeed = connectorSpeed(player);
        if (detectedSpeed > 0.0F) {
            updateData(stack, tag -> {
                tag.putBoolean(IN_CONNECTOR_RANGE, true);
                tag.putBoolean(POWERED, true);
                tag.putFloat(CONNECTOR_SPEED, detectedSpeed);
                tag.putInt(RESERVE_BLOCKS, MAX_RESERVE_BLOCKS);
            });
            return;
        }

        int reserve = reserveBlocks(stack);
        if (reserve > 0) {
            updateData(stack, tag -> {
                tag.putInt(RESERVE_BLOCKS, reserve - 1);
                tag.putFloat(CONNECTOR_SPEED, AUTONOMOUS_AIR_SPEED);
                tag.putBoolean(POWERED, true);
            });
            return;
        }

        int remainder = Math.clamp(data(stack).getInt(AIR_REMAINDER), 0, 999);
        int total = remainder + airCostPerBlockMilli(stack);
        int airToConsume = total / 1_000;
        int newRemainder = total % 1_000;
        int remaining = airToConsume;
        for (ItemStack backtank : BacktankUtil.getAllWithAir(player)) {
            if (remaining <= 0) {
                break;
            }
            int amount = Math.min(remaining, BacktankUtil.getAir(backtank));
            BacktankUtil.consumeAir(player, backtank, amount);
            remaining -= amount;
        }
        updateData(stack, tag -> {
            tag.putInt(AIR_REMAINDER, newRemainder);
            tag.putBoolean(POWERED, hasEnoughBacktankAir(stack, player));
            tag.putFloat(CONNECTOR_SPEED, AUTONOMOUS_AIR_SPEED);
        });
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
