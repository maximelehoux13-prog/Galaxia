package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public final class MouseDebugOverlayWidget extends ParentWidget<MouseDebugOverlayWidget> {

    private final Supplier<Boolean> enabledSupplier;

    public MouseDebugOverlayWidget(Supplier<Boolean> enabledSupplier) {
        this.enabledSupplier = enabledSupplier;
    }

    @Override
    public boolean canHoverThrough() {
        return true;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        if (!enabledSupplier.get()) return;
        int mouseX = getContext().getMouseX();
        int mouseY = getContext().getMouseY();

        Gui.drawRect(mouseX - 6, mouseY, mouseX + 7, mouseY + 1, EnumColors.MAP_COLOR_MOUSE_DEBUG_CROSSHAIR.getColor());
        Gui.drawRect(mouseX, mouseY - 6, mouseX + 1, mouseY + 7, EnumColors.MAP_COLOR_MOUSE_DEBUG_CROSSHAIR.getColor());
        Gui.drawRect(mouseX - 1, mouseY - 1, mouseX + 2, mouseY + 2, EnumColors.MAP_COLOR_MOUSE_DEBUG_CENTER.getColor());

        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            "Mouse " + mouseX + "," + mouseY,
            mouseX + 10,
            mouseY + 8,
            EnumColors.MAP_COLOR_MOUSE_DEBUG_TEXT.getColor());
    }
}
