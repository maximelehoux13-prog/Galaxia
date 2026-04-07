package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.outpost.network.OutpostBuildModulePacket;
import com.gtnewhorizons.galaxia.outpost.network.OutpostModuleActionPacket;
import com.gtnewhorizons.galaxia.outpost.network.OutpostModuleConfigPacket;
import com.gtnewhorizons.galaxia.outpost.network.OutpostRequestSyncPacket;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.PowerModuleData;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyAssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.utility.EnumColors;

@Desugar
record ButtonRect(int left, int top, int right, int bottom) {

    boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}

@Desugar
record ModalBounds(int left, int top, int right, int bottom) {}

@Desugar
record PendingAssetCreation(String celestialObjectId, String displayName, CelestialAssetKind kind,
    CelestialAssetLocation location, List<CelestialAssetRequirement> requiredResources) {}

@Desugar
record PendingAssetRename(CelestialManagedAsset asset) {}

@Desugar
record PendingAssetDestruction(CelestialManagedAsset asset, boolean armed) {}

@Desugar
record PendingAssetManagement(CelestialManagedAsset asset) {}

@Desugar
record PendingConstructionCancellation(CelestialManagedAsset asset) {}

@Desugar
record PendingResourceTransfer(CelestialManagedAsset asset, List<StationTransferTarget> targets) {}

@Desugar
record StationTransferTarget(String assetId, String displayName, OrbitalCelestialBody hostBody) {}

@Desugar
record TransferTargetRow(StationTransferTarget target, int left, int top, int right, int bottom,
    ButtonRect sendButton) {}

@Desugar
record PinnedInfoRow(String label, String value, List<ItemStack> items, boolean inlineItems) {

    static PinnedInfoRow section(String label) {
        return new PinnedInfoRow(label, "", Collections.emptyList(), false);
    }

    static PinnedInfoRow inlineItems(String value, List<ItemStack> items) {
        return new PinnedInfoRow("", value, items, true);
    }

    PinnedInfoRow(String label, String value) {
        this(label, value, Collections.emptyList(), false);
    }

    PinnedInfoRow(String label, String value, List<ItemStack> items) {
        this(label, value, items, false);
    }
}

public final class AssetManagementSystem {

    public static final class OrbitalAssetSupport {

        boolean hasStoredConstructionResources(CelestialManagedAsset asset) {
            if (asset == null) return false;
            for (CelestialAssetRequirement entry : asset.constructionInventory()) if (entry.amount() > 0) return true;
            return false;
        }

        boolean isManageableStationAsset(CelestialManagedAsset asset) {
            if (asset == null || asset.status() != CelestialAssetStatus.OPERATIONAL) return false;
            return asset.kind() == CelestialAssetKind.STATION || asset.kind() == CelestialAssetKind.AUTOMATED_STATION
                || asset.kind() == CelestialAssetKind.AUTOMATED_OUTPOST;
        }

        String formatAssetDisplayName(CelestialManagedAsset asset) {
            return switch (asset.status()) {
                case CONSTRUCTION_SITE -> asset.displayName() + " (In construction)";
                case DECONSTRUCTION -> asset.displayName() + " (Deconstruction)";
                default -> asset.displayName();
            };
        }

        String buildConstructionInventorySummary(CelestialManagedAsset asset) {
            if (asset.status() == CelestialAssetStatus.DECONSTRUCTION)
                return buildStoredInventorySummary(asset.constructionInventory());
            if (asset.requiredResources()
                .isEmpty()) return "Empty";
            StringBuilder sb = new StringBuilder();
            for (CelestialAssetRequirement required : asset.requiredResources()) {
                long storedAmount = 0;
                for (CelestialAssetRequirement stored : asset.constructionInventory())
                    if (required.matches(stored.stack())) storedAmount += stored.amount();
                if (sb.length() > 0) sb.append(", ");
                sb.append(storedAmount)
                    .append('/')
                    .append(required.amount())
                    .append(' ')
                    .append(required.displayName());
            }
            return sb.toString();
        }

        List<StationTransferTarget> getTransferTargetsInSystem(OrbitalCelestialBody root, OrbitalCelestialBody body) {
            List<StationTransferTarget> targets = new ArrayList<>();
            if (body == null) return targets;
            OrbitalCelestialBody hostStar = findHostStar(root, body, null);
            if (hostStar == null) return targets;
            collectTargets(hostStar, targets);
            return targets;
        }

        private void collectTargets(OrbitalCelestialBody current, List<StationTransferTarget> targets) {
            CelestialBodyAssetState state = CelestialAssetStore.getState(current.id());
            for (CelestialManagedAsset asset : state.assets()) {
                if (asset.status() == CelestialAssetStatus.OPERATIONAL
                    && asset.location() == CelestialAssetLocation.ORBIT
                    && (asset.kind() == CelestialAssetKind.STATION
                        || asset.kind() == CelestialAssetKind.AUTOMATED_STATION)) {
                    targets.add(new StationTransferTarget(asset.assetId(), asset.displayName(), current));
                }
            }
            for (OrbitalCelestialBody child : current.children()) collectTargets(child, targets);
        }

        String formatAssetKind(CelestialAssetKind kind) {
            return switch (kind) {
                case STATION -> "Station";
                case AUTOMATED_STATION -> "Automated Station";
                case AUTOMATED_OUTPOST -> "Automated Outpost";
            };
        }

        String formatAssetLocation(CelestialAssetLocation location) {
            return switch (location) {
                case ORBIT -> "Orbit";
                case SURFACE -> "Surface";
            };
        }

        private String buildStoredInventorySummary(List<CelestialAssetRequirement> storedResources) {
            if (storedResources.isEmpty()) return "Empty";
            StringBuilder sb = new StringBuilder();
            for (CelestialAssetRequirement stored : storedResources) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(stored.amount())
                    .append(' ')
                    .append(stored.displayName());
            }
            return sb.toString();
        }

        private OrbitalCelestialBody findHostStar(OrbitalCelestialBody current, OrbitalCelestialBody target,
            OrbitalCelestialBody currentStar) {
            OrbitalCelestialBody nextStar = current.objectClass() == CelestialObjectClass.STAR ? current : currentStar;
            if (current == target) return nextStar;
            for (OrbitalCelestialBody child : current.children()) {
                OrbitalCelestialBody found = findHostStar(child, target, nextStar);
                if (found != null) return found;
            }
            return null;
        }

    }

    public static final class OrbitalAssetActionController {

        interface Callbacks {

            boolean isCreativeBuildModeEnabled();

            void showActionStatus(String message);

            void beginRenameInput(String currentText);

            void endRenameInput();

            String getRenameInput();

            void createResourceTransfer(OrbitalCelestialBody sourceBody, CelestialManagedAsset sourceAsset,
                StationTransferTarget target);
        }

        private final OrbitalAssetSupport assetSupport;
        private final Callbacks callbacks;

        OrbitalAssetActionController(OrbitalAssetSupport assetSupport, Callbacks callbacks) {
            this.assetSupport = assetSupport;
            this.callbacks = callbacks;
        }

        void openAssetManagement(OrbitalAssetUiState state, OrbitalCelestialBody body) {
            if (body == null || body.objectClass() == CelestialObjectClass.GALAXY) return;
            state.openAssetManagement(body);
            closePendingAssetRename(state);
        }

        void closeAssetManagement(OrbitalAssetUiState state) {
            state.closeAssetManagement();
            closePendingAssetRename(state);
        }

        void createBaseStation(OrbitalCelestialBody body) {
            if (body == null) return;
            CelestialAssetStore.createOperationalAsset(
                body.id(),
                buildDefaultAssetDisplayName(body, CelestialAssetKind.STATION),
                CelestialAssetKind.STATION,
                getDefaultAssetLocation(CelestialAssetKind.STATION));
            callbacks.showActionStatus("Station created");
        }

        void triggerAssetCreation(OrbitalAssetUiState state, OrbitalCelestialBody body, CelestialAssetKind kind,
            boolean openManagementFirst) {
            if (body == null) return;
            if (openManagementFirst) openAssetManagement(state, body);
            CelestialAssetLocation location = getDefaultAssetLocation(kind);
            String displayName = buildDefaultAssetDisplayName(body, kind);
            if (callbacks.isCreativeBuildModeEnabled()) {
                CelestialAssetStore.createOperationalAsset(body.id(), displayName, kind, location);
                callbacks.showActionStatus(assetSupport.formatAssetKind(kind) + " created");
                return;
            }
            state.pendingAssetCreation = new PendingAssetCreation(
                body.id(),
                displayName,
                kind,
                location,
                CelestialAssetStore.previewRequirements(kind));
        }

        void confirmPendingAssetCreation(OrbitalAssetUiState state) {
            if (state.pendingAssetCreation == null) return;
            if (callbacks.isCreativeBuildModeEnabled()) {
                CelestialAssetStore.createOperationalAsset(
                    state.pendingAssetCreation.celestialObjectId(),
                    state.pendingAssetCreation.displayName(),
                    state.pendingAssetCreation.kind(),
                    state.pendingAssetCreation.location());
                callbacks
                    .showActionStatus(assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " created");
            } else {
                CelestialAssetStore.createAssetInConstruction(
                    state.pendingAssetCreation.celestialObjectId(),
                    state.pendingAssetCreation.displayName(),
                    state.pendingAssetCreation.kind(),
                    state.pendingAssetCreation.location());
                callbacks.showActionStatus(
                    assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " construction planned");
            }
            state.pendingAssetCreation = null;
        }

        void dismissPendingAssetCreation(OrbitalAssetUiState state) {
            state.pendingAssetCreation = null;
        }

        void openPendingAssetRename(OrbitalAssetUiState state, CelestialManagedAsset asset) {
            if (asset == null) return;
            state.pendingAssetRename = new PendingAssetRename(asset);
            callbacks.beginRenameInput(asset.displayName());
        }

        void closePendingAssetRename(OrbitalAssetUiState state) {
            state.pendingAssetRename = null;
            callbacks.endRenameInput();
        }

        void openPendingAssetDestruction(OrbitalAssetUiState state, CelestialManagedAsset asset) {
            if (asset == null) return;
            state.pendingAssetDestruction = new PendingAssetDestruction(asset, false);
        }

        void dismissPendingAssetDestruction(OrbitalAssetUiState state) {
            state.pendingAssetDestruction = null;
        }

        void advancePendingAssetDestruction(OrbitalAssetUiState state) {
            if (state.pendingAssetDestruction == null) return;
            if (!state.pendingAssetDestruction.armed()) {
                state.pendingAssetDestruction = new PendingAssetDestruction(
                    state.pendingAssetDestruction.asset(),
                    true);
                return;
            }
            CelestialAssetStore.destroyAsset(
                state.pendingAssetDestruction.asset()
                    .assetId());
            callbacks.showActionStatus("Asset destroyed");
            state.pendingAssetDestruction = null;
        }

        void openPendingAssetManagement(OrbitalAssetUiState state, CelestialManagedAsset asset) {
            if (asset == null || !assetSupport.isManageableStationAsset(asset)) return;
            state.pendingAssetManagement = new PendingAssetManagement(asset);
        }

        void closePendingAssetManagement(OrbitalAssetUiState state) {
            state.pendingAssetManagement = null;
        }

        void openPendingConstructionCancellation(OrbitalAssetUiState state, CelestialManagedAsset asset) {
            if (asset == null) return;
            state.pendingConstructionCancellation = new PendingConstructionCancellation(asset);
        }

        void dismissPendingConstructionCancellation(OrbitalAssetUiState state) {
            state.pendingConstructionCancellation = null;
        }

        void confirmPendingConstructionCancellation(OrbitalAssetUiState state) {
            if (state.pendingConstructionCancellation == null) return;
            CelestialAssetStore.startDeconstruction(
                state.pendingConstructionCancellation.asset()
                    .assetId());
            callbacks.showActionStatus("Construction site converted to deconstruction");
            state.pendingConstructionCancellation = null;
        }

        void openPendingResourceTransfer(OrbitalAssetUiState state, OrbitalCelestialBody root,
            CelestialManagedAsset asset) {
            if (asset == null) return;
            state.pendingResourceTransfer = new PendingResourceTransfer(
                asset,
                assetSupport.getTransferTargetsInSystem(root, state.assetManagementBody));
        }

        void dismissPendingResourceTransfer(OrbitalAssetUiState state) {
            state.pendingResourceTransfer = null;
        }

        void sendPendingResourceTransfer(OrbitalAssetUiState state, StationTransferTarget target) {
            if (state.pendingResourceTransfer != null) {
                callbacks
                    .createResourceTransfer(state.assetManagementBody, state.pendingResourceTransfer.asset(), target);
            }
            state.pendingResourceTransfer = null;
        }

        void confirmPendingAssetRename(OrbitalAssetUiState state) {
            if (state.pendingAssetRename == null) return;
            String renamed = callbacks.getRenameInput()
                .trim();
            if (renamed.isEmpty()) {
                callbacks.showActionStatus("Name cannot be empty");
                return;
            }
            if (renamed.equals(
                state.pendingAssetRename.asset()
                    .displayName())) {
                closePendingAssetRename(state);
                return;
            }
            if (CelestialAssetStore.renameAsset(
                state.pendingAssetRename.asset()
                    .assetId(),
                renamed)) {
                callbacks.showActionStatus("Asset renamed");
                closePendingAssetRename(state);
                return;
            }
            callbacks.showActionStatus("Rename failed");
        }

        void dismissPendingModalByOutsideClick(OrbitalAssetUiState state) {
            if (state.pendingAssetRename != null) {
                closePendingAssetRename(state);
                return;
            }
            if (state.pendingResourceTransfer != null) {
                dismissPendingResourceTransfer(state);
                return;
            }
            if (state.pendingAssetManagement != null) {
                closePendingAssetManagement(state);
                return;
            }
            if (state.pendingConstructionCancellation != null) {
                dismissPendingConstructionCancellation(state);
                return;
            }
            if (state.pendingAssetDestruction != null) {
                dismissPendingAssetDestruction(state);
                return;
            }
            if (state.pendingAssetCreation != null) dismissPendingAssetCreation(state);
        }

        private String buildDefaultAssetDisplayName(OrbitalCelestialBody body, CelestialAssetKind kind) {
            return body.displayName() + " " + assetSupport.formatAssetKind(kind);
        }

        private CelestialAssetLocation getDefaultAssetLocation(CelestialAssetKind kind) {
            return kind == CelestialAssetKind.AUTOMATED_OUTPOST ? CelestialAssetLocation.SURFACE
                : CelestialAssetLocation.ORBIT;
        }
    }

    public static final class OrbitalAssetUiState {

        OrbitalCelestialBody assetManagementBody;
        PendingAssetCreation pendingAssetCreation;
        PendingAssetDestruction pendingAssetDestruction;
        PendingConstructionCancellation pendingConstructionCancellation;
        PendingResourceTransfer pendingResourceTransfer;
        PendingAssetManagement pendingAssetManagement;
        PendingAssetRename pendingAssetRename;
        int assetManagementTab = 0;
        /** Index of the module whose configuration sub-menu is open; -1 when none. */
        int configuringModuleIndex = -1;

        boolean isAssetManagementOpen() {
            return assetManagementBody != null;
        }

        boolean hasBlockingModal() {
            return pendingAssetCreation != null || pendingAssetDestruction != null
                || pendingConstructionCancellation != null
                || pendingResourceTransfer != null
                || pendingAssetManagement != null
                || pendingAssetRename != null;
        }

        void openAssetManagement(OrbitalCelestialBody body) {
            assetManagementBody = body;
            clearTransientState();
        }

        void closeAssetManagement() {
            assetManagementBody = null;
            clearTransientState();
        }

        void clearTransientState() {
            pendingAssetCreation = null;
            pendingAssetDestruction = null;
            pendingConstructionCancellation = null;
            pendingResourceTransfer = null;
            pendingAssetManagement = null;
            pendingAssetRename = null;
            assetManagementTab = 0;
            configuringModuleIndex = -1;
        }
    }

    public static final class OrbitalAssetManagementWidget extends ParentWidget<OrbitalAssetManagementWidget> {

        interface Callbacks {

            boolean isCreativeBuildModeEnabled();

            boolean isGT5AutomationAvailable();

            boolean canCreateBaseStation(OrbitalCelestialBody body);

            boolean canCreateAutomatedStation(OrbitalCelestialBody body);

            boolean canCreateAutomatedOutpost(OrbitalCelestialBody body);

            boolean hasStoredConstructionResources(CelestialManagedAsset asset);

            boolean isManageableStationAsset(CelestialManagedAsset asset);

            String formatAssetDisplayName(CelestialManagedAsset asset);

            String buildConstructionInventorySummary(CelestialManagedAsset asset);

            String formatAssetKind(CelestialAssetKind kind);

            String formatAssetLocation(CelestialAssetLocation location);

            void drawAssetIcon(CelestialAssetKind kind, int x, int y, int size, float alpha);

            void closeAssetManagement();

            void createBaseStation(OrbitalCelestialBody body);

            void triggerAssetCreation(OrbitalCelestialBody body, CelestialAssetKind kind, boolean openManagementFirst);

            void openPendingAssetRename(CelestialManagedAsset asset);

            void openPendingConstructionCancellation(CelestialManagedAsset asset);

            void openPendingResourceTransfer(CelestialManagedAsset asset);

            void openPendingAssetManagement(CelestialManagedAsset asset);

            void openPendingAssetDestruction(CelestialManagedAsset asset);

            void confirmPendingAssetCreation();

            void dismissPendingAssetCreation();

            void closePendingAssetRename();

            void confirmPendingAssetRename();

            void dismissPendingAssetDestruction();

            void advancePendingAssetDestruction();

            void dismissPendingConstructionCancellation();

            void confirmPendingConstructionCancellation();

            void dismissPendingResourceTransfer();

            void sendPendingResourceTransfer(StationTransferTarget target);

            void closePendingAssetManagement();

            void dismissPendingModalByOutsideClick();

            void showActionStatus(String message);
        }

        private static final int MODAL_MAX_WIDTH = 520;
        private static final int MODAL_MAX_HEIGHT = 420;
        private static final int MODAL_MARGIN_X = 80;
        private static final int MODAL_MARGIN_Y = 60;
        private static final int HEADER_HEIGHT = 28;
        private static final int CONTENT_TOP = 54;
        private static final int CONTENT_PADDING = 10;
        private static final int CONTENT_SCROLLBAR_GAP = 14;
        private static final int ROW_HEIGHT = 42;
        private static final int ROW_SPACING = 6;
        private static final int ICON_BUTTON_SIZE = 22;
        private static final int FOOTER_BUTTON_HEIGHT = 20;
        private static final int RENAME_INPUT_HEIGHT = 22;
        private static final int RENAME_MODAL_WIDTH = 340;
        private static final int RENAME_INPUT_PADDING = 14;

        private final OrbitalAssetUiState state;
        private final Callbacks callbacks;

        private int structureVersion = 0;
        private int contentVersion = 0;
        private int lastStructureVersion = -1;
        private int lastContentVersion = -1;
        private boolean lastOutpostStatePresent = false;
        private int lastOutpostSyncRevision = -1;

        private int modalLeft, modalTop, modalRight, modalBottom;
        private int scrollLeft, scrollTop, scrollRight, scrollBottom;
        private ScrollWidget<?> activeScrollWidget;
        private ScrollWidget<?> mainScrollWidget;
        private ParentWidget<?> mainScrollContent;
        private VerticalScrollData mainScrollData;
        private int mainContentWidth, mainContentHeight;

        OrbitalAssetManagementWidget(OrbitalAssetUiState state, Callbacks callbacks) {
            this.state = state;
            this.callbacks = callbacks;
            setEnabled(false);
            background(
                drawable(
                    (c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_OVERLAY_BG.getColor())));
        }

        public void markStructureDirty() {
            structureVersion++;
        }

        public void markContentDirty() {
            contentVersion++;
        }

        boolean isPointInScrollViewport(int localX, int localY) {
            return shouldShowPanel() && localX >= scrollLeft
                && localX <= scrollRight
                && localY >= scrollTop
                && localY <= scrollBottom;
        }

        ButtonRect getRenameInputBounds() {
            if (state.pendingAssetRename == null) return null;
            ModalBounds bounds = createCenteredModalBounds(RENAME_MODAL_WIDTH, 126);
            return new ButtonRect(
                bounds.left() + RENAME_INPUT_PADDING,
                bounds.top() + CONTENT_TOP + 4,
                bounds.right() - RENAME_INPUT_PADDING,
                bounds.top() + CONTENT_TOP + 4 + RENAME_INPUT_HEIGHT);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();

            boolean visible = shouldShowOverlay();
            if (!visible) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                clearBounds();
                clearMainPanelState();
                activeScrollWidget = null;
                lastStructureVersion = -1;
                lastContentVersion = -1;
                lastOutpostStatePresent = false;
                lastOutpostSyncRevision = -1;
                setEnabled(false);
                return;
            }

            setEnabled(true);

            // Handle asynchronous data arrival for automated outposts
            if (state.pendingAssetManagement != null) {
                boolean present = OutpostDataStore.get().getByAssetId(state.pendingAssetManagement.asset().assetId()) != null;
                if (present && !lastOutpostStatePresent) {
                    markStructureDirty();
                }
                if (present) {
                    AutomatedOutpostState outpost = OutpostDataStore.get()
                        .getByAssetId(state.pendingAssetManagement.asset().assetId());
                    if (outpost != null && outpost.getSyncRevision() != lastOutpostSyncRevision) {
                        lastOutpostSyncRevision = outpost.getSyncRevision();
                        markStructureDirty();
                    }
                } else {
                    lastOutpostSyncRevision = -1;
                }
                lastOutpostStatePresent = present;
            } else {
                lastOutpostStatePresent = false;
                lastOutpostSyncRevision = -1;
            }

            // Consume item picker result — works even if the starmap was closed and reopened
            // between the button click and the user returning from the item picker screen.
            if (ItemPickerScreen.hasPendingPickForOutpost()) {
                String targetId = ItemPickerScreen.getPendingForOutpostId();
                ItemStack pickedStack = ItemPickerScreen.pollPendingPickForOutpost();
                AutomatedOutpostState outpost = targetId != null
                    ? OutpostDataStore.get().getByAssetId(targetId)
                    : null;
                if (pickedStack != null && outpost != null) {
                    ItemStackWrapper wrapper = ItemStackWrapper.of(pickedStack);
                    if (wrapper != null && outpost.logisticsConfig.get(wrapper) == LogisticsResourceConfig.DEFAULT) {
                        LogisticsResourceConfig newCfg = new LogisticsResourceConfig(0, 64, false, false);
                        outpost.logisticsConfig.set(wrapper, newCfg);
                        Galaxia.GALAXIA_NETWORK.sendToServer(
                            new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, newCfg));
                    }
                    // Refresh the modal if the correct outpost is currently open
                    if (state.pendingAssetManagement != null
                        && state.pendingAssetManagement.asset().assetId().equals(targetId)) {
                        markStructureDirty();
                    }
                }
            }
            if (ItemPickerScreen.hasPendingPickForMinerBlacklist()) {
                String targetId = ItemPickerScreen.getPendingForOutpostId();
                int moduleIndex = ItemPickerScreen.getPendingModuleIndex();
                ItemStack pickedStack = ItemPickerScreen.pollPendingPickForMinerBlacklist();
                AutomatedOutpostState outpost = targetId != null
                    ? OutpostDataStore.get().getByAssetId(targetId)
                    : null;
                if (pickedStack != null && outpost != null && moduleIndex >= 0 && moduleIndex < outpost.modules().size()) {
                    ItemStackWrapper wrapper = ItemStackWrapper.of(pickedStack);
                    if (wrapper != null) {
                        AutomatedOutpostModule module = outpost.modules().get(moduleIndex);
                        if (module.getData() instanceof MinerModuleData minerData) {
                            module.setData(minerData.withAddedBlacklist(wrapper.toKey()));
                            Galaxia.GALAXIA_NETWORK.sendToServer(
                                new OutpostModuleConfigPacket(outpost.assetId, moduleIndex, "ADD_MINER_BLACKLIST",
                                    wrapper.toKey()));
                        }
                    }
                    if (state.pendingAssetManagement != null
                        && state.pendingAssetManagement.asset().assetId().equals(targetId)) {
                        markStructureDirty();
                    }
                }
            }

            if (structureVersion != lastStructureVersion) {
                rebuildChildren();
                lastStructureVersion = structureVersion;
                lastContentVersion = contentVersion;
                return;
            }

            if (shouldShowPanel() && contentVersion != lastContentVersion) {
                refreshMainPanelContent();
                lastContentVersion = contentVersion;
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!shouldShowOverlay()) return;
            super.drawBackground(context, widgetTheme);
        }

        private boolean shouldShowOverlay() {
            return state.isAssetManagementOpen();
        }

        private boolean shouldShowPanel() {
            return state.isAssetManagementOpen() && !state.hasBlockingModal();
        }

        private void rebuildChildren() {
            clearMainPanelState();
            activeScrollWidget = null;
            removeAll();
            clearBounds();
            OrbitalCelestialBody body = state.assetManagementBody;
            if (body == null) return;
            child(createBackdropButton());
            if (state.hasBlockingModal()) {
                buildPendingModal();
                return;
            }
            buildMainPanel(body);
            refreshMainPanelContent();
        }

        private void buildMainPanel(OrbitalCelestialBody body) {
            ModalBounds bounds = calculateManagementBounds();
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            int modalWidth = bounds.right() - bounds.left();
            int modalHeight = bounds.bottom() - bounds.top();
            int contentHeight = modalHeight - CONTENT_TOP - 12;
            int contentWidth = modalWidth - (CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP;
            scrollLeft = bounds.left() + CONTENT_PADDING;
            scrollTop = bounds.top() + CONTENT_TOP;
            scrollRight = scrollLeft + contentWidth;
            scrollBottom = scrollTop + contentHeight;
            mainContentWidth = contentWidth;
            mainContentHeight = contentHeight;
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(createTitleText("Manage Assets").pos(12, 10));
            int titleRight = 12 + Minecraft.getMinecraft().fontRenderer.getStringWidth("Manage Assets");
            int assetNameMaxWidth = Math.max(0, modalWidth - 40 - (titleRight + 24));
            if (assetNameMaxWidth > 0) {
                String assetName = trimToWidth(body.displayName(), assetNameMaxWidth);
                int assetNameWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(assetName);
                int assetNameX = Math.max(titleRight + 12, modalWidth - 40 - assetNameWidth);
                modal.child(createBodyText(assetName, EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(assetNameX, 10));
            }
            modal.child(
                createGlyphButton(AssetManagerButtonGlyph.CLOSE, "Close", true, callbacks::closeAssetManagement)
                    .pos(modalWidth - 28, 6));
            modal.child(
                createAssetKindButton(
                    CelestialAssetKind.STATION,
                    "Create Station",
                    callbacks.canCreateBaseStation(body),
                    () -> callbacks.createBaseStation(body)).pos(14, 30));
            modal.child(
                createAssetKindButton(
                    CelestialAssetKind.AUTOMATED_STATION,
                    "Create Automated Station",
                    callbacks.canCreateAutomatedStation(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAssetKind.AUTOMATED_STATION, false))
                        .pos(42, 30));
            modal.child(
                createAssetKindButton(
                    CelestialAssetKind.AUTOMATED_OUTPOST,
                    "Create Automated Outpost",
                    callbacks.canCreateAutomatedOutpost(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAssetKind.AUTOMATED_OUTPOST, false))
                        .pos(70, 30));
            if (!callbacks.isGT5AutomationAvailable()) {
                modal.child(
                    createBodyText("GT5U required for automated assets", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(104, 36));
            }
            VerticalScrollData scrollData = new VerticalScrollData();
            mainScrollData = scrollData;
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(CONTENT_PADDING, CONTENT_TOP)
                .widthRelOffset(1f, -(CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP)
                .heightRelOffset(1f, -(CONTENT_TOP + 12))
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            activeScrollWidget = scroll;
            mainScrollWidget = scroll;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f)
                .height(contentHeight);
            mainScrollContent = content;
            scroll.child(content);
            modal.child(scroll);
            child(modal);
        }

        private void refreshMainPanelContent() {
            if (!shouldShowPanel() || mainScrollContent == null || mainScrollWidget == null || mainScrollData == null)
                return;
            OrbitalCelestialBody body = state.assetManagementBody;
            if (body == null) return;
            CelestialBodyAssetState assetState = CelestialAssetStore.getState(body.id());
            int contentScrollSize = Math.max(mainContentHeight, computeContentHeight(assetState));
            mainScrollData.setScrollSize(contentScrollSize);
            mainScrollContent.removeAll();
            mainScrollContent.widthRel(1f)
                .height(contentScrollSize);
            populateContent(mainScrollContent, mainContentWidth, assetState);
            mainScrollContent.scheduleResize();
            mainScrollWidget.scheduleResize();
        }

        private void buildPendingModal() {
            activeScrollWidget = null;
            scrollLeft = scrollTop = scrollRight = scrollBottom = 0;
            if (state.pendingAssetCreation != null) {
                buildPendingAssetCreationModal();
                return;
            }
            if (state.pendingAssetDestruction != null) {
                buildPendingAssetDestructionModal();
                return;
            }
            if (state.pendingConstructionCancellation != null) {
                buildPendingConstructionCancellationModal();
                return;
            }
            if (state.pendingResourceTransfer != null) {
                buildPendingResourceTransferModal();
                return;
            }
            if (state.pendingAssetManagement != null) {
                buildPendingAssetManagementModal();
                return;
            }
            if (state.pendingAssetRename != null) buildPendingAssetRenameModal();
        }

        private void buildPendingAssetCreationModal() {
            PendingAssetCreation creation = state.pendingAssetCreation;
            if (creation == null) return;
            int height = 150 + Math.max(
                0,
                creation.requiredResources()
                    .size() - 2)
                * 12;
            ModalBounds bounds = createCenteredModalBounds(320, height);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(
                createAssetIconWidget(creation.kind(), 1.0f).pos(12, 10)
                    .size(18, 18));
            modal.child(createTitleText("Confirm " + callbacks.formatAssetKind(creation.kind())).pos(36, 10));
            modal.child(createBodyText(creation.displayName(), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(36, 28));
            modal.child(createSectionText("Required resources").pos(12, 52));
            int resourceY = 68;
            for (CelestialAssetRequirement requirement : creation.requiredResources()) {
                modal.child(
                    createBodyText(
                        "- " + requirement.amount() + " " + requirement.displayName(),
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(16, resourceY));
                resourceY += 12;
            }
            addFooterButtons(
                modal,
                bounds,
                "Cancel",
                callbacks::dismissPendingAssetCreation,
                "Confirm",
                callbacks::confirmPendingAssetCreation,
                false);
            child(modal);
        }

        private void buildPendingAssetRenameModal() {
            if (state.pendingAssetRename == null) return;
            ModalBounds bounds = createCenteredModalBounds(RENAME_MODAL_WIDTH, 126);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(createTitleText("Rename Asset").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(state.pendingAssetRename.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText("New name", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(RENAME_INPUT_PADDING, 42));
            modal.child(drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_RENAME_INPUT_BG.getColor());
                Gui.drawRect(x, y, x + width, y + 1, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
                Gui.drawRect(x, y + height - 1, x + width, y + height, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
                Gui.drawRect(x, y, x + 1, y + height, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
                Gui.drawRect(x + width - 1, y, x + width, y + height, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
            }).asWidget()
                .pos(RENAME_INPUT_PADDING, CONTENT_TOP + 4)
                .size(312, RENAME_INPUT_HEIGHT));
            addFooterButtons(
                modal,
                bounds,
                "Cancel",
                callbacks::closePendingAssetRename,
                "Confirm",
                callbacks::confirmPendingAssetRename,
                false);
            child(modal);
        }

        private void buildPendingAssetDestructionModal() {
            PendingAssetDestruction destruction = state.pendingAssetDestruction;
            if (destruction == null) return;
            ModalBounds bounds = createCenteredModalBounds(360, 150);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            int modalWidth = bounds.right() - bounds.left();
            ParentWidget<?> modal = createModalRoot(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                EnumColors.MAP_COLOR_MODAL_DANGER_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_DANGER_ACCENT.getColor(),
                -1);
            modal.child(
                createCenteredLargeText("THIS IS IRREVERSIBLE", 1.45f, EnumColors.MAP_COLOR_TEXT_DANGER.getColor())
                    .pos(12, 16)
                    .size(modalWidth - 24, 22));
            modal.child(
                createBodyText("You are about to destroy:", EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(18, 52));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(destruction.asset()),
                    EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(18, 68));
            modal.child(
                createBodyText(
                    destruction.armed() ? "Click Destroy again to confirm." : "Press Destroy to arm confirmation.",
                    EnumColors.MAP_COLOR_TEXT_DANGER_BODY.getColor()).pos(18, 92));
            int cancelX = destruction.armed() ? (modalWidth - 18 - 130) : 18;
            int destroyX = destruction.armed() ? 18 : (modalWidth - 18 - 130);
            modal.child(
                createFooterButton("Cancel", true, callbacks::dismissPendingAssetDestruction)
                    .pos(cancelX, bounds.bottom() - bounds.top() - 34)
                    .size(130, FOOTER_BUTTON_HEIGHT));
            modal.child(
                createDangerFooterButton("Destroy", callbacks::advancePendingAssetDestruction)
                    .pos(destroyX, bounds.bottom() - bounds.top() - 34)
                    .size(130, FOOTER_BUTTON_HEIGHT));
            child(modal);
        }

        private void buildPendingConstructionCancellationModal() {
            if (state.pendingConstructionCancellation == null) return;
            ModalBounds bounds = createCenteredModalBounds(360, 124);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                EnumColors.MAP_COLOR_MODAL_WARNING_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_WARNING_ACCENT.getColor());
            modal.child(createTitleText("Cancel Construction?").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(state.pendingConstructionCancellation.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText(
                    "Stored resources will be moved into deconstruction recovery.",
                    EnumColors.MAP_COLOR_TEXT_WARNING.getColor()).pos(12, 54));
            addFooterButtons(
                modal,
                bounds,
                "Cancel",
                callbacks::dismissPendingConstructionCancellation,
                "Confirm",
                callbacks::confirmPendingConstructionCancellation,
                false);
            child(modal);
        }

        private void buildPendingResourceTransferModal() {
            PendingResourceTransfer transfer = state.pendingResourceTransfer;
            if (transfer == null) return;
            int height = Math.min(
                280,
                120 + transfer.targets()
                    .size() * 42);
            ModalBounds bounds = createCenteredModalBounds(420, height);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(createTitleText("Send Resources To").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(transfer.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText(
                    "Requires an orbital rocket with enough capacity.",
                    EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 46));
            modal.child(
                createFooterButton("Close", true, callbacks::dismissPendingResourceTransfer)
                    .pos(bounds.right() - bounds.left() - 96, 8)
                    .size(78, FOOTER_BUTTON_HEIGHT));
            if (transfer.targets()
                .isEmpty()) {
                modal.child(
                    createBodyText("No stations available in this system", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(16, 74));
                child(modal);
                return;
            }
            int rowTop = 66;
            for (int i = 0; i < transfer.targets()
                .size(); i++) {
                StationTransferTarget target = transfer.targets()
                    .get(i);
                int currentTop = rowTop + i * 42;
                modal.child(
                    drawable(
                        (context, x, y, width, h) -> Gui
                            .drawRect(x, y, x + width, y + h, EnumColors.MAP_COLOR_TRANSFER_ROW_BG.getColor()))
                                .asWidget()
                                .pos(14, currentTop)
                                .size(bounds.right() - bounds.left() - 28, 36));
                modal.child(
                    createAssetIconWidget(CelestialAssetKind.STATION, 1.0f).pos(24, currentTop + 9)
                        .size(16, 16));
                modal.child(
                    createBodyText(target.displayName(), EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                        .pos(46, currentTop + 6));
                modal.child(
                    createBodyText(
                        target.hostBody()
                            .displayName(),
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(46, currentTop + 18));
                modal.child(
                    createFooterButton("Send", true, () -> callbacks.sendPendingResourceTransfer(target))
                        .pos(bounds.right() - bounds.left() - 92, currentTop + 8)
                        .size(72, FOOTER_BUTTON_HEIGHT));
            }
            child(modal);
        }

        private void buildPendingAssetManagementModal() {
            if (state.pendingAssetManagement == null) return;
            CelestialManagedAsset asset = state.pendingAssetManagement.asset();

            if (asset.kind() != CelestialAssetKind.AUTOMATED_OUTPOST) {
                ModalBounds bounds = createCenteredModalBounds(360, 150);
                updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
                ParentWidget<?> modal = createModalRoot(bounds);
                modal.child(createAssetIconWidget(asset.kind(), 1.0f).pos(12, 10).size(18, 18));
                modal.child(createTitleText("Manage Station").pos(36, 10));
                modal.child(createBodyText(callbacks.formatAssetDisplayName(asset), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(36, 28));
                modal.child(createBodyText("This panel is not implemented yet.", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(14, 62));
                modal.child(createFooterButton("Close", true, callbacks::closePendingAssetManagement).pos(bounds.right() - bounds.left() - 18 - 110, 8).size(110, FOOTER_BUTTON_HEIGHT));
                child(modal);
                return;
            }

            AutomatedOutpostState outpost = OutpostDataStore.get().getByAssetId(asset.assetId());
            ModalBounds bounds = createCenteredModalBounds(520, 420);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);

            if (outpost == null) {
                Galaxia.GALAXIA_NETWORK.sendToServer(new OutpostRequestSyncPacket(asset.assetId()));
                modal.child(createTitleText("Manage Outpost").pos(12, 10));
                modal.child(createBodyText("Loading data...", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 50));
            } else {
                if (state.configuringModuleIndex >= 0 && state.configuringModuleIndex < outpost.modules().size()) {
                    AutomatedOutpostModule module = outpost.modules().get(state.configuringModuleIndex);
                    if (module.kind == OutpostModuleKind.HAMMER || module.kind == OutpostModuleKind.BIG_HAMMER) {
                        buildLogisticsSubMenu(modal, outpost);
                    } else if (module.kind == OutpostModuleKind.MINER) {
                        buildMinerConfigSubMenu(modal, outpost, module);
                    } else if (module.kind == OutpostModuleKind.POWER) {
                        buildPowerConfigSubMenu(modal, outpost, module);
                    }
                } else if (state.assetManagementTab == 0) {
                    buildModulesTab(modal, outpost);
                } else {
                    buildInventoryTab(modal, outpost);
                }
            }

            modal.child(createGlyphButton(AssetManagerButtonGlyph.CLOSE, "Close", true, callbacks::closePendingAssetManagement)
                .pos(bounds.right() - bounds.left() - 28, 6));
            child(modal);
        }

        private void buildModulesTab(ParentWidget<?> modal, AutomatedOutpostState outpost) {
            modal.child(createTitleText("Manage Outpost").pos(12, 10));
            modal.child(createBodyText(callbacks.formatAssetDisplayName(state.pendingAssetManagement.asset()), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));

            // Tab switcher
            modal.child(createTextButton("Modules", true, () -> { state.assetManagementTab = 0; markStructureDirty(); }, state.assetManagementTab == 1).pos(12, 45).size(80, 18));
            modal.child(createTextButton("Inventory", true, () -> { state.assetManagementTab = 1; markStructureDirty(); }, state.assetManagementTab == 0).pos(94, 45).size(80, 18));

            // ── Build toolbar: one small button per module type ──────────────
            OutpostModuleKind[] kinds = OutpostModuleKind.values();
            int toolbarBtnW = (490 - (kinds.length - 1) * 6) / kinds.length;
            boolean isAutomatedOutpost = state.pendingAssetManagement.asset().kind() == CelestialAssetKind.AUTOMATED_OUTPOST;
            for (int k = 0; k < kinds.length; k++) {
                final OutpostModuleKind buildKind = kinds[k];
                boolean buildEnabled = buildKind != OutpostModuleKind.MINER || isAutomatedOutpost;
                int bx = 10 + k * (toolbarBtnW + 6);
                modal.child(createFooterButton("+ " + buildKind.displayName, buildEnabled, () -> {
                    Galaxia.GALAXIA_NETWORK.sendToServer(
                        new OutpostBuildModulePacket(
                            outpost.assetId,
                            buildKind,
                            callbacks.isCreativeBuildModeEnabled()));
                }).pos(bx, 68).size(toolbarBtnW, 18));
            }

            // ── Installed modules (scrollable) ───────────────────────────────
            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(10, 92).size(500, 308)
                .background(drawable((c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int y = 0;

            List<AutomatedOutpostModule> modules = outpost.modules();
            for (int i = 0; i < modules.size(); i++) {
                AutomatedOutpostModule m = modules.get(i);
                final int index = i;
                ParentWidget<?> row = new ParentWidget<>().pos(5, y).size(480, 44)
                    .background(drawable((c, x, y1, w, h) -> Gui.drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

                row.child(createBodyText(m.kind.displayName, EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(8, 6));

                boolean isHammer = m.kind == OutpostModuleKind.HAMMER || m.kind == OutpostModuleKind.BIG_HAMMER;
                boolean isConfigurable = isHammer || m.kind == OutpostModuleKind.MINER || m.kind == OutpostModuleKind.POWER;
                boolean operational = m.getStatus() != AutomatedOutpostModule.Status.IN_CONSTRUCTION;
                boolean isDisabled = m.getStatus() == AutomatedOutpostModule.Status.DISABLED;

                if (!operational) {
                    row.child(createBodyText(
                        "Building... " + (int)(m.getConstructionProgress() * 100) + "%",
                        EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 22));
                    row.child(drawable((c, x, y1, w, h) -> {
                        Gui.drawRect(x, y1, x + 100, y1 + 4, 0xFF333333);
                        Gui.drawRect(x, y1, x + (int)(m.getConstructionProgress() * 100), y1 + 4, 0xFF00FF00);
                    }).asWidget().pos(130, 24).size(100, 4));
                } else {
                    String statusLabel = isDisabled ? "Disabled" : "Active";
                    String powerLabel = m.kind == OutpostModuleKind.POWER
                        ? "Generating power: " + (isDisabled ? 0 : PowerModuleData.GENERATION_EU_PER_TICK) + " EU/t"
                        : "Power: " + Math.max(0L, m.getDisplayedPowerEuPerTick()) + " EU/t";
                    row.child(createBodyText(
                        statusLabel + " | " + powerLabel,
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(8, 22));
                }
                if (isConfigurable) {
                    row.child(createConfigureButton("Cfg", operational, () -> {
                        state.configuringModuleIndex = index;
                        markStructureDirty();
                    }).pos(248, 12).size(60, 20));
                }
                row.child(createDisableButton(isDisabled ? "Enable" : "Disable", operational, () -> {
                    String action = isDisabled ? "ENABLE" : "DISABLE";
                    Galaxia.GALAXIA_NETWORK.sendToServer(new OutpostModuleActionPacket(outpost.assetId, index, action));
                }).pos(313, 12).size(62, 20));
                row.child(createDestroyModuleButton(true, () -> {
                    Galaxia.GALAXIA_NETWORK.sendToServer(new OutpostModuleActionPacket(outpost.assetId, index, "DESTROY"));
                }).pos(380, 12).size(65, 20));
                content.child(row);
                y += 50;
            }

            if (modules.isEmpty()) {
                content.child(createBodyText("No modules installed. Use the buttons above to build one.",
                    EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 8));
            }

            int contentHeight = Math.max(308, y + 8);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            modal.child(scroll);
        }

        private void buildInventoryTab(ParentWidget<?> modal, AutomatedOutpostState outpost) {
            modal.child(createTitleText("Manage Outpost").pos(12, 10));
            modal.child(createBodyText(callbacks.formatAssetDisplayName(state.pendingAssetManagement.asset()), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));

            // Tab switcher
            modal.child(createTextButton("Modules", true, () -> { state.assetManagementTab = 0; markStructureDirty(); }, state.assetManagementTab == 1).pos(12, 45).size(80, 18));
            modal.child(createTextButton("Inventory", true, () -> { state.assetManagementTab = 1; markStructureDirty(); }, state.assetManagementTab == 0).pos(94, 45).size(80, 18));

            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(10, 70).size(500, 330)
                .background(drawable((c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int y = 0;
            for (Map.Entry<ItemStackWrapper, Long> entry : outpost.inventory.snapshot().entrySet()) {
                ParentWidget<?> row = new ParentWidget<>().pos(5, y).size(480, 30)
                    .background(drawable((c, x, y1, w, h) -> Gui.drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
                
                ItemStack stack = entry.getKey().toStack(1);
                row.child(createItemWidget(stack, 16).pos(5, 7).size(16, 16));
                
                row.child(createBodyText(stack.getDisplayName(), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(30, 10));
                row.child(createBodyText(formatAmount(entry.getValue()), EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(400, 10));
                content.child(row);
                y += 35;
            }
            int contentHeight = Math.max(330, y + 4);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            modal.child(scroll);
        }

        private Widget<?> createItemWidget(ItemStack stack, int size) {
            ItemStack displayStack = stack.copy();
            return drawable((context, x, y, width, height) -> drawGuiItemStack(displayStack, x, y, size)).asWidget()
                .tooltip(t -> t.addLine(displayStack.getDisplayName()));
        }

        private Widget<?> createReserveValueWidget(String text) {
            return drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor());
                Gui.drawRect(x, y, x + width, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y + height - 1, x + width, y + height, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x, y, x + 1, y + height, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Gui.drawRect(x + width - 1, y, x + width, y + height, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                Minecraft mc = Minecraft.getMinecraft();
                int textWidth = mc.fontRenderer.getStringWidth(text);
                mc.fontRenderer.drawStringWithShadow(
                    text,
                    x + (width - textWidth) / 2,
                    y + (height - mc.fontRenderer.FONT_HEIGHT) / 2 + 1,
                    EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
            }).asWidget();
        }

        private void drawGuiItemStack(ItemStack stack, int x, int y, int size) {
            Minecraft mc = Minecraft.getMinecraft();
            float scale = size / 16.0f;
            com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
            com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
            com.cleanroommc.modularui.utils.GlStateManager.scale(scale, scale, 1f);
            com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            net.minecraft.client.renderer.entity.RenderItem ri = net.minecraft.client.renderer.entity.RenderItem.getInstance();
            float previousZ = ri.zLevel;
            ri.zLevel = 200f;
            net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
            ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
            ri.zLevel = previousZ;
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_COLOR_MATERIAL);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
            com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
            com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
        }

        /**
         * Renders the logistics routing configuration for a HAMMER or BIG_HAMMER module.
         *
         * <p>Shows all explicitly configured items in a scrollable list. Each row follows
         * the layout: [Icon] [Name] [Amount] [−][Reserve][+] [Import] [Export].
         *
         * <p>The reserve value is displayed as a text widget between the decrement/increment
         * buttons, while the buttons themselves send a {@link LogisticsConfigUpdatePacket}.
         */
        private void buildLogisticsSubMenu(ParentWidget<?> modal, AutomatedOutpostState outpost) {
            List<AutomatedOutpostModule> modules = outpost.modules();
            String moduleLabel = (state.configuringModuleIndex >= 0
                && state.configuringModuleIndex < modules.size())
                    ? modules.get(state.configuringModuleIndex).kind.displayName
                    : "HAMMER";

            modal.child(createTitleText("Logistics: " + moduleLabel).pos(12, 10));
            modal.child(createFooterButton("Back", true, () -> {
                state.configuringModuleIndex = -1;
                markStructureDirty();
            }).pos(12, 32).size(60, 20));

            // ── Add Item button: opens a separate NEI-enabled screen to pick an item ──
            modal.child(createFooterButton("Add Item", true, () -> {
                ItemPickerScreen.setPendingForOutpost(outpost.assetId);
                ItemPickerScreen.FACTORY.openClient();
            }).pos(90, 32).size(80, 18));

            // ── Column header labels ──────────────────────────────────────────
            int hdrY = 58;
            modal.child(createBodyText("Item", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(26, hdrY));
            modal.child(createBodyText("In Inventory", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(220, hdrY));
            modal.child(createBodyText("Reserve", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(310, hdrY));
            modal.child(createBodyText("Import", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(390, hdrY));
            modal.child(createBodyText("Export", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(450, hdrY));

            // ── Scrollable item rows ─────────────────────────────────────────
            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(10, 72).size(500, 328)
                .background(drawable((c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);

            Map<ItemStackWrapper, LogisticsResourceConfig> configSnapshot =
                outpost.logisticsConfig.snapshot();

            int rowY = 0;
            for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> entry : configSnapshot.entrySet()) {
                final ItemStackWrapper wrapper = entry.getKey();
                final LogisticsResourceConfig cfg = entry.getValue();
                final ItemStack displayStack = wrapper.toStack(1);
                long currentAmount = outpost.inventory.getAmount(wrapper);

                ParentWidget<?> row = new ParentWidget<>().pos(4, rowY).size(488, 28)
                    .background(drawable((c, x, y1, w, h) -> Gui.drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

                // Icon
                row.child(createItemWidget(displayStack, 16).pos(4, 6).size(16, 16));
                // Name (truncated)
                String name = displayStack.getDisplayName();
                row.child(createBodyText(name, EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(24, 8));
                // Current amount in inventory
                row.child(createBodyText(formatAmount(currentAmount), EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(220, 8));
                // Reserve: [−] value [+]
                row.child(createFooterButton("-", true, () -> {
                    int newRes = Math.max(0, cfg.minReserve() - 1);
                    LogisticsResourceConfig updated = cfg.withMinReserve(newRes);
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK.sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(310, 4).size(18, 20));
                row.child(createReserveValueWidget(String.valueOf(cfg.minReserve())).pos(332, 4).size(28, 20));
                row.child(createFooterButton("+", true, () -> {
                    int newRes = cfg.minReserve() + 1;
                    LogisticsResourceConfig updated = cfg.withMinReserve(newRes);
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK.sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(363, 4).size(18, 20));
                // Import toggle
                row.child(createFooterButton(cfg.isImportEnabled() ? "ON" : "OFF", true, () -> {
                    LogisticsResourceConfig updated = cfg.withImportEnabled(!cfg.isImportEnabled());
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK.sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(382, 4).size(42, 20));
                // Export toggle
                row.child(createFooterButton(cfg.isSupplyEnabled() ? "ON" : "OFF", true, () -> {
                    LogisticsResourceConfig updated = cfg.withSupplyEnabled(!cfg.isSupplyEnabled());
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK.sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(432, 4).size(42, 20));

                content.child(row);
                rowY += 32;
            }

            if (configSnapshot.isEmpty()) {
                content.child(createBodyText(
                    "No items tracked. Use the 'Add Item' button to start.",
                    EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 8));
            }

            int contentHeight = Math.max(328, rowY + 8);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            modal.child(scroll);
        }

        private void buildMinerConfigSubMenu(ParentWidget<?> modal, AutomatedOutpostState outpost,
            AutomatedOutpostModule module) {
            MinerModuleData minerData = module.getData() instanceof MinerModuleData typed ? typed : new MinerModuleData();

            modal.child(createTitleText("Miner Configuration").pos(12, 10));
            modal.child(createFooterButton("Back", true, () -> {
                state.configuringModuleIndex = -1;
                markStructureDirty();
            }).pos(12, 32).size(60, 20));
            modal.child(createFooterButton("Add Ore", true, () -> {
                ItemPickerScreen.setPendingForMinerBlacklist(outpost.assetId, state.configuringModuleIndex);
                ItemPickerScreen.FACTORY.openClient();
            }).pos(90, 32).size(80, 18));
            modal.child(createBodyText("Blacklisted ores", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 58));

            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(10, 78).size(500, 322)
                .background(drawable((c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int rowY = 0;
            for (String key : minerData.blacklistedItemKeys()) {
                ItemStackWrapper wrapper = ItemStackWrapper.fromKey(key);
                ItemStack stack = wrapper == null ? null : wrapper.toStack(1);
                ParentWidget<?> row = new ParentWidget<>().pos(4, rowY).size(488, 28)
                    .background(drawable((c, x, y1, w, h) -> Gui.drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
                if (stack != null) {
                    row.child(createItemWidget(stack, 16).pos(4, 6).size(16, 16));
                    row.child(createBodyText(stack.getDisplayName(), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(24, 8));
                } else {
                    row.child(createBodyText(key, EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(8, 8));
                }
                row.child(createDestroyModuleButton(true, () -> {
                    if (!(module.getData() instanceof MinerModuleData currentData)) return;
                    module.setData(currentData.withRemovedBlacklist(key));
                    Galaxia.GALAXIA_NETWORK.sendToServer(
                        new OutpostModuleConfigPacket(outpost.assetId, state.configuringModuleIndex,
                            "REMOVE_MINER_BLACKLIST", key));
                    markStructureDirty();
                }).pos(410, 4).size(65, 20));
                content.child(row);
                rowY += 32;
            }
            if (minerData.blacklistedItemKeys().isEmpty()) {
                content.child(createBodyText("No blacklisted ores.", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 8));
            }
            int contentHeight = Math.max(322, rowY + 8);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            modal.child(scroll);
        }

        private void buildPowerConfigSubMenu(ParentWidget<?> modal, AutomatedOutpostState outpost,
            AutomatedOutpostModule module) {
            modal.child(createTitleText("Power Configuration").pos(12, 10));
            modal.child(createFooterButton("Back", true, () -> {
                state.configuringModuleIndex = -1;
                markStructureDirty();
            }).pos(12, 32).size(60, 20));
            modal.child(createBodyText("No settings yet.", EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 70));
            modal.child(createBodyText(
                "Generating power: " + PowerModuleData.GENERATION_EU_PER_TICK + " EU/t",
                EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(12, 92));
        }

        private String formatAmount(long amount) {
            if (amount < 1000) return String.valueOf(amount);
            if (amount < 1000000) return (amount / 1000) + "k";
            return (amount / 1000000) + "M";
        }

        private void addFooterButtons(ParentWidget<?> modal, ModalBounds bounds, String cancelLabel,
            Runnable cancelAction, String confirmLabel, Runnable confirmAction, boolean confirmDanger) {
            int btnWidth = 110;
            int modalWidth = bounds.right() - bounds.left();
            int btnY = bounds.bottom() - bounds.top() - 34;
            modal.child(
                createFooterButton(cancelLabel, true, cancelAction).pos(18, btnY)
                    .size(btnWidth, FOOTER_BUTTON_HEIGHT));
            if (confirmDanger) {
                modal.child(
                    createDangerFooterButton(confirmLabel, confirmAction).pos(modalWidth - 18 - btnWidth, btnY)
                        .size(btnWidth, FOOTER_BUTTON_HEIGHT));
            } else {
                modal.child(
                    createFooterButton(confirmLabel, true, confirmAction).pos(modalWidth - 18 - btnWidth, btnY)
                        .size(btnWidth, FOOTER_BUTTON_HEIGHT));
            }
        }

        private ModalBounds calculateManagementBounds() {
            int availableWidth = getAvailableOverlayWidth();
            int availableHeight = getAvailableOverlayHeight();
            int width = Math.min(MODAL_MAX_WIDTH, availableWidth - MODAL_MARGIN_X);
            int height = Math.min(MODAL_MAX_HEIGHT, availableHeight - MODAL_MARGIN_Y);
            int left = (availableWidth - width) / 2;
            int top = (availableHeight - height) / 2;
            return new ModalBounds(left, top, left + width, top + height);
        }

        private ModalBounds createCenteredModalBounds(int width, int height) {
            int left = (getAvailableOverlayWidth() - width) / 2;
            int top = (getAvailableOverlayHeight() - height) / 2;
            return new ModalBounds(left, top, left + width, top + height);
        }

        private int computeContentHeight(CelestialBodyAssetState assetState) {
            List<CelestialManagedAsset> construction = getConstructionAssets(assetState.assets());
            List<CelestialManagedAsset> deployed = getOperationalAssets(assetState.assets());
            int y = 0;
            if (!construction.isEmpty()) {
                y += 16;
                y += construction.size() * ROW_HEIGHT + Math.max(0, construction.size() - 1) * ROW_SPACING;
                y += 4;
            }
            y += 16;
            if (deployed.isEmpty()) y += 24;
            else y += deployed.size() * ROW_HEIGHT + Math.max(0, deployed.size() - 1) * ROW_SPACING + 8;
            return y;
        }

        private void populateContent(ParentWidget<?> content, int contentWidth, CelestialBodyAssetState assetState) {
            List<CelestialManagedAsset> construction = getConstructionAssets(assetState.assets());
            List<CelestialManagedAsset> deployed = getOperationalAssets(assetState.assets());
            int y = 0;
            if (!construction.isEmpty()) {
                content.child(createSectionText("Construction").pos(4, y));
                y += 16;
                for (CelestialManagedAsset a : construction) {
                    content.child(createConstructionRow(a, contentWidth - 8).pos(4, y));
                    y += ROW_HEIGHT + ROW_SPACING;
                }
                y += 4;
            }
            content.child(createSectionText("Assets").pos(4, y));
            y += 16;
            if (deployed.isEmpty()) {
                content
                    .child(createBodyText("No deployed assets", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, y));
                return;
            }
            for (CelestialManagedAsset a : deployed) {
                content.child(createAssetRow(a, contentWidth - 8).pos(4, y));
                y += ROW_HEIGHT + ROW_SPACING;
            }
        }

        private ParentWidget<?> createConstructionRow(CelestialManagedAsset asset, int rowWidth) {
            ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -8)
                .height(ROW_HEIGHT)
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_ROW_BG.getColor())));
            row.child(
                createAssetIconWidget(asset.kind(), 1.0f).pos(10, 9)
                    .size(16, 16));
            boolean deconstruction = asset.status() == CelestialAssetStatus.DECONSTRUCTION;
            int actionButtonsWidth = ICON_BUTTON_SIZE;
            int textWidth = rowWidth - 32 - actionButtonsWidth - 16;
            row.child(createNameButton(asset, textWidth).pos(32, 4));
            row.child(
                createBodyText(
                    (deconstruction ? "Stored: " : "Inventory: ") + callbacks.buildConstructionInventorySummary(asset),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(32, 18)
                        .width(textWidth));
            row.child(
                createGlyphButton(
                    deconstruction ? AssetManagerButtonGlyph.SEND : AssetManagerButtonGlyph.CANCEL,
                    deconstruction ? "Send To..." : "Cancel Build",
                    true,
                    () -> handleConstructionAction(asset)).pos(rowWidth - 34, 9));
            return row;
        }

        private ParentWidget<?> createAssetRow(CelestialManagedAsset asset, int rowWidth) {
            ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -8)
                .height(ROW_HEIGHT)
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_ROW_BG.getColor())));
            row.child(
                createAssetIconWidget(asset.kind(), 1.0f).pos(10, 9)
                    .size(16, 16));
            boolean manageable = callbacks.isManageableStationAsset(asset);
            int actionButtonsWidth = manageable ? (ICON_BUTTON_SIZE * 2 + 4) : ICON_BUTTON_SIZE;
            int textWidth = rowWidth - 32 - actionButtonsWidth - 16;
            row.child(createNameButton(asset, textWidth).pos(32, 4));
            row.child(
                createBodyText(
                    trimToWidth(
                        callbacks.formatAssetKind(asset.kind()) + " | "
                            + callbacks.formatAssetLocation(asset.location()),
                        textWidth),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(32, 16)
                        .width(textWidth));
            int buttonX = rowWidth - 34;
            if (manageable) {
                row.child(
                    createGlyphButton(
                        AssetManagerButtonGlyph.MANAGE,
                        "Manage",
                        true,
                        () -> callbacks.openPendingAssetManagement(asset)).pos(buttonX - 28, 9));
            }
            row.child(
                createGlyphButton(
                    AssetManagerButtonGlyph.DESTROY,
                    "Destroy",
                    true,
                    () -> callbacks.openPendingAssetDestruction(asset)).pos(buttonX, 9));
            return row;
        }

        private ButtonWidget<?> createNameButton(CelestialManagedAsset asset, int width) {
            String text = trimToWidth(callbacks.formatAssetDisplayName(asset), Math.max(8, width));
            return new ScrollAwareButtonWidget().size(Math.max(8, width), 12)
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .overlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    fr.drawStringWithShadow(
                        text,
                        x,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
                }))
                .hoverOverlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    fr.drawStringWithShadow(
                        text,
                        x,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return true;
                    callbacks.openPendingAssetRename(asset);
                    return true;
                });
        }

        private boolean forwardActiveScroll(UpOrDown direction, int amount) {
            return activeScrollWidget != null && activeScrollWidget.onMouseScroll(direction, amount);
        }

        private ButtonWidget<?> createBackdropButton() {
            return new ButtonWidget<>().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return true;
                    if (state.hasBlockingModal()) callbacks.dismissPendingModalByOutsideClick();
                    else callbacks.closeAssetManagement();
                    return true;
                });
        }

        private ParentWidget<?> createModalRoot(ModalBounds bounds) {
            return createModalRoot(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                EnumColors.MAP_COLOR_MODAL_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
        }

        private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom) {
            return createModalRoot(
                left,
                top,
                right,
                bottom,
                EnumColors.MAP_COLOR_MODAL_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
        }

        private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom, int backgroundColor,
            int accentColor) {
            return createModalRoot(
                left,
                top,
                right,
                bottom,
                backgroundColor,
                accentColor,
                EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
        }

        private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom, int backgroundColor,
            int accentColor, int headerColor) {
            ParentWidget<?> modal = new ParentWidget<>().pos(left, top)
                .size(right - left, bottom - top);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(createModalBackgroundDrawable(backgroundColor, headerColor));
            modal.child(backgroundLayer);
            modal.child(WidgetOutline.create(backgroundLayer, 3, accentColor));
            return modal;
        }

        private TextWidget<?> createTitleText(String text) {
            return new TextWidget<>(IKey.str(text)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true);
        }

        private TextWidget<?> createSectionText(String text) {
            return new TextWidget<>(IKey.str(text)).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                .shadow(true);
        }

        private TextWidget<?> createBodyText(String text, int color) {
            return new TextWidget<>(IKey.str(text)).color(color)
                .shadow(true);
        }

        private ButtonWidget<?> createAssetKindButton(CelestialAssetKind kind, String tooltip, boolean enabled,
            Runnable action) {
            return createIconButton(kind, AssetManagerButtonGlyph.NONE, tooltip, enabled, action);
        }

        private ButtonWidget<?> createGlyphButton(AssetManagerButtonGlyph glyph, String tooltip, boolean enabled,
            Runnable action) {
            return createIconButton(null, glyph, tooltip, enabled, action);
        }

        private ButtonWidget<?> createIconButton(CelestialAssetKind iconKind, AssetManagerButtonGlyph glyph,
            String tooltip, boolean enabled, Runnable action) {
            ButtonWidget<?> button = new ScrollAwareButtonWidget().size(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
                .background(createButtonBackground(enabled, false))
                .hoverBackground(createButtonBackground(enabled, true))
                .tooltip(t -> t.addLine(tooltip))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || !enabled) return true;
                    action.run();
                    return true;
                });
            if (iconKind != null) button.overlay(createAssetIconDrawable(iconKind, enabled ? 1.0f : 0.45f));
            else button.overlay(
                createGlyphDrawable(
                    glyph,
                    enabled ? EnumColors.MAP_COLOR_TEXT_TITLE.getColor()
                        : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor()));
            return button;
        }

        private ButtonWidget<?> createFooterButton(String label, boolean enabled, Runnable action) {
            return createTextButton(label, enabled, action, false);
        }

        private ButtonWidget<?> createDangerFooterButton(String label, Runnable action) {
            return createTextButton(label, true, action, true);
        }

        private ButtonWidget<?> createConfigureButton(String label, boolean enabled, Runnable action) {
            return createColoredButton(label, enabled, action,
                EnumColors.MAP_COLOR_BTN_CONFIGURE_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_CONFIGURE_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_CONFIGURE_BORDER.getColor());
        }

        private ButtonWidget<?> createDisableButton(String label, boolean enabled, Runnable action) {
            return createColoredButton(label, enabled, action,
                EnumColors.MAP_COLOR_BTN_DISABLE_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_DISABLE_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_DISABLE_BORDER.getColor());
        }

        private ButtonWidget<?> createDestroyModuleButton(boolean enabled, Runnable action) {
            return createColoredButton("Destroy", enabled, action,
                EnumColors.MAP_COLOR_BTN_DESTROY_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_DESTROY_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor());
        }

        private ButtonWidget<?> createColoredButton(String label, boolean enabled, Runnable action,
            int defaultBg, int hoverBg, int border) {
            return new ScrollAwareButtonWidget()
                .background(createRectFrameDrawable(
                    enabled ? defaultBg : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                    enabled ? border : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor()))
                .hoverBackground(createRectFrameDrawable(
                    enabled ? hoverBg : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                    enabled ? border : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor()))
                .overlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    fr.drawStringWithShadow(
                        label,
                        x + (w - textW) / 2,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        enabled ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                            : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor());
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || !enabled) return true;
                    action.run();
                    return true;
                });
        }

        private ButtonWidget<?> createTextButton(String label, boolean enabled, Runnable action, boolean danger) {
            return new ScrollAwareButtonWidget().background(createTextButtonBackground(enabled, false, danger))
                .hoverBackground(createTextButtonBackground(enabled, true, danger))
                .overlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    int color = enabled ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                        : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor();
                    fr.drawStringWithShadow(label, x + (w - textW) / 2, y + (h - fr.FONT_HEIGHT) / 2 + 1, color);
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || !enabled) return true;
                    action.run();
                    return true;
                });
        }

        private IDrawable createButtonBackground(boolean enabled, boolean hovered) {
            int bg = !enabled ? EnumColors.MAP_COLOR_BTN_DISABLED.getColor()
                : hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                    : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor();
            int border = enabled ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
                : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor();
            return createRectFrameDrawable(bg, border);
        }

        private IDrawable createTextButtonBackground(boolean enabled, boolean hovered, boolean danger) {
            if (danger) {
                int bg = hovered ? EnumColors.MAP_COLOR_BTN_DANGER_HOVERED.getColor()
                    : EnumColors.MAP_COLOR_BTN_DANGER_DEFAULT.getColor();
                return createRectFrameDrawable(bg, EnumColors.MAP_COLOR_BTN_DANGER_BORDER.getColor());
            }
            return createButtonBackground(enabled, hovered);
        }

        private IDrawable createRectFrameDrawable(int backgroundColor, int borderColor) {
            return drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, backgroundColor);
                Gui.drawRect(x, y, x + width, y + 1, borderColor);
                Gui.drawRect(x, y + height - 1, x + width, y + height, borderColor);
                Gui.drawRect(x, y, x + 1, y + height, borderColor);
                Gui.drawRect(x + width - 1, y, x + width, y + height, borderColor);
            });
        }

        private IDrawable createAssetIconDrawable(CelestialAssetKind kind, float alpha) {
            return drawable(
                (context, x, y, width, height) -> callbacks
                    .drawAssetIcon(kind, x + (width - 14) / 2, y + (height - 14) / 2, 14, alpha));
        }

        private Widget<?> createAssetIconWidget(CelestialAssetKind kind, float alpha) {
            return createAssetIconDrawable(kind, alpha).asWidget();
        }

        private IDrawable createGlyphDrawable(AssetManagerButtonGlyph glyph, int color) {
            return drawable((context, x, y, width, height) -> drawGlyph(x, y, width, height, glyph, color));
        }

        private Widget<?> createCenteredLargeText(String text, float scale, int color) {
            return drawable((context, x, y, width, height) -> {
                Minecraft mc = Minecraft.getMinecraft();
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + width / 2f, y, 0);
                GlStateManager.scale(scale, scale, 1f);
                float textWidth = mc.fontRenderer.getStringWidth(text);
                mc.fontRenderer.drawStringWithShadow(text, Math.round(-textWidth / 2f), 0, color);
                GlStateManager.popMatrix();
            }).asWidget();
        }

        private void drawGlyph(int x, int y, int width, int height, AssetManagerButtonGlyph glyph, int color) {
            int centerX = x + width / 2;
            int centerY = y + height / 2;
            switch (glyph) {
                case CANCEL, DESTROY, CLOSE -> drawGlyphX(centerX, centerY, 5, color);
                case SEND -> drawGlyphSend(centerX, centerY, color);
                case MANAGE -> drawGlyphManage(centerX, centerY, color);
                case NONE -> {}
            }
        }

        private void drawGlyphX(int cx, int cy, int radius, int color) {
            for (int i = -radius; i <= radius; i++) {
                Gui.drawRect(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                Gui.drawRect(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
            }
        }

        private void drawGlyphSend(int cx, int cy, int color) {
            Gui.drawRect(cx - 5, cy - 1, cx + 3, cy + 1, color);
            Gui.drawRect(cx + 2, cy - 3, cx + 3, cy + 4, color);
            Gui.drawRect(cx + 3, cy - 2, cx + 4, cy + 3, color);
            Gui.drawRect(cx + 4, cy - 1, cx + 5, cy + 2, color);
            Gui.drawRect(cx + 5, cy, cx + 6, cy + 1, color);
        }

        private void drawGlyphManage(int cx, int cy, int color) {
            Gui.drawRect(cx - 5, cy - 4, cx + 6, cy - 3, color);
            Gui.drawRect(cx - 5, cy, cx + 6, cy + 1, color);
            Gui.drawRect(cx - 5, cy + 4, cx + 6, cy + 5, color);
        }

        private IDrawable createModalBackgroundDrawable(int backgroundColor, int headerColor) {
            return drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, backgroundColor);
                if (headerColor >= 0) Gui.drawRect(x, y, x + width, y + HEADER_HEIGHT, headerColor);
            });
        }

        private List<CelestialManagedAsset> getConstructionAssets(List<CelestialManagedAsset> assets) {
            List<CelestialManagedAsset> matching = new ArrayList<>();
            for (CelestialManagedAsset asset : assets) {
                if (asset.status() == CelestialAssetStatus.CONSTRUCTION_SITE
                    || asset.status() == CelestialAssetStatus.DECONSTRUCTION) matching.add(asset);
            }
            return matching;
        }

        private List<CelestialManagedAsset> getOperationalAssets(List<CelestialManagedAsset> assets) {
            List<CelestialManagedAsset> matching = new ArrayList<>();
            for (CelestialManagedAsset asset : assets) {
                if (asset.status() == CelestialAssetStatus.OPERATIONAL) matching.add(asset);
            }
            return matching;
        }

        private void handleConstructionAction(CelestialManagedAsset asset) {
            if (asset.status() == CelestialAssetStatus.DECONSTRUCTION) {
                callbacks.openPendingResourceTransfer(asset);
                return;
            }
            if (callbacks.isCreativeBuildModeEnabled()) {
                CelestialAssetStore.cancelConstruction(asset.assetId());
                callbacks.showActionStatus("Construction canceled");
                return;
            }
            if (callbacks.hasStoredConstructionResources(asset)) {
                callbacks.openPendingConstructionCancellation(asset);
                return;
            }
            CelestialAssetStore.cancelConstruction(asset.assetId());
            callbacks.showActionStatus("Construction canceled");
        }

        private void updateModalBounds(int left, int top, int right, int bottom) {
            modalLeft = left;
            modalTop = top;
            modalRight = right;
            modalBottom = bottom;
        }

        private void clearBounds() {
            modalLeft = modalTop = modalRight = modalBottom = 0;
            scrollLeft = scrollTop = scrollRight = scrollBottom = 0;
        }

        private int getAvailableOverlayWidth() {
            int width = getArea().width;
            if (hasParent()) width = Math.max(width, getParentArea().width - Math.max(0, getArea().rx));
            return width;
        }

        private int getAvailableOverlayHeight() {
            int height = getArea().height;
            if (hasParent()) height = Math.max(height, getParentArea().height - Math.max(0, getArea().ry));
            return height;
        }

        private void clearMainPanelState() {
            mainScrollWidget = null;
            mainScrollContent = null;
            mainScrollData = null;
            mainContentWidth = 0;
            mainContentHeight = 0;
        }

        private String trimToWidth(String text, int width) {
            return Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, width);
        }

        private IDrawable drawable(DrawCommand drawCommand) {
            return (context, x, y, width, height, widgetTheme) -> drawCommand.draw(context, x, y, width, height);
        }

        @FunctionalInterface
        private interface DrawCommand {

            void draw(GuiContext context, int x, int y, int width, int height);
        }

        private enum AssetManagerButtonGlyph {
            NONE,
            CLOSE,
            CANCEL,
            SEND,
            DESTROY,
            MANAGE
        }

        private final class PassiveRow extends ParentWidget<PassiveRow> {

            @Override
            public boolean canHover() {
                return false;
            }

            @Override
            public boolean canHoverThrough() {
                return true;
            }
        }

        private final class PassiveLayer extends ParentWidget<PassiveLayer> {

            @Override
            public boolean canHover() {
                return false;
            }

            @Override
            public boolean canHoverThrough() {
                return true;
            }
        }

        private final class ScrollAwareButtonWidget extends ButtonWidget<ScrollAwareButtonWidget> {

            @Override
            public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
                return super.onMouseScroll(scrollDirection, amount) || forwardActiveScroll(scrollDirection, amount);
            }
        }
    }
}
