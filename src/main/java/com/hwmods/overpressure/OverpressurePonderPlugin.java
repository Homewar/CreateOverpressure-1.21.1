package com.hwmods.overpressure;

import java.util.List;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;

import net.createmod.catnip.math.Pointing;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        helper.forComponents(rl("pneumatic_tube"))
                .addStoryBoard("curvature_tube", curvedTubeScene())
                .addStoryBoard("tube_flow", pumpRequiredScene());

        helper.forComponents(rl("item_pump"))
                .addStoryBoard("item_pump", pumpSpeedScene());

        helper.forComponents(rl("pneumatic_connection"))
                .addStoryBoard("pneumatic_connection", connectorScene());

        helper.forComponents(ResourceLocation.fromNamespaceAndPath("create", "packager"))
                .addStoryBoard("packagers", packagerScene());

        helper.forComponents(rl("valve"))
                .addStoryBoard("valve_pounder", valveScene());

        helper.forComponents(rl("clog_sensor"))
                .addStoryBoard("flow_sensor_pounder", flowSensorScene());
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
            scene.configureBasePlate(0, 0, 7);
            scene.world().showSection(util.select().layer(0), Direction.UP);
            scene.idle(5);
            scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
            scene.idle(15);

            scene.overlay().showText(70)
                    .text("A curved section is a visual shell and may have no collision in some places.")
                    .independent();
            scene.idle(80);

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().fromTo(6, 1, 0, 6, 1, 6), 70);
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().fromTo(0, 1, 6, 6, 1, 6), 70);
            scene.overlay().showText(70)
                    .text("The actual path is formed by invisible tube blocks arranged at an angle.")
                    .independent();
            scene.idle(80);

            scene.overlay().showText(70)
                    .text("The entire structure behaves as one continuous tube section.")
                    .independent();
            scene.idle(80);

            scene.markAsFinished();
        };
    }

    private PonderStoryBoard connectorScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("pneumatic_connection", "Pneumatic Connectors");
            scene.configureBasePlate(0, 0, 7);
            scene.world().showSection(util.select().layer(0), Direction.UP);
            scene.idle(5);

            BlockPos pumpPos = new BlockPos(3, 1, 3);
            BlockPos sourceConnector = new BlockPos(5, 1, 3);
            BlockPos targetConnector = new BlockPos(1, 1, 3);
            List<BlockPos> transportPath = List.of(
                    new BlockPos(4, 1, 3),
                    pumpPos,
                    new BlockPos(2, 1, 3)
            );
            List<BlockPos> tubeOwners = List.of(transportPath.get(2), transportPath.get(0));
            ItemStack transportedStack = new ItemStack(Items.IRON_BLOCK);

            BlockState pumpState = ModBlocks.ITEM_PUMP.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST);
            scene.world().setBlock(pumpPos, pumpState, false);
            scene.world().showSection(util.select().fromTo(0, 1, 3, 6, 1, 3), Direction.DOWN);
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class,
                    pump -> pump.setSpeed(32.0f));
            scene.idle(15);

            scene.overlay().showText(70)
                    .text("Pneumatic connectors link a tube line to containers.")
                    .independent();
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 18, 80, 26, true
            );

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(sourceConnector), 70);
            scene.overlay().showText(70)
                    .text("In extract mode, a connector sends items from the container into the tube.")
                    .pointAt(util.vector().centerOf(sourceConnector))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 18, 80, 26, true
            );

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(targetConnector), 70);
            scene.overlay().showText(70)
                    .text("In insert mode, a connector sends items from the tube into the container.")
                    .pointAt(util.vector().centerOf(targetConnector))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 18, 80, 26, true
            );

            scene.overlay().showControls(util.vector().topOf(sourceConnector), Pointing.DOWN, 40)
                    .rightClick()
                    .withItem(AllItems.WRENCH.asStack());
            scene.overlay().showText(70)
                    .text("Use a Wrench to change the connector's orientation and operating mode.")
                    .pointAt(util.vector().centerOf(sourceConnector))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 18, 80, 26, true
            );

            scene.world().modifyBlockEntity(sourceConnector, PneumaticConnectionBlockEntity.class,
                    connector -> connector.setFilter(AllItems.FILTER.asStack()));
            scene.overlay().showControls(util.vector().topOf(sourceConnector), Pointing.DOWN, 40)
                    .rightClick()
                    .withItem(AllItems.FILTER.asStack());
            scene.overlay().showText(70)
                    .text("A filter limits which items the connector may extract.")
                    .pointAt(util.vector().centerOf(sourceConnector))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 18, 80, 26, true
            );

            clearPonderFlow(scene, tubeOwners);
            scene.world().modifyBlockEntity(transportPath.get(0), PneumaticTubeBlockEntity.class,
                    tube -> tube.startPonderTransport(
                            transportedStack, transportPath, sourceConnector, targetConnector, 40));
            tickPonderFlow(scene, tubeOwners, transportPath, 10, true);

            scene.world().toggleRedstonePower(util.select().position(sourceConnector));
            scene.world().modifyBlock(sourceConnector,
                    state -> state.setValue(PneumaticConnectionBlock.POWERED, true), true);
            scene.effects().indicateRedstone(sourceConnector);
            scene.overlay().showOutline(PonderPalette.RED, new Object(),
                    util.select().position(sourceConnector), 70);
            scene.overlay().showText(70)
                    .text("A redstone signal blocks the extractor; its illuminated outline shows that it is disabled.")
                    .pointAt(util.vector().centerOf(sourceConnector))
                    .placeNearTarget();
            tickPonderFlow(scene, tubeOwners, transportPath, 70, true);

            scene.overlay().showText(70)
                    .text("Items already in transit continue moving to their destination.")
                    .independent();
            tickPonderFlow(scene, tubeOwners, transportPath, 80, true);

            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class,
                    pump -> pump.setSpeed(0.0f));
            clearPonderFlow(scene, tubeOwners);
            scene.markAsFinished();
        };
    }

    private PonderStoryBoard pumpRequiredScene() {
        return pumpScene(
                "item_pump_required",
                "Item Pump",
                "Pneumatic tubes form a route, but do not create item flow on their own.",
                "Install an Item Pump in the line to move items.",
                "The pump requires a source of rotation.",
                "Supply rotation to the pump's cogwheel to start the flow.",
                "Flow speed depends on rotational speed.",
                "The higher the rotational speed, the faster items move."
        );
    }

    private PonderStoryBoard pumpSpeedScene() {
        return pumpScene(
                "item_pump",
                "Flow Speed",
                "The line's throughput can be increased with a pump.",
                "Replace one section of tube with a pump.",
                "The pump requires rotation to operate.",
                "Supply rotation to the pump's cogwheel.",
                "Rotational speed determines the flow speed.",
                "A higher rotational speed makes items move faster."
        );
    }

    private PonderStoryBoard pumpScene(String sceneId, String title, String... texts) {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title(sceneId, title);
            scene.configureBasePlate(0, 0, 7);
            scene.world().showSection(util.select().layer(0), Direction.UP);
            scene.idle(5);

            BlockPos pumpPos = new BlockPos(3, 1, 3);
            BlockPos cogPos = new BlockPos(3, 2, 4);
            BlockPos sourceConnector = new BlockPos(5, 1, 3);
            BlockPos targetConnector = new BlockPos(1, 1, 3);
            List<BlockPos> transportPath = List.of(
                    new BlockPos(4, 1, 3),
                    pumpPos,
                    new BlockPos(2, 1, 3)
            );
            List<BlockPos> tubeOwners = List.of(transportPath.get(2), transportPath.get(0));
            ItemStack transportedStack = new ItemStack(Items.IRON_BLOCK);

            BlockState pumpState = ModBlocks.ITEM_PUMP.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST);

            scene.world().setBlock(pumpPos, pumpState, false);
            scene.world().showSection(util.select().fromTo(0, 1, 3, 6, 1, 3), Direction.DOWN);
            scene.idle(15);

            scene.overlay().showText(70)
                    .text(texts[0])
                    .independent();
            scene.idle(80);

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(pumpPos), 70);
            scene.overlay().showText(70)
                    .text(texts[1])
                    .independent();
            scene.idle(80);

            scene.overlay().showText(70)
                    .text(texts[2])
                    .independent();
            scene.idle(80);

            ElementLink<WorldSectionElement> cog =
                    scene.world().showIndependentSection(util.select().position(cogPos), Direction.DOWN);
            scene.world().configureCenterOfRotation(cog, new Vec3(3.5, 2.5, 4.5));
            scene.idle(10);

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(cogPos), 60);
            scene.overlay().showText(70)
                    .text(texts[3])
                    .independent();
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class, pump -> pump.setSpeed(16.0f));
            scene.world().rotateSection(cog, -384, 0, 0, 160);
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 24, 80, 30, true
            );

            scene.overlay().showText(70)
                    .text(texts[4])
                    .independent();
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 24, 80, 30, true
            );

            scene.overlay().showText(70)
                    .text(texts[5])
                    .independent();
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class, pump -> pump.setSpeed(64.0f));
            updatePonderFlowSpeed(scene, tubeOwners, 16);
            scene.world().rotateSection(cog, -1536, 0, 0, 160);
            runPonderFlow(
                    scene, transportPath, tubeOwners, sourceConnector, targetConnector,
                    transportedStack, 16, 160, 20, true
            );
            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class, pump -> pump.setSpeed(0.0f));
            clearPonderFlow(scene, tubeOwners);
            scene.idle(5);

            scene.markAsFinished();
        };
    }

    private PonderStoryBoard packagerScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("packagers", "Transporting Packages");
            scene.configureBasePlate(0, 0, 9);
            scene.scaleSceneView(0.75f);
            scene.world().showSection(util.select().layer(0), Direction.UP);
            scene.idle(5);
            scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
            scene.idle(15);

            BlockPos receivingPackager = new BlockPos(0, 1, 4);
            BlockPos receivingConnector = new BlockPos(1, 1, 4);
            BlockPos pumpPos = new BlockPos(4, 1, 4);
            BlockPos sendingConnector = new BlockPos(7, 1, 4);
            BlockPos sendingPackager = new BlockPos(8, 1, 4);
            List<BlockPos> path = List.of(
                    new BlockPos(6, 1, 4),
                    new BlockPos(5, 1, 4),
                    pumpPos,
                    new BlockPos(3, 1, 4),
                    new BlockPos(2, 1, 4)
            );
            List<BlockPos> tubeOwners = List.of(
                    path.get(4),
                    path.get(3),
                    path.get(1),
                    path.get(0)
            );
            ItemStack packageStack = PackageStyles.getDefaultBox();

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(sendingPackager), 70);
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(receivingPackager), 70);
            scene.overlay().showText(70)
                    .text("Pneumatic connectors allow Create Packagers to exchange sealed packages through a tube line.")
                    .pointAt(util.vector().centerOf(sendingConnector))
                    .placeNearTarget();
            scene.idle(80);

            scene.overlay().showText(45)
                    .text("The sending Packager seals its items into a package.")
                    .pointAt(util.vector().centerOf(sendingPackager))
                    .placeNearTarget();
            scene.world().modifyBlockEntity(sendingPackager, PackagerBlockEntity.class,
                    packager -> startPackagerAnimation(packager, packageStack, false));
            scene.idle(25);

            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class,
                    pump -> pump.setSpeed(64.0f));
            scene.effects().indicateSuccess(pumpPos);
            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(pumpPos), 90);
            scene.overlay().showText(80)
                    .text("The rotating Item Pump drives whole packages through the tubes as pneumatic capsules.")
                    .pointAt(util.vector().centerOf(pumpPos))
                    .placeNearTarget();

            scene.world().modifyBlockEntity(sendingPackager, PackagerBlockEntity.class,
                    OverpressurePonderPlugin::clearPackagerAnimation);
            scene.world().modifyBlockEntity(path.get(0), PneumaticTubeBlockEntity.class,
                    tube -> tube.startPonderTransport(
                            packageStack,
                            path,
                            sendingConnector,
                            receivingConnector,
                            16
                    ));

            for (int tick = 0; tick < 150; tick++) {
                if (tick == 30) {
                    scene.world().modifyBlockEntity(sendingPackager, PackagerBlockEntity.class,
                            packager -> startPackagerAnimation(packager, packageStack, false));
                }
                if (tick == 50) {
                    scene.world().modifyBlockEntity(sendingPackager, PackagerBlockEntity.class,
                            OverpressurePonderPlugin::clearPackagerAnimation);
                    scene.world().modifyBlockEntity(path.get(0), PneumaticTubeBlockEntity.class,
                            tube -> tube.startPonderTransport(
                                    packageStack,
                                    path,
                                    sendingConnector,
                                    receivingConnector,
                                    16
                            ));
                }
                if (tick == 65) {
                    scene.overlay().showText(55)
                            .text("At the other end, the receiving Packager opens each arriving package.")
                            .pointAt(util.vector().centerOf(receivingPackager))
                            .placeNearTarget();
                }
                if (tick == 74 || tick == 124) {
                    scene.world().modifyBlockEntity(path.get(4), PneumaticTubeBlockEntity.class,
                            PneumaticTubeBlockEntity::finishPonderTransport);
                    scene.world().modifyBlockEntity(receivingPackager, PackagerBlockEntity.class,
                            packager -> startPackagerAnimation(packager, packageStack, true));
                    scene.effects().indicateSuccess(receivingPackager);
                }

                for (BlockPos tubePos : tubeOwners) {
                    scene.world().modifyBlockEntity(tubePos, PneumaticTubeBlockEntity.class,
                            PneumaticTubeBlockEntity::tickPonderTransport);
                }
                scene.idle(1);
            }

            scene.world().modifyBlockEntity(pumpPos, ItemPumpBlockEntity.class,
                    pump -> pump.setSpeed(0.0f));
            scene.idle(25);
            scene.markAsFinished();
        };
    }

    private static void startPackagerAnimation(
            PackagerBlockEntity packager,
            ItemStack packageStack,
            boolean inward
    ) {
        packager.animationInward = inward;
        packager.animationTicks = PackagerBlockEntity.CYCLE;
        if (inward) {
            packager.heldBox = ItemStack.EMPTY;
            packager.previouslyUnwrapped = packageStack.copy();
        } else {
            packager.heldBox = packageStack.copy();
            packager.previouslyUnwrapped = ItemStack.EMPTY;
        }
        packager.setChanged();
    }

    private static void clearPackagerAnimation(PackagerBlockEntity packager) {
        packager.animationTicks = 0;
        packager.heldBox = ItemStack.EMPTY;
        packager.previouslyUnwrapped = ItemStack.EMPTY;
        packager.setChanged();
    }

    private static void runPonderFlow(
            SceneBuilder scene,
            List<BlockPos> path,
            List<BlockPos> tubeOwners,
            BlockPos source,
            BlockPos target,
            ItemStack stack,
            int moveTime,
            int ticks,
            int spawnInterval,
            boolean finishAtDestination
    ) {
        for (int tick = 0; tick < ticks; tick++) {
            if (tick % spawnInterval == 0) {
                scene.world().modifyBlockEntity(path.get(0), PneumaticTubeBlockEntity.class,
                        tube -> tube.startPonderTransport(stack, path, source, target, moveTime));
            }

            for (BlockPos tubePos : tubeOwners) {
                scene.world().modifyBlockEntity(tubePos, PneumaticTubeBlockEntity.class,
                        PneumaticTubeBlockEntity::tickPonderTransport);
            }

            if (finishAtDestination) {
                scene.world().modifyBlockEntity(path.get(path.size() - 1), PneumaticTubeBlockEntity.class,
                        PneumaticTubeBlockEntity::finishPonderTransport);
            }
            scene.idle(1);
        }
    }

    private static void updatePonderFlowSpeed(
            SceneBuilder scene,
            List<BlockPos> tubeOwners,
            int moveTime
    ) {
        for (BlockPos tubePos : tubeOwners) {
            scene.world().modifyBlockEntity(tubePos, PneumaticTubeBlockEntity.class,
                    tube -> tube.setPonderTransportMoveTime(moveTime));
        }
    }

    private static void tickPonderFlow(
            SceneBuilder scene,
            List<BlockPos> tubeOwners,
            List<BlockPos> path,
            int ticks,
            boolean finishAtDestination
    ) {
        for (int tick = 0; tick < ticks; tick++) {
            for (BlockPos tubePos : tubeOwners) {
                scene.world().modifyBlockEntity(tubePos, PneumaticTubeBlockEntity.class,
                        PneumaticTubeBlockEntity::tickPonderTransport);
            }
            if (finishAtDestination) {
                scene.world().modifyBlockEntity(path.get(path.size() - 1), PneumaticTubeBlockEntity.class,
                        PneumaticTubeBlockEntity::finishPonderTransport);
            }
            scene.idle(1);
        }
    }

    private static void clearPonderFlow(SceneBuilder scene, List<BlockPos> tubeOwners) {
        for (BlockPos tubePos : tubeOwners) {
            scene.world().modifyBlockEntity(tubePos, PneumaticTubeBlockEntity.class,
                    PneumaticTubeBlockEntity::clearPonderTransport);
        }
    }

    private PonderStoryBoard valveScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("valve", "Pneumatic Valve");
            scene.scaleSceneView(0.9f);
            revealScene(scene, util);
            scene.idle(15);

            BlockPos valvePos = new BlockPos(2, 1, 2);
            BlockPos linkDataPos = valvePos.north();
            BlockPos linkVisualPos = valvePos.above();
            BlockPos source = new BlockPos(-1, 1, 2);
            BlockPos target = new BlockPos(5, 1, 2);
            List<BlockPos> transportPath = List.of(
                    new BlockPos(0, 1, 2),
                    new BlockPos(1, 1, 2),
                    valvePos,
                    new BlockPos(3, 1, 2),
                    new BlockPos(4, 1, 2)
            );
            List<BlockPos> tubeOwners = List.of(
                    transportPath.get(4),
                    transportPath.get(3),
                    transportPath.get(2),
                    transportPath.get(1),
                    transportPath.get(0)
            );
            ItemStack transportedStack = new ItemStack(Items.IRON_BLOCK);

            scene.overlay().showText(70)
                    .text("The Pneumatic Valve can shut off the item flow.")
                    .pointAt(util.vector().topOf(valvePos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 80, 14, true
            );

            scene.overlay().showText(60)
                    .text("The valve is open by default.")
                    .pointAt(util.vector().topOf(valvePos).add(0, 0.75, 0))
                    .placeNearTarget();
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(valvePos), 60);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 70, 14, true
            );

            scene.overlay().showControls(util.vector().topOf(valvePos), Pointing.DOWN, 45)
                    .rightClick()
                    .withItem(AllBlocks.REDSTONE_LINK.asStack());
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 10, 14, true
            );

            BlockState linkState = AllBlocks.REDSTONE_LINK.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP)
                    .setValue(RedstoneLinkBlock.RECEIVER, false)
                    .setValue(RedstoneLinkBlock.POWERED, false);
            scene.world().hideSection(util.select().position(linkDataPos), Direction.NORTH);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 20, 14, true
            );
            scene.world().setBlock(linkDataPos, linkState, false);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 1, 14, true
            );
            ElementLink<WorldSectionElement> valveLink = scene.world()
                    .showIndependentSectionImmediately(util.select().position(linkDataPos));
            scene.world().moveSection(valveLink, new Vec3(0, 1.75, 1), 0);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 1, 14, true
            );
            scene.world().moveSection(valveLink, new Vec3(0, -0.75, 0), 12);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 17, 14, true
            );
            scene.effects().indicateSuccess(linkVisualPos);

            scene.overlay().showText(70)
                    .text("Attach a Redstone Link to the valve to control it.")
                    .pointAt(util.vector().topOf(linkVisualPos).add(0, 0.5, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 80, 14, true
            );

            scene.world().modifyBlock(linkDataPos,
                    state -> state.setValue(RedstoneLinkBlock.RECEIVER, true), true);

            scene.world().modifyBlock(linkDataPos,
                    state -> state.setValue(RedstoneLinkBlock.POWERED, true), false);
            scene.world().modifyBlock(valvePos,
                    state -> state.setValue(BlockStateProperties.POWERED, true), true);
            scene.effects().indicateRedstone(linkVisualPos);
            scene.effects().indicateRedstone(valvePos);
            scene.overlay().showOutline(PonderPalette.RED, new Object(),
                    util.select().position(valvePos), 70);
            scene.overlay().showText(70)
                    .text("When it receives a signal, the valve closes and stops the flow.")
                    .pointAt(util.vector().topOf(valvePos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 80, 14, true
            );

            scene.world().modifyBlock(linkDataPos,
                    state -> state.setValue(RedstoneLinkBlock.POWERED, false), false);
            scene.world().modifyBlock(valvePos,
                    state -> state.setValue(BlockStateProperties.POWERED, false), true);
            scene.effects().indicateSuccess(valvePos);
            scene.overlay().showText(60)
                    .text("When the signal is removed, the valve opens again.")
                    .pointAt(util.vector().topOf(valvePos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 70, 14, true
            );

            clearPonderFlow(scene, tubeOwners);

            scene.markAsFinished();
        };
    }

    private PonderStoryBoard flowSensorScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("flow_sensor", "Clog Sensor");
            scene.scaleSceneView(0.9f);
            revealScene(scene, util);
            scene.idle(15);

            BlockPos sensorPos = new BlockPos(2, 1, 2);
            BlockPos linkDataPos = sensorPos.north();
            BlockPos linkVisualPos = sensorPos.above();
            BlockPos source = new BlockPos(-1, 1, 2);
            BlockPos target = new BlockPos(5, 1, 2);
            List<BlockPos> transportPath = List.of(
                    new BlockPos(0, 1, 2),
                    new BlockPos(1, 1, 2),
                    sensorPos,
                    new BlockPos(3, 1, 2),
                    new BlockPos(4, 1, 2)
            );
            List<BlockPos> tubeOwners = List.of(
                    transportPath.get(4),
                    transportPath.get(3),
                    transportPath.get(2),
                    transportPath.get(1),
                    transportPath.get(0)
            );
            ItemStack transportedStack = new ItemStack(Items.IRON_BLOCK);

            scene.overlay().showText(70)
                    .text("The sensor monitors the state of the flow inside the tube.")
                    .pointAt(util.vector().topOf(sensorPos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 80, 14, true
            );

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(sensorPos), 60);
            scene.overlay().showText(60)
                    .text("The green indicator means that the line is operating normally.")
                    .pointAt(util.vector().topOf(sensorPos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 70, 14, true
            );

            scene.overlay().showControls(util.vector().topOf(sensorPos), Pointing.DOWN, 45)
                    .rightClick()
                    .withItem(AllBlocks.REDSTONE_LINK.asStack());
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 10, 14, true
            );

            BlockState linkState = AllBlocks.REDSTONE_LINK.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP)
                    .setValue(RedstoneLinkBlock.RECEIVER, false)
                    .setValue(RedstoneLinkBlock.POWERED, false);
            scene.world().hideSection(util.select().position(linkDataPos), Direction.NORTH);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 20, 14, true
            );
            scene.world().setBlock(linkDataPos, linkState, false);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 1, 14, true
            );
            ElementLink<WorldSectionElement> sensorLink = scene.world()
                    .showIndependentSectionImmediately(util.select().position(linkDataPos));
            scene.world().moveSection(sensorLink, new Vec3(0, 1.75, 1), 0);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 1, 14, true
            );
            scene.world().moveSection(sensorLink, new Vec3(0, -0.75, 0), 12);
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 17, 14, true
            );
            scene.effects().indicateSuccess(linkVisualPos);

            scene.overlay().showText(70)
                    .text("If the route is blocked, a queue begins to build up before the obstruction.")
                    .pointAt(util.vector().topOf(sensorPos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 80, 14, false
            );

            scene.world().modifyBlock(sensorPos,
                    state -> state.setValue(BlockStateProperties.POWERED, true), true);
            scene.world().modifyBlock(linkDataPos,
                    state -> state.setValue(RedstoneLinkBlock.POWERED, true), false);
            scene.effects().indicateRedstone(sensorPos);
            scene.effects().indicateRedstone(linkVisualPos);
            scene.overlay().showOutline(PonderPalette.RED, new Object(),
                    util.select().position(sensorPos), 70);
            scene.overlay().showText(70)
                    .text("When a clog is detected, the sensor lights its red indicator and emits a maximum-strength redstone signal.")
                    .pointAt(util.vector().topOf(sensorPos).add(0, 0.75, 0))
                    .placeNearTarget();
            runPonderFlow(
                    scene, transportPath, tubeOwners, source, target,
                    transportedStack, 10, 80, 14, false
            );

            clearPonderFlow(scene, tubeOwners);

            scene.markAsFinished();
        };
    }
}
