package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.orbitalGUI.GalaxiaRegistry;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class GalacticChartGui {

    private static final int LEFT_PANEL_WIDTH = 216;

    private static final class MouseDebugOverlayWidget extends ParentWidget<MouseDebugOverlayWidget> {

        private final Supplier<Boolean> enabledSupplier;

        private MouseDebugOverlayWidget(Supplier<Boolean> enabledSupplier) {
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

            Gui.drawRect(
                mouseX - 6,
                mouseY,
                mouseX + 7,
                mouseY + 1,
                EnumColors.MAP_COLOR_MOUSE_DEBUG_CROSSHAIR.getColor());
            Gui.drawRect(
                mouseX,
                mouseY - 6,
                mouseX + 1,
                mouseY + 7,
                EnumColors.MAP_COLOR_MOUSE_DEBUG_CROSSHAIR.getColor());
            Gui.drawRect(
                mouseX - 1,
                mouseY - 1,
                mouseX + 2,
                mouseY + 2,
                EnumColors.MAP_COLOR_MOUSE_DEBUG_CENTER.getColor());

            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                "Mouse " + mouseX + "," + mouseY,
                mouseX + 10,
                mouseY + 8,
                EnumColors.MAP_COLOR_MOUSE_DEBUG_TEXT.getColor());
        }
    }

    public ModularPanel build(PanelSyncManager syncManager) {
        ModularPanel panel = ModularPanel.defaultPanel("galactic_orbital_map")
            .fullScreenInvisible();
        OrbitalCelestialBody galaxyRoot = GalaxiaRegistry.root();
        int currentDimension = Minecraft.getMinecraft().thePlayer == null ? 0
            : Minecraft.getMinecraft().thePlayer.dimension;
        OrbitalCelestialBody currentStar = GalaxiaRegistry.findCurrentStar(currentDimension)
            .orElseGet(
                () -> GalaxiaRegistry.getPrimaryStar()
                    .orElse(galaxyRoot));
        TextFieldWidget renameField = new TextFieldWidget().left(LEFT_PANEL_WIDTH)
            .top(-1000)
            .width(180)
            .height(22)
            .setMaxLength(48)
            .setTextColor(EnumColors.MapSidebarSearchInput.getColor())
            .hintText("Asset name")
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .setFocusOnGuiOpen(false);
        GalacticMapWidget galacticMap = new GalacticMapWidget(galaxyRoot, currentStar, renameField);
        MouseDebugOverlayWidget mouseDebugOverlay = new MouseDebugOverlayWidget(
            () -> galacticMap.mapWidget()
                .isDebugOverlayEnabled());
        CelestialSidebarWidget sidebar = new CelestialSidebarWidget(galaxyRoot, currentStar, galacticMap.mapWidget());
        return panel.child(
            (IWidget) mouseDebugOverlay.left(0)
                .top(0)
                .widthRel(1f)
                .heightRel(1f))
            .child(
            (IWidget) sidebar.left(0)
                .top(0)
                .width(LEFT_PANEL_WIDTH)
                .heightRel(1f))
            .child(
                (IWidget) galacticMap.left(LEFT_PANEL_WIDTH)
                    .top(0)
                    .widthRelOffset(1f, -LEFT_PANEL_WIDTH)
                    .heightRel(1f))
            .child((IWidget) renameField);
    }
}
