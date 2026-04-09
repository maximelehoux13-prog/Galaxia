package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.utility.EnumColors;

/**
 * Map overlay that shows aggregated logistics signals for the current map scope.
 *
 * <p>Toggled via a small "Signals" button in the top-left of the map area.
 * Scope follows the map's {@code viewRoot}:
 * <ul>
 *   <li>GALACTIC — galaxy root → all outposts</li>
 *   <li>SYSTEM — star body → outposts sharing that star's system</li>
 *   <li>PLANETARY — planet/moon body → outposts sharing the same planetary anchor</li>
 * </ul>
 *
 * <p>Per-item rows show: item name, net balance (surplus positive / deficit negative),
 * and total units currently in transit within scope.
 * Hovering an item row shows a per-station tooltip filtered to the current scope.
 */
public final class LogisticsSignalsWidget extends ParentWidget<LogisticsSignalsWidget> {

    // ── Layout ──────────────────────────────────────────────────────────────
    private static final int TOGGLE_X = 10;
    private static final int TOGGLE_Y = 10;
    private static final int TOGGLE_W = 66;
    private static final int TOGGLE_H = 16;
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = TOGGLE_Y + TOGGLE_H + 4;
    private static final int PANEL_W = 390;
    private static final int ROW_H = 22;
    private static final int MAX_VISIBLE_ROWS = 20;
    private static final int COL_ICON = 4;
    private static final int COL_NAME = 22;
    private static final int COL_NET = 200;
    private static final int COL_TRANSIT = 300;

    // ── Scope ────────────────────────────────────────────────────────────────
    private enum ViewScope {
        GALACTIC, SYSTEM, PLANETARY
    }

    // ── Config ───────────────────────────────────────────────────────────────
    private final OrbitalCelestialBody galaxyRoot;
    private final Supplier<OrbitalCelestialBody> viewRootSupplier;

    // ── State ────────────────────────────────────────────────────────────────
    private boolean isOpen = false;
    /** Fingerprint of last-built revision. Changing forces a rebuild. */
    private int lastRevision = Integer.MIN_VALUE;
    private OrbitalCelestialBody lastViewRoot = null;

    LogisticsSignalsWidget(OrbitalCelestialBody galaxyRoot, Supplier<OrbitalCelestialBody> viewRootSupplier) {
        this.galaxyRoot = galaxyRoot;
        this.viewRootSupplier = viewRootSupplier;
    }

    // ── Widget lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onUpdate() {
        super.onUpdate();
        OrbitalCelestialBody viewRoot = viewRootSupplier.get();
        int rev = currentRevision(viewRoot);
        if (rev != lastRevision || viewRoot != lastViewRoot) {
            lastRevision = rev;
            lastViewRoot = viewRoot;
            removeAll();
            buildContent(viewRoot);
        }
    }

    private int currentRevision(OrbitalCelestialBody viewRoot) {
        if (!isOpen) return 0;
        int r = 0x1A2B3C4D;
        r = r * 31 + OutpostDataStore.get().clientSignalRevision();
        r = r * 31 + OutpostDataStore.get().clientTaskRevision();
        r = r * 31 + System.identityHashCode(viewRoot);
        return r == 0 ? Integer.MAX_VALUE : r;
    }

    private void rebuildNow() {
        lastRevision = Integer.MIN_VALUE;
        lastViewRoot = null;
        removeAll();
        buildContent(viewRootSupplier.get());
    }

    // ── Content building ─────────────────────────────────────────────────────

    private void buildContent(OrbitalCelestialBody viewRoot) {
        child(makeButton(isOpen ? "Signals \u25b2" : "Signals", () -> {
            isOpen = !isOpen;
            rebuildNow();
        }).pos(TOGGLE_X, TOGGLE_Y).size(TOGGLE_W, TOGGLE_H));

        if (!isOpen) return;

        ViewScope scope = scopeFor(viewRoot);
        List<SignalRow> rows = aggregateSignals(scope, viewRoot);
        int rowsToShow = Math.min(MAX_VISIBLE_ROWS, rows.size());
        int overflow = rows.size() - rowsToShow;
        int panelH = 30 + 16 + rowsToShow * (ROW_H + 2) + (overflow > 0 ? 14 : 0) + (rows.isEmpty() ? 18 : 0) + 8;

        ParentWidget<?> panel = new ParentWidget<>().pos(PANEL_X, PANEL_Y).size(PANEL_W, panelH)
            .background(drawable((ctx, x, y, w, h) -> {
                Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor());
                Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
            }));

        // For PLANETARY scope the data is aggregated by planetary anchor, so use the
        // anchor's name in the title — viewRoot may be a moon whose anchor is its parent planet.
        String planetaryLabelName = viewRoot.name();
        if (scope == ViewScope.PLANETARY) {
            OrbitalCelestialBody anchor = OrbitalTransferPlanner.findPlanetaryAnchor(galaxyRoot, viewRoot);
            if (anchor != null && anchor != viewRoot) planetaryLabelName = anchor.name();
        }
        String scopeLabel = scope == ViewScope.GALACTIC ? "Logistics Signals \u2014 Galaxy"
            : scope == ViewScope.SYSTEM ? "Logistics Signals \u2014 " + viewRoot.name() + " system"
            : "Logistics Signals \u2014 " + planetaryLabelName;
        panel.child(
            new TextWidget<>(IKey.str(scopeLabel)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true).pos(10, 8));
        panel.child(makeButton("X", () -> {
            isOpen = false;
            rebuildNow();
        }).pos(PANEL_W - 26, 5).size(20, 16));

        // Column headers
        panel.child(
            new TextWidget<>(IKey.str("Item")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(COL_NAME, 28));
        panel.child(
            new TextWidget<>(IKey.str("Net balance")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_NET, 28));
        panel.child(
            new TextWidget<>(IKey.str("In transit")).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_TRANSIT, 28));

        if (rows.isEmpty()) {
            panel.child(
                new TextWidget<>(IKey.str("No items tracked in this scope."))
                    .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(10, 46));
        } else {
            int ry = 44;
            for (int i = 0; i < rowsToShow; i++) {
                panel.child(buildSignalRow(rows.get(i), scope, viewRoot).pos(4, ry));
                ry += ROW_H + 2;
            }
            if (overflow > 0) {
                panel.child(
                    new TextWidget<>(IKey.str("\u2026 +" + overflow + " more items"))
                        .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(10, ry));
            }
        }
        child(panel);
    }

    private ParentWidget<?> buildSignalRow(SignalRow row, ViewScope scope, OrbitalCelestialBody viewRoot) {
        ItemStack displayStack = row.item().toStack(1);
        String name = displayStack != null ? displayStack.getDisplayName() : row.item().toKey();
        long net = row.net();
        int netColor = net >= 0 ? 0xFF55FF55 : 0xFFFF6666;
        String netStr = (net >= 0 ? "+" : "") + formatAmount(net);
        String transitStr = row.inTransit() > 0 ? formatAmount(row.inTransit()) : "\u2014";

        // Per-station tooltip lines — filtered to current scope
        List<String> tooltipLines = new ArrayList<>();
        tooltipLines.add(name);
        for (AutomatedOutpostState outpost : OutpostDataStore.get().allOutposts()) {
            if (!isOutpostInScope(outpost, scope, viewRoot)) continue;
            CelestialManagedAsset asset = CelestialAssetStore.findAsset(outpost.assetId);
            if (asset == null) continue;
            long stock = outpost.inventory.getAmount(row.item());
            LogisticsResourceConfig cfg = outpost.logisticsConfig.get(row.item());
            if (stock == 0 && cfg.minReserve() == 0 && !cfg.isImportEnabled() && !cfg.isSupplyEnabled()) continue;
            long localNet = stock - cfg.minReserve();
            String flags = (cfg.isImportEnabled() ? "I" : "-") + (cfg.isSupplyEnabled() ? "E" : "-");
            tooltipLines.add(
                asset.displayName() + " [" + flags + "] " + stock + "/" + cfg.minReserve()
                    + " net:" + (localNet >= 0 ? "+" : "") + localNet);
        }

        ParentWidget<?> rowWidget = new ParentWidget<>().size(PANEL_W - 8, ROW_H)
            .background(drawable((ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

        if (displayStack != null) {
            final ItemStack icon = displayStack;
            final List<String> tip = tooltipLines;
            rowWidget.child(
                drawable((ctx, x, y, w, h) -> renderItemIcon(icon, x + 1, y + 3)).asWidget()
                    .pos(COL_ICON, 0).size(16, ROW_H)
                    .tooltip(t -> { for (String line : tip) t.addLine(line); }));
        }

        rowWidget.child(
            new TextWidget<>(IKey.str(trimToPixels(name, COL_NET - COL_NAME - 6)))
                .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(COL_NAME, 6));
        rowWidget.child(new TextWidget<>(IKey.str(netStr)).color(netColor).pos(COL_NET, 6));
        rowWidget.child(
            new TextWidget<>(IKey.str(transitStr)).color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .pos(COL_TRANSIT, 6));

        return rowWidget;
    }

    // ── Scope helpers ────────────────────────────────────────────────────────

    private ViewScope scopeFor(OrbitalCelestialBody viewRoot) {
        if (viewRoot == null || viewRoot == galaxyRoot
            || viewRoot.objectClass() == CelestialObjectClass.GALAXY) return ViewScope.GALACTIC;
        if (viewRoot.objectClass() == CelestialObjectClass.STAR) return ViewScope.SYSTEM;
        return ViewScope.PLANETARY;
    }

    private boolean isOutpostInScope(AutomatedOutpostState outpost, ViewScope scope, OrbitalCelestialBody viewRoot) {
        switch (scope) {
            case GALACTIC:
                return true;
            case SYSTEM:
                return viewRoot.id().equals(outpost.systemId);
            case PLANETARY: {
                OrbitalCelestialBody outpostBody = OrbitalTransferPlanner.findBodyById(galaxyRoot, outpost.celestialBodyId);
                if (outpostBody == null) return false;
                OrbitalCelestialBody outpostAnchor = OrbitalTransferPlanner.findPlanetaryAnchor(galaxyRoot, outpostBody);
                OrbitalCelestialBody viewAnchor = OrbitalTransferPlanner.findPlanetaryAnchor(galaxyRoot, viewRoot);
                return outpostAnchor != null && outpostAnchor == viewAnchor;
            }
            default:
                return false;
        }
    }

    private boolean isBodyIdInScope(String bodyId, ViewScope scope, OrbitalCelestialBody viewRoot) {
        if (bodyId == null || bodyId.isEmpty()) return false;
        switch (scope) {
            case GALACTIC:
                return true;
            case SYSTEM: {
                OrbitalCelestialBody body = OrbitalTransferPlanner.findBodyById(galaxyRoot, bodyId);
                if (body == null) return false;
                OrbitalCelestialBody star = OrbitalTransferPlanner.findHostStar(galaxyRoot, body);
                return star != null && star == viewRoot;
            }
            case PLANETARY: {
                OrbitalCelestialBody body = OrbitalTransferPlanner.findBodyById(galaxyRoot, bodyId);
                if (body == null) return false;
                OrbitalCelestialBody anchor = OrbitalTransferPlanner.findPlanetaryAnchor(galaxyRoot, body);
                OrbitalCelestialBody viewAnchor = OrbitalTransferPlanner.findPlanetaryAnchor(galaxyRoot, viewRoot);
                return anchor != null && anchor == viewAnchor;
            }
            default:
                return false;
        }
    }

    // ── Aggregation ──────────────────────────────────────────────────────────

    /**
     * Builds the display rows from the server-synced signal data stored in
     * {@link OutpostDataStore}.  Net balance values come from
     * {@link OutpostDataStore#clientSignalsForSystem} /
     * {@link OutpostDataStore#clientSignalsForPlanet}, which mirror exactly what
     * {@link com.gtnewhorizons.galaxia.outpost.logistics.LogisticsSignalStore}
     * held at the last sync cycle — no local re-derivation from raw inventory.
     *
     * <p>In-transit amounts are drawn from {@link OutpostDataStore#clientTasks()}.
     * A task contributes to this scope's in-transit count when <em>either</em>
     * endpoint (source or destination body) falls within the scope — covering
     * both outbound shipments leaving the scope and inbound ones arriving in it.
     */
    private List<SignalRow> aggregateSignals(ViewScope scope, OrbitalCelestialBody viewRoot) {
        // Fetch pre-aggregated net amounts from the client signal store
        Map<String, Long> signalData;
        switch (scope) {
            case SYSTEM:
                signalData = OutpostDataStore.get().clientSignalsForSystem(viewRoot.id());
                break;
            case PLANETARY: {
                OrbitalCelestialBody anchor = OrbitalTransferPlanner.findPlanetaryAnchor(galaxyRoot, viewRoot);
                String anchorId = anchor != null ? anchor.id() : viewRoot.id();
                signalData = OutpostDataStore.get().clientSignalsForPlanet(anchorId);
                break;
            }
            default: // GALACTIC — placeholder, not yet implemented
                signalData = Collections.emptyMap();
                break;
        }

        // slot[0] = net balance (from server signals), slot[1] = in-transit amount
        Map<ItemStackWrapper, long[]> acc = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : signalData.entrySet()) {
            ItemStackWrapper item = ItemStackWrapper.fromKey(e.getKey());
            if (item == null) continue;
            acc.put(item, new long[] { e.getValue(), 0L });
        }

        // Accumulate in-transit amounts from the client task snapshot.
        // A task is counted for this scope when at least one endpoint body is in scope.
        // Items that have no active net signal but are still in transit get a row with
        // net = 0 so they remain visible until the shipment arrives.
        for (OutpostDataStore.ClientLogisticsTask task : OutpostDataStore.get().clientTasks()) {
            boolean fromInScope = isBodyIdInScope(task.fromBodyId(), scope, viewRoot);
            boolean toInScope = isBodyIdInScope(task.toBodyId(), scope, viewRoot);
            if (!fromInScope && !toInScope) continue;
            acc.computeIfAbsent(task.resource(), k -> new long[] { 0L, 0L })[1] += task.amount();
        }

        List<SignalRow> rows = new ArrayList<>(acc.size());
        for (Map.Entry<ItemStackWrapper, long[]> e : acc.entrySet()) {
            rows.add(new SignalRow(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        rows.sort(Comparator.comparingLong((SignalRow r) -> r.net()).reversed());
        return rows;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ButtonWidget<?> makeButton(String label, Runnable onClick) {
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
            .overlay(IKey.str(label))
            .onMousePressed(mb -> { onClick.run(); return true; });
    }

    private IDrawable drawable(DrawCommand cmd) {
        return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
    }

    @FunctionalInterface
    private interface DrawCommand {

        void draw(GuiContext ctx, int x, int y, int w, int h);
    }

    // ── Item rendering ───────────────────────────────────────────────────────

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
        com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
        com.cleanroommc.modularui.utils.GlStateManager.scale(1f, 1f, 1f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        net.minecraft.client.renderer.entity.RenderItem ri = net.minecraft.client.renderer.entity.RenderItem
            .getInstance();
        float prevZ = ri.zLevel;
        ri.zLevel = 200f;
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(
            net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        ri.zLevel = prevZ;
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
    }

    // ── Formatting ───────────────────────────────────────────────────────────

    private static String formatAmount(long v) {
        long abs = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (abs < 1_000) return String.valueOf(v);
        if (abs < 1_000_000) return sign + (abs / 1_000) + "k";
        return sign + (abs / 1_000_000) + "M";
    }

    private static String trimToPixels(String s, int maxPx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || s == null) return s;
        return mc.fontRenderer.trimStringToWidth(s, maxPx);
    }

    // ── Data record ──────────────────────────────────────────────────────────

    @Desugar
    private record SignalRow(ItemStackWrapper item, long net, long inTransit) {}
}
