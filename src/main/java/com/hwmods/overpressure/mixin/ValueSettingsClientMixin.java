package com.hwmods.overpressure.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hwmods.overpressure.DeviderGearFormatter;
import com.hwmods.overpressure.DeviderGearScreen;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;

import net.minecraft.core.BlockPos;

@Mixin(value = ValueSettingsClient.class, remap = false)
public class ValueSettingsClientMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/core/BlockPos;Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsBoard;Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsBehaviour$ValueSettings;Ljava/util/function/Consumer;I)Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsScreen;"
            )
    )
    private ValueSettingsScreen overpressure$createDeviderGearScreen(
            BlockPos pos,
            ValueSettingsBoard board,
            ValueSettings settings,
            Consumer<ValueSettings> onHover,
            int networkId
    ) {
        if (board.formatter() instanceof DeviderGearFormatter) {
            return new DeviderGearScreen(pos, board, settings, onHover, networkId);
        }
        return new ValueSettingsScreen(pos, board, settings, onHover, networkId);
    }
}
