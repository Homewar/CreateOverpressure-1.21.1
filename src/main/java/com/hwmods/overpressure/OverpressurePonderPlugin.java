package com.hwmods.overpressure;

import com.simibubi.create.AllItems;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Registers Overpressure scenes for the Create Ponder system.
 *
 * The scenes are backed by structure files in
 * src/main/resources/assets/overpressure/ponder/*.nbt.
 */
public class OverpressurePonderPlugin implements PonderPlugin {

    private static final String MODID = Overpressure.MODID;

    @Override
    public String getModId() {
        return MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(rl("pneumatic_tube"), rl("pneumatic_connection"), rl("item_pump"))
                .addStoryBoard("curvature_tube", curvedTubeScene())
                .addStoryBoard("minimal_work", extractorScene())
                .addStoryBoard("with_pump", pumpUpgradeScene());
    }

    private ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void revealScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
    }

    private PonderStoryBoard curvedTubeScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("curvature_tube", "Curved Pneumatic Tubes");
            revealScene(scene, util);
            scene.idle(15);

            scene.overlay().showText(70)
                    .text("Curved pneumatic tubes let a route turn smoothly while staying connected.")
                    .independent();
            scene.idle(80);

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().fromTo(6, 1, 0, 6, 1, 6), 70);
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().fromTo(0, 1, 6, 6, 1, 6), 70);
            scene.overlay().showText(70)
                    .text("The curve section is treated as one continuous tube path.")
                    .independent();
            scene.idle(80);

            scene.markAsFinished();
        };
    }

    private PonderStoryBoard extractorScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("pneumatic_connection", "Pneumatic Extractors");
            revealScene(scene, util);
            scene.idle(15);

            scene.overlay().showText(70)
                    .text("Pneumatic connectors decide whether items leave or enter a container.")
                    .independent();
            scene.idle(80);

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(5, 1, 3), 60);
            scene.overlay().showText(70)
                    .text("Extractor mode pulls from the container into the tube line.")
                    .independent();
            scene.idle(80);

            scene.overlay().showOutline(PonderPalette.RED, new Object(),
                    util.select().position(1, 1, 3), 60);
            scene.overlay().showText(70)
                    .text("Insert mode sends items from the tube line into the container.")
                    .independent();
            scene.idle(80);

            scene.overlay().showText(70)
                    .text("Rotate the connector with a wrench to change the side and direction.")
                    .independent();
            scene.idle(80);

            BlockPos extractorPos = new BlockPos(5, 1, 3);
            scene.world().modifyBlockEntity(extractorPos, PneumaticConnectionBlockEntity.class,
                    connector -> connector.setFilter(AllItems.FILTER.asStack()));
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(extractorPos), 60);
            scene.overlay().showText(70)
                    .text("Place a filter into an extractor to choose which items it may pull from the container.")
                    .independent();
            scene.idle(80);

            scene.effects().indicateRedstone(extractorPos);
            scene.world().toggleRedstonePower(util.select().position(extractorPos));
            scene.world().modifyBlock(extractorPos,
                    state -> state.setValue(PneumaticConnectionBlock.POWERED, true), true);
            scene.overlay().showOutline(PonderPalette.RED, new Object(),
                    util.select().position(extractorPos), 60);
            scene.overlay().showText(70)
                    .text("A redstone signal disables new extraction and lights up the extractor pipe.")
                    .independent();
            scene.idle(80);

            scene.overlay().showText(70)
                    .text("Items already travelling through the tube line continue on to their destination.")
                    .independent();
            scene.idle(80);

            scene.markAsFinished();
        };
    }

    private PonderStoryBoard pumpUpgradeScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("item_pump", "Item Pump Upgrade");
            scene.configureBasePlate(0, 0, 5);
            scene.world().showSection(util.select().layer(0), Direction.UP);
            scene.idle(5);

            BlockPos pumpPos = new BlockPos(3, 1, 3);
            BlockPos cogPos = new BlockPos(3, 2, 4);

            BlockState tubeState = ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState()
                    .setValue(PneumaticTubeBlock.EAST, true)
                    .setValue(PneumaticTubeBlock.WEST, true)
                    .setValue(PneumaticTubeBlock.HAS_RIM, false)
                    .setValue(PneumaticTubeBlock.RIM, Direction.WEST);
            BlockState pumpState = ModBlocks.ITEM_PUMP.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST);

            scene.world().setBlock(pumpPos, tubeState, false);
            scene.world().showSection(util.select().fromTo(0, 1, 3, 6, 1, 3), Direction.DOWN);
            scene.idle(15);

            scene.overlay().showText(70)
                    .text("Start with a normal pneumatic tube line between two connectors.")
                    .independent();
            scene.idle(80);

            scene.world().replaceBlocks(util.select().position(pumpPos), pumpState, true);
            scene.idle(10);

            scene.overlay().showText(70)
                    .text("Replace the center tube with an Item Pump to speed the line up.")
                    .independent();
            scene.idle(80);

            scene.overlay().showText(70)
                    .text("By itself the pump is idle; its internal gear does not spin yet.")
                    .independent();
            scene.idle(80);

            ElementLink<WorldSectionElement> cog =
                    scene.world().showIndependentSection(util.select().position(cogPos), Direction.DOWN);
            scene.world().configureCenterOfRotation(cog, new Vec3(3.5, 2.5, 4.5));
            scene.idle(10);

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(cogPos), 60);
            scene.overlay().showText(70)
                    .text("The pump needs rotation from a cogwheel to start working.")
                    .independent();
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class, pump -> pump.setSpeed(16.0f));
            scene.world().rotateSection(cog, -384, 0, 0, 160);
            scene.idle(80);

            scene.overlay().showText(70)
                    .text("Slow rotation gives the pump a modest transfer speed.")
                    .independent();
            scene.idle(80);

            scene.overlay().showText(70)
                    .text("The faster the cogwheel turns, the faster items move through the line.")
                    .independent();
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class, pump -> pump.setSpeed(64.0f));
            scene.world().rotateSection(cog, -1536, 0, 0, 160);
            scene.idle(160);
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class, pump -> pump.setSpeed(0.0f));
            scene.idle(5);

            scene.markAsFinished();
        };
    }
}
