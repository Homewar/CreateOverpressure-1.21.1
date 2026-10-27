package com.hwmods.overpressure;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MultitoolRadialScreen extends Screen {
    private static final int RADIUS = 72;
    private static final int SLOT_SIZE = 24;
    private final InteractionHand hand;

    public MultitoolRadialScreen(InteractionHand hand) {
        super(Component.translatable("screen.overpressure.multitool.title"));
        this.hand = hand;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Apply blur before any menu geometry so only the world is blurred.
        renderBlurredBackground(partialTick);
        graphics.fill(0, 0, width, height, 0x88000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int cy = height / 2;
        drawDisc(graphics, cx, cy, RADIUS, 0xD01A1C20);
        drawDisc(graphics, cx, cy, RADIUS - 3, 0xD02A2E33);

        ItemStack tool = Minecraft.getInstance().player == null
                ? ItemStack.EMPTY : Minecraft.getInstance().player.getItemInHand(hand);
        boolean drill = PneumaticMultitoolItem.isInstalled(tool, PneumaticMultitoolItem.Attachment.DRILL);
        boolean saw = PneumaticMultitoolItem.isInstalled(tool, PneumaticMultitoolItem.Attachment.SAW);
        PneumaticMultitoolItem.Attachment selected = PneumaticMultitoolItem.selected(tool);

        int drillX = cx - 54;
        int sawX = cx + 30;
        int slotY = cy - 12;
        drawSlot(graphics, drillX, slotY, new ItemStack(ModBlocks.PNEUMATIC_DRILL_ATTACHMENT.get()),
                drill, selected == PneumaticMultitoolItem.Attachment.DRILL, mouseX, mouseY);
        drawSlot(graphics, sawX, slotY, new ItemStack(ModBlocks.PNEUMATIC_CHAINSAW_ATTACHMENT.get()),
                saw, selected == PneumaticMultitoolItem.Attachment.SAW, mouseX, mouseY);

        graphics.renderItem(tool, cx - 8, cy - 8);
        graphics.drawCenteredString(font, title, cx, cy - RADIUS - 18, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.overpressure.multitool.drill"),
                drillX + SLOT_SIZE / 2, slotY + SLOT_SIZE + 6, drill ? 0xE9C46A : 0xA0A0A0);
        graphics.drawCenteredString(font, Component.translatable("screen.overpressure.multitool.saw"),
                sawX + SLOT_SIZE / 2, slotY + SLOT_SIZE + 6, saw ? 0xE9C46A : 0xA0A0A0);
        graphics.drawCenteredString(font, Component.translatable("screen.overpressure.multitool.hint")
                .withStyle(ChatFormatting.GRAY), cx, cy + RADIUS + 8, 0xA0A0A0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = width / 2;
            int cy = height / 2;
            if (inside(mouseX, mouseY, cx - 54, cy - 12)) {
                PacketDistributor.sendToServer(new MultitoolActionPayload(
                        hand, MultitoolActionPayload.Action.TOGGLE_DRILL));
                return true;
            }
            if (inside(mouseX, mouseY, cx + 30, cy - 12)) {
                PacketDistributor.sendToServer(new MultitoolActionPayload(
                        hand, MultitoolActionPayload.Action.TOGGLE_SAW));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            PacketDistributor.sendToServer(new MultitoolActionPayload(hand, MultitoolActionPayload.Action.CYCLE));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, ItemStack stack, boolean installed,
                          boolean selected, int mouseX, int mouseY) {
        int color = selected ? 0xFFE9C46A : installed ? 0xFF9B7A38 : 0xFF555A60;
        graphics.fill(x - 2, y - 2, x + SLOT_SIZE + 2, y + SLOT_SIZE + 2, color);
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, installed ? 0xFF34383D : 0xFF202328);
        graphics.renderItem(stack, x + 4, y + 4);
        if (!installed) {
            graphics.fill(x + 3, y + 3, x + SLOT_SIZE - 3, y + SLOT_SIZE - 3, 0x70303030);
        }
        if (inside(mouseX, mouseY, x, y)) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 2 && mouseX < x + SLOT_SIZE + 2
                && mouseY >= y - 2 && mouseY < y + SLOT_SIZE + 2;
    }

    private static void drawDisc(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - halfWidth, cy + dy, cx + halfWidth + 1, cy + dy + 1, color);
        }
    }
}
