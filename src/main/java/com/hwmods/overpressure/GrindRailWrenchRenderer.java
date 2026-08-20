package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Renders the grinding wrench in world space so camera yaw cannot move it. */
@EventBusSubscriber(modid = Overpressure.MODID, value = Dist.CLIENT)
public final class GrindRailWrenchRenderer {
    private static final double WRENCH_CENTER_BELOW_RAIL = 7.0 / 16.0;
    private static final double WRENCH_LIFT = 3.5 / 16.0;
    private static final double WRENCH_LEFT_OFFSET = 1.0 / 16.0;
    // The FIXED transform and the asymmetric Create wrench model put the lower
    // handle slightly away from the item origin. Cancel that offset so the
    // visible center of the handle sits on the raised palm.
    private static final double HANDLE_X_OFFSET = 0.4 / 16.0;
    private static final double HANDLE_Z_OFFSET = -0.75 / 16.0;

    @SubscribeEvent
    static void renderLockedWrench(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        Vec3 railPoint = GrindRailRidingHandler.clientRailPoint(player, event.getPartialTick());
        Vec3 railDirection = GrindRailRidingHandler.clientRailDirection(player);
        if (railPoint == null || railDirection == null) {
            return;
        }

        ItemStack wrench = AllItems.WRENCH.isIn(player.getMainHandItem())
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (!AllItems.WRENCH.isIn(wrench)) {
            return;
        }

        Vec3 playerRenderPosition = player.getPosition(event.getPartialTick());
        Vec3 offset = railPoint.subtract(playerRenderPosition);
        float railYaw = (float)Math.toDegrees(Math.atan2(railDirection.z, railDirection.x));

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                offset.x,
                offset.y - WRENCH_CENTER_BELOW_RAIL + WRENCH_LIFT,
                offset.z
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - railYaw));
        poseStack.translate(WRENCH_LEFT_OFFSET - HANDLE_X_OFFSET, 0.0, -HANDLE_Z_OFFSET);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(
                player,
                wrench,
                ItemDisplayContext.FIXED,
                false,
                poseStack,
                event.getMultiBufferSource(),
                player.level(),
                event.getPackedLight(),
                OverlayTexture.NO_OVERLAY,
                player.getId()
        );
        poseStack.popPose();
    }

    private GrindRailWrenchRenderer() {
    }
}
