package com.hwmods.overpressure;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GrindRailRenderer implements BlockEntityRenderer<GrindRailBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private static final double HALF_SIZE = 0.085;
    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_EAST = new Vec3(1.0, 0.0, 0.0);

    public GrindRailRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(GrindRailBlockEntity rail) {
        return rail.shouldRenderCurve();
    }

    @Override
    public AABB getRenderBoundingBox(GrindRailBlockEntity rail) {
        Vec3 first = rail.getWorldPoint(rail.getRenderStart());
        AABB bounds = new AABB(first, first);
        for (int index = 1; index <= 16; index++) {
            double t = rail.getRenderStart()
                    + (rail.getRenderEnd() - rail.getRenderStart()) * index / 16.0;
            Vec3 point = rail.getWorldPoint(t);
            bounds = bounds.minmax(new AABB(point, point));
        }
        return bounds.inflate(0.25);
    }

    @Override
    public void render(
            GrindRailBlockEntity rail,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (!rail.shouldRenderCurve()) {
            return;
        }

        int segments = getSegmentCount(rail);
        Vec3[][] sections = buildSections(rail, segments);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entitySolid(TEXTURE));
        Matrix4f pose = poseStack.last().pose();

        for (int index = 0; index < segments; index++) {
            Vec3[] from = sections[index];
            Vec3[] to = sections[index + 1];
            float u0 = index / (float) segments;
            float u1 = (index + 1) / (float) segments;
            addQuad(buffer, pose, from[0], from[1], to[1], to[0], u0, u1, packedLight, packedOverlay);
            addQuad(buffer, pose, from[1], from[2], to[2], to[1], u0, u1, packedLight, packedOverlay);
            addQuad(buffer, pose, from[2], from[3], to[3], to[2], u0, u1, packedLight, packedOverlay);
            addQuad(buffer, pose, from[3], from[0], to[0], to[3], u0, u1, packedLight, packedOverlay);
        }
    }

    private int getSegmentCount(GrindRailBlockEntity rail) {
        double controlLength = (rail.getWorldP0().distanceTo(rail.getWorldP1())
                + rail.getWorldP1().distanceTo(rail.getWorldP2())
                + rail.getWorldP2().distanceTo(rail.getWorldP3()))
                * (rail.getRenderEnd() - rail.getRenderStart());
        return Math.max(24, Math.min(256, (int) Math.ceil(controlLength * 10.0)));
    }

    private Vec3[][] buildSections(GrindRailBlockEntity rail, int segments) {
        Vec3[][] sections = new Vec3[segments + 1][4];
        Vec3 previousRight = null;
        Vec3 previousUp = null;
        for (int index = 0; index <= segments; index++) {
            double t = rail.getRenderStart()
                    + (rail.getRenderEnd() - rail.getRenderStart()) * index / segments;
            Vec3 center = rail.getPoint(t);
            Vec3 tangent = rail.getWorldTangent(t);
            Vec3[] basis = getBasis(tangent, previousRight, previousUp);
            Vec3 right = basis[0];
            Vec3 up = basis[1];
            sections[index] = makeSection(center, right, up);
            previousRight = right;
            previousUp = up;
        }
        return sections;
    }

    private Vec3[] getBasis(Vec3 tangent, Vec3 previousRight, Vec3 previousUp) {
        if (previousRight != null && previousUp != null) {
            Vec3 right = previousRight.subtract(tangent.scale(previousRight.dot(tangent)));
            if (right.lengthSqr() > 1.0E-6) {
                right = right.normalize();
                return new Vec3[] { right, right.cross(tangent).normalize() };
            }
        }
        Vec3 reference = Math.abs(tangent.dot(WORLD_UP)) > 0.92 ? WORLD_EAST : WORLD_UP;
        Vec3 right = tangent.cross(reference).normalize();
        return new Vec3[] { right, right.cross(tangent).normalize() };
    }

    private Vec3[] makeSection(Vec3 center, Vec3 right, Vec3 up) {
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
            float u1,
            int packedLight,
            int packedOverlay
    ) {
        Vec3 normal = c.subtract(a).cross(b.subtract(a));
        normal = normal.lengthSqr() < 1.0E-6 ? WORLD_UP : normal.normalize();
        addVertex(buffer, pose, a, u0, 0.0f, normal, packedLight, packedOverlay);
        addVertex(buffer, pose, d, u1, 0.0f, normal, packedLight, packedOverlay);
        addVertex(buffer, pose, c, u1, 1.0f, normal, packedLight, packedOverlay);
        addVertex(buffer, pose, b, u0, 1.0f, normal, packedLight, packedOverlay);
    }

    private void addVertex(
            VertexConsumer buffer,
            Matrix4f pose,
            Vec3 point,
            float u,
            float v,
            Vec3 normal,
            int packedLight,
            int packedOverlay
    ) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}
