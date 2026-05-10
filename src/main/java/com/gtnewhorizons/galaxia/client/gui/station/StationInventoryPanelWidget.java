package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.DrawableCommand;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class StationInventoryPanelWidget extends ParentWidget<StationInventoryPanelWidget> {

    static final int BUTTON_WIDTH = 78;
    static final int BUTTON_HEIGHT = 20;
    static final int PANEL_WIDTH = 384;
    static final int PANEL_HEIGHT = 236;

    private static final int PANEL_Y = BUTTON_HEIGHT + 4;
    private static final int HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 2;
    private static final int SCROLL_X = 6;
    private static final int SCROLL_Y = 48;
    private static final int SCROLL_WIDTH = PANEL_WIDTH - 12;
    private static final int SCROLL_HEIGHT = PANEL_HEIGHT - SCROLL_Y - 8;
    private static final int ICON_X = 4;
    private static final int NAME_X = 24;
    private static final int NAME_WIDTH = 150;
    private static final int AMOUNT_X = 180;
    private static final int AMOUNT_INPUT_X = 226;
    private static final int AMOUNT_INPUT_WIDTH = 48;
    private static final int MODE_BUTTON_X = 278;
    private static final int MODE_BUTTON_WIDTH = 54;
    private static final int VOID_X = 336;
    private static final int VOID_WIDTH = 38;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final @Nullable CelestialAsset.ID assetId;
    private final ParentWidget<?> panelRoot = new ParentWidget<>();
    private final Map<String, Boolean> amountModes = new LinkedHashMap<>();
    private final Map<String, String> amountInputs = new LinkedHashMap<>();
    private boolean open;
    private String rowSignature = "";

    StationInventoryPanelWidget(@Nullable CelestialAsset.ID assetId) {
        this.assetId = assetId;
        size(PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        child(
            ModuleConfigModalSupport.button(() -> assetId != null, this::toggleLabel, this::toggleOpen)
                .pos(0, 0)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT));
        panelRoot.pos(0, PANEL_Y)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .setEnabled(false);
        child(panelRoot);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!open) {
            if (panelRoot.isEnabled()) {
                panelRoot.setEnabled(false);
                panelRoot.removeAll();
                rowSignature = "";
            }
            return;
        }
        AutomatedFacility facility = facility();
        if (facility == null) {
            open = false;
            return;
        }
        List<Map.Entry<ItemStackWrapper, Long>> rows = rows(facility);
        String nextSignature = rowSignature(rows);
        if (!panelRoot.isEnabled() || !nextSignature.equals(rowSignature)) {
            rebuildPanel(rows);
            rowSignature = nextSignature;
        }
    }

    @Override
    public boolean canHoverThrough() {
        return !open;
    }

    @Override
    public boolean canHover() {
        return open || super.canHover();
    }

    boolean isPointInPanel(int localX, int localY) {
        return open && localX >= 0 && localX <= PANEL_WIDTH && localY >= PANEL_Y && localY <= PANEL_Y + PANEL_HEIGHT;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawBackground(context, widgetTheme);
        if (!open) return;
        ModuleConfigModalSupport.drawFrameAt("Station Inventory", 0, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        ModuleConfigModalSupport
            .drawLine("Item", NAME_X + SCROLL_X, PANEL_Y + 32, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport
            .drawLine("Amount", AMOUNT_X + SCROLL_X, PANEL_Y + 32, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private void rebuildPanel(List<Map.Entry<ItemStackWrapper, Long>> rows) {
        panelRoot.removeAll();
        panelRoot.setEnabled(true);
        if (rows.isEmpty()) {
            panelRoot.child(
                new TextWidget<>(IKey.str("Inventory is empty.")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(8, 48));
            panelRoot.scheduleResize();
            return;
        }

        VerticalScrollData scrollData = new VerticalScrollData();
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(SCROLL_X, SCROLL_Y)
            .size(SCROLL_WIDTH, SCROLL_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
        ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
        int y = 0;
        for (Map.Entry<ItemStackWrapper, Long> row : rows) {
            String rowKey = row.getKey()
                .toKey();
            amountModes.putIfAbsent(rowKey, false);
            amountInputs.putIfAbsent(rowKey, Long.toString(row.getValue()));
            content.child(buildRow(row).pos(0, y));
            y += ROW_HEIGHT + ROW_GAP;
        }
        int contentHeight = Math.max(SCROLL_HEIGHT, y);
        content.height(contentHeight);
        scrollData.setScrollSize(contentHeight);
        scroll.child(content);
        panelRoot.child(scroll);
        panelRoot.scheduleResize();
    }

    private ParentWidget<?> buildRow(Map.Entry<ItemStackWrapper, Long> row) {
        ItemStackWrapper wrapper = row.getKey();
        ItemStack displayStack = wrapper.toStack(1);
        String rowKey = wrapper.toKey();
        ParentWidget<?> rowWidget = new ParentWidget<>().widthRel(1f)
            .height(ROW_HEIGHT)
            .background(
                drawable(
                    (ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
        rowWidget.child(
            drawable((ctx, x, y, w, h) -> renderItemIcon(displayStack, x, y + 4)).asWidget()
                .pos(ICON_X, 0)
                .size(16, ROW_HEIGHT)
                .tooltip(t -> t.addLine(displayStack.getDisplayName())));
        rowWidget.child(
            drawable(
                (ctx, x, y, w, h) -> ModuleConfigModalSupport.drawTrimmedLine(
                    displayStack.getDisplayName(),
                    x,
                    y + 8,
                    NAME_WIDTH,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor())).asWidget()
                        .pos(NAME_X, 0)
                        .size(NAME_WIDTH, ROW_HEIGHT));
        rowWidget.child(
            new TextWidget<>(IKey.dynamic(() -> formatAmount(currentAmount(wrapper))))
                .color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true)
                .pos(AMOUNT_X, 8));
        rowWidget.child(
            amountField(rowKey).pos(AMOUNT_INPUT_X, 3)
                .size(AMOUNT_INPUT_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> isAmountMode(rowKey), "Amount", () -> setAmountMode(rowKey, false))
                .pos(MODE_BUTTON_X, 3)
                .size(MODE_BUTTON_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> !isAmountMode(rowKey), "ALL", () -> setAmountMode(rowKey, true))
                .pos(MODE_BUTTON_X, 3)
                .size(MODE_BUTTON_WIDTH, 18));
        rowWidget.child(
            ModuleConfigModalSupport.button(() -> currentAmount(wrapper) > 0L, "Void", () -> voidRow(wrapper))
                .pos(VOID_X, 3)
                .size(VOID_WIDTH, 18));
        return rowWidget;
    }

    private TextFieldWidget amountField(String rowKey) {
        return new TextFieldWidget().setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(
                ModuleConfigModalSupport.drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .value(
                new StringValue.Dynamic(
                    () -> amountInputs.getOrDefault(rowKey, "0"),
                    text -> { amountInputs.put(rowKey, text == null ? "" : text); }))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> isAmountMode(rowKey));
    }

    private void toggleOpen() {
        open = !open;
    }

    private String toggleLabel() {
        return open ? "Close Inv" : "Inventory";
    }

    private void setAmountMode(String rowKey, boolean amountMode) {
        amountModes.put(rowKey, amountMode);
    }

    private boolean isAmountMode(String rowKey) {
        return amountModes.getOrDefault(rowKey, false);
    }

    private void voidRow(ItemStackWrapper wrapper) {
        if (assetId == null) return;
        String rowKey = wrapper.toKey();
        long amount = StationInventoryPanelModel
            .voidAmount(isAmountMode(rowKey), currentAmount(wrapper), amountInputs.getOrDefault(rowKey, ""));
        if (amount <= 0L) return;
        if (amount >= currentAmount(wrapper)) {
            CelestialClient.removeInventory(assetId, wrapper);
        } else {
            CelestialClient.removeInventoryAmount(assetId, wrapper, amount);
        }
    }

    private long currentAmount(ItemStackWrapper wrapper) {
        AutomatedFacility facility = facility();
        return facility == null ? 0L : facility.inventory.getAmount(wrapper);
    }

    private @Nullable AutomatedFacility facility() {
        return assetId != null && CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility
            : null;
    }

    private List<Map.Entry<ItemStackWrapper, Long>> rows(AutomatedFacility facility) {
        List<Map.Entry<ItemStackWrapper, Long>> rows = new ArrayList<>(
            facility.inventory.snapshot()
                .entrySet());
        rows.removeIf(row -> row.getValue() <= 0L);
        rows.sort(
            Comparator.comparing(
                row -> row.getKey()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private String rowSignature(List<Map.Entry<ItemStackWrapper, Long>> rows) {
        StringBuilder signature = new StringBuilder(rows.size() * 24);
        for (Map.Entry<ItemStackWrapper, Long> row : rows) {
            signature.append(
                row.getKey()
                    .toKey())
                .append(':')
                .append(row.getValue())
                .append(';');
        }
        return signature.toString();
    }

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || mc.getTextureManager() == null) return;
        com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
        com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 200f;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
    }

    private static String formatAmount(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private com.cleanroommc.modularui.api.drawable.IDrawable drawable(DrawableCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }
}
