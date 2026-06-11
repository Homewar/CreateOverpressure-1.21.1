package com.hwmods.boilingpoint;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class PneumaticTubeRenderer implements BlockEntityRenderer<PneumaticTubeBlockEntity> {
    private static final double CAPSULE_RADIUS = 0.16;
    private static final double CAPSULE_LENGTH = 0.34;
    private static final ResourceLocation CAPSULE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BoilingPoint.MODID,
            "textures/block/item_pipe_texture/core.png"
    );

    public PneumaticTubeRenderer(BlockEntityRendererProvider.Context context) {
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
        renderMovingCapsule(tube, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    public static void renderMovingCapsule(
            PneumaticTubeBlockEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        MovingTubeItem item = tube.getRenderMovingItem();

        if (item == null || !tube.shouldRenderMovingItem(partialTick)) {
            return;
        }

        Vec3 center = getCapsuleCenter(tube, item, partialTick);
        Vec3 tangent = getCapsuleTangent(tube, item, partialTick);
        Vec3[] basis = getBasis(tangent);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(CAPSULE_TEXTURE));
        Matrix4f pose = poseStack.last().pose();

        renderBox(
                buffer,
                pose,
                center,
                basis[0],
                basis[1],
                tangent,
                packedLight,
                packedOverlay
        );
    }

    private static Vec3 getCapsuleCenter(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        RenderStep step = getRenderStep(tube, item, partialTick);
        return getPathPoint(tube, item, step.pathIndex(), step.progress());
    }

    private static Vec3 getCapsuleTangent(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        RenderStep step = getRenderStep(tube, item, partialTick);
        float before = Math.max(0.0f, step.progress() - 0.01f);
        float after = Math.min(1.0f, step.progress() + 0.01f);
        Vec3 tangent = getPathPoint(tube, item, step.pathIndex(), after)
                .subtract(getPathPoint(tube, item, step.pathIndex(), before));
        return normalizeOrDefault(tangent);
    }

    private static RenderStep getRenderStep(PneumaticTubeBlockEntity tube, MovingTubeItem item, float partialTick) {
        float segments = tube.getMovingProgressSegments(partialTick);
        int pathIndex = Math.min(Math.max(0, (int) Math.floor(segments)), item.path.size() - 1);
        float progress = Math.min(1.0f, Math.max(0.0f, segments - pathIndex));
        return new RenderStep(pathIndex, progress);
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

    private static Vec3 normalizeOrDefault(Vec3 value) {
        if (value.lengthSqr() < 1.0E-6) {
            return new Vec3(0.0, 0.0, 1.0);
        }

        return value.normalize();
    }

    private static Vec3[] getBasis(Vec3 tangent) {
        Vec3 reference = Math.abs(tangent.y) > 0.92 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = tangent.cross(reference).normalize();
        Vec3 up = right.cross(tangent).normalize();
        return new Vec3[] { right, up };
    }

    private record RenderStep(int pathIndex, float progress) {
    }

    private static void renderBox(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 center,
            Vec3 right,
            Vec3 up,
            Vec3 forward,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 r = right.scale(CAPSULE_RADIUS);
        Vec3 u = up.scale(CAPSULE_RADIUS);
        Vec3 f = forward.scale(CAPSULE_LENGTH * 0.5);

        Vec3 p000 = center.subtract(r).subtract(u).subtract(f);
        Vec3 p001 = center.subtract(r).subtract(u).add(f);
        Vec3 p010 = center.subtract(r).add(u).subtract(f);
        Vec3 p011 = center.subtract(r).add(u).add(f);
        Vec3 p100 = center.add(r).subtract(u).subtract(f);
        Vec3 p101 = center.add(r).subtract(u).add(f);
        Vec3 p110 = center.add(r).add(u).subtract(f);
        Vec3 p111 = center.add(r).add(u).add(f);

        addQuad(buffer, pose, p000, p100, p110, p010, packedLight, packedOverlay);
        addQuad(buffer, pose, p101, p001, p011, p111, packedLight, packedOverlay);
        addQuad(buffer, pose, p001, p000, p010, p011, packedLight, packedOverlay);
        addQuad(buffer, pose, p100, p101, p111, p110, packedLight, packedOverlay);
        addQuad(buffer, pose, p010, p110, p111, p011, packedLight, packedOverlay);
        addQuad(buffer, pose, p001, p101, p100, p000, packedLight, packedOverlay);
    }

    private static void addQuad(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 normal = normalizeOrDefault(b.subtract(a).cross(c.subtract(a)));

        addVertex(buffer, pose, a, normal, 0.0f, 0.0f, packedLight, packedOverlay);
        addVertex(buffer, pose, b, normal, 1.0f, 0.0f, packedLight, packedOverlay);
        addVertex(buffer, pose, c, normal, 1.0f, 1.0f, packedLight, packedOverlay);
        addVertex(buffer, pose, d, normal, 0.0f, 1.0f, packedLight, packedOverlay);
    }

    private static void addVertex(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 position,
            Vec3 normal,
            float u,
            float v,
            int packedLight,
            int packedOverlay
    ) {
        buffer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(255, 214, 83, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}
