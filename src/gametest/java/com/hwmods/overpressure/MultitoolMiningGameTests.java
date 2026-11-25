package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Overpressure.MODID)
@PrefixGameTestTemplate(false)
public class MultitoolMiningGameTests {
    @GameTest(template = "routing_empty")
    public static void miningProgressTracksPressureAndSpeedSetting(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 5, 3);
        helper.setBlock(target, Blocks.DIRT);
        var state = helper.getBlockState(target);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        ItemStack tool = ModBlocks.PNEUMATIC_MULTITOOL_CORE.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);

        float lowPressure = progress(helper, target, state, player, tool, 10.0F, 100, true);
        float mediumPressure = progress(helper, target, state, player, tool, 15.0F, 100, true);
        float highPressure = progress(helper, target, state, player, tool, 20.0F, 100, true);
        float lowSetting = progress(helper, target, state, player, tool, 20.0F, 10, true);
        float tank = progress(helper, target, state, player, tool, 20.0F, 100, false);
        helper.assertTrue(lowPressure > 0 && lowPressure < mediumPressure && mediumPressure < highPressure,
                "Mining progress should increase with tube speed");
        helper.assertTrue(lowSetting > 0 && lowSetting < highPressure,
                "The speed slider should slow down mining");
        helper.assertTrue(Math.abs(lowPressure - 0.063F) < 0.0001F, "Minimum flow should keep the previous mining rate");
        helper.assertTrue(highPressure == 1.0F && tank < 1.0F,
                "Only maximum network flow at full throttle should break instantly");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void pumpRpmSetsMultitoolFlow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 5, 3);
        helper.setBlock(pos, ModBlocks.ITEM_PUMP.get());
        var pump = (ItemPumpBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        pump.setSpeed(127);
        helper.assertTrue(pump.getMultitoolFlowSpeed() == 0, "A pump below 128 RPM should not power the tool");
        pump.setSpeed(128);
        helper.assertTrue(pump.getMultitoolFlowSpeed() == 10, "128 RPM should supply 10 blocks per second");
        pump.setSpeed(256);
        helper.assertTrue(pump.getMultitoolFlowSpeed() == 20, "256 RPM should supply maximum flow");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void connectedToolRaisesCreateStress(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 5, 3);
        helper.setBlock(pos, ModBlocks.ITEM_PUMP.get());
        var pump = (ItemPumpBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        pump.setSpeed(128);
        pump.setNetwork(pump.getBlockPos().asLong());
        var network = pump.getOrCreateNetwork();
        float baseline = pump.calculateStressApplied();
        float extra = Config.MULTITOOL_PUMP_EXTRA_STRESS.get().floatValue();
        helper.assertTrue(network.getActualStressOf(pump) == baseline * 128, "Idle pump should have its base load");

        pump.requestToolLoad();
        pump.requestToolLoad();
        helper.assertTrue(network.getActualStressOf(pump) == (baseline + extra) * 128,
                "A connected tool should update the Create network without stacking duplicate demand");
        helper.assertTrue(pump.getPotentialMultitoolFlowSpeed() == 10.0F,
                "Pump should remain the selected source during overload");
        helper.runAfterDelay(ItemPumpBlockEntity.TOOL_LOAD_GRACE_TICKS + 1, () -> {
            helper.assertTrue(pump.calculateStressApplied() == baseline,
                    "The extra load should expire after the tool disconnects");
            helper.succeed();
        });
    }

    @GameTest(template = "routing_empty")
    public static void unpoweredMultitoolCannotStartMining(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 5, 3);
        helper.setBlock(target, Blocks.DIRT);
        var level = helper.getLevel();
        var pos = helper.absolutePos(target);
        var state = level.getBlockState(pos);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        var tool = ModBlocks.PNEUMATIC_MULTITOOL_CORE.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        CustomData.update(DataComponents.CUSTOM_DATA, tool, root -> {
            CompoundTag data = new CompoundTag();
            data.putBoolean("DrillInstalled", true);
            root.put("OverpressureMultitool", data);
        });

        var item = ModBlocks.PNEUMATIC_MULTITOOL_CORE.get();
        helper.assertTrue(!item.canAttackBlock(state, level, pos, player), "Even creative mining needs pressure");
        helper.assertTrue(state.getDestroyProgress(player, level, pos) == 0.0F, "An empty tool should make no progress");

        CustomData.update(DataComponents.CUSTOM_DATA, tool, root ->
                root.getCompound("OverpressureMultitool").putInt("ReserveBlocks", 1));
        helper.assertTrue(item.canAttackBlock(state, level, pos, player), "The internal reserve should still allow mining");
        helper.succeed();
    }

    private static float progress(GameTestHelper helper, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.entity.player.Player player,
            ItemStack tool, float speed, int setting, boolean connected) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, root -> {
            CompoundTag data = new CompoundTag();
            data.putBoolean("DrillInstalled", true);
            data.putBoolean("Powered", true);
            data.putBoolean("InConnectorRange", connected);
            data.putFloat("ConnectorSpeed", speed);
            root.put("OverpressureMultitool", data);
        });
        PneumaticMultitoolItem.setSpeedSetting(tool, setting);
        return state.getDestroyProgress(player, helper.getLevel(), helper.absolutePos(pos));
    }
}
