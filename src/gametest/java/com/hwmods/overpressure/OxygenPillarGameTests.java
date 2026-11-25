package com.hwmods.overpressure;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.armor.BacktankUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

@GameTestHolder(Overpressure.MODID)
@PrefixGameTestTemplate(false)
public class OxygenPillarGameTests {
    @GameTest(template = "routing_empty")
    public static void wallPillarKeepsItsMountFace(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos wall = new BlockPos(3, 5, 3);
        helper.setBlock(wall, Blocks.STONE);
        Direction face = Direction.EAST;
        BlockPos pos = helper.absolutePos(wall.relative(face));
        helper.assertTrue(OxygenPillarEntity.place(level, pos, face, false), "The pillar should attach beside the wall");
        var pillar = level.getEntitiesOfClass(OxygenPillarEntity.class, new AABB(pos)).getFirst();
        var loaded = new OxygenPillarEntity(ModEntities.OXYGEN_PILLAR.get(), level);
        loaded.load(pillar.saveWithoutId(new CompoundTag()));
        helper.assertTrue(loaded.mountFace() == face, "The wall attachment must survive a save and load");

        for (Direction direction : Direction.values()) {
            var up = direction.getRotation().transform(new Vector3f(0, 1, 0));
            var normal = direction.getNormal();
            helper.assertTrue(up.distance(normal.getX(), normal.getY(), normal.getZ()) < 0.0001,
                    "The model and port must point away from " + direction + " mounts");
        }
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void divingSuitUsesPillarAir(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tank = AllItems.COPPER_BACKTANK.asStack();
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.COPPER_DIVING_HELMET.asStack());
        player.setItemSlot(EquipmentSlot.CHEST, tank);

        BlockPos water = new BlockPos(3, 6, 3);
        helper.setBlock(water, Blocks.WATER);
        BlockPos waterPos = helper.absolutePos(water);
        player.setPos(waterPos.getX() + 0.5, waterPos.getY() + 0.5 - player.getEyeHeight(), waterPos.getZ() + 0.5);
        var pillar = new OxygenPillarEntity(ModEntities.OXYGEN_PILLAR.get(), level);
        pillar.setPos(player.getX() + 2, player.getY(), player.getZ());
        level.addFreshEntity(pillar);

        helper.setBlock(water, Blocks.WATER);
        player.baseTick();
        tank.set(AllDataComponents.BACKTANK_AIR, 0);
        var standalone = NeoForge.EVENT_BUS.post(new LivingBreatheEvent(player, false, 1, 0));
        helper.assertTrue(!standalone.canBreathe(), "A standalone pillar should not supply air");

        BlockPos connector = new BlockPos(5, 5, 5);
        BlockPos pump = connector.south();
        helper.setBlock(connector, ModBlocks.PNEUMATIC_CONNECTOR.get());
        helper.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, Direction.NORTH));

        // Allow the connector to refresh, then check on Create's consumption tick.
        long delay = 40 - Math.floorMod(level.getGameTime(), 20);
        helper.runAfterDelay(delay, () -> {
            helper.setBlock(water, Blocks.WATER);
            player.baseTick();
            helper.assertTrue(player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()), "The player's helmet should be submerged");
            tank.set(AllDataComponents.BACKTANK_AIR, 60);
            var near = NeoForge.EVENT_BUS.post(new LivingBreatheEvent(player, false, 1, 0));
            helper.assertTrue(near.canBreathe() && BacktankUtil.getAir(tank) == 60, "The pillar should supply air without draining the tank");
            var supply = PneumaticAirNetwork.findPillarSupply(level, player.position());
            helper.assertTrue(supply != null && supply.entryPillar() == pillar,
                    "The breathing hose should use the pillar that leads to the pump");

            tank.set(AllDataComponents.BACKTANK_AIR, 0);
            var empty = NeoForge.EVENT_BUS.post(new LivingBreatheEvent(player, false, 1, 0));
            helper.assertTrue(empty.canBreathe(), "A powered pillar should also supply an empty suit");

            helper.setBlock(pump, Blocks.AIR);
            tank.set(AllDataComponents.BACKTANK_AIR, 60);
            var disconnected = NeoForge.EVENT_BUS.post(new LivingBreatheEvent(player, false, 1, 0));
            helper.assertTrue(disconnected.canBreathe() && BacktankUtil.getAir(tank) == 59,
                    "Create should resume consuming air when the connected pump disappears");
            helper.succeed();
        });
    }
}
