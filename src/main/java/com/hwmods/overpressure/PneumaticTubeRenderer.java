package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.client.model.data.ModelData;

public class PneumaticTubeRenderer implements BlockEntityRenderer<PneumaticTubeBlockEntity> {
    private static final float ITEM_SCALE = 0.32f;
    private static final float CAPSULE_SCALE = 0.88f;
    private static final TagKey<Item> CREATE_PACKAGES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create", "packages")
    );

    private final ItemRenderer itemRenderer;

    public PneumaticTubeRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            PneumaticTubeBlockEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        renderMovingItem(tube, partialTick, poseStack, bufferSource, packedLight, packedOverlay, itemRenderer);
    }

    public static void renderMovingItem(
            PneumaticTubeBlockEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            ItemRenderer itemRenderer
    ) {
        MovingTubeItem item = tube.getRenderMovingItem();

        if (item == null || !tube.shouldRenderMovingItem(partialTick)) {
            return;
        }

        Vec3 center = getMovingItemCenter(tube, item, partialTick);
        int itemLight = getItemLight(tube, packedLight);
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        if (isCardboard(item.stack)) {
            renderCapsule(tube, item, partialTick, poseStack, bufferSource, itemLight, packedOverlay);
        } else {
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(
                    item.stack,
                    ItemDisplayContext.FIXED,
                    itemLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    tube.getLevel(),
                    (int) item.animationId
            );
        }
        poseStack.popPose();
    }

    private static boolean isCardboard(net.minecraft.world.item.ItemStack stack) {
        return stack.is(CREATE_PACKAGES);
    }

    private static void renderCapsule(
            PneumaticTubeBlockEntity tube,
            MovingTubeItem item,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 direction = getCapsuleDirection(tube, item, partialTick);
        rotateCapsule(poseStack, direction);
        poseStack.scale(CAPSULE_SCALE, CAPSULE_SCALE, CAPSULE_SCALE);
        poseStack.translate(-0.5, -0.25, -0.5);

        BakedModel model = Minecraft.getInstance()
                .getModelManager()
                .getModel(OverpressureClient.PNEUMATIC_CAPSULE_MODEL);
        Minecraft.getInstance()
                .getBlockRenderer()
                .getModelRenderer()
                .renderModel(
                        poseStack.last(),
                        bufferSource.getBuffer(Sheets.cutoutBlockSheet()),
                        Blocks.AIR.defaultBlockState(),
                        model,
                        1.0f,
                        1.0f,
                        1.0f,
                        LightTexture.FULL_BRIGHT,
                        packedOverlay,
                        ModelData.EMPTY,
                        null
                );
    }

    private static Vec3 getCapsuleDirection(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        Vec3 curveDirection = getCurveDirection(tube, item, partialTick);

        if (curveDirection != null) {
            return curveDirection;
        }

        BlockPos from = tube.getBlockPos();
        BlockPos toward = item.waitingAtDestination
                ? item.targetConnector
                : item.pathIndex + 1 < item.path.size() ? item.path.get(item.pathIndex + 1) : item.targetConnector;
        Direction direction = Direction.getNearest(
                toward.getX() - from.getX(),
                toward.getY() - from.getY(),
                toward.getZ() - from.getZ()
        );
        return Vec3.atLowerCornerOf(direction.getNormal());
    }

    private static Vec3 getCurveDirection(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        if (tube.getLevel() == null
                || item.pathIndex < 0
                || item.pathIndex >= item.path.size()) {
            return null;
        }

        BlockPos segmentPos = item.path.get(item.pathIndex);
        BlockEntity segmentBlockEntity = tube.getLevel().getBlockEntity(segmentPos);

        if (!(segmentBlockEntity instanceof CurvaturePneumaticTubeEntity curvatureTube)) {
            return null;
        }

        float progress = item.waitingForNextTube || item.waitingAtDestination
                ? 1.0f
                : tube.getMovingProgress(partialTick);
        boolean reversed = isCurveReversed(tube.getBlockPos(), item, item.pathIndex, curvatureTube);
        float curveProgress = reversed ? 1.0f - progress : progress;
        Vec3 tangent = getCurveTangent(curvatureTube, curveProgress);

        if (reversed) {
            tangent = tangent.scale(-1.0);
        }

        return tangent;
    }

    private static Vec3 getCurveTangent(CurvaturePneumaticTubeEntity tube, float t) {
        double u = 1.0 - t;
        Vec3 tangent = tube.getP1().subtract(tube.getP0()).scale(3.0 * u * u)
                .add(tube.getP2().subtract(tube.getP1()).scale(6.0 * u * t))
                .add(tube.getP3().subtract(tube.getP2()).scale(3.0 * t * t));

        if (tangent.lengthSqr() < 1.0E-6) {
            return new Vec3(0.0, 0.0, 1.0);
        }

        return tangent.normalize();
    }

    private static void rotateCapsule(PoseStack poseStack, Vec3 direction) {
        Vec3 normalized = direction.lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 0.0, 1.0)
                : direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(normalized.x, normalized.z));
        float pitch = (float) -Math.toDegrees(Math.asin(normalized.y));

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    private static int getItemLight(PneumaticTubeBlockEntity tube, int packedLight) {
        if (!(tube.getBlockState().getBlock() instanceof EncasedPneumaticTubeBlock)
                || tube.getLevel() == null) {
            return packedLight;
        }

        int blockLight = LightTexture.block(packedLight);
        int skyLight = LightTexture.sky(packedLight);

        for (Direction direction : Direction.values()) {
            int neighborLight = LevelRenderer.getLightColor(tube.getLevel(), tube.getBlockPos().relative(direction));
            blockLight = Math.max(blockLight, LightTexture.block(neighborLight));
            skyLight = Math.max(skyLight, LightTexture.sky(neighborLight));
        }

        return LightTexture.pack(blockLight, skyLight);
    }

    private static Vec3 getMovingItemCenter(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        if (item.waitingAtDestination) {
            Vec3 curveEnd = getCurveEndPoint(tube, item);

            if (curveEnd != null) {
                return curveEnd;
            }

            return getOpenEnd(tube.getBlockPos(), item.targetConnector);
        }

        if (item.waitingForNextTube && item.pathIndex + 1 < item.path.size()) {
            Vec3 curveEnd = getCurveEndPoint(tube, item);

            if (curveEnd != null) {
                return curveEnd;
            }

            return getOpenEnd(tube.getBlockPos(), item.path.get(item.pathIndex + 1));
        }

        RenderStep step = getRenderStep(tube, item, partialTick);
        return getPathPoint(tube, item, step.pathIndex(), step.progress());
    }

    private static RenderStep getRenderStep(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        return new RenderStep(item.pathIndex, tube.getMovingProgress(partialTick));
    }

    private static Vec3 getPathPoint(PneumaticTubeBlockEntity tube, MovingTubeItem item, int pathIndex, float progress) {
        BlockPos currentPos = tube.getBlockPos();
        BlockPos segmentPos = item.path.get(pathIndex);
        Vec3 offset = Vec3.atLowerCornerOf(segmentPos.subtract(currentPos));
        BlockEntity segmentBlockEntity = tube.getLevel() == null ? null : tube.getLevel().getBlockEntity(segmentPos);

        if (segmentBlockEntity instanceof CurvaturePneumaticTubeEntity curvatureTube) {
            float curveProgress = isCurveReversed(currentPos, item, pathIndex, curvatureTube)
                    ? 1.0f - progress
                    : progress;
            return offset.add(curvatureTube.getPoint(curveProgress));
        }

        Vec3 current = offset.add(0.5, 0.5, 0.5);
        Vec3 next = getNextLocalCenter(currentPos, item, pathIndex);

        return current.scale(1.0 - progress).add(next.scale(progress));
    }

    private static Vec3 getCurveEndPoint(PneumaticTubeBlockEntity tube, MovingTubeItem item) {
        if (tube.getLevel() == null
                || item.pathIndex < 0
                || item.pathIndex >= item.path.size()) {
            return null;
        }

        BlockPos currentPos = tube.getBlockPos();
        BlockPos segmentPos = item.path.get(item.pathIndex);
        BlockEntity segmentBlockEntity = tube.getLevel().getBlockEntity(segmentPos);

        if (!(segmentBlockEntity instanceof CurvaturePneumaticTubeEntity curvatureTube)) {
            return null;
        }

        Vec3 offset = Vec3.atLowerCornerOf(segmentPos.subtract(currentPos));
        float curveProgress = isCurveReversed(currentPos, item, item.pathIndex, curvatureTube)
                ? 0.0f
                : 1.0f;
        return offset.add(curvatureTube.getPoint(curveProgress));
    }

    private static boolean isCurveReversed(
            BlockPos rendererPos,
            MovingTubeItem item,
            int pathIndex,
            CurvaturePneumaticTubeEntity curvatureTube
    ) {
        Vec3 next = getNextLocalCenter(rendererPos, item, pathIndex);
        Vec3 curveOrigin = Vec3.atLowerCornerOf(curvatureTube.getBlockPos().subtract(rendererPos));
        Vec3 p0 = curveOrigin.add(curvatureTube.getP0());
        Vec3 p3 = curveOrigin.add(curvatureTube.getP3());

        return p0.distanceToSqr(next) < p3.distanceToSqr(next);
    }

    private static Vec3 getNextLocalCenter(BlockPos currentPos, MovingTubeItem item, int pathIndex) {
        if (pathIndex + 1 >= item.path.size()) {
            return Vec3.atLowerCornerOf(item.targetConnector.subtract(currentPos)).add(0.5, 0.5, 0.5);
        }

        BlockPos next = item.path.get(pathIndex + 1);
        return Vec3.atLowerCornerOf(next.subtract(currentPos)).add(0.5, 0.5, 0.5);
    }

    private static Vec3 getOpenEnd(BlockPos from, BlockPos toward) {
        Direction direction = Direction.getNearest(
                toward.getX() - from.getX(), toward.getY() - from.getY(), toward.getZ() - from.getZ());
        return new Vec3(0.5 + direction.getStepX() * 0.42,
                0.5 + direction.getStepY() * 0.42,
                0.5 + direction.getStepZ() * 0.42);
    }

    private record RenderStep(int pathIndex, float progress) {
    }
}
