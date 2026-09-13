package com.hwmods.overpressure;

import java.util.List;
import java.util.UUID;

import com.hwmods.overpressure.math.CubicBezier;
import com.hwmods.overpressure.tube.TubeSection;
import com.hwmods.overpressure.tube.TubeSectionStorage;
import com.hwmods.overpressure.transport.TransportLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Overpressure.MODID)
@PrefixGameTestTemplate(false)
public class TubeSectionGameTests {
    @GameTest(template = "routing_empty")
    public static void cargoCompletionRendersTheLastBlockBeforeDisappearing(GameTestHelper helper) {
        var level = helper.getLevel();
        UUID id = UUID.randomUUID(), otherId = UUID.randomUUID();
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        var animation = new com.hwmods.overpressure.tube.CargoAnimation();
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(2, new Vec3(1, 0, 0), new Vec3(1, 0, 0), null, 0));
        var other = new com.hwmods.overpressure.tube.CargoAnimation();
        other.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(2, new Vec3(0, 0, 1), new Vec3(1, 0, 0), null, 0));
        var old = java.util.Map.of(id, new com.hwmods.overpressure.tube.SectionCargoPayload.Visual(stack, animation),
                otherId, new com.hwmods.overpressure.tube.SectionCargoPayload.Visual(stack, other));
        CompoundTag packet = new CompoundTag(), entry = new CompoundTag();
        packet.putLong("Tick", 3); packet.putBoolean("Partial", true);
        entry.putUUID("Id", id); entry.put("Stack", stack.save(level.registryAccess())); entry.putBoolean("Finished", true);
        entry.putDouble("PositionX", 2); entry.putDouble("DirectionX", 1);
        var entries = new net.minecraft.nbt.ListTag(); entries.add(entry); packet.put("Cargo", entries);
        var updated = com.hwmods.overpressure.tube.SectionCargoPayload.updateVisuals(level, old, packet);
        var motion = updated.get(id).animation();
        helper.assertTrue(updated.get(otherId).animation() == other && !other.expired(3), "Completion of one cargo must not remove another");
        for (double tick = 2; tick < 3; tick += 0.05) {
            helper.assertTrue(!motion.expired(tick) && Math.abs(motion.at(tick).position().x - (tick - 1)) < 1.0E-8,
                    "Completed cargo must traverse the last block on its buffered timeline");
        }
        helper.assertTrue(motion.at(3).position().distanceToSqr(new Vec3(2, 0, 0)) < 1.0E-8 && motion.expired(3),
                "Animation must terminate at the exact outlet");
        var empty = new CompoundTag(); empty.putLong("Tick", 4); empty.put("Cargo", new net.minecraft.nbt.ListTag());
        var after = com.hwmods.overpressure.tube.SectionCargoPayload.updateVisuals(level, updated, empty);
        helper.assertTrue(after.get(id).animation() == motion && motion.expired(3), "Later snapshots must preserve the final segment and its removal time");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void dividerSideCurvesHaveClearPlacementInEveryOrientation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(16, 16, 16));
        var item = (PneumaticTubeBlockItem) ModBlocks.PNEUMATIC_TUBE_ITEM.get();
        for (var input : net.minecraft.core.Direction.values()) {
            var state = ModBlocks.DEVIDER.get().defaultBlockState().setValue(DeviderBlock.INPUT, input)
                    .setValue(DeviderBlock.FACING, input.getAxis().isVertical() ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.UP);
            level.setBlock(origin, state, 3);
            java.util.List<UUID> placed = new java.util.ArrayList<>();
            java.util.List<BlockPos> targets = new java.util.ArrayList<>();
            for (var side : java.util.List.of(DeviderBlock.getLeftOutputDirection(state), DeviderBlock.getRightOutputDirection(state))) {
                BlockPos target = origin.relative(input.getOpposite(), 6).relative(side, 6);
                targets.add(target);
                level.setBlock(target, ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState(), 3);
                var start = new PneumaticTubeBlockItem.CurveStart(origin, input.getOpposite(), side);
                var plan = item.planClientSection(level, start, target, side.getOpposite(), Vec3.atCenterOf(target));
                helper.assertTrue(plan.valid(), "Clear side connection must be placeable: input=" + input + ", side=" + side + ", error=" + plan.errorMessage());
                var section = com.hwmods.overpressure.tube.TubeSectionPlacement.fromPlan(plan.tubes());
                com.hwmods.overpressure.tube.TubeSections.put(level, section); placed.add(section.id());
            }
            for (UUID id : placed) com.hwmods.overpressure.tube.TubeSections.remove(level, id);
            for (BlockPos target : targets) level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(origin, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void filterSideCurvesHaveClearPlacementInEveryOrientation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(16, 16, 16));
        var item = (PneumaticTubeBlockItem) ModBlocks.PNEUMATIC_TUBE_ITEM.get();
        for (var input : net.minecraft.core.Direction.values()) {
            var state = ModBlocks.FILTER_PIPE.get().defaultBlockState().setValue(FilterPipeBlock.INPUT, input)
                    .setValue(FilterPipeBlock.FACING, input.getAxis().isVertical() ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.UP);
            level.setBlock(origin, state, 3);
            java.util.List<UUID> placed = new java.util.ArrayList<>();
            java.util.List<BlockPos> targets = new java.util.ArrayList<>();
            for (var side : java.util.List.of(FilterPipeBlock.getBranchDirection(state))) {
                BlockPos target = origin.relative(input.getOpposite(), 6).relative(side, 6);
                targets.add(target);
                level.setBlock(target, ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState(), 3);
                var start = new PneumaticTubeBlockItem.CurveStart(origin, input.getOpposite(), side);
                var plan = item.planClientSection(level, start, target, side.getOpposite(), Vec3.atCenterOf(target));
                helper.assertTrue(plan.valid(), "Clear side connection must be placeable: input=" + input + ", side=" + side + ", error=" + plan.errorMessage());
                var section = com.hwmods.overpressure.tube.TubeSectionPlacement.fromPlan(plan.tubes());
                helper.assertTrue(com.hwmods.overpressure.tube.SectionTransport.portPoint(level, origin, side).distanceToSqr(section.port(true).position()) < 1.0E-8
                        && com.hwmods.overpressure.tube.SectionTransport.portVector(level, origin, side).dot(section.port(true).outward()) < -0.99,
                        "Transport and placement must agree on the diagonal filter port");
                BlockPos obstruction = BlockPos.containing(section.geometry().pointAtDistance(section.geometry().length() / 2));
                level.setBlock(obstruction, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                helper.assertTrue(!com.hwmods.overpressure.tube.TubeSectionPlacement.canPlace(level, section, null), "A real obstacle must still block the side connection");
                level.setBlock(obstruction, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                com.hwmods.overpressure.tube.TubeSections.put(level, section); placed.add(section.id());
            }
            for (UUID id : placed) com.hwmods.overpressure.tube.TubeSections.remove(level, id);
            for (BlockPos target : targets) level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(origin, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void cargoAnimationStaysSmoothWithUnevenPacketArrival(GameTestHelper helper) {
        var animation = new com.hwmods.overpressure.tube.CargoAnimation();
        int nextTick = 0;
        double previous = 0;
        for (int frame = 0; frame <= 400; frame++) {
            double clientTime = frame / 10.0;
            while (nextTick + (nextTick % 4 == 0 ? 0 : 1) <= clientTime) {
                animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(nextTick,
                        new Vec3(nextTick * 0.125, 0, 0), new Vec3(1, 0, 0), null, 0));
                nextTick += 2;
            }
            double renderTime = clientTime - com.hwmods.overpressure.tube.CargoAnimation.RENDER_DELAY_TICKS;
            var pose = animation.at(renderTime);
            double expected = Math.max(0, renderTime) * 0.125;
            helper.assertTrue(pose != null && Math.abs(pose.position().x - expected) < 1.0E-8,
                    "Uneven packet arrival must not reset interpolation or pause constant motion at frame " + frame);
            helper.assertTrue(pose.position().x >= previous - 1.0E-8, "Cargo must not jump backwards when a packet arrives");
            previous = pose.position().x;
        }
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void cargoAnimationStopsAndKeepsItsTailUntilRemoval(GameTestHelper helper) {
        var animation = new com.hwmods.overpressure.tube.CargoAnimation();
        var direction = new Vec3(1, 0, 0);
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(0, Vec3.ZERO, direction, null, 0));
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(2, direction, direction, null, 0));
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(4, direction, direction, null, 0));
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(3, direction.scale(100), direction, null, 0));
        for (double time = 2; time < 10; time += 0.1)
            helper.assertTrue(animation.at(time).position().distanceToSqr(direction) < 1.0E-8,
                    "A stopped cargo must not extrapolate through the next queued item or accept stale snapshots");
        animation.removed(6);
        helper.assertTrue(!animation.expired(5.99) && animation.expired(6), "Removal must follow the render timeline, not packet arrival");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void cargoAnimationInterpolatesDistanceAlongCurve(GameTestHelper helper) {
        var section = makeSection(Vec3.ZERO);
        var animation = new com.hwmods.overpressure.tube.CargoAnimation();
        double length = section.geometry().length();
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(0, section.port(true).position(), new Vec3(1, 0, 0), section.id(), 0));
        animation.add(new com.hwmods.overpressure.tube.CargoAnimation.Sample(2, section.port(false).position(), new Vec3(0, 0, 1), section.id(), length));
        var middle = animation.at(1);
        helper.assertTrue(section.id().equals(middle.section()) && Math.abs(middle.distance() - length / 2) < 1.0E-8,
                "Curved cargo must interpolate its distance along geometry, preserving the section identity");
        helper.assertTrue(section.geometry().pointAtDistance(middle.distance()).distanceTo(middle.position()) > 0.1,
                "Following the curve must differ from cutting across its chord");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void mixedRouteRespectsDividerPriority(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 8, 16)), pump = source.east(), divider = pump.east();
        level.setBlock(source, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(divider, ModBlocks.DEVIDER.get().defaultBlockState()
                .setValue(DeviderBlock.INPUT, net.minecraft.core.Direction.WEST), 3);
        var junction = (DeviderBlockEntity) level.getBlockEntity(divider);
        var branches = junction.orderedBranchPorts();
        BlockPos preferred = null;
        for (int branch = 0; branch < 2; branch++) {
            BlockPos neighbor = branches.get(branch);
            var direction = net.minecraft.core.Direction.getNearest(neighbor.getX() - divider.getX(), neighbor.getY() - divider.getY(), neighbor.getZ() - divider.getZ());
            BlockPos end = divider.east(8).relative(direction, 5);
            BlockPos target = end.relative(direction, branch == 0 ? 3 : 0);
            if (branch == 0) preferred = target;
            level.setBlock(target, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                    .setValue(PneumaticConnectionBlock.FACING, direction)
                    .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.INSERT), 3);
            level.setBlock(target.relative(direction), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
            var tube = (PneumaticTubeBlock) ModBlocks.PNEUMATIC_TUBE.get();
            for (int i = 2; branch == 0 && i >= 0; i--) {
                BlockPos pos = end.relative(direction, i);
                level.setBlock(pos, tube.getTubeStateForPlacement(level, pos), 3);
            }
            Vec3 from = com.hwmods.overpressure.tube.SectionTransport.portPoint(level, divider, direction);
            Vec3 tangent = com.hwmods.overpressure.tube.SectionTransport.portVector(level, divider, direction);
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
            Vec3 to = Vec3.atCenterOf(end).subtract(normal.scale(0.5));
            var section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                    new CubicBezier(from, from.add(tangent.scale(4)), to.subtract(normal.scale(3)), to), 0xFFFFFF, false)), 10);
            com.hwmods.overpressure.tube.TubeSections.put(level, section);
        }
        boolean old = Config.PACKAGERS_ONLY.get(); Config.PACKAGERS_ONLY.set(false);
        try {
            var route = com.hwmods.overpressure.tube.SectionTransport.find(level, source, net.minecraft.core.Direction.EAST,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            helper.assertTrue(route != null && preferred.equals(route.target()), "Divider preference must take precedence over the shorter alternative route");
        } finally { Config.PACKAGERS_ONLY.set(old); }
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void sectionDismantlingReturnsMaterialsOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int mode = 0; mode < 3; mode++) {
            Vec3 from = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(5, 8, 5 + mode * 8))).add(0, 0.5, 0.5);
            var section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                    new CubicBezier(from, from.add(1, 0, 0), from.add(2, 0, 0), from.add(3, 0, 0)), 0xFFFFFF, false)), 131);
            com.hwmods.overpressure.tube.TubeSections.put(level, section);
            var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SectionRefund"));
            player.gameMode.changeGameModeForPlayer(mode == 2 ? net.minecraft.world.level.GameType.CREATIVE : net.minecraft.world.level.GameType.SURVIVAL);
            Vec3 eye = from.add(1, 0, -2);
            player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z); player.setYRot(0); player.setXRot(0);
            boolean wrench = mode == 1;
            if (wrench) {
                player.setShiftKeyDown(true);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
            }
            for (int i = 0; i < 2; i++) com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), !wrench,
                    net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(com.hwmods.overpressure.tube.TubeSections.get(level).get(section.id()) == null, "Dismantling must remove the section");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, section.geometry().bounds().inflate(2)).stream()
                    .filter(item -> item.getItem().is(ModBlocks.PNEUMATIC_TUBE_ITEM.get())).toList();
            int dropped = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
            int stored = player.getInventory().countItem(ModBlocks.PNEUMATIC_TUBE_ITEM.get());
            helper.assertTrue(dropped == (mode == 0 ? 131 : 0) && stored == (wrench ? 131 : 0),
                    "Survival must drop materials, wrench must return them, creative must not duplicate them; mode=" + mode);
            helper.assertTrue(drops.stream().allMatch(item -> item.getItem().getCount() <= item.getItem().getMaxStackSize() && item.hasPickUpDelay()),
                    "Drops must respect stack size and normal pickup delay");
        }
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void straightTubeJoinsBothSectionEndsInEveryDirection(GameTestHelper helper) {
        var level = helper.getLevel();
        int index = 0;
        for (var direction : net.minecraft.core.Direction.values()) {
            BlockPos target = helper.absolutePos(new BlockPos(6 + (index % 3) * 10, 8, 7 + (index / 3) * 15));
            index++;
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
            Vec3 side = direction.getAxis() == net.minecraft.core.Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
            Vec3 end = Vec3.atCenterOf(target).subtract(normal.scale(0.5));
            Vec3 from = end.subtract(normal.scale(3)).subtract(side.scale(3));
            for (boolean reverse : new boolean[]{false, true}) {
                var curve = new CubicBezier(from, from.add(side.scale(2)), end.subtract(normal.scale(2)), end);
                if (reverse) curve = new CubicBezier(curve.p3(), curve.p2(), curve.p1(), curve.p0());
                var section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(curve, 0xFFFFFF, false)), 5);
                com.hwmods.overpressure.tube.TubeSections.put(level, section);
                var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StraightJoin"));
                player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                player.setShiftKeyDown(true);
                Vec3 eye = end.add(normal.scale(2));
                player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
                Vec3 look = normal.scale(-1);
                player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
                player.setXRot((float) -Math.toDegrees(Math.asin(look.y)));
                var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(ModBlocks.PNEUMATIC_TUBE_ITEM.get(), 2));
                com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), false, hand);
                var state = level.getBlockState(target);
                helper.assertTrue(state.is(ModBlocks.PNEUMATIC_TUBE.get()), "Straight tube must be placed at section end: " + direction + ", reverse=" + reverse);
                helper.assertTrue(state.getValue(PneumaticTubeBlock.getConnectionProperty(direction.getOpposite())), "Straight tube must connect toward section");
                helper.assertTrue(((PneumaticTubeBlockEntity) level.getBlockEntity(target)).canTravelTo(level, direction.getOpposite()), "Joined end must allow transport");
                helper.assertTrue(state.getValue(PneumaticTubeBlock.HAS_RIM), "A straight/curved joint must render its connecting collar");
                com.hwmods.overpressure.tube.TubeSections.remove(level, section.id());
                level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "routing_empty", timeoutTicks = 160)
    public static void connectorExtractsDirectlyIntoSectionBeforePump(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 5, 3));
        BlockPos pump = source.offset(8, 0, 8), target = pump.south();
        level.setBlock(source.west(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(source, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
        level.setBlock(target.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(target, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.INSERT), 3);
        Vec3 from = Vec3.atCenterOf(source).add(0.5, 0, 0), to = Vec3.atCenterOf(pump).add(0, 0, -0.5);
        var section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(new CubicBezier(from, from.add(5, 0, 0),
                to.add(0, 0, -5), to), 0xFFFFFF, false)), 12);
        com.hwmods.overpressure.tube.TubeSections.put(level, section);
        var input = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(source.west());
        input.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        boolean old = Config.PACKAGERS_ONLY.get(); Config.PACKAGERS_ONLY.set(false);
        helper.runAfterDelay(100, () -> {
            try {
                var output = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(target.south());
                int count = 0;
                for (int i = 0; i < output.getContainerSize(); i++) if (output.getItem(i).is(net.minecraft.world.item.Items.DIAMOND)) count += output.getItem(i).getCount();
                helper.assertTrue(input.isEmpty() && count == 7, "Connector must extract and deliver through a section before the pump; input="
                        + input.getItem(0) + ", output=" + count + ", source=" + level.getBlockState(source)
                        + ", target=" + level.getBlockState(target) + ", route="
                        + com.hwmods.overpressure.tube.SectionTransport.find(level, source, net.minecraft.core.Direction.EAST,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)));
                helper.assertTrue(level.getBlockState(source.east()).isAir(), "Direct section extraction must need no intermediate tube block");
                helper.succeed();
            } finally { Config.PACKAGERS_ONLY.set(old); }
        });
    }
    @GameTest(template = "routing_empty")
    public static void independentSectionCanBePaintedBuiltAgainstAndBroken(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 5, 8));
        Vec3 from = Vec3.atLowerCornerOf(base).add(0, 0.5, 0.5), to = from.add(2, 0, 0);
        var section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(new CubicBezier(from, from.add(0.66, 0, 0), from.add(1.33, 0, 0), to), 0xFFFFFF, false)), 2);
        com.hwmods.overpressure.tube.TubeSections.put(level, section);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SectionInteractions"));
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Vec3 eye = from.add(0.5, 0, -2);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z); player.setYRot(0); player.setXRot(0);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_DYE, 2));
        com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), false, hand);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && com.hwmods.overpressure.tube.TubeSections.get(level).get(section.id()).spans().getFirst().color() != 0xFFFFFF,
                "A visible section must accept dye without a block owner");
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOW_INK_SAC, 2));
        com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), false, hand);
        helper.assertTrue(player.getMainHandItem().getCount() == 1 && com.hwmods.overpressure.tube.TubeSections.get(level).get(section.id()).spans().getFirst().glowing(), "A visible section must accept glow ink");
        player.setShiftKeyDown(true);
        player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.STONE, 2));
        com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), false, hand);
        helper.assertTrue(level.getBlockState(base.north()).is(net.minecraft.world.level.block.Blocks.STONE) && player.getMainHandItem().getCount() == 1,
                "Shift placement must use the visible section surface");
        com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), true, hand);
        helper.assertTrue(com.hwmods.overpressure.tube.TubeSections.get(level).get(section.id()) != null, "A wall must block section interactions");
        level.setBlock(base.north(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        com.hwmods.overpressure.tube.TubeSectionInteractions.interact(player, section.id(), true, hand);
        helper.assertTrue(com.hwmods.overpressure.tube.TubeSections.get(level).get(section.id()) == null && level.getBlockState(base).isAir(), "Breaking must remove the independent section");
        int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, section.geometry().bounds().inflate(2)).stream()
                .filter(item -> item.getItem().is(ModBlocks.PNEUMATIC_TUBE_ITEM.get())).mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertTrue(dropped == 2, "Breaking must refund exactly the section material cost");
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, section.geometry().bounds().inflate(2)).stream()
                .filter(item -> item.getItem().is(ModBlocks.PNEUMATIC_TUBE_ITEM.get())).allMatch(net.minecraft.world.entity.item.ItemEntity::hasPickUpDelay),
                "Broken tubes must have the normal pickup delay");
        helper.succeed();
    }
    @GameTest(template = "routing_empty")
    public static void legacySectionMigratesWithoutSupportsOrLostCargo(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(3, 5, 3)), b = a.east();
        level.setBlock(a, ModBlocks.CURVATURE_PNEUMATIC_TUBE.get().defaultBlockState(), 3);
        level.setBlock(b, ModBlocks.CURVATURE_PNEUMATIC_TUBE.get().defaultBlockState(), 3);
        var first = (CurvaturePneumaticTubeEntity) level.getBlockEntity(a);
        var second = (CurvaturePneumaticTubeEntity) level.getBlockEntity(b);
        UUID id = UUID.randomUUID(); first.setSectionId(id); second.setSectionId(id);
        first.setCurve(new Vec3(10, 0.5, 0.5), new Vec3(10.33, 0.5, 0.5), new Vec3(10.66, 0.5, 0.5), new Vec3(11, 0.5, 0.5));
        second.setCurve(new Vec3(10, 0.5, 0.5), new Vec3(10.33, 0.5, 0.5), new Vec3(10.66, 0.5, 0.5), new Vec3(11, 0.5, 0.5));
        first.setTubeColor(0xFF2233); second.setTubeGlowing(true);
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
        var snapshot = new MovingTubeItem(stack, List.of(a, b), null);
        com.hwmods.overpressure.transport.TubeTransportManager.get(level).restore(a, snapshot);
        helper.assertTrue(com.hwmods.overpressure.tube.LegacyTubeMigration.migrate(level, a), "Migration must complete");
        helper.assertTrue(level.getBlockState(a).isAir() && level.getBlockState(b).isAir(), "Migration must remove every support block");
        var section = com.hwmods.overpressure.tube.TubeSections.get(level).get(id);
        helper.assertTrue(section != null && section.materialCost() == 2 && section.spans().getFirst().color() == 0xFF2233
                && section.spans().getLast().glowing(), "Migration must retain identity, cost, dye and glow");
        var saved = com.hwmods.overpressure.tube.SectionTransport.get(level).save(new CompoundTag(), level.registryAccess());
        helper.assertTrue(cargoForSection(saved, id) == 1, "Migration must retain cargo once in section storage");
        helper.assertTrue(com.hwmods.overpressure.transport.TubeTransportManager.get(level).getSnapshot(a) == null, "Legacy cargo ownership must be released");
        helper.assertTrue(com.hwmods.overpressure.tube.LegacyTubeMigration.migrate(level, a), "Repeated migration must be harmless");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void sectionRouteDeliversCargoAfterSaveAndReload(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 5, 3)), pump = source.east();
        BlockPos target = pump.offset(8, 0, 8);
        level.setBlock(source, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.EXTRACT), 3);
        level.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(target, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.INSERT), 3);
        level.setBlock(target.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        Vec3 from = Vec3.atCenterOf(pump).add(0.5, 0, 0), to = Vec3.atCenterOf(target).add(0, 0, -0.5);
        TubeSection section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                new CubicBezier(from, from.add(5, 0, 0), to.add(0, 0, -5), to), 0xFFFFFF, false)), 12);
        com.hwmods.overpressure.tube.TubeSections.put(level, section);
        boolean old = Config.PACKAGERS_ONLY.get();
        Config.PACKAGERS_ONLY.set(false);
        try {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
            var route = com.hwmods.overpressure.tube.SectionTransport.find(level, source, net.minecraft.core.Direction.EAST, stack);
            helper.assertTrue(route != null && target.equals(route.target()), "Route must traverse a standalone section directly into its connector");
            var transport = com.hwmods.overpressure.tube.SectionTransport.get(level);
            helper.assertTrue(transport.accept(level, stack, route), "Pump must accept cargo into section route");
            for (int i = 0; i < 8; i++) transport.tick(level);
            var saved = transport.save(new CompoundTag(), level.registryAccess());
            var loaded = com.hwmods.overpressure.tube.SectionTransport.load(saved, level.registryAccess());
            level.getDataStorage().set("overpressure_section_cargo", loaded);
            for (int i = 0; i < 180; i++) loaded.tick(level);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(target.south());
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(net.minecraft.world.item.Items.DIAMOND)) count += chest.getItem(slot).getCount();
            helper.assertTrue(count == 7, "Reloaded transport must deliver exactly one cargo stack");
            helper.assertTrue(cargoForSection(loaded.save(new CompoundTag(), level.registryAccess()), section.id()) == 0, "Delivered cargo must be removed from saved storage");
        } finally { Config.PACKAGERS_ONLY.set(old); }
        helper.succeed();
    }
    @GameTest(template = "routing_empty")
    public static void sectionRouteCrossesStraightTubesAfterSaveAndReload(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 5, 3)), pump = source.east();
        BlockPos target = pump.offset(8, 0, 8);
        level.setBlock(source, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.EXTRACT), 3);
        level.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(target, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.INSERT), 3);
        level.setBlock(target.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        var tube = (PneumaticTubeBlock) ModBlocks.PNEUMATIC_TUBE.get();
        for (BlockPos pos : List.of(pump.east(), target.north()))
            level.setBlock(pos, tube.getTubeStateForPlacement(level, pos), 3);
        Vec3 from = Vec3.atCenterOf(pump.east()).add(0.5, 0, 0), to = Vec3.atCenterOf(target.north()).add(0, 0, -0.5);
        TubeSection section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                new CubicBezier(from, from.add(5, 0, 0), to.add(0, 0, -5), to), 0xFFFFFF, false)), 12);
        com.hwmods.overpressure.tube.TubeSections.put(level, section);
        boolean old = Config.PACKAGERS_ONLY.get();
        Config.PACKAGERS_ONLY.set(false);
        try {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
            var route = com.hwmods.overpressure.tube.SectionTransport.find(level, source, net.minecraft.core.Direction.EAST, stack);
            helper.assertTrue(route != null && target.equals(route.target()), "Route must traverse a standalone section through straight tubes on both ends");
            var transport = com.hwmods.overpressure.tube.SectionTransport.get(level);
            helper.assertTrue(transport.accept(level, stack, route), "Pump must accept cargo into section route");
            for (int i = 0; i < 8; i++) transport.tick(level);
            var saved = transport.save(new CompoundTag(), level.registryAccess());
            var loaded = com.hwmods.overpressure.tube.SectionTransport.load(saved, level.registryAccess());
            level.getDataStorage().set("overpressure_section_cargo", loaded);
            for (int i = 0; i < 180; i++) loaded.tick(level);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(target.south());
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(net.minecraft.world.item.Items.DIAMOND)) count += chest.getItem(slot).getCount();
            helper.assertTrue(count == 7, "Reloaded transport must deliver exactly one cargo stack");
            helper.assertTrue(cargoForSection(loaded.save(new CompoundTag(), level.registryAccess()), section.id()) == 0, "Delivered cargo must be removed from saved storage");
        } finally { Config.PACKAGERS_ONLY.set(old); }
        helper.succeed();
    }
    @GameTest(template = "routing_empty")
    public static void mixedRouteReleasesCargoFromOpenStraightEnd(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 5, 3)), pump = source.east();
        BlockPos target = pump.offset(8, 0, 8);
        level.setBlock(source, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.EXTRACT), 3);
        level.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(target, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.INSERT), 3);
        level.setBlock(target.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        var tube = (PneumaticTubeBlock) ModBlocks.PNEUMATIC_TUBE.get();
        for (BlockPos pos : List.of(pump.east(), target.north()))
            level.setBlock(pos, tube.getTubeStateForPlacement(level, pos), 3);
        level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        Vec3 from = Vec3.atCenterOf(pump.east()).add(0.5, 0, 0), to = Vec3.atCenterOf(target.north()).add(0, 0, -0.5);
        TubeSection section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                new CubicBezier(from, from.add(5, 0, 0), to.add(0, 0, -5), to), 0xFFFFFF, false)), 12);
        com.hwmods.overpressure.tube.TubeSections.put(level, section);
        boolean old = Config.PACKAGERS_ONLY.get();
        Config.PACKAGERS_ONLY.set(false);
        try {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
            var route = com.hwmods.overpressure.tube.SectionTransport.find(level, source, net.minecraft.core.Direction.EAST, stack);
            helper.assertTrue(route != null && route.target() == null, "A mixed route must reach an open straight end");
            var transport = com.hwmods.overpressure.tube.SectionTransport.get(level);
            helper.assertTrue(transport.accept(level, stack, route), "Pump must accept cargo into section route");
            for (int i = 0; i < 8; i++) transport.tick(level);
            var saved = transport.save(new CompoundTag(), level.registryAccess());
            var loaded = com.hwmods.overpressure.tube.SectionTransport.load(saved, level.registryAccess());
            level.getDataStorage().set("overpressure_section_cargo", loaded);
            for (int i = 0; i < 180; i++) loaded.tick(level);
            int count = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(target).inflate(2)).stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(count == 7, "An open straight end must release exactly the transported stack");
            helper.assertTrue(cargoForSection(loaded.save(new CompoundTag(), level.registryAccess()), section.id()) == 0, "Delivered cargo must be removed from saved storage");
        } finally { Config.PACKAGERS_ONLY.set(old); }
        helper.succeed();
    }
    @GameTest(template = "routing_empty")
    public static void movingCargoFollowsStraightTubeAddedAtCurveEnd(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 5, 3)), pump = source.east();
        BlockPos target = pump.offset(8, 0, 8);
        level.setBlock(source, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.EXTRACT), 3);
        level.setBlock(pump, ModBlocks.CREATIVE_ITEM_PUMP.get().defaultBlockState()
                .setValue(ItemPumpBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(target, ModBlocks.PNEUMATIC_CONNECTION.get().defaultBlockState()
                .setValue(PneumaticConnectionBlock.FACING, net.minecraft.core.Direction.SOUTH)
                .setValue(PneumaticConnectionBlock.MODE, PneumaticConnectionBlock.ConnectionMode.INSERT), 3);
        level.setBlock(target.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        var tube = (PneumaticTubeBlock) ModBlocks.PNEUMATIC_TUBE.get();
        for (BlockPos pos : List.of(pump.east()))
            level.setBlock(pos, tube.getTubeStateForPlacement(level, pos), 3);
        Vec3 from = Vec3.atCenterOf(pump.east()).add(0.5, 0, 0), to = Vec3.atCenterOf(target.north()).add(0, 0, -0.5);
        TubeSection section = new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(
                new CubicBezier(from, from.add(5, 0, 0), to.add(0, 0, -5), to), 0xFFFFFF, false)), 12);
        com.hwmods.overpressure.tube.TubeSections.put(level, section);
        boolean old = Config.PACKAGERS_ONLY.get();
        Config.PACKAGERS_ONLY.set(false);
        try {
            var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
            var route = com.hwmods.overpressure.tube.SectionTransport.find(level, source, net.minecraft.core.Direction.EAST, stack);
            helper.assertTrue(route != null && route.target() == null, "Initial route must end at the open curve end");
            var transport = com.hwmods.overpressure.tube.SectionTransport.get(level);
            helper.assertTrue(transport.accept(level, stack, route), "Pump must accept cargo into section route");
            for (int i = 0; i < 8; i++) transport.tick(level);
            level.setBlock(target.north(), tube.getTubeStateForPlacement(level, target.north()), 3);
            var saved = transport.save(new CompoundTag(), level.registryAccess());
            var loaded = com.hwmods.overpressure.tube.SectionTransport.load(saved, level.registryAccess());
            level.getDataStorage().set("overpressure_section_cargo", loaded);
            for (int i = 0; i < 180; i++) loaded.tick(level);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(target.south());
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(net.minecraft.world.item.Items.DIAMOND)) count += chest.getItem(slot).getCount();
            helper.assertTrue(count == 7, "Reloaded transport must deliver exactly one cargo stack");
            helper.assertTrue(cargoForSection(loaded.save(new CompoundTag(), level.registryAccess()), section.id()) == 0, "Delivered cargo must be removed from saved storage");
        } finally { Config.PACKAGERS_ONLY.set(old); }
        helper.succeed();
    }
    @GameTest(template = "routing_empty")
    public static void independentSectionPersistsWithoutBlocks(GameTestHelper helper) {
        Vec3 origin = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(3, 5, 3)));
        TubeSection section = makeSection(origin);
        TubeSectionStorage storage = new TubeSectionStorage();
        storage.put(section);
        CompoundTag saved = storage.save(new CompoundTag(), helper.getLevel().registryAccess());
        TubeSectionStorage loaded = TubeSectionStorage.load(saved, helper.getLevel().registryAccess());
        TubeSection restored = loaded.get(section.id());
        helper.assertTrue(restored != null && restored.materialCost() == 24, "Section identity and cost must persist");
        helper.assertTrue(restored.spans().equals(section.spans()), "Geometry and individual span appearance must persist");
        for (var frame : restored.geometry().frames()) {
            BlockPos pos = BlockPos.containing(frame.center());
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Storing a section must not create hidden blocks");
        }
        helper.assertTrue(loaded.intersecting(restored.geometry().bounds()).contains(restored), "Spatial index must be restored");
        loaded.remove(section.id());
        helper.assertTrue(loaded.intersecting(restored.geometry().bounds()).isEmpty(), "Removing section must remove spatial entries");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void independentSectionHasContinuousCollisionAndDistance(GameTestHelper helper) {
        TubeSection section = makeSection(new Vec3(0, 0, 0));
        var geometry = section.geometry();
        double step = geometry.length() / 400;
        Vec3 previous = geometry.pointAtDistance(0);
        for (int i = 1; i <= 400; i++) {
            Vec3 point = geometry.pointAtDistance(i * step);
            double moved = point.distanceTo(previous);
            helper.assertTrue(moved > step * 0.99 && moved <= step * 1.0001, "Distance sampling must maintain speed through long bends");
            helper.assertTrue(geometry.boxes().stream().anyMatch(box -> box.contains(point)), "Collision must cover the whole curve");
            helper.assertTrue(geometry.clip(point.add(0, 2, 0), point.add(0, -2, 0)).isPresent(), "The whole visible curve must be pickable");
            previous = point;
        }
        Vec3 empty = new Vec3(0, 0, 20);
        helper.assertTrue(geometry.boxes().stream().noneMatch(box -> box.intersects(new AABB(empty, empty.add(0.1, 0.1, 0.1)))),
                "Empty space inside curve bounds must stay free");
        helper.assertTrue(section.port(true).position().equals(geometry.pointAtDistance(0)), "Start must belong to section geometry");
        helper.assertTrue(section.port(false).position().equals(geometry.pointAtDistance(geometry.length())), "End must belong to section geometry");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void sectionTransportIdentityDoesNotAliasWorldCells(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        TransportLocation first = new TransportLocation.Section(id, true);
        TransportLocation second = new TransportLocation.Section(id, false);
        helper.assertTrue(first.equals(TransportLocation.load(first.save())), "Section transport identity must persist");
        helper.assertTrue(!first.equals(second), "Opposite entrances must remain distinct");
        helper.assertTrue(!first.equals(new TransportLocation.Block(BlockPos.ZERO)), "A section must never be addressed as a world cell");
        helper.succeed();
    }

    private static int cargoForSection(CompoundTag data, UUID id) {
        int count = 0;
        var entries = data.getList("Cargo", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var route = entries.getCompound(i).getList("Route", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int j = 0; j < route.size(); j++) {
                var hop = route.getCompound(j);
                if (hop.hasUUID("Section") && hop.getUUID("Section").equals(id)) { count++; break; }
            }
        }
        return count;
    }

    private static TubeSection makeSection(Vec3 origin) {
        CubicBezier curve = new CubicBezier(origin, origin.add(14, 0, 0), origin.add(20, 0, 6), origin.add(20, 0, 20));
        List<CubicBezier> parts = curve.splitEqually(2);
        return new TubeSection(UUID.randomUUID(), List.of(new TubeSection.Span(parts.get(0), 0xFF3344, true),
                new TubeSection.Span(parts.get(1), 0xFFFFFF, false)), 24);
    }
}
