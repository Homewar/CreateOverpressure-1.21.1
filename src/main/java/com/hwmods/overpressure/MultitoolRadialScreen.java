package com.hwmods.overpressure;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Renderable;
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
    protected void init() {
        ItemStack tool = Minecraft.getInstance().player == null
                ? ItemStack.EMPTY : Minecraft.getInstance().player.getItemInHand(hand);
        int initialSpeed = PneumaticMultitoolItem.speedSetting(tool);
        int sliderWidth = 132;
        int sliderY = Math.min(height / 2 + RADIUS + 8, height - 50);
        addRenderableWidget(new AbstractSliderButton(
                width / 2 - sliderWidth / 2,
                sliderY,
                sliderWidth,
                26,
                Component.translatable("screen.overpressure.multitool.speed", initialSpeed, "%"),
                (initialSpeed - PneumaticMultitoolItem.MIN_SPEED_SETTING)
                        / (double) (PneumaticMultitoolItem.MAX_SPEED_SETTING
                        - PneumaticMultitoolItem.MIN_SPEED_SETTING)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(
                        "screen.overpressure.multitool.speed", selectedSpeed(), "%"));
            }

            @Override
            protected void applyValue() {
                int speed = selectedSpeed();
                if (Minecraft.getInstance().player != null) {
                    ItemStack held = Minecraft.getInstance().player.getItemInHand(hand);
                    if (PneumaticMultitoolItem.from(held) != null) {
                        PneumaticMultitoolItem.setSpeedSetting(held, speed);
                    }
                }
                PacketDistributor.sendToServer(new MultitoolSpeedPayload(hand, speed));
            }

            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                int x = getX();
                int y = getY();
                int right = x + width;
                int bottom = y + height;

                // Create-like brass frame, recessed face and corner rivets.
                graphics.fill(x + 2, y + 3, right + 2, bottom + 3, 0x90000000);
                graphics.fill(x, y, right, bottom, 0xFF4B351B);
                graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xFFB88A3B);
                graphics.fill(x + 2, y + 2, right - 2, bottom - 2, 0xFF302A22);
                graphics.fill(x + 3, y + 3, right - 3, y + 4, 0xFF5B5142);
                int rivet = isHovered ? 0xFFFFD477 : 0xFFD6A64D;
                graphics.fill(x + 4, y + 4, x + 6, y + 6, rivet);
                graphics.fill(right - 6, y + 4, right - 4, y + 6, rivet);

                graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                        x + width / 2, y + 4, 0xFFF3E3BC);

                int trackLeft = x + 9;
                int trackRight = right - 9;
                int trackY = bottom - 7;
                int handleCenter = trackLeft + (int) Math.round(value * (trackRight - trackLeft));
                graphics.fill(trackLeft - 1, trackY - 1, trackRight + 1, trackY + 4, 0xFF17130F);
                graphics.fill(trackLeft, trackY, trackRight, trackY + 2, 0xFF6A5A43);
                graphics.fill(trackLeft, trackY, handleCenter, trackY + 2, 0xFFE0AC4C);

                graphics.fill(handleCenter - 4, trackY - 4, handleCenter + 5, trackY + 6, 0xFF3A2814);
                graphics.fill(handleCenter - 3, trackY - 3, handleCenter + 4, trackY + 5,
                        isHovered || isFocused() ? 0xFFF2C15C : 0xFFC99138);
                graphics.fill(handleCenter - 2, trackY - 2, handleCenter + 3, trackY - 1, 0xFFFFDC85);
            }

            private int selectedSpeed() {
                int range = PneumaticMultitoolItem.MAX_SPEED_SETTING
                        - PneumaticMultitoolItem.MIN_SPEED_SETTING;
                return PneumaticMultitoolItem.MIN_SPEED_SETTING + (int) Math.round(value * range);
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the background once. Screen#render would invoke renderBackground again and
        // place a second blur/menu layer over the radial menu.
        renderBlurredBackground(partialTick);
        graphics.fill(0, 0, width, height, 0x88000000);
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
                .withStyle(ChatFormatting.GRAY), cx, Math.min(cy + RADIUS + 42, height - 12), 0xA0A0A0);
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
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
