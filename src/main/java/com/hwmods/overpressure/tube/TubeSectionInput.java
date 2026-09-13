package com.hwmods.overpressure.tube;

import com.hwmods.overpressure.Overpressure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class TubeSectionInput {
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || (!event.isAttack() && !event.isUseItem())) return;
        var hit = TubeSectionInteractions.hit(mc.player, mc.player.blockInteractionRange());
        if (hit == null) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new TubeSectionInteractionPayload(hit.section().id(), event.isAttack(), event.getHand()));
    }
    @SubscribeEvent public static void outline(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var hit = TubeSectionInteractions.hit(mc.player, mc.player.blockInteractionRange());
        if (hit == null) return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        var frames = hit.section().geometry().frames();
        for (int i = 0; i + 1 < frames.size(); i++) {
            for (int side = 0; side < 4; side++) line(pose, lines, frames.get(i).corner(side).subtract(camera), frames.get(i + 1).corner(side).subtract(camera));
        }
        for (var frame : java.util.List.of(frames.getFirst(), frames.getLast())) for (int side = 0; side < 4; side++)
            line(pose, lines, frame.corner(side).subtract(camera), frame.corner((side + 1) % 4).subtract(camera));
        buffers.endBatch(RenderType.lines());
    }
    private static void line(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer lines,
                             net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        var normal = to.subtract(from).normalize();
        for (var point : java.util.List.of(from, to)) lines.addVertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(0, 0, 0, 102).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }
    @SubscribeEvent public static void hideBlockBehindSection(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event) {
        var player = Minecraft.getInstance().player;
        if (player != null && TubeSectionInteractions.hit(player, player.blockInteractionRange()) != null) event.setCanceled(true);
    }
}
