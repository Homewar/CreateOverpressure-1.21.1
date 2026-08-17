package com.hwmods.overpressure;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

public class DeviderGearBehaviour extends ScrollValueBehaviour {
    private static final String NBT_KEY = "GearConfiguration";
    private static final int ROUTING_COUNT = DeviderBlockEntity.RoutingSelection.values().length;
    private static final int CONFIGURATION_COUNT = DeviderBlockEntity.JunctionRole.values().length * ROUTING_COUNT;

    public DeviderGearBehaviour(Component label, SmartBlockEntity blockEntity, ValueBoxTransform slot) {
        super(label, blockEntity, slot);
        between(0, CONFIGURATION_COUNT - 1);
        value = encode(DeviderBlockEntity.JunctionRole.DIVIDER, DeviderBlockEntity.RoutingSelection.BALANCED);
        withFormatter(DeviderGearBehaviour::getShortLabel);
    }

    public DeviderBlockEntity.JunctionRole getRole() {
        return DeviderBlockEntity.JunctionRole.values()[value / ROUTING_COUNT];
    }

    public DeviderBlockEntity.RoutingSelection getRoutingSelection() {
        return DeviderBlockEntity.RoutingSelection.values()[value % ROUTING_COUNT];
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return new ValueSettingsBoard(
                label,
                CONFIGURATION_COUNT - 1,
                1,
                List.of(Component.empty()),
                new DeviderGearFormatter()
        );
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        int selected = Math.max(0, Math.min(CONFIGURATION_COUNT - 1, settings.value()));
        if (selected == value) {
            return;
        }
        setValue(selected);
        playFeedbackSound(this);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, value);
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt(NBT_KEY, value);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        int previous = value;
        if (tag.contains(NBT_KEY)) {
            value = Math.max(0, Math.min(CONFIGURATION_COUNT - 1, tag.getInt(NBT_KEY)));
        } else {
            value = readLegacyConfiguration(tag);
        }
        if (clientPacket && previous != value) {
            PneumaticTubeBlockEntity.invalidateClientPathAt(blockEntity.getLevel(), blockEntity.getBlockPos());
        }
    }

    public static int encode(
            DeviderBlockEntity.JunctionRole role,
            DeviderBlockEntity.RoutingSelection routing
    ) {
        return role.ordinal() * ROUTING_COUNT + routing.ordinal();
    }

    public static DeviderBlockEntity.JunctionRole decodeRole(int configuration) {
        return DeviderBlockEntity.JunctionRole.values()[configuration / ROUTING_COUNT];
    }

    public static DeviderBlockEntity.RoutingSelection decodeRouting(int configuration) {
        return DeviderBlockEntity.RoutingSelection.values()[configuration % ROUTING_COUNT];
    }

    public static String getTranslationKey(int configuration) {
        DeviderBlockEntity.JunctionRole role = decodeRole(configuration);
        DeviderBlockEntity.RoutingSelection routing = decodeRouting(configuration);
        return "overpressure.devider.gear."
                + role.name().toLowerCase(java.util.Locale.ROOT)
                + "."
                + routing.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String getShortLabel(int configuration) {
        return switch (decodeRouting(configuration)) {
            case LEFT_PRIORITY -> "L";
            case BALANCED -> "<>";
            case RIGHT_PRIORITY -> "R";
            case REDSTONE -> "RS";
            case REDSTONE_INVERTED -> "RS!";
        };
    }

    private static int readLegacyConfiguration(CompoundTag tag) {
        if (!tag.contains("JunctionRole")
                && !tag.contains("RoutingMode")
                && !tag.contains("AutomaticBranchMode")
                && !tag.contains("RedstoneDefaultBranch")) {
            return encode(
                    DeviderBlockEntity.JunctionRole.DIVIDER,
                    DeviderBlockEntity.RoutingSelection.BALANCED
            );
        }

        DeviderBlockEntity.JunctionRole role = tag.getInt("JunctionRole") == 1
                ? DeviderBlockEntity.JunctionRole.MERGER
                : DeviderBlockEntity.JunctionRole.DIVIDER;
        boolean redstone = tag.getInt("RoutingMode") == 1;
        DeviderBlockEntity.RoutingSelection routing;
        if (redstone) {
            routing = tag.getInt("RedstoneDefaultBranch") == 1
                    ? DeviderBlockEntity.RoutingSelection.REDSTONE_INVERTED
                    : DeviderBlockEntity.RoutingSelection.REDSTONE;
        } else {
            routing = switch (tag.getInt("AutomaticBranchMode")) {
                case 0 -> DeviderBlockEntity.RoutingSelection.LEFT_PRIORITY;
                case 2 -> DeviderBlockEntity.RoutingSelection.RIGHT_PRIORITY;
                default -> DeviderBlockEntity.RoutingSelection.BALANCED;
            };
        }
        return encode(role, routing);
    }
}
