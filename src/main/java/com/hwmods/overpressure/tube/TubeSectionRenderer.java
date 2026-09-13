package com.hwmods.overpressure.tube;

import com.hwmods.overpressure.*;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
            for (boolean start : new boolean[]{true, false}) {
                var port = section.port(start);
                if (!TubeSections.portsAt(mc.level, port.position(), port.outward()).isEmpty()) continue;
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
