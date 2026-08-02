package com.hwmods.overpressure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class ItemPumpRenderer extends KineticBlockEntityRenderer<ItemPumpBlockEntity> {
    private static final PartialModel COG_MODEL = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "block/item_pump/cog"));
    private static final PartialModel CREATIVE_COG_MODEL = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "block/creative_item_pump/cog"));

    public ItemPumpRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void init() {
    }

    @Override
    protected void renderSafe(
            ItemPumpBlockEntity be,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        BlockState state = getRenderedBlockState(be);
        renderRotatingBuffer(be, getRotatedModel(be, state), poseStack, buffer.getBuffer(getRenderType(be, state)), light);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(ItemPumpBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(be.isCreative() ? CREATIVE_COG_MODEL : COG_MODEL, state);
    }
}
