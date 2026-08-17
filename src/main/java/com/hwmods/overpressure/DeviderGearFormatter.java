package com.hwmods.overpressure;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;

import net.minecraft.network.chat.Component;

/** Marker formatter used to replace Create's linear value screen with the junction gate. */
public class DeviderGearFormatter extends ValueSettingsFormatter {
    public DeviderGearFormatter() {
        super(settings -> Component.translatable(DeviderGearBehaviour.getTranslationKey(settings.value())));
    }
}
