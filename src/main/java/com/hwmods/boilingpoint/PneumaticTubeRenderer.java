package com.hwmods.boilingpoint;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeRenderer implements BlockEntityRenderer<PneumaticTubeBlockEntity> {
    private static final float ITEM_SCALE = 0.32f;

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
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        itemRenderer.renderStatic(
                item.stack,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                tube.getLevel(),
                (int) item.animationId
        );
        poseStack.popPose();
    }

    private static Vec3 getMovingItemCenter(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        if (item.waitingAtDestination) {
            return getOpenEnd(tube.getBlockPos(), item.targetConnector);
        }

        if (item.waitingForNextTube && item.pathIndex + 1 < item.path.size()) {
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
