package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.phys.AABB;
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
    public AABB getRenderBoundingBox(PneumaticTubeBlockEntity tube) {
        BlockPos origin = tube.getBlockPos();
        double minX = origin.getX();
        double minY = origin.getY();
        double minZ = origin.getZ();
        double maxX = origin.getX() + 1.0;
        double maxY = origin.getY() + 1.0;
        double maxZ = origin.getZ() + 1.0;
        MovingTubeItem item = tube.getRenderMovingItem();

        if (item != null && item.pathIndex >= 0) {
            if (item.sourceConnector != null && item.pathIndex == item.startPathIndex) {
                minX = Math.min(minX, item.sourceConnector.getX());
                minY = Math.min(minY, item.sourceConnector.getY());
                minZ = Math.min(minZ, item.sourceConnector.getZ());
                maxX = Math.max(maxX, item.sourceConnector.getX() + 1.0);
                maxY = Math.max(maxY, item.sourceConnector.getY() + 1.0);
                maxZ = Math.max(maxZ, item.sourceConnector.getZ() + 1.0);
            }
            int startIndex = item.sourceConnector != null && item.pathIndex == item.startPathIndex
                    ? 0
                    : item.pathIndex;
            int endIndex = Math.min(item.path.size(), item.pathIndex + 6);
            for (int pathIndex = startIndex; pathIndex < endIndex; pathIndex++) {
                BlockPos pathPos = item.path.get(pathIndex);
                minX = Math.min(minX, pathPos.getX());
                minY = Math.min(minY, pathPos.getY());
                minZ = Math.min(minZ, pathPos.getZ());
                maxX = Math.max(maxX, pathPos.getX() + 1.0);
                maxY = Math.max(maxY, pathPos.getY() + 1.0);
                maxZ = Math.max(maxZ, pathPos.getZ() + 1.0);
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.5);
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
        PneumaticTubeBlockEntity.ClientRenderStep step = tube.getClientRenderStep(partialTick);

        if (item == null || step == null) {
            return;
        }

        Vec3 center = getMovingItemCenter(tube, item, step);
        int itemLight = getItemLight(tube, packedLight);
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        if (isCardboard(item.stack)) {
            renderCapsule(tube, item, step, poseStack, bufferSource, itemLight, packedOverlay);
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
            PneumaticTubeBlockEntity.ClientRenderStep step,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 direction = getCapsuleDirection(tube, item, step);
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

    private static Vec3 getCapsuleDirection(
            PneumaticTubeBlockEntity tube,
            MovingTubeItem item,
            PneumaticTubeBlockEntity.ClientRenderStep step
    ) {
        float beforeProgress = Math.max(0.0f, step.progress() - 0.01f);
        float afterProgress = Math.min(1.0f, step.progress() + 0.01f);
        Vec3 before = getPathPoint(
                tube,
                item,
                step.pathIndex(),
                step.nextOwnerPathIndex(),
                beforeProgress
        );
        Vec3 after = getPathPoint(
                tube,
                item,
                step.pathIndex(),
                step.nextOwnerPathIndex(),
                afterProgress
        );
        Vec3 direction = after.subtract(before);
        return direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
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

    private static Vec3 getMovingItemCenter(
            PneumaticTubeBlockEntity tube,
            MovingTubeItem item,
            PneumaticTubeBlockEntity.ClientRenderStep step
    ) {
        if (item.waitingAtDestination) {
            Vec3 curveEnd = getCurveEndPoint(tube, item);

            if (curveEnd != null) {
                return curveEnd;
            }

            return getOpenEnd(tube.getBlockPos(), item.targetConnector);
        }

        if (item.waitingForNextTube && item.pathIndex + 1 < item.path.size()) {
            return getPathPoint(
                    tube,
                    item,
                    step.pathIndex(),
                    step.nextOwnerPathIndex(),
                    step.progress()
            );
        }

        return getPathPoint(
                tube,
                item,
                step.pathIndex(),
                step.nextOwnerPathIndex(),
                step.progress()
        );
    }

    private static Vec3 getPathPoint(
            PneumaticTubeBlockEntity tube,
            MovingTubeItem item,
            int pathIndex,
            int nextOwnerPathIndex,
            float progress
    ) {
        BlockPos currentPos = tube.getBlockPos();
        BlockPos segmentPos = item.path.get(pathIndex);
        Vec3 offset = Vec3.atLowerCornerOf(segmentPos.subtract(currentPos));
        BlockEntity segmentBlockEntity = tube.getLevel() == null ? null : tube.getLevel().getBlockEntity(segmentPos);

        if (segmentBlockEntity instanceof DeviderBlockEntity devider && pathIndex + 1 < item.path.size()) {
            BlockPos previous = pathIndex > 0 ? item.path.get(pathIndex - 1) : null;
            BlockPos next = item.path.get(pathIndex + 1);
            Vec3 center = offset.add(0.5, 0.5, 0.5);
            List<Vec3> points = new ArrayList<>();
            addSourcePrefix(points, currentPos, item, pathIndex);

            if (previous != null && devider.isBranchPosition(previous)) {
                Direction incomingSide = Direction.getNearest(
                        previous.getX() - segmentPos.getX(),
                        previous.getY() - segmentPos.getY(),
                        previous.getZ() - segmentPos.getZ()
                );
                points.add(offset.add(DeviderBlockEntity.getLocalOutputPoint(
                        incomingSide,
                        devider.getInputDirection()
                )));
                points.add(center);
                points.add(Vec3.atLowerCornerOf(next.subtract(currentPos)).add(0.5, 0.5, 0.5));
                return interpolatePath(points, progress);
            }

            Direction outgoingSide = Direction.getNearest(
                    next.getX() - segmentPos.getX(),
                    next.getY() - segmentPos.getY(),
                    next.getZ() - segmentPos.getZ()
            );
            points.add(center);
            points.add(offset.add(DeviderBlockEntity.getLocalOutputPoint(
                    outgoingSide,
                    devider.getInputDirection()
            )));
            return interpolatePath(points, progress);
        }

        if (segmentBlockEntity instanceof CurvaturePneumaticTubeEntity curvatureTube) {
            boolean reversed = isCurveReversed(currentPos, item, pathIndex, curvatureTube);
            if (item.sourceConnector == null || pathIndex != item.startPathIndex) {
                float curveProgress = reversed ? 1.0f - progress : progress;
                return offset.add(curvatureTube.getPoint(curveProgress));
            }

            List<Vec3> points = new ArrayList<>();
            addSourcePrefix(points, currentPos, item, pathIndex);
            for (int sample = 0; sample <= 12; sample++) {
                float curveProgress = sample / 12.0f;
                if (reversed) {
                    curveProgress = 1.0f - curveProgress;
                }
                points.add(offset.add(curvatureTube.getPoint(curveProgress)));
            }
            return interpolatePath(points, progress);
        }

        Vec3 current = offset.add(0.5, 0.5, 0.5);
        List<Vec3> points = new ArrayList<>();
        addSourcePrefix(points, currentPos, item, pathIndex);
        points.add(current);
        if (nextOwnerPathIndex <= pathIndex) {
            Vec3 next = Vec3.atLowerCornerOf(item.targetConnector.subtract(currentPos)).add(0.5, 0.5, 0.5);
            points.add(next);
            return interpolatePath(points, progress);
        }

        for (int pointIndex = pathIndex + 1; pointIndex <= nextOwnerPathIndex; pointIndex++) {
            Vec3 next = Vec3.atLowerCornerOf(item.path.get(pointIndex).subtract(currentPos)).add(0.5, 0.5, 0.5);
            points.add(next);
        }
        return interpolatePath(points, progress);
    }

    private static void addSourcePrefix(
            List<Vec3> points,
            BlockPos rendererPos,
            MovingTubeItem item,
            int pathIndex
    ) {
        if (item.sourceConnector == null || pathIndex != item.startPathIndex || item.path.isEmpty()) {
            return;
        }

        BlockPos firstPathPos = item.path.get(0);
        Direction direction = Direction.getNearest(
                firstPathPos.getX() - item.sourceConnector.getX(),
                firstPathPos.getY() - item.sourceConnector.getY(),
                firstPathPos.getZ() - item.sourceConnector.getZ()
        );
        Vec3 sourceOutlet = Vec3.atLowerCornerOf(item.sourceConnector.subtract(rendererPos))
                .add(
                        0.5 + direction.getStepX() * 0.42,
                        0.5 + direction.getStepY() * 0.42,
                        0.5 + direction.getStepZ() * 0.42
                );
        points.add(sourceOutlet);

        for (int pointIndex = 0; pointIndex < pathIndex; pointIndex++) {
            points.add(Vec3.atLowerCornerOf(item.path.get(pointIndex).subtract(rendererPos)).add(0.5, 0.5, 0.5));
        }
    }

    private static Vec3 interpolatePath(List<Vec3> points, float progress) {
        if (points.isEmpty()) {
            return Vec3.ZERO;
        }
        if (points.size() == 1) {
            return points.get(0);
        }

        double totalLength = 0.0;
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            totalLength += points.get(pointIndex - 1).distanceTo(points.get(pointIndex));
        }
        if (totalLength < 1.0E-6) {
            return points.get(0);
        }

        double remainingDistance = Math.max(0.0, Math.min(1.0, progress)) * totalLength;
        Vec3 previous = points.get(0);
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            Vec3 next = points.get(pointIndex);
            double segmentLength = previous.distanceTo(next);
            if (remainingDistance <= segmentLength || pointIndex == points.size() - 1) {
                double segmentProgress = segmentLength < 1.0E-6 ? 1.0 : remainingDistance / segmentLength;
                return previous.scale(1.0 - segmentProgress).add(next.scale(segmentProgress));
            }
            remainingDistance -= segmentLength;
            previous = next;
        }

        return previous;
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

}
