package com.hwmods.overpressure;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final TagKey<Item> CREATE_PACKAGES = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create", "packages")
    );

    public static final ModConfigSpec.DoubleValue TUBE_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue PACKAGERS_ONLY;
    public static final ModConfigSpec.DoubleValue SOUND_VOLUME;

    static {
        BUILDER.push("pneumaticTubes");
        TUBE_SPEED_MULTIPLIER = BUILDER
                .comment(
                        "Global pneumatic tube speed multiplier.",
                        "2.0 is twice as fast; 0.5 is twice as slow."
                )
                .defineInRange("speedMultiplier", 1.0, 0.1, 10.0);
        PACKAGERS_ONLY = BUILDER
                .comment(
                        "When enabled, pneumatic connectors only work with Create Packagers",
                        "and only sealed packages can enter a tube network."
                )
                .define("packagersOnly", false);
        BUILDER.pop();

        SOUND_VOLUME = BUILDER
                .comment("Volume for all grind rail sounds.")
                .defineInRange("soundVolume", 0.5, 0.0, 1.0);
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int applyTubeSpeed(int moveTime) {
        if (moveTime <= 0) {
            return 0;
        }
        return Math.max(1, (int)Math.ceil(moveTime / TUBE_SPEED_MULTIPLIER.get()));
    }

    public static boolean canEnterTube(ItemStack stack) {
        return !PACKAGERS_ONLY.get() || stack.is(CREATE_PACKAGES);
    }

    private Config() {
    }
}
