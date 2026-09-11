package com.hwmods.overpressure;

import java.util.List;

import com.hwmods.overpressure.mixin.ArmBlockEntityAccessor;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
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
                .addStoryBoard("pneumatic_connection", connectorScene())
                .addStoryBoard("autohand_marchruting", armCapsuleRoutingScene());

        helper.forComponents(ResourceLocation.fromNamespaceAndPath("create", "packager"))
                .addStoryBoard("packagers", packagerScene());

        helper.forComponents(rl("valve"))
                .addStoryBoard("valve_pounder", valveScene());

        helper.forComponents(rl("clog_sensor"))
                .addStoryBoard("flow_sensor_pounder", flowSensorScene());

        helper.forComponents(rl("filter_pipe"))
                .addStoryBoard("filter_pipe", filterPipeScene());

        helper.forComponents(rl("capsule_port"))
                .addStoryBoard("hand_port", capsulePortScene());

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

            scene.overlay().showText(90)
                    .text("To create a curved tube, click the starting node while holding a tube.")
                    .independent();
            scene.idle(100);

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

    private PonderStoryBoard filterPipeScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("filter_pipe", "Filtered Pneumatic Pipe");
            scene.scaleSceneView(0.9f);
            revealScene(scene, util);
            scene.idle(15);

            BlockPos filterPipe = new BlockPos(2, 1, 2);
            BlockPos source = new BlockPos(5, 1, 2);
            List<BlockPos> straightPath = List.of(
                    new BlockPos(4, 1, 2),
                    new BlockPos(3, 1, 2),
                    filterPipe,
                    new BlockPos(1, 1, 2),
                    new BlockPos(0, 1, 2)
            );
            List<BlockPos> branchPath = List.of(
                    new BlockPos(4, 1, 2),
                    new BlockPos(3, 1, 2),
                    filterPipe,
                    new BlockPos(2, 1, 3),
                    new BlockPos(1, 1, 3),
                    new BlockPos(0, 1, 3)
            );
            ItemStack configuredFilter = itemFilterFor(new ItemStack(Items.GOLD_BLOCK));

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(filterPipe), 65);
            scene.overlay().showText(65)
                    .text("The Filtered Pneumatic Pipe separates matching blocks from the rest of the flow.")
                    .pointAt(util.vector().centerOf(filterPipe))
                    .placeNearTarget();
            scene.idle(75);

            Vec3 filterSlot = util.vector().centerOf(filterPipe).add(0, 0, -0.45);
            scene.overlay().showControls(filterSlot, Pointing.DOWN, 45)
                    .rightClick()
                    .withItem(configuredFilter);
            scene.world().modifyBlockEntity(filterPipe, FilterPipeBlockEntity.class,
                    pipe -> pipe.setFilter(configuredFilter));
            scene.effects().indicateSuccess(filterPipe);
            scene.overlay().showText(65)
                    .text("Insert an item or a configured Create Filter into the white filter slot.")
                    .pointAt(filterSlot)
                    .placeNearTarget();
            scene.idle(75);

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().fromTo(0, 1, 2, 1, 1, 2), 65);
            scene.overlay().showText(65)
                    .text("Blocks that do not match the filter continue straight ahead.")
                    .pointAt(util.vector().centerOf(new BlockPos(1, 1, 2)))
                    .placeNearTarget();
            runPonderFlow(
                    scene, straightPath, straightPath, source, new BlockPos(-1, 1, 2),
                    new ItemStack(Items.IRON_BLOCK), 10, 70, 90, true
            );

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().fromTo(0, 1, 3, 2, 1, 3), 65);
            scene.overlay().showText(65)
                    .text("Matching blocks are diverted into the curved branch.")
                    .pointAt(util.vector().centerOf(new BlockPos(2, 1, 3)))
                    .placeNearTarget();
            runPonderFlow(
                    scene, branchPath, branchPath, source, new BlockPos(-1, 1, 3),
                    new ItemStack(Items.GOLD_BLOCK), 10, 70, 90, true
            );

            clearPonderFlow(scene, straightPath);
            clearPonderFlow(scene, branchPath);
            scene.markAsFinished();
        };
    }

    private PonderStoryBoard capsulePortScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("hand_port", "Capsule Port");
            scene.scaleSceneView(0.9f);
            revealScene(scene, util);
            scene.idle(15);

            BlockPos portPos = new BlockPos(2, 1, 2);
            List<BlockPos> path = List.of(
                    new BlockPos(2, 2, 2),
                    new BlockPos(2, 3, 2)
            );
            ItemStack loadedItems = new ItemStack(Items.IRON_INGOT, 16);
            ItemStack packageStack = PackageStyles.getDefaultBox();

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(portPos), 65);
            scene.overlay().showText(65)
                    .text("The Capsule Port lets a player send items into a pneumatic network by hand.")
                    .pointAt(util.vector().centerOf(portPos))
                    .placeNearTarget();
            scene.idle(75);

            scene.overlay().showControls(util.vector().centerOf(portPos).add(0.65, 0, 0),
                            Pointing.DOWN, 50)
                    .rightClick()
                    .withItem(loadedItems);
            scene.world().modifyBlockEntity(portPos, CapsulePortBlockEntity.class,
                    port -> port.insertFromPlayer(loadedItems));
            scene.effects().indicateSuccess(portPos);
            scene.overlay().showText(65)
                    .text("Right-click the port with items to load them into its capsule.")
                    .pointAt(util.vector().centerOf(portPos))
                    .placeNearTarget();
            scene.idle(75);

            scene.world().modifyBlockEntity(portPos, CapsulePortBlockEntity.class, port -> {
                port.takeLastItem();
            });
            scene.world().modifyBlock(portPos,
                    state -> state.setValue(CapsulePortBlock.OPEN, false)
                            .setValue(CapsulePortBlock.POWERED, true), true);
            scene.effects().indicateRedstone(portPos);
            scene.overlay().showText(70)
                    .text("A redstone signal seals the loaded items and dispatches the capsule through the network.")
                    .pointAt(util.vector().centerOf(portPos))
                    .placeNearTarget();
            runPonderFlow(
                    scene, path, path, portPos, new BlockPos(2, 4, 2),
                    packageStack, 10, 75, 90, true
            );

            clearPonderFlow(scene, path);
            scene.markAsFinished();
        };
    }

    private PonderStoryBoard armCapsuleRoutingScene() {
        return (SceneBuilder scene, SceneBuildingUtil util) -> {
            scene.title("arm_capsule_routing", "Routing Capsules with Mechanical Arms");
            scene.configureBasePlate(0, 0, 9);
            scene.scaleSceneView(0.75f);
            scene.world().showSection(util.select().layer(0), Direction.UP);
            scene.idle(5);
            scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
            scene.idle(15);

            BlockPos armPos = new BlockPos(4, 1, 4);
            BlockPos depotPos = new BlockPos(6, 1, 4);
            BlockPos workshopConnector = new BlockPos(3, 1, 7);
            BlockPos storageConnector = new BlockPos(4, 1, 7);
            BlockPos fallbackConnector = new BlockPos(5, 1, 7);
            BlockPos workshopTube = workshopConnector.south();
            BlockPos storageTube = storageConnector.south();
            BlockPos fallbackTube = fallbackConnector.south();
            ItemStack workshopFilter = addressFilter("Workshop");
            ItemStack storageFilter = addressFilter("Storage");
            ItemStack storagePackage = addressedPackage("Storage");
            ItemStack unnamedPackage = PackageStyles.getDefaultBox();
            ItemStack unmatchedPackage = addressedPackage("Office");

            returnArmToDepot(scene, armPos);
            scene.idle(34);

            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(armPos), 160);
            scene.overlay().showText(160)
                    .text("A Mechanical Arm can act as a router for packages entering the depot.")
                    .pointAt(util.vector().centerOf(armPos).add(0, 0.75, 0))
                    .placeNearTarget();
            routePackagesDuringText(
                    scene, armPos, depotPos, fallbackConnector, fallbackTube,
                    unnamedPackage, 0, 2
            );

            scene.world().modifyBlockEntity(workshopConnector, PneumaticConnectionBlockEntity.class,
                    connector -> connector.setFilter(workshopFilter));
            scene.world().modifyBlockEntity(storageConnector, PneumaticConnectionBlockEntity.class,
                    connector -> connector.setFilter(storageFilter));
            scene.overlay().showControls(util.vector().topOf(workshopConnector), Pointing.DOWN, 160)
                    .rightClick()
                    .withItem(workshopFilter);
            scene.overlay().showControls(util.vector().topOf(storageConnector), Pointing.DOWN, 160)
                    .rightClick()
                    .withItem(storageFilter);
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(workshopConnector), 160);
            scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
                    util.select().position(storageConnector), 160);
            scene.overlay().showText(160)
                    .text("Put a Package Filter with an address into each destination connector.")
                    .pointAt(util.vector().topOf(storageConnector))
                    .placeNearTarget();
            routePackagesDuringText(
                    scene, armPos, depotPos, storageConnector, storageTube,
                    storagePackage, 1, 2
            );

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(storageConnector), 160);
            scene.overlay().showText(160)
                    .text("The arm compares the box address and carries it to the matching connector.")
                    .pointAt(util.vector().centerOf(storageConnector))
                    .placeNearTarget();
            routePackagesDuringText(
                    scene, armPos, depotPos, storageConnector, storageTube,
                    storagePackage, 1, 2
            );

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(fallbackConnector), 160);
            scene.overlay().showText(160)
                    .text("Leave one connector unfiltered as the fallback route for boxes without an address.")
                    .pointAt(util.vector().topOf(fallbackConnector))
                    .placeNearTarget();
            routePackagesDuringText(
                    scene, armPos, depotPos, fallbackConnector, fallbackTube,
                    unnamedPackage, 0, 2
            );

            scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(),
                    util.select().position(fallbackConnector), 160);
            scene.overlay().showText(160)
                    .text("The fallback also receives named boxes whose address matches none of the filtered outputs.")
                    .pointAt(util.vector().topOf(fallbackConnector))
                    .placeNearTarget();
            routePackagesDuringText(
                    scene, armPos, depotPos, fallbackConnector, fallbackTube,
                    unmatchedPackage, 0, 2
            );

            clearPonderFlow(scene, List.of(workshopTube, storageTube, fallbackTube));
            scene.world().modifyBlockEntity(armPos, ArmBlockEntity.class, arm -> arm.setSpeed(0));
            scene.markAsFinished();
        };
    }

    private static ItemStack itemFilterFor(ItemStack filteredStack) {
        ItemStack filter = AllItems.FILTER.asStack();
        AllItems.FILTER.get().getFilterItemHandler(filter).setStackInSlot(0, filteredStack.copy());
        return filter;
    }

    private static ItemStack addressFilter(String address) {
        ItemStack filter = AllItems.PACKAGE_FILTER.asStack();
        PackageItem.addAddress(filter, address);
        return filter;
    }

    private static ItemStack addressedPackage(String address) {
        ItemStack packageStack = PackageStyles.getDefaultBox();
        PackageItem.addAddress(packageStack, address);
        return packageStack;
    }

    private static void moveArmToOutput(
            SceneBuilder scene,
            BlockPos armPos,
            ItemStack packageStack,
            int outputIndex
    ) {
        scene.world().modifyBlockEntity(armPos, ArmBlockEntity.class, arm -> {
            var registries = arm.getLevel().registryAccess();
            var tag = arm.saveWithFullMetadata(registries);
            tag.putString("Phase", ArmBlockEntity.Phase.MOVE_TO_OUTPUT.name());
            tag.putInt("TargetPointIndex", outputIndex);
            tag.putFloat("MovementProgress", 0);
            tag.put("HeldItem", packageStack.save(registries));
            ((ArmBlockEntityAccessor) arm).overpressure$readPonderState(tag, registries, true);
            arm.setSpeed(32);
            arm.setChanged();
        });
    }

    private static void clearArmCargo(SceneBuilder scene, BlockPos armPos) {
        scene.world().modifyBlockEntity(armPos, ArmBlockEntity.class, arm -> {
            ((ArmBlockEntityAccessor) arm).overpressure$setHeldItem(ItemStack.EMPTY);
            arm.setChanged();
        });
    }

    private static void routePackagesDuringText(
            SceneBuilder scene,
            BlockPos armPos,
            BlockPos depotPos,
            BlockPos connectorPos,
            BlockPos tubePos,
            ItemStack packageStack,
            int outputIndex,
            int cycles
    ) {
        List<BlockPos> path = List.of(tubePos);
        for (int cycle = 0; cycle < cycles; cycle++) {
            scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class,
                    depot -> depot.setHeldItem(packageStack.copy()));
            scene.idle(12);

            scene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class,
                    DepotBlockEntity::clearContent);
            moveArmToOutput(scene, armPos, packageStack, outputIndex);
            scene.idle(34);

            clearArmCargo(scene, armPos);
            clearPonderFlow(scene, path);
            scene.world().modifyBlockEntity(tubePos, PneumaticTubeBlockEntity.class,
                    tube -> tube.startPonderTransport(
                            packageStack, path, connectorPos, tubePos.south(), 8));
            returnArmToDepot(scene, armPos);
            tickPonderFlow(scene, path, path, 34, true);
        }
    }

    private static void returnArmToDepot(
            SceneBuilder scene,
            BlockPos armPos
    ) {
        scene.world().modifyBlockEntity(armPos, ArmBlockEntity.class, arm -> {
            var registries = arm.getLevel().registryAccess();
            var tag = arm.saveWithFullMetadata(registries);
            tag.putString("Phase", ArmBlockEntity.Phase.MOVE_TO_INPUT.name());
            tag.putInt("TargetPointIndex", 0);
            tag.putFloat("MovementProgress", 0);
            tag.remove("HeldItem");
            ((ArmBlockEntityAccessor) arm).overpressure$readPonderState(tag, registries, true);
            arm.setSpeed(32);
            arm.setChanged();
        });
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
