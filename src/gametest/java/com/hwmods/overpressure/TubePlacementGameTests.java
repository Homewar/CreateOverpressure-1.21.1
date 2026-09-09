package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Overpressure.MODID)
@PrefixGameTestTemplate(false)
// Loaded only by the dedicated GameTest run, which supplies the Minecraft runtime.
public class TubePlacementGameTests {
    private static final BlockPos START = new BlockPos(3, 5, 3);

    @GameTest(template = "routing_empty")
    public static void curveTrimOnlyAppearsAtExposedOrNodeEnds(GameTestHelper helper) {
        helper.setBlock(START, ModBlocks.CURVATURE_PNEUMATIC_TUBE.get());
        var level = helper.getLevel();
        var curve = (CurvaturePneumaticTubeEntity) level.getBlockEntity(helper.absolutePos(START));
        helper.assertTrue(curve.needsEndTrim(true) && curve.needsEndTrim(false), "Both open ends need trim");
        helper.setBlock(START.north(), ModBlocks.ITEM_PUMP.get());
        helper.assertTrue(curve.needsEndTrim(true), "Direct node connection needs trim");
        helper.setBlock(START.east(), ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState().setValue(PneumaticTubeBlock.WEST, true));
        helper.assertTrue(!curve.needsEndTrim(false), "A straight continuation already supplies trim");
        helper.setBlock(START.east(), ModBlocks.CURVATURE_PNEUMATIC_TUBE.get());
        var continuation = (CurvaturePneumaticTubeEntity) level.getBlockEntity(helper.absolutePos(START.east()));
        continuation.setCurve(new Vec3(0, 0.5, 0.5), new Vec3(0.3, 0.5, 0.5),
                new Vec3(0.7, 0.5, 0.5), new Vec3(1, 0.5, 0.5));
        helper.assertTrue(!curve.needsEndTrim(false) && !continuation.needsEndTrim(true), "Internal curve joins must have no trim");
        helper.setBlock(START.east(), Blocks.AIR);
        helper.assertTrue(curve.needsEndTrim(false), "Removing the continuation must restore trim");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void paintingStopsAt32TubesAndCostsOneDye(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState()
                .setValue(PneumaticTubeBlock.EAST, true).setValue(PneumaticTubeBlock.WEST, true);
        for (int i = 0; i < 34; i++) helper.setBlock(START.east(i), state);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().mayBuild = true;
        var dye = new ItemStack(net.minecraft.world.item.Items.RED_DYE, 3);
        BlockPos pos = helper.absolutePos(START);
        ModBlocks.PNEUMATIC_TUBE.get().useItemOn(dye, level.getBlockState(pos), level, pos, player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        int red = net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF;
        for (int i = 0; i < 34; i++) {
            var tube = (PneumaticTubeBlockEntity) level.getBlockEntity(pos.east(i));
            helper.assertTrue(tube.getTubeColor() == (i < 32 ? red : 0xFFFFFF), "Paint must stop at 32 tubes: " + i);
        }
        helper.assertTrue(dye.getCount() == 2, "A whole section costs one dye");
        ModBlocks.PNEUMATIC_TUBE.get().useItemOn(dye, level.getBlockState(pos), level, pos, player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(dye.getCount() == 2, "Painting an unchanged section costs nothing");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void paintingCrossesCurvesButStopsAtNodesAndPersists(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState()
                .setValue(PneumaticTubeBlock.EAST, true).setValue(PneumaticTubeBlock.WEST, true);
        helper.setBlock(START, state);
        helper.setBlock(START.east(), ModBlocks.CURVATURE_PNEUMATIC_TUBE.get().defaultBlockState()
                .setValue(PneumaticTubeBlock.EAST, true).setValue(PneumaticTubeBlock.WEST, true));
        helper.setBlock(START.east(2), ModBlocks.ITEM_PUMP.get().defaultBlockState().setValue(ItemPumpBlock.FACING, Direction.EAST));
        helper.setBlock(START.east(3), state);
        helper.setBlock(START.south(), state);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().mayBuild = true;
        BlockPos pos = helper.absolutePos(START);
        helper.assertTrue(TubePainting.paint(level, pos, player, 0xFF0000) == 2, "Paint must cross the curve and stop at the pump");
        helper.assertTrue(((PneumaticTubeBlockEntity) level.getBlockEntity(pos.east(3))).getTubeColor() == 0xFFFFFF,
                "Tube beyond the pump must keep its color");
        helper.assertTrue(((PneumaticTubeBlockEntity) level.getBlockEntity(pos.south())).getTubeColor() == 0xFFFFFF,
                "Touching unconnected tubes must keep their color");
        var curve = (PneumaticTubeBlockEntity) level.getBlockEntity(pos.east());
        var tag = new net.minecraft.nbt.CompoundTag();
        curve.write(tag, level.registryAccess(), false);
        curve.setTubeColor(0xFFFFFF);
        curve.read(tag, level.registryAccess(), false);
        helper.assertTrue(curve.getTubeColor() == 0xFF0000, "Curve color must survive saving and loading");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void creativePumpSpeedIsAdjustableAndSaved(GameTestHelper helper) {
        helper.setBlock(START, ModBlocks.CREATIVE_ITEM_PUMP.get());
        var pump = (ItemPumpBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(START));
        var speed = com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(
                helper.getLevel(), pump.getBlockPos(),
                com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE);
        helper.assertTrue(speed instanceof CreativePumpSpeedBehaviour, "Creative pump must expose speed settings");
        int fast = pump.getMoveTime();
        speed.setValue(64);
        helper.assertTrue(pump.getMoveTime() > fast && pump.isRunning(), "Lower speed must slow transport without requiring a drive");
        var tag = new net.minecraft.nbt.CompoundTag();
        speed.write(tag, helper.getLevel().registryAccess(), false);
        speed.setValue(256);
        speed.read(tag, helper.getLevel().registryAccess(), false);
        helper.assertTrue(speed.getValue() == 64, "Speed must survive saving and loading");
        speed.read(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess(), false);
        helper.assertTrue(speed.getValue() == 256 && pump.getMoveTime() == fast, "Old pumps must retain maximum speed");
        speed.setValue(-10);
        helper.assertTrue(speed.getValue() == 1, "Minimum speed must be bounded");
        speed.setValue(999);
        helper.assertTrue(speed.getValue() == 256, "Maximum speed must be bounded");
        helper.setBlock(START.east(3), ModBlocks.ITEM_PUMP.get());
        var ordinary = (ItemPumpBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(START.east(3)));
        helper.assertTrue(!ordinary.isRunning() && ordinary.getMoveTime() == 0, "Ordinary pumps must still require rotation");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void bendLimitsMeasureGeometryAtDifferentLengths(GameTestHelper helper) {
        for (double scale : new double[] { 1, 4, 12, 24 }) {
            var curve = new com.hwmods.overpressure.math.CubicBezier(Vec3.ZERO,
                    new Vec3(0.5523, 0, 0).scale(scale), new Vec3(1, 0, 0.4477).scale(scale),
                    new Vec3(1, 0, 1).scale(scale));
            helper.assertTrue(curve.satisfiesCurvatureLimits(32, 0.55),
                    "Scaling up a gentle bend must not make it too sharp: " + scale);
            helper.assertTrue(!curve.satisfiesTurnAngleLimit(16, 60), "A 90 degree segment still exceeds the turn limit");
        }
        var tight = new com.hwmods.overpressure.math.CubicBezier(Vec3.ZERO,
                new Vec3(0.11, 0, 0), new Vec3(0.2, 0, 0.09), new Vec3(0.2, 0, 0.2));
        helper.assertTrue(!tight.satisfiesCurvatureLimits(32, 0.55), "A genuinely tight bend must remain invalid");
        var cusp = new com.hwmods.overpressure.math.CubicBezier(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(1, 0, 0));
        helper.assertTrue(!cusp.satisfiesCurvatureLimits(32, 0.55), "A degenerate tangent must remain invalid");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void longGentleBendCanBeBuilt(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 1).normalize(), 64);
        fixture.item().setPlacementReach(fixture.player(), 28);
        var preview = fixture.preview();
        helper.assertTrue(preview.plan().valid(), "Long gentle bend must be valid: " + describe(helper, preview));
        helper.assertTrue(preview.plan().tubes().stream().anyMatch(tube -> tube.curveP0() != null),
                "Regression route must contain a bend");
        fixture.build();
        assertPlaced(helper, preview);
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void curveLeavesEmptyCornersAvailableForInteraction(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(START);
        var level = helper.getLevel();
        level.setBlock(pos, ModBlocks.CURVATURE_PNEUMATIC_TUBE.get().defaultBlockState(), 3);
        var state = level.getBlockState(pos);
        var shape = state.getShape(level, pos);
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        helper.assertTrue(shape.clip(origin.add(0.05, 2, 0.05), origin.add(0.05, -1, 0.05), pos) == null,
                "Empty corner of a bend must not intercept interaction");
        helper.assertTrue(shape.clip(origin.add(0.5, 2, 0.1), origin.add(0.5, -1, 0.1), pos) != null,
                "The visible pipe must remain selectable");
        helper.assertTrue(state.getCollisionShape(level, pos).clip(
                origin.add(0.05, 2, 0.05), origin.add(0.05, -1, 0.05), pos) == null,
                "Empty corner must not block movement");
        level.setBlock(pos.above(), ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState(), 3);
        var hit = level.clip(new net.minecraft.world.level.ClipContext(
                origin.add(0.1, 0.9, 0.5), origin.add(0.5, 2, 0.5),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        helper.assertTrue(hit.getBlockPos().equals(pos.above()), "A neighboring tube must be reachable through empty space");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void curveShapeFollowsGeometryChanges(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(START);
        var level = helper.getLevel();
        level.setBlock(pos, ModBlocks.CURVATURE_PNEUMATIC_TUBE.get().defaultBlockState(), 3);
        var tube = (CurvaturePneumaticTubeEntity) level.getBlockEntity(pos);
        var state = level.getBlockState(pos);
        var original = state.getCollisionShape(level, pos);
        tube.setCurve(new Vec3(0.5, 0, 0.5), new Vec3(0.5, 0.33, 0.5),
                new Vec3(0.5, 0.66, 0.5), new Vec3(0.5, 1, 0.5));
        var updated = state.getCollisionShape(level, pos);
        helper.assertTrue(updated != original, "Changing geometry must invalidate the shape cache");
        helper.assertTrue(Math.abs(updated.bounds().minY) < 1.0E-6 && Math.abs(updated.bounds().maxY - 1) < 1.0E-6,
                "Vertical bend must use its own geometry rather than a cached block-state shape");
        helper.assertTrue(updated == state.getCollisionShape(level, pos), "Repeated queries must reuse the shape");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void adjustedReachChangesPreviewAndPlacement(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 64);
        int initialCount = fixture.preview().plan().tubes().size();
        fixture.item().setPlacementReach(fixture.player(), 10);
        var longer = fixture.preview();
        helper.assertTrue(longer.plan().valid() && longer.plan().tubes().size() > initialCount, "Increasing reach must extend the preview");
        fixture.item().setPlacementReach(fixture.player(), 2);
        helper.assertTrue(fixture.preview().plan().tubes().size() < initialCount, "Decreasing reach must shorten the preview");
        fixture.item().setPlacementReach(fixture.player(), 10);
        fixture.build();
        assertPlaced(helper, longer);
        helper.assertTrue(fixture.stack().getCount() == 64 - longer.plan().tubes().size(), "Placement must use the adjusted preview length");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void reachIsBoundedAndSurvivesFailedBuild(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 1);
        var start = fixture.item().getSelectedStart(helper.getLevel(), fixture.player());
        fixture.item().setPlacementReach(fixture.player(), Integer.MIN_VALUE);
        helper.assertTrue(fixture.item().getPlacementReach(fixture.player(), start) == 2, "Reach has a lower bound");
        fixture.item().setPlacementReach(fixture.player(), Integer.MAX_VALUE);
        helper.assertTrue(fixture.item().getPlacementReach(fixture.player(), start) == TubePlacementTargeting.MAX_DISTANCE, "Reach has an upper bound");
        fixture.item().setPlacementReach(fixture.player(), 10);
        fixture.build();
        helper.assertTrue(fixture.item().getSelectedStart(helper.getLevel(), fixture.player()) != null, "Failed build keeps the anchor");
        helper.assertTrue(fixture.item().getPlacementReach(fixture.player(), start) == 10, "Failed build keeps the selected reach");
        fixture.player().setShiftKeyDown(true);
        fixture.build();
        helper.assertTrue(fixture.item().getPlacementReach(fixture.player(), start) == 6, "Cancellation resets the selected reach");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void aimSnapsToAllSixStartDirections(GameTestHelper helper) {
        for (Direction direction : Direction.values()) {
            Vec3 forward = Vec3.atLowerCornerOf(direction.getNormal());
            Vec3 aim = Vec3.atCenterOf(BlockPos.ZERO).add(forward.scale(6));
            var end = TubePlacementTargeting.resolve(BlockPos.ZERO, direction, null, aim, forward);
            helper.assertTrue(end.pos().equals(BlockPos.ZERO.relative(direction, 6)), "Straight aim must preserve the start axis: " + direction);
            helper.assertTrue(end.outgoing() == direction, "Straight outlet must face forward: " + direction);
        }
        var slight = TubePlacementTargeting.resolve(BlockPos.ZERO, Direction.EAST, null,
                new Vec3(6.5, 1.1, 1.2), new Vec3(1, 0, 0));
        helper.assertTrue(slight.pos().equals(new BlockPos(6, 0, 0)), "Small aim drift must stay straight");
        var vertical = TubePlacementTargeting.resolve(BlockPos.ZERO, Direction.EAST, null,
                new Vec3(4.5, 6.5, 1.5), new Vec3(1, 1, 0).normalize());
        helper.assertTrue(vertical.pos().getZ() == 0 && vertical.outgoing() == Direction.UP, "Vertical aim must select a vertical bend");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void freeStraightMatchesPreviewAndCanBeExtended(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 64);
        var preview = fixture.preview();
        helper.assertTrue(preview.plan().valid() && !preview.connected(), "Air must produce a valid free preview");
        int count = preview.plan().tubes().size();
        fixture.build();
        assertPlaced(helper, preview);
        helper.assertTrue(fixture.stack().getCount() == 64 - count, "Consume exactly the previewed number of tubes");
        helper.assertTrue(fixture.item().getSelectedStart(helper.getLevel(), fixture.player()) == null, "Successful build clears selection");
        fixture.item().useOn(new UseOnContext(fixture.player(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(preview.endPos()), preview.outgoing(), preview.endPos(), false)));
        helper.assertTrue(fixture.item().getSelectedStart(helper.getLevel(), fixture.player()) != null, "Free end must be selectable for the next section");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void freeHorizontalBendMatchesPreview(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 1).normalize(), 64);
        var preview = fixture.preview();
        helper.assertTrue(preview.plan().valid(), "Horizontal free bend must be valid: " + describe(helper, preview));
        helper.assertTrue(preview.plan().tubes().stream().anyMatch(tube -> tube.curveP0() != null), "Sideways aim must produce a curve");
        fixture.build();
        assertPlaced(helper, preview);
        helper.assertTrue(helper.getLevel().getBlockState(preview.endPos()).getBlock() == ModBlocks.PNEUMATIC_TUBE.get(), "A curved free route needs a regular terminal tube");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void freeVerticalBendMatchesPreview(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 1, 0).normalize(), 64);
        var preview = fixture.preview();
        helper.assertTrue(preview.plan().valid(), "Vertical free bend must be valid: " + describe(helper, preview));
        helper.assertTrue(preview.outgoing() == Direction.UP, "Vertical bend outlet points up");
        fixture.build();
        assertPlaced(helper, preview);
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void missingMaterialsPreserveSelection(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 1);
        helper.assertTrue(fixture.preview().plan().tubes().size() > 1, "Test requires more than one tube");
        fixture.build();
        helper.assertTrue(fixture.item().getSelectedStart(helper.getLevel(), fixture.player()) != null, "Failed build preserves the anchor");
        helper.assertTrue(fixture.stack().getCount() == 1, "Failed build consumes no items");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(START.east())).isAir(), "Failed build must not place a partial route");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void blockedRoutePreservesWorldAndSelection(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 64);
        helper.setBlock(START.east(), Blocks.STONE);
        helper.assertTrue(!fixture.preview().plan().valid(), "Obstructed route must have an invalid preview");
        fixture.build();
        helper.assertBlockPresent(Blocks.STONE, START.east());
        helper.assertTrue(fixture.stack().getCount() == 64, "Obstructed route consumes no items");
        helper.assertTrue(fixture.item().getSelectedStart(helper.getLevel(), fixture.player()) != null, "Obstructed route preserves the anchor");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void shiftCancelsInAir(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 64);
        fixture.player().setShiftKeyDown(true);
        fixture.build();
        helper.assertTrue(fixture.item().getSelectedStart(helper.getLevel(), fixture.player()) == null, "Shift-click in air cancels routing");
        helper.assertTrue(fixture.stack().getCount() == 64, "Cancellation consumes no items");
        helper.succeed();
    }

    @GameTest(template = "routing_empty")
    public static void existingTubeWinsOverFreePlacement(GameTestHelper helper) {
        Fixture fixture = selectStart(helper, new Vec3(1, 0, 0), 64);
        helper.setBlock(START.east(7), ModBlocks.PNEUMATIC_TUBE.get());
        var preview = fixture.preview();
        helper.assertTrue(preview.connected() && preview.plan().valid(), "Aim at an existing tube must snap to its port");
        fixture.build();
        assertPlaced(helper, preview);
        helper.succeed();
    }

    private static Fixture selectStart(GameTestHelper helper, Vec3 look, int count) {
        helper.setBlock(START, ModBlocks.PNEUMATIC_TUBE.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = false;
        ItemStack stack = new ItemStack(ModBlocks.PNEUMATIC_TUBE.get(), count);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 eye = Vec3.atCenterOf(helper.absolutePos(START.east()));
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        player.setXRot((float) -Math.toDegrees(Math.asin(look.y)));
        PneumaticTubeBlockItem item = (PneumaticTubeBlockItem) stack.getItem();
        BlockPos start = helper.absolutePos(START);
        item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(start), Direction.EAST, start, false)));
        helper.assertTrue(item.getSelectedStart(helper.getLevel(), player) != null, "First click selects an anchor");
        return new Fixture(player, item, stack);
    }

    private static void assertPlaced(GameTestHelper helper, PneumaticTubeBlockItem.PlacementPreview preview) {
        for (var tube : preview.plan().tubes()) {
            helper.assertTrue(helper.getLevel().getBlockState(tube.pos()).getBlock() == tube.state().getBlock(), "Built route must match preview at " + tube.pos());
            if (tube.curveP0() != null) {
                var entity = (CurvaturePneumaticTubeEntity) helper.getLevel().getBlockEntity(tube.pos());
                helper.assertTrue(entity.getP0().equals(tube.curveP0()) && entity.getP3().equals(tube.curveP3()), "Built curve endpoints must match the preview");
            }
        }
    }

    private static String describe(GameTestHelper helper, PneumaticTubeBlockItem.PlacementPreview preview) {
        return preview.plan().errorMessage() + ", end=" + preview.endPos().subtract(helper.absolutePos(START))
                + ", outlet=" + preview.outgoing() + ", occupied=" + preview.plan().tubes().stream()
                .filter(tube -> !helper.getLevel().getBlockState(tube.pos()).canBeReplaced())
                .map(tube -> tube.pos().subtract(helper.absolutePos(START)) + ":" + helper.getLevel().getBlockState(tube.pos()))
                .toList();
    }

    private record Fixture(Player player, PneumaticTubeBlockItem item, ItemStack stack) {
        PneumaticTubeBlockItem.PlacementPreview preview() {
            return item.previewPlacement(player.level(), player, item.getSelectedStart(player.level(), player));
        }
        void build() {
            item.use(player.level(), player, InteractionHand.MAIN_HAND);
        }
    }
}
