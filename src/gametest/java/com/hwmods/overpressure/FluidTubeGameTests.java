package com.hwmods.overpressure;

import java.util.*;
import com.hwmods.overpressure.tube.*;
import com.hwmods.overpressure.math.CubicBezier;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Overpressure.MODID)
@PrefixGameTestTemplate(false)
public class FluidTubeGameTests {
    private static PneumaticTubeBlockItem.Planner fluidPlanner() {
        return Objects.requireNonNull(PneumaticTubeBlockItem.planner(AllBlocks.FLUID_PIPE.asStack()));
    }

    private static TubeSection curve(BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from).add(0.5, 0, 0), b = Vec3.atCenterOf(to).add(0, 0, -0.5);
        return new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                new CubicBezier(a, a.add(3, 0, 0), b.add(0, 0, -3), b), 0xFFFFFF, false)), 10, true);
    }
    @GameTest(template = "routing_empty", timeoutTicks = 40)
    public static void copperPipeSelectsCurveStartInsteadOfExtendingIt(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pipePos = helper.absolutePos(new BlockPos(4, 5, 4));
        var state = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction direction : Direction.values())
            state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                    direction.getAxis() == Direction.Axis.X);
        level.setBlock(pipePos, state, 3);

        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FluidCurveClick"));
        var stack = AllBlocks.FLUID_PIPE.asStack();
        var planner = fluidPlanner();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var context = new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pipePos).add(0.5, 0, 0),
                        Direction.EAST, pipePos, false));

        var result = planner.useOn(context);
        var selected = planner.getSelectedStart(level, player);
        helper.assertTrue(result.consumesAction() && selected != null
                        && selected.pos().equals(pipePos) && selected.direction() == Direction.EAST,
                "The copper pipe must select the clicked copper-pipe face for curved routing");
        helper.assertTrue(level.getBlockState(pipePos.east()).isAir(),
                "Selecting a curve endpoint must not place another straight copper pipe");
        helper.succeed();
    }
    @GameTest(template = "routing_empty", timeoutTicks = 40)
    public static void straightFluidRouteIsAChainOfCopperPipeBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(3, 5, 4));
        BlockPos target = start.east(6);
        level.setBlock(start, AllBlocks.FLUID_PIPE.getDefaultState(), 3);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), 3);

        var planner = fluidPlanner();
        var plan = planner.planClientSection(level, new PneumaticTubeBlockItem.CurveStart(start, Direction.EAST),
                target, Direction.WEST, Vec3.atCenterOf(target).add(-0.5, 0, 0));

        helper.assertTrue(plan.valid() && plan.tubes().size() == 5,
                "A straight six-block connection must plan five individual pipe blocks");
        helper.assertTrue(plan.tubes().stream().allMatch(tube -> tube.curveP0() == null
                        && tube.state().is(AllBlocks.FLUID_PIPE.get())),
                "Every straight fluid-route element must be a regular Create copper pipe");
        helper.assertTrue(TubeSectionPlacement.fromPlan(plan.tubes()) == null,
                "A straight fluid route must not become one standalone long section");
        helper.succeed();
    }
    @GameTest(template = "routing_empty", timeoutTicks = 40)
    public static void freeFluidRouteKeepsRimOnLastPipe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(3, 5, 4));
        var startState = AllBlocks.FLUID_PIPE.getDefaultState()
                .setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST), true)
                .setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST), true);
        level.setBlock(start, startState, 3);

        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FluidFreeEnd"));
        Vec3 eye = Vec3.atCenterOf(start.east());
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.setYRot(-90);
        player.setXRot(0);
        var planner = fluidPlanner();
        var stack = AllBlocks.FLUID_PIPE.asStack();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        planner.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(start).add(0.5, 0, 0),
                        Direction.EAST, start, false)));
        var selected = planner.getSelectedStart(level, player);
        helper.assertTrue(selected != null, "Copper pipe must select a free-route start");
        planner.setPlacementReach(player, 6);

        var preview = planner.previewPlacement(level, player, selected);
        var tip = preview.plan().tubes().getLast().state();
        helper.assertTrue(preview.plan().valid() && tip.is(AllBlocks.FLUID_PIPE.get()),
                "Free fluid route must end with a regular copper pipe");
        helper.assertTrue(tip.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST))
                        && tip.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST)),
                "Last copper pipe must keep its outward arm so the end rim is visible");
        helper.succeed();
    }
    @GameTest(template = "routing_empty", timeoutTicks = 80)
    public static void copperPipeBuildsAndRefundsFluidCurve(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos start = helper.absolutePos(new BlockPos(4, 8, 4)), end = start.offset(6, 0, 6);
        level.setBlock(start, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(end, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        helper.runAfterDelay(5, () -> {
            var player = new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "FluidBuilder"));
            player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            var stack = AllBlocks.FLUID_PIPE.asStack(); stack.setCount(64); player.setItemInHand(hand, stack);
            var planner = PneumaticTubeBlockItem.planner(stack);
            planner.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(start).add(0.5, 0, 0), Direction.EAST, start, false)));
            helper.assertTrue(planner.getSelectedStart(level, player) != null, "Copper pipe must select a fluid node");
            Vec3 eye = Vec3.atCenterOf(end).add(0, 0, -2);
            player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z); player.setYRot(0); player.setXRot(0);
            var preview = planner.previewPlacement(level, player, planner.getSelectedStart(level, player));
            helper.assertTrue(preview.plan().valid(), "Fluid preview must allow the clear bend: " + preview.plan().errorMessage());
            planner.use(level, player, hand);
            TubeSection built = TubeSections.get(level).sections().stream().filter(section -> section.isFluid()
                    && section.port(true).position().distanceToSqr(Vec3.atCenterOf(start).add(0.5, 0, 0)) < 1.0E-8).findFirst().orElse(null);
            helper.assertTrue(built != null && stack.getCount() == 64 - built.materialCost(), "Building must consume copper pipes and create a distinct fluid section");
            helper.assertTrue(planner.getSelectedStart(level, player) == null && level.getBlockState(start.east()).isAir(), "Built curve must have no support blocks");
            Vec3 hit = built.geometry().pointAtDistance(built.geometry().length() / 2);
            player.setPos(hit.x, hit.y + 2 - player.getEyeHeight(), hit.z); player.setXRot(90);
            TubeSectionInteractions.interact(player, built.id(), true, hand);
            int drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, built.geometry().bounds().inflate(2)).stream()
                    .filter(e -> e.getItem().is(AllBlocks.FLUID_PIPE.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(TubeSections.get(level).get(built.id()) == null && drops == built.materialCost(), "Breaking fluid curves must return copper pipes");
            helper.succeed();
        });
    }
    @GameTest(template = "routing_empty", timeoutTicks = 240)
    public static void createPumpPullsWaterThroughCurveAndStopsWithoutRotation(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos source = helper.absolutePos(new BlockPos(4, 8, 4)), pump = source.offset(6, 0, 6), target = pump.south();
        level.setBlock(source, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.SOUTH), 3);
        TubeSections.put(level, curve(source, pump));
        helper.runAfterDelay(5, () -> level.getCapability(Capabilities.FluidHandler.BLOCK, source, Direction.EAST)
                .fill(new FluidStack(Fluids.WATER, 6000), IFluidHandler.FluidAction.EXECUTE));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, target, Direction.NORTH).getFluidInTank(0).isEmpty(), "Stopped pump must not transfer fluid");
            var be = (PumpBlockEntity) level.getBlockEntity(pump); be.setSpeed(32); be.onSpeedChanged(0);
            be.updatePipesOnSide(Direction.NORTH); be.updatePipesOnSide(Direction.SOUTH);
        });
        helper.runAfterDelay(70, () -> {
            var be = (PumpBlockEntity) level.getBlockEntity(pump); be.setSpeed(0); be.onSpeedChanged(32);
        });
        helper.runAfterDelay(90, () -> {
            var output = level.getCapability(Capabilities.FluidHandler.BLOCK, target, Direction.NORTH);
            int amount = output.getFluidInTank(0).getAmount();
            helper.assertTrue(amount > 0 && amount < 6000, "Pump must pull fluid through the curved inlet");
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(output.getFluidInTank(0).getAmount() == amount, "Fluid must stop when rotation stops");
                int input = level.getCapability(Capabilities.FluidHandler.BLOCK, source, Direction.EAST).getFluidInTank(0).getAmount();
                helper.assertTrue(input + amount == 6000, "Pulling must conserve fluid"); helper.succeed();
            });
        });
    }
    @GameTest(template = "routing_empty", timeoutTicks = 240)
    public static void createPumpTransfersWaterThroughStandaloneCurve(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pump = helper.absolutePos(new BlockPos(5, 5, 5)), target = pump.offset(6, 0, 6);
        level.setBlock(pump.west(), AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), 3);
        var section = curve(pump, target); TubeSections.put(level, section);
        helper.runAfterDelay(5, () -> {
            var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST);
            helper.assertTrue(input != null, "Tank must expose a fluid handler");
            input.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            var be = (PumpBlockEntity) level.getBlockEntity(pump);
            be.setSpeed(64); be.onSpeedChanged(0);
            be.updatePipesOnSide(Direction.EAST); be.updatePipesOnSide(Direction.WEST);
        });
        helper.runAfterDelay(180, () -> {
            var output = level.getCapability(Capabilities.FluidHandler.BLOCK, target, Direction.NORTH);
            var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST);
            int out = output.getFluidInTank(0).getAmount(), in = input.getFluidInTank(0).getAmount();
            helper.assertTrue(out > 0 && out + in == 1000, "Create pump must move water without loss; output=" + out + ", input=" + in);
            helper.assertTrue(level.getBlockState(pump.east()).isAir(), "Curve must not install a hidden endpoint block");
            helper.succeed();
        });
    }
    @GameTest(template = "routing_empty", timeoutTicks = 240)
    public static void twoCreatePumpsTransferAcrossCurve(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pump = helper.absolutePos(new BlockPos(5, 5, 5)), target = pump.offset(6, 0, 6);
        level.setBlock(pump.west(), AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(target, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(target.south(), AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), 3);
        var section = curve(pump, target); TubeSections.put(level, section);
        helper.runAfterDelay(5, () -> {
            var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST);
            helper.assertTrue(input != null, "Tank must expose a fluid handler");
            input.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            var be = (PumpBlockEntity) level.getBlockEntity(pump);
            var second = (PumpBlockEntity) level.getBlockEntity(target);
            second.setSpeed(64); second.onSpeedChanged(0);
            second.updatePipesOnSide(Direction.NORTH); second.updatePipesOnSide(Direction.SOUTH);
            be.setSpeed(64); be.onSpeedChanged(0);
            be.updatePipesOnSide(Direction.EAST); be.updatePipesOnSide(Direction.WEST);
        });
        helper.runAfterDelay(180, () -> {
            var output = level.getCapability(Capabilities.FluidHandler.BLOCK, target.south(), Direction.NORTH);
            var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST);
            int out = output.getFluidInTank(0).getAmount(), in = input.getFluidInTank(0).getAmount();
            helper.assertTrue(out > 0 && out + in == 1000, "Create pump must move water without loss; output=" + out + ", input=" + in);
            helper.assertTrue(level.getBlockState(pump.east()).isAir(), "Curve must not install a hidden endpoint block");
            helper.succeed();
        });
    }
    @GameTest(template = "routing_empty", timeoutTicks = 240)
    public static void createPumpTransfersThroughCopperPipesAndCurve(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pump = helper.absolutePos(new BlockPos(5, 5, 5)), target = pump.offset(6, 0, 6);
        level.setBlock(pump.west(), AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), 3);
        var straight = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction direction : Direction.values()) straight = straight.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction), false);
        level.setBlock(pump.east(), straight
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST, true)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, true), 3);
        level.setBlock(target.north(), straight
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, true)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, true), 3);
        var section = curve(pump.east(), target.north());
        helper.runAfterDelay(5, () -> {
            var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST);
            helper.assertTrue(input != null, "Tank must expose a fluid handler");
            var be = (PumpBlockEntity) level.getBlockEntity(pump);
            be.setSpeed(64); be.onSpeedChanged(0);
            be.updatePipesOnSide(Direction.EAST); be.updatePipesOnSide(Direction.WEST);
        });
        // This is the player-facing case: the copper pipe and the pump are
        // already active when the curved section is placed.
        helper.runAfterDelay(40, () -> {
            TubeSections.put(level, section);
            helper.assertTrue(FluidTubeBridge.at(level, pump.east(2), Direction.WEST) != null,
                    "Placing a curve beside an active pipe must expose its fluid endpoint");
        });
        helper.runAfterDelay(50, () -> level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST)
                .fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE));
        helper.runAfterDelay(180, () -> {
            var output = level.getCapability(Capabilities.FluidHandler.BLOCK, target, Direction.NORTH);
            var input = level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST);
            int out = output.getFluidInTank(0).getAmount(), in = input.getFluidInTank(0).getAmount();
            helper.assertTrue(out > 0 && out + in == 1000, "Create pump must move water without loss; output=" + out + ", input=" + in
                    + ", sourcePipe=" + level.getBlockState(pump.east()) + ", targetPipe=" + level.getBlockState(target.north())
                    + ", bridge=" + FluidTubeBridge.at(level, pump.east(2), Direction.WEST).fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.SIMULATE)
                    + ", flow=" + com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, pump.east()).getFlow(Direction.EAST));
            helper.assertTrue(level.getBlockState(pump.east(2)).isAir(), "Curve must not install a hidden endpoint block");
            helper.succeed();
        });
    }
    @GameTest(template = "routing_empty", timeoutTicks = 240)
    public static void playerPlannedFluidCurveTransfersWater(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pump = helper.absolutePos(new BlockPos(5, 5, 5)), target = pump.offset(6, 0, 6);
        BlockPos sourcePipe = pump.east(), targetPipe = target.north();
        level.setBlock(pump.west(), AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST), 3);
        var pipe = AllBlocks.FLUID_PIPE.getDefaultState();
        for (Direction direction : Direction.values()) pipe = pipe.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(direction), false);
        // The route itself must open the previously closed faces of ordinary
        // copper pipes; a player does not preconfigure those block states.
        level.setBlock(sourcePipe, pipe.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, true), 3);
        level.setBlock(targetPipe, pipe.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, true), 3);
        var planner = fluidPlanner();
        var plan = planner.planClientSection(level, new PneumaticTubeBlockItem.CurveStart(sourcePipe, Direction.EAST),
                targetPipe, Direction.NORTH, Vec3.atCenterOf(targetPipe).add(0, 0, -0.5));
        helper.assertTrue(plan.valid(), "The route produced by the copper-pipe item must be valid: " + plan.errorMessage());
        var raw = TubeSectionPlacement.fromPlan(plan.tubes());
        helper.assertTrue(raw != null, "The player route must produce a standalone section");
        TubeSections.put(level, new TubeSection(raw.id(), raw.spans(), raw.materialCost(), true));
        helper.assertTrue(level.getBlockState(sourcePipe).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST)
                        && level.getBlockState(targetPipe).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH),
                "Placing the curve must open both attached copper-pipe faces");
        helper.runAfterDelay(5, () -> {
            level.getCapability(Capabilities.FluidHandler.BLOCK, pump.west(), Direction.EAST)
                    .fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            var entity = (PumpBlockEntity) level.getBlockEntity(pump);
            entity.setSpeed(64); entity.onSpeedChanged(0);
            entity.updatePipesOnSide(Direction.EAST); entity.updatePipesOnSide(Direction.WEST);
        });
        helper.runAfterDelay(180, () -> {
            int output = level.getCapability(Capabilities.FluidHandler.BLOCK, target, Direction.NORTH).getFluidInTank(0).getAmount();
            helper.assertTrue(output > 0, "Water must pass through the route built by the player item");
            helper.succeed();
        });
    }
    @GameTest(template = "routing_empty", timeoutTicks = 80)
    public static void fluidCurvePersistsAndSeparatesItemPorts(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos start = helper.absolutePos(new BlockPos(4, 5, 4)), target = start.offset(6, 0, 6);
        level.setBlock(target, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        TubeSection section = curve(start, target); TubeSections.put(level, section);
        helper.assertTrue(TubeSection.load(section.save()).isFluid(), "Fluid kind must persist");
        helper.assertTrue(!TubeSections.connects(level, start, Direction.EAST), "Fluid curves must not connect to item routes");
        helper.runAfterDelay(5, () -> {
            var bridge = FluidTubeBridge.at(level, start.east(), Direction.WEST);
            helper.assertTrue(bridge != null, "Virtual endpoint must exist without a block");
            var water = new FluidStack(Fluids.WATER, 500);
            helper.assertTrue(bridge.fill(water, IFluidHandler.FluidAction.SIMULATE) == 500, "Simulation must accept tank capacity");
            helper.assertTrue(bridge.getFluidInTank(0).isEmpty(), "Simulation must not create fluid");
            helper.assertTrue(bridge.fill(water, IFluidHandler.FluidAction.EXECUTE) == 500, "Bridge must fill target tank");
            helper.assertTrue(bridge.drain(200, IFluidHandler.FluidAction.EXECUTE).getAmount() == 200, "Bridge must support reverse extraction");
            TubeSections.remove(level, section.id());
            helper.assertTrue(bridge.drain(100, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Cached endpoint must stop working after section removal");
            helper.succeed();
        });
    }
}
