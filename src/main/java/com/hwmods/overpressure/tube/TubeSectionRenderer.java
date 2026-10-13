package com.hwmods.overpressure.tube;

import com.hwmods.overpressure.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class TubeSectionRenderer {
    private static final PartialModel TRIM = PartialModel.of(ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "block/item_pipe/rim/curve_end"));
    public static void init() {}
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            renderCargo(event);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        for (TubeSection section : TubeSections.get(mc.level).sections()) {
            if (!event.getFrustum().isVisible(section.geometry().bounds())) continue;
            // Translate near the section before converting world coordinates to floats.
            Vec3 origin = section.port(true).position();
            pose.pushPose();
            pose.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
            var frames = section.geometry().frames();
            var buffer = buffers.getBuffer(OverpressureRenderTypes.curveTube());
            for (int i = 0; i + 1 < frames.size(); i++) {
                var a = frames.get(i); var b = frames.get(i + 1);
                var span = section.spans().get(b.span());
                int light = span.glowing() ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(mc.level, BlockPos.containing(a.center()));
                for (int side = 0; side < 4; side++) {
                    int next = (side + 1) % 4;
                    CurvaturePneumaticTubeRenderer.addQuad(buffer, pose.last().pose(),
                            a.corner(side).subtract(origin), a.corner(next).subtract(origin),
                            b.corner(next).subtract(origin), b.corner(side).subtract(origin),
                            (float) a.distance(), 0, (float) b.distance(), 1, light, OverlayTexture.NO_OVERLAY, span.color());
                }
            }
            if (section.isFluid()) {
                var fluid = FluidTubeFlowPayload.sectionFluid(mc.level, section.id());
                if (!fluid.isEmpty()) renderFluid(section, fluid, origin, pose, buffers);
            }
            for (boolean start : new boolean[]{true, false}) {
                var port = section.port(start);
                if (!TubeSections.portsAt(mc.level, port.position(), port.outward(), section.isFluid()).isEmpty()) continue;
                var neighbor = mc.level.getBlockState(BlockPos.containing(port.position().add(port.outward().scale(0.05))));
                if (TubePainting.isTube(neighbor)) continue;
                var frame = start ? frames.getFirst() : frames.getLast();
                Vec3 right = frame.right().scale(start ? -1 : 1), up = frame.up(), inward = right.cross(up);
                Vec3 end = port.position().subtract(origin);
                pose.pushPose();
                pose.translate(end.x, end.y, end.z);
                pose.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(
                        (float) right.x, (float) right.y, (float) right.z,
                        (float) up.x, (float) up.y, (float) up.z,
                        (float) inward.x, (float) inward.y, (float) inward.z)));
                pose.translate(-0.5, -0.5, 0);
                CachedBuffers.partial(TRIM, ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState())
                        .light(LevelRenderer.getLightColor(mc.level, BlockPos.containing(port.position())))
                        .renderInto(pose, buffers.getBuffer(Sheets.cutoutBlockSheet()));
                pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch(OverpressureRenderTypes.curveTube());
    }

    private static void renderFluid(TubeSection section, net.neoforged.neoforge.fluids.FluidStack fluid,
                                    Vec3 origin, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite texture = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extension.getStillTexture(fluid));
        int color = extension.getTintColor(fluid);
        VertexConsumer buffer = FluidRenderHelper.getFluidBuilder(buffers);
        var frames = section.geometry().frames();
        final double radius = 0.155;
        for (int i = 0; i + 1 < frames.size(); i++) {
            var a = frames.get(i); var b = frames.get(i + 1);
            int light = LevelRenderer.getLightColor(Minecraft.getInstance().level, BlockPos.containing(a.center()));
            int luminosity = Math.max((light >> 4) & 15, fluid.getFluid().getFluidType().getLightLevel(fluid));
            light = (light & 0xF00000) | luminosity << 4;
            for (int side = 0; side < 4; side++) {
                int next = (side + 1) % 4;
                addFluidQuad(buffer, pose, fluidCorner(a, side, radius).subtract(origin),
                        fluidCorner(a, next, radius).subtract(origin), fluidCorner(b, next, radius).subtract(origin),
                        fluidCorner(b, side, radius).subtract(origin), texture, (float) a.distance(),
                        (float) b.distance(), light, color);
            }
        }
    }

    private static Vec3 fluidCorner(TubeSectionGeometry.Frame frame, int index, double radius) {
        return frame.center().add(frame.right().scale(index == 0 || index == 3 ? radius : -radius))
                .add(frame.up().scale(index < 2 ? radius : -radius));
    }

    private static void addFluidQuad(VertexConsumer buffer, PoseStack pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                     TextureAtlasSprite texture, float distanceA, float distanceB,
                                     int light, int color) {
        Vec3 normal = c.subtract(a).cross(b.subtract(a)).normalize();
        // Since 1.21 TextureAtlasSprite expects normalized sprite coordinates (0..1).
        // Passing the legacy 0..16 values samples unrelated regions of the block atlas.
        float u0 = texture.getU(distanceA % 1), u1 = texture.getU(distanceB % 1);
        // Preserve one texture pixel per world-space pixel across the narrow pipe.
        // Only a centered, pixel-aligned strip is shown instead of compressing the
        // whole sprite into the width of this face.
        int textureHeight = texture.contents().height();
        int visiblePixels = Math.max(1, Math.min(textureHeight,
                Math.round((float) a.distanceTo(b) * textureHeight)));
        int firstPixel = (textureHeight - visiblePixels) / 2;
        float v0 = texture.getV(firstPixel / (float) textureHeight);
        float v1 = texture.getV((firstPixel + visiblePixels) / (float) textureHeight);
        fluidVertex(buffer, pose, a, u0, v0, normal, light, color);
        fluidVertex(buffer, pose, d, u1, v0, normal, light, color);
        fluidVertex(buffer, pose, c, u1, v1, normal, light, color);
        fluidVertex(buffer, pose, b, u0, v1, normal, light, color);
    }

    private static void fluidVertex(VertexConsumer buffer, PoseStack pose, Vec3 point, float u, float v,
                                    Vec3 normal, int light, int color) {
        buffer.addVertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
                .setUv(u, v).setLight(light)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void renderCargo(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        for (var cargo : SectionCargoPayload.visuals(mc.level)) {
            double time = mc.level.getGameTime() + (double) event.getPartialTick().getGameTimeDeltaPartialTick(false)
                    - CargoAnimation.RENDER_DELAY_TICKS;
            if (cargo.animation().expired(time)) continue;
            var sample = cargo.animation().at(time);
            if (sample == null) continue;
            Vec3 position = sample.position();
            if (sample.section() != null) {
                var section = TubeSections.get(mc.level).get(sample.section());
                if (section != null) position = section.geometry().pointAtDistance(sample.distance());
            }
            pose.pushPose();
            pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            PneumaticTubeRenderer.renderSectionCargo(mc.level, cargo.stack(), sample.direction(), pose, buffers,
                    LevelRenderer.getLightColor(mc.level, BlockPos.containing(position)));
            pose.popPose();
        }
        // Flush cargo before translucent tube walls write depth, just like block-entity cargo.
        buffers.endBatch();
    }
}
