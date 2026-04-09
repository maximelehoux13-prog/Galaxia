package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.Supplier;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public final class TransferVisibilityWidget extends ParentWidget<TransferVisibilityWidget> {

    private static final int TOGGLE_X = 82;
    private static final int TOGGLE_Y = 10;
    private static final int TOGGLE_W = 92;
    private static final int TOGGLE_H = 16;

    private final Supplier<Boolean> hiddenSupplier;
    private final Runnable toggleAction;
    private final ButtonWidget<?> toggleButton;
    private String toggleLabel = "Hide Transfers";

    TransferVisibilityWidget(Supplier<Boolean> hiddenSupplier, Runnable toggleAction) {
        this.hiddenSupplier = hiddenSupplier;
        this.toggleAction = toggleAction;
        this.toggleButton = makeButtonDynamic(() -> toggleLabel, () -> {
            toggleAction.run();
            updateLabel();
        }).pos(TOGGLE_X, TOGGLE_Y).size(TOGGLE_W, TOGGLE_H);
        child(toggleButton);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        updateLabel();
    }

    private void updateLabel() {
        toggleLabel = hiddenSupplier.get() ? "Show Transfers" : "Hide Transfers";
    }

    private ButtonWidget<?> makeButtonDynamic(Supplier<String> labelSupplier, Runnable onClick) {
        return new ButtonWidget<>()
            .background(drawable((ctx, x, y, w, h) -> {
                Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor());
                Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .hoverBackground(drawable((ctx, x, y, w, h) -> {
                Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor());
                Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .overlay(IKey.dynamic(labelSupplier::get))
            .onMousePressed(mb -> {
                onClick.run();
                return true;
            });
    }

    private IDrawable drawable(DrawCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    @FunctionalInterface
    private interface DrawCommand {
        void draw(GuiContext ctx, int x, int y, int w, int h);
    }
}
