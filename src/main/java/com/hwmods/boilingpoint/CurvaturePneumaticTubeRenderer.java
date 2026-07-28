package com.hwmods.boilingpoint;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CurvaturePneumaticTubeRenderer implements BlockEntityRenderer<CurvaturePneumaticTubeEntity> {
    private static final int SEGMENTS = 18;
    private static final double HALF_SIZE = 0.2425;
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_EAST = new Vec3(1.0, 0.0, 0.0);

    private final ItemRenderer itemRenderer;

    public CurvaturePneumaticTubeRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public boolean shouldRenderOffScreen(CurvaturePneumaticTubeEntity tube) {
        return true;
    }

    @Override
    public boolean shouldRender(CurvaturePneumaticTubeEntity tube, Vec3 cameraPos) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(CurvaturePneumaticTubeEntity tube) {
        Vec3 origin = Vec3.atLowerCornerOf(tube.getBlockPos());
        Vec3 p0 = origin.add(tube.getP0());
        Vec3 p1 = origin.add(tube.getP1());
        Vec3 p2 = origin.add(tube.getP2());
        Vec3 p3 = origin.add(tube.getP3());

        double minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
        double minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
        double minZ = Math.min(Math.min(p0.z, p1.z), Math.min(p2.z, p3.z));
        double maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
        double maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
        double maxZ = Math.max(Math.max(p0.z, p1.z), Math.max(p2.z, p3.z));

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(HALF_SIZE + 0.25);
    }

    @Override
    public void render(
            CurvaturePneumaticTubeEntity tube,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        RenderType renderType = tube.getLevel() instanceof PonderLevel
                ? BoilingPointRenderTypes.curveTubeInPonder()
                : BoilingPointRenderTypes.curveTube();
        VertexConsumer buffer = bufferSource.getBuffer(renderType);
        Matrix4f pose = poseStack.last().pose();
        Vec3[][] sections = buildSections(tube);

        for (int i = 0; i < SEGMENTS; i++) {
            Vec3[] from = sections[i];
            Vec3[] to = sections[i + 1];
            float u0 = (float) i / SEGMENTS;
            float u1 = (float) (i + 1) / SEGMENTS;

            addQuad(buffer, pose, from[0], from[1], to[1], to[0], u0, 0.0f, u1, 1.0f, packedLight, packedOverlay);
            addQuad(buffer, pose, from[1], from[2], to[2], to[1], u0, 0.0f, u1, 1.0f, packedLight, packedOverlay);
            addQuad(buffer, pose, from[2], from[3], to[3], to[2], u0, 0.0f, u1, 1.0f, packedLight, packedOverlay);
            addQuad(buffer, pose, from[3], from[0], to[0], to[3], u0, 0.0f, u1, 1.0f, packedLight, packedOverlay);
        }

        PneumaticTubeRenderer.renderMovingItem(
                tube, partialTick, poseStack, bufferSource, packedLight, packedOverlay, itemRenderer);
    }

    private Vec3[][] buildSections(CurvaturePneumaticTubeEntity tube) {
        Vec3[][] sections = new Vec3[SEGMENTS + 1][4];
        Vec3 previousRight = null;
        Vec3 previousUp = null;

        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            Vec3 center = tube.getPoint(t);
            Vec3 tangent = getTangent(tube, t);
            Vec3[] basis = getSectionBasis(tangent, previousRight, previousUp);
            Vec3 right = basis[0];
            Vec3 up = basis[1];

            sections[i] = makeSquareSection(center, right, up);
            previousRight = right;
            previousUp = up;
        }

        return sections;
    }

    private Vec3 getTangent(CurvaturePneumaticTubeEntity tube, float t) {
        float before = Math.max(0.0f, t - 0.01f);
        float after = Math.min(1.0f, t + 0.01f);
        Vec3 tangent = tube.getPoint(after).subtract(tube.getPoint(before));

        if (tangent.lengthSqr() < 1.0E-6) {
            return new Vec3(0.0, 0.0, 1.0);
        }

        return tangent.normalize();
    }

    private Vec3[] getSectionBasis(Vec3 tangent, Vec3 previousRight, Vec3 previousUp) {
        if (previousRight != null && previousUp != null) {
            Vec3 right = previousRight.subtract(tangent.scale(previousRight.dot(tangent)));

            if (right.lengthSqr() > 1.0E-6) {
                right = right.normalize();
                Vec3 up = right.cross(tangent).normalize();
                return new Vec3[] { right, up };
            }
        }

        Vec3 reference = Math.abs(tangent.dot(WORLD_UP)) > 0.92 ? WORLD_EAST : WORLD_UP;
        Vec3 right = tangent.cross(reference).normalize();
        Vec3 up = right.cross(tangent).normalize();

        return new Vec3[] { right, up };
    }

    private Vec3[] makeSquareSection(Vec3 center, Vec3 right, Vec3 up) {
        Vec3 horizontal = right.scale(HALF_SIZE);
        Vec3 vertical = up.scale(HALF_SIZE);

        return new Vec3[] {
                center.add(horizontal).add(vertical),
                center.subtract(horizontal).add(vertical),
                center.subtract(horizontal).subtract(vertical),
                center.add(horizontal).subtract(vertical)
        };
    }

    private void addQuad(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            float u0,
            float v0,
            float u1,
            float v1,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 normal = c.subtract(a).cross(b.subtract(a));

        if (normal.lengthSqr() < 1.0E-6) {
            normal = new Vec3(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }

        addVertex(buffer, pose, a, u0, v0, normal, packedLight, packedOverlay);
        addVertex(buffer, pose, b, u0, v1, normal, packedLight, packedOverlay);
        addVertex(buffer, pose, c, u1, v1, normal, packedLight, packedOverlay);
        addVertex(buffer, pose, d, u1, v0, normal, packedLight, packedOverlay);
    }

    private void addVertex(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 position,
            float u,
            float v,
            Vec3 normal,
            int packedLight,
            int packedOverlay
    ) {
        buffer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}
