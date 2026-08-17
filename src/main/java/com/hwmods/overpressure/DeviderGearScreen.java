package com.hwmods.overpressure;

import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

public class DeviderGearScreen extends ValueSettingsScreen {
    private static final int PANEL_WIDTH = 550;
    private static final int PANEL_HEIGHT = 176;
    private static final int GROUP_WIDTH = 275;
    private static final int DIVIDER_GROUP_X = 0;
    private static final int MERGER_GROUP_X = 275;
    private static final int LEFT_NODE_OFFSET = 130;
    private static final int REDSTONE_OFFSET_X = 65;
    private static final int TOP_Y = 68;
    private static final int MIDDLE_Y = 100;
    private static final int BOTTOM_Y = 132;
    private static final int LABEL_HEIGHT = 15;

    private final ValueSettings initialSettings;

    public DeviderGearScreen(
            BlockPos pos,
            ValueSettingsBoard board,
            ValueSettings settings,
            Consumer<ValueSettings> onHover,
            int networkId
    ) {
        super(pos, board, settings, onHover, networkId);
        initialSettings = settings;
    }

    @Override
    protected void init() {
        super.init();
        windowWidth = PANEL_WIDTH;
        windowHeight = PANEL_HEIGHT;
        guiLeft = (width - windowWidth) / 2;
        guiTop = (height - windowHeight) / 2;

        Vec2 selectedPoint = getCoordinateOfValue(initialSettings.row(), initialSettings.value());
        double guiScale = minecraft.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(
                minecraft.getWindow().getWindow(),
                selectedPoint.x * guiScale,
                selectedPoint.y * guiScale
        );
    }

    @Override
    public ValueSettings getClosestCoordinate(int mouseX, int mouseY) {
        int closestConfiguration = initialSettings.value();
        double closestDistance = Double.MAX_VALUE;

        for (DeviderBlockEntity.JunctionRole role : DeviderBlockEntity.JunctionRole.values()) {
            for (DeviderBlockEntity.RoutingSelection routing : DeviderBlockEntity.RoutingSelection.values()) {
                int configuration = DeviderGearBehaviour.encode(role, routing);
                Vec2 point = getCoordinateOfValue(0, configuration);
                double dx = point.x - mouseX;
                double dy = point.y - mouseY;
                double distance = dx * dx + dy * dy;
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestConfiguration = configuration;
                }
            }
        }

        return new ValueSettings(0, closestConfiguration);
    }

    @Override
    public Vec2 getCoordinateOfValue(int row, int value) {
        DeviderBlockEntity.JunctionRole role = DeviderGearBehaviour.decodeRole(value);
        DeviderBlockEntity.RoutingSelection routing = DeviderGearBehaviour.decodeRouting(value);
        int groupX = role == DeviderBlockEntity.JunctionRole.DIVIDER
                ? DIVIDER_GROUP_X
                : MERGER_GROUP_X;
        int leftX = groupX + LEFT_NODE_OFFSET;
        int rightX = leftX + REDSTONE_OFFSET_X;

        return switch (routing) {
            case LEFT_PRIORITY -> point(leftX, TOP_Y);
            case BALANCED -> point(leftX, MIDDLE_Y);
            case RIGHT_PRIORITY -> point(leftX, BOTTOM_Y);
            case REDSTONE -> point(rightX, MIDDLE_Y);
            case REDSTONE_INVERTED -> point(rightX, BOTTOM_Y);
        };
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int candidate = getClosestCoordinate(mouseX, mouseY).value();
        DeviderBlockEntity.JunctionRole selectedRole = DeviderGearBehaviour.decodeRole(candidate);
        DeviderBlockEntity.RoutingSelection selectedRouting = DeviderGearBehaviour.decodeRouting(candidate);

        graphics.fill(guiLeft, guiTop, guiLeft + PANEL_WIDTH, guiTop + PANEL_HEIGHT, 0xEE17130F);
        drawFrame(graphics, guiLeft, guiTop, PANEL_WIDTH, PANEL_HEIGHT);
        drawCentered(
                graphics,
                Component.translatable("overpressure.devider.gear_selector"),
                guiLeft + PANEL_WIDTH / 2,
                guiTop + 8,
                0xFFF0D69A
        );

        drawGearGroup(
                graphics,
                DeviderBlockEntity.JunctionRole.DIVIDER,
                DIVIDER_GROUP_X,
                Component.translatable("overpressure.devider.junction_role.divider"),
                selectedRole,
                selectedRouting
        );
        drawGearGroup(
                graphics,
                DeviderBlockEntity.JunctionRole.MERGER,
                MERGER_GROUP_X,
                Component.translatable("overpressure.devider.junction_role.merger"),
                selectedRole,
                selectedRouting
        );

        drawCentered(
                graphics,
                CreateLang.translateDirect(
                        "gui.value_settings.release_to_confirm",
                        Component.keybind("key.use")
                ),
                guiLeft + PANEL_WIDTH / 2,
                guiTop + PANEL_HEIGHT - 14,
                0xFF9A8B70
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    private void drawGearGroup(
            GuiGraphics graphics,
            DeviderBlockEntity.JunctionRole role,
            int localGroupX,
            Component title,
            DeviderBlockEntity.JunctionRole selectedRole,
            DeviderBlockEntity.RoutingSelection selectedRouting
    ) {
        int groupX = guiLeft + localGroupX;
        int leftX = groupX + LEFT_NODE_OFFSET;
        int rightX = leftX + REDSTONE_OFFSET_X;
        int topY = guiTop + TOP_Y;
        int middleY = guiTop + MIDDLE_Y;
        int bottomY = guiTop + BOTTOM_Y;

        int titleCenter = groupX + GROUP_WIDTH / 2;
        drawCentered(
                graphics,
                title,
                titleCenter,
                guiTop + 31,
                selectedRole == role ? 0xFFFFD75A : 0xFFC5B690
        );
        drawTrack(graphics, leftX, topY, rightX, middleY, bottomY);

        drawRoutingNode(graphics, role, DeviderBlockEntity.RoutingSelection.LEFT_PRIORITY,
                leftX, topY, selectedRole, selectedRouting);
        drawRoutingNode(graphics, role, DeviderBlockEntity.RoutingSelection.BALANCED,
                leftX, middleY, selectedRole, selectedRouting);
        drawRoutingNode(graphics, role, DeviderBlockEntity.RoutingSelection.RIGHT_PRIORITY,
                leftX, bottomY, selectedRole, selectedRouting);
        drawRoutingNode(graphics, role, DeviderBlockEntity.RoutingSelection.REDSTONE,
                rightX, middleY, selectedRole, selectedRouting);
        drawRoutingNode(graphics, role, DeviderBlockEntity.RoutingSelection.REDSTONE_INVERTED,
                rightX, bottomY, selectedRole, selectedRouting);

        int leftLabelX = groupX + 7;
        int leftLabelWidth = leftX - leftLabelX - 18;
        int rightLabelX = rightX + 18;
        int rightLabelWidth = groupX + GROUP_WIDTH - 7 - rightLabelX;

        drawLabelWithArrow(graphics, Component.translatable("overpressure.devider.gear.left"),
                leftLabelX, topY, leftLabelWidth, leftX, true);
        drawLabelWithArrow(graphics, Component.translatable("overpressure.devider.gear.balanced"),
                leftLabelX, middleY, leftLabelWidth, leftX, true);
        drawLabelWithArrow(graphics, Component.translatable("overpressure.devider.gear.right"),
                leftLabelX, bottomY, leftLabelWidth, leftX, true);
        drawLabelWithArrow(graphics, Component.translatable("overpressure.devider.gear.redstone"),
                rightLabelX, middleY, rightLabelWidth, rightX, false);
        drawLabelWithArrow(graphics, Component.translatable("overpressure.devider.gear.redstone_inverted_short"),
                rightLabelX, bottomY, rightLabelWidth, rightX, false);
    }

    private void drawLabelWithArrow(
            GuiGraphics graphics,
            Component label,
            int boxX,
            int centerY,
            int boxWidth,
            int nodeX,
            boolean pointsRight
    ) {
        int boxY = centerY - LABEL_HEIGHT / 2;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + LABEL_HEIGHT, 0xFF8C6429);
        graphics.fill(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + LABEL_HEIGHT - 1, 0xFF211A13);
        int textX = boxX + Math.max(4, (boxWidth - font.width(label)) / 2);
        graphics.drawString(font, label, textX, centerY - 4, 0xFFE7D7B1, false);

        int arrowColor = 0xFFD09A3D;
        if (pointsRight) {
            int tipX = nodeX - 10;
            graphics.fill(boxX + boxWidth, centerY - 1, tipX - 2, centerY + 2, arrowColor);
            drawArrowHead(graphics, tipX, centerY, true, arrowColor);
        } else {
            int tipX = nodeX + 10;
            graphics.fill(tipX + 2, centerY - 1, boxX, centerY + 2, arrowColor);
            drawArrowHead(graphics, tipX, centerY, false, arrowColor);
        }
    }

    private static void drawArrowHead(
            GuiGraphics graphics,
            int tipX,
            int centerY,
            boolean pointsRight,
            int color
    ) {
        int direction = pointsRight ? -1 : 1;
        for (int distance = 0; distance <= 4; distance++) {
            int x = tipX + direction * distance;
            graphics.fill(x, centerY - distance, x + 1, centerY + distance + 1, color);
        }
    }

    private static void drawRoutingNode(
            GuiGraphics graphics,
            DeviderBlockEntity.JunctionRole role,
            DeviderBlockEntity.RoutingSelection routing,
            int x,
            int y,
            DeviderBlockEntity.JunctionRole selectedRole,
            DeviderBlockEntity.RoutingSelection selectedRouting
    ) {
        drawNode(graphics, x, y, role == selectedRole && routing == selectedRouting);
    }

    private Vec2 point(int x, int y) {
        return new Vec2(guiLeft + x, guiTop + y);
    }

    private static void drawTrack(
            GuiGraphics graphics,
            int leftX,
            int topY,
            int rightX,
            int middleY,
            int bottomY
    ) {
        graphics.fill(leftX - 2, topY, leftX + 3, bottomY + 1, 0xFF402B16);
        graphics.fill(leftX, middleY - 2, rightX + 1, middleY + 3, 0xFF402B16);
        graphics.fill(rightX - 2, middleY, rightX + 3, bottomY + 1, 0xFF402B16);
        graphics.fill(leftX - 1, topY, leftX + 2, bottomY + 1, 0xFFD09A3D);
        graphics.fill(leftX, middleY - 1, rightX + 1, middleY + 2, 0xFFD09A3D);
        graphics.fill(rightX - 1, middleY, rightX + 2, bottomY + 1, 0xFFD09A3D);
    }

    private static void drawNode(GuiGraphics graphics, int centerX, int centerY, boolean selected) {
        fillCircle(graphics, centerX, centerY, selected ? 9 : 7, 0xFF2B1B0C);
        fillCircle(graphics, centerX, centerY, selected ? 7 : 5, selected ? 0xFFFFD75A : 0xFFB58235);
        if (selected) {
            fillCircle(graphics, centerX, centerY, 3, 0xFFFFFFFF);
        }
    }

    private static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - halfWidth, centerY + y, centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + 2, 0xFFD5A34E);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xFF7B5425);
        graphics.fill(x, y, x + 2, y + height, 0xFFD5A34E);
        graphics.fill(x + width - 2, y, x + width, y + height, 0xFF7B5425);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }
}
