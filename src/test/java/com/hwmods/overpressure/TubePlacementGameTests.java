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
public class TubePlacementGameTests {
    private static final BlockPos START = new BlockPos(3, 5, 3);

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
