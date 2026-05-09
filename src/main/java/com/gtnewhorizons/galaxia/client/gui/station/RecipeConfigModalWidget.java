package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeInputScreen;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

final class RecipeConfigModalWidget extends ParentWidget<RecipeConfigModalWidget> {

    static final int WIDTH = 440;
    static final int HEIGHT = 248;

    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + 10;
    private static final int ROW_TOP = BODY_TOP + 32;
    private static final int ROW_HEIGHT = 25;
    private static final int ROWS_PER_PAGE = 5;
    private static final int FOOTER_Y = HEIGHT - 28;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SLOT_X = 8;
    private static final int ENABLE_X = 30;
    private static final int RECIPE_X = 76;
    private static final int INPUT_X = 192;
    private static final int OUTPUT_X = 248;
    private static final int PRIORITY_X = 304;
    private static final int ORDER_X = 350;
    private static final int REMOVE_X = 394;
    private static final int ENABLE_WIDTH = 40;
    private static final int RECIPE_WIDTH = 108;
    private static final int SMALL_FIELD_WIDTH = 46;
    private static final int REMOVE_WIDTH = 38;
    private static final int PAGE_BUTTON_WIDTH = 28;
    private static final int MODE_BUTTON_WIDTH = 96;
    private static final int ADD_BUTTON_WIDTH = 52;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private int page;

    RecipeConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;

        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int rowY = ROW_TOP + row * ROW_HEIGHT;
            int rowIndex = row;
            child(
                ModuleConfigModalSupport
                    .button(() -> canUseRow(rowIndex), () -> enabledLabel(rowIndex), () -> toggleEnabled(rowIndex))
                    .pos(ENABLE_X, rowY)
                    .size(ENABLE_WIDTH, BUTTON_HEIGHT));
            child(
                numberField(rowIndex, Field.INPUT_GUARD).pos(INPUT_X, rowY)
                    .size(SMALL_FIELD_WIDTH, BUTTON_HEIGHT));
            child(
                numberField(rowIndex, Field.OUTPUT_GUARD).pos(OUTPUT_X, rowY)
                    .size(SMALL_FIELD_WIDTH, BUTTON_HEIGHT));
            child(
                numberField(rowIndex, Field.PRIORITY).pos(PRIORITY_X, rowY)
                    .size(SMALL_FIELD_WIDTH, BUTTON_HEIGHT));
            child(
                numberField(rowIndex, Field.ORDER_SIZE).pos(ORDER_X, rowY)
                    .size(SMALL_FIELD_WIDTH, BUTTON_HEIGHT));
            child(
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "X", () -> removeSlot(rowIndex))
                    .pos(REMOVE_X, rowY)
                    .size(REMOVE_WIDTH, BUTTON_HEIGHT));
        }

        child(
            ModuleConfigModalSupport.button(this::hasPreviousPage, "<", this::previousPage)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(PAGE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::hasNextPage, ">", this::nextPage)
                .pos(ModuleConfigModalSupport.PANEL_PADDING + PAGE_BUTTON_WIDTH + 4, FOOTER_Y)
                .size(PAGE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfigureRecipes, this::modeLabel, this::cycleMode)
                .pos(76, FOOTER_Y)
                .size(MODE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfigureRecipes, "Add", this::addRecipe)
                .pos(178, FOOTER_Y)
                .size(ADD_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isRecipeConfigOpen, "Close", controller::close)
                .pos(WIDTH - CLOSE_BUTTON_WIDTH - ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        ModuleInstance module = selectedModule();
        String title = module != null ? ModuleConfigModalSupport.moduleTitle(module, "Recipes") : "Recipes";
        ModuleConfigModalSupport.drawFrame(title, WIDTH, HEIGHT);

        RecipeConfig config = selectedConfig();
        int slotCount = slots().size();
        int color = canConfigureRecipes() ? EnumColors.MAP_COLOR_TEXT_BODY.getColor()
            : EnumColors.MAP_COLOR_TEXT_MUTED.getColor();
        ModuleConfigModalSupport
            .drawLine("Slots: " + slotCount, ModuleConfigModalSupport.PANEL_PADDING, BODY_TOP, color);
        ModuleConfigModalSupport.drawLine(modeLabel(), 116, BODY_TOP, color);
        drawHeader(color);

        List<RecipeSlot> slots = slots();
        if (slots.isEmpty()) {
            ModuleConfigModalSupport
                .drawLine("No recipes configured", ModuleConfigModalSupport.PANEL_PADDING, ROW_TOP, color);
            return;
        }

        int first = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int slotIndex = first + row;
            if (slotIndex >= slots.size()) break;
            drawSlotRow(row, slotIndex, slots.get(slotIndex), color);
        }

        if (config == null && module != null) {
            ModuleConfigModalSupport.drawLine(
                "Config will be created when adding a recipe",
                ModuleConfigModalSupport.PANEL_PADDING,
                ROW_TOP + ROWS_PER_PAGE * ROW_HEIGHT + 2,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        }
    }

    private void drawHeader(int color) {
        int y = ROW_TOP - 12;
        ModuleConfigModalSupport.drawLine("#", SLOT_X, y, color);
        ModuleConfigModalSupport.drawLine("On", ENABLE_X + 9, y, color);
        ModuleConfigModalSupport.drawLine("Recipe", RECIPE_X, y, color);
        ModuleConfigModalSupport.drawLine("In", INPUT_X + 14, y, color);
        ModuleConfigModalSupport.drawLine("Out", OUTPUT_X + 11, y, color);
        ModuleConfigModalSupport.drawLine("Pri", PRIORITY_X + 11, y, color);
        ModuleConfigModalSupport.drawLine("Size", ORDER_X + 6, y, color);
    }

    private void drawSlotRow(int row, int slotIndex, RecipeSlot slot, int color) {
        int y = ROW_TOP + row * ROW_HEIGHT + 6;
        ModuleConfigModalSupport.drawLine(Integer.toString(slotIndex + 1), SLOT_X, y, color);
        ModuleConfigModalSupport.drawTrimmedLine(
            RecipeSlotUiModel.slotTitle(slot),
            RECIPE_X,
            y,
            RECIPE_WIDTH,
            slot.enabled() ? EnumColors.MAP_COLOR_TEXT_BODY.getColor() : EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private TextFieldWidget numberField(int rowIndex, Field field) {
        return new TextFieldWidget().setMaxLength(6)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(field.defaultValue)
            .setNumbers(field.min, field.max)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (!canUseRow(rowIndex)) return;
                com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .value(new StringValue.Dynamic(() -> fieldText(rowIndex, field), text -> setField(rowIndex, field, text)))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> canUseRow(rowIndex));
    }

    private String fieldText(int rowIndex, Field field) {
        RecipeSlot slot = slotAtRow(rowIndex);
        if (slot == null) return "";
        return Integer.toString(field.value(slot));
    }

    private void setField(int rowIndex, Field field, String text) {
        RecipeSlot slot = slotAtRow(rowIndex);
        if (slot == null) return;
        int value = RecipeSlotUiModel.parseIntOrCurrent(text, field.value(slot), field.min, field.max);
        updateSlot(rowIndex, field.updated(slot, value));
    }

    private boolean canConfigureRecipes() {
        return controller.isRecipeConfigOpen() && selectedRecipeModule() != null;
    }

    private boolean canUseRow(int rowIndex) {
        return canConfigureRecipes() && slotAtRow(rowIndex) != null;
    }

    private String enabledLabel(int rowIndex) {
        RecipeSlot slot = slotAtRow(rowIndex);
        return slot != null && slot.enabled() ? "On" : "Off";
    }

    private void toggleEnabled(int rowIndex) {
        RecipeSlot slot = slotAtRow(rowIndex);
        if (slot == null) return;
        updateSlot(
            rowIndex,
            new RecipeSlot(
                slot.recipe(),
                !slot.enabled(),
                slot.inputGuard(),
                slot.outputGuard(),
                slot.priority(),
                slot.orderSize()));
    }

    private void removeSlot(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0 || slotAtRow(rowIndex) == null) return;
        CelestialClient.updateModuleRecipeSlot(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT,
            (byte) slotIndex,
            null);
        page = Math.min(page, maxPageAfterRemoval());
    }

    private void updateSlot(int rowIndex, RecipeSlot slot) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0) return;
        CelestialClient.updateModuleRecipeSlot(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT,
            (byte) slotIndex,
            slot);
    }

    private void cycleMode() {
        IRecipeModule recipeModule = selectedRecipeModule();
        if (recipeModule == null) return;
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_RECIPE_SCHEDULER_MODE,
            RecipeSlotUiModel.nextMode(recipeModule.getRecipeConfig()));
    }

    private void addRecipe() {
        ModuleInstance module = selectedModule();
        if (module == null) return;
        RecipeInputScreen.open(assetId, controller.moduleIndex(), module);
    }

    private void previousPage() {
        if (hasPreviousPage()) page--;
    }

    private void nextPage() {
        if (hasNextPage()) page++;
    }

    private boolean hasPreviousPage() {
        return page > 0;
    }

    private boolean hasNextPage() {
        return (page + 1) * ROWS_PER_PAGE < slots().size();
    }

    private int maxPageAfterRemoval() {
        int remaining = Math.max(0, slots().size() - 1);
        return remaining == 0 ? 0 : (remaining - 1) / ROWS_PER_PAGE;
    }

    private String modeLabel() {
        return RecipeSlotUiModel.modeLabel(selectedConfig());
    }

    private @Nullable RecipeSlot slotAtRow(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        List<RecipeSlot> slots = slots();
        return slotIndex >= 0 && slotIndex < slots.size() ? slots.get(slotIndex) : null;
    }

    private int slotIndexForRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= ROWS_PER_PAGE) return -1;
        return page * ROWS_PER_PAGE + rowIndex;
    }

    private List<RecipeSlot> slots() {
        RecipeConfig config = selectedConfig();
        return config == null ? List.of()
            : config.slots()
                .toList();
    }

    private @Nullable RecipeConfig selectedConfig() {
        IRecipeModule recipeModule = selectedRecipeModule();
        return recipeModule != null ? recipeModule.getRecipeConfig() : null;
    }

    private @Nullable IRecipeModule selectedRecipeModule() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof IRecipeModule recipeModule ? recipeModule : null;
    }

    private @Nullable ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private enum Field {

        INPUT_GUARD(0, RecipeSlotUiModel.MAX_GUARD, 0) {

            @Override
            int value(RecipeSlot slot) {
                return slot.inputGuard();
            }

            @Override
            RecipeSlot updated(RecipeSlot slot, int value) {
                return new RecipeSlot(
                    slot.recipe(),
                    slot.enabled(),
                    value,
                    slot.outputGuard(),
                    slot.priority(),
                    slot.orderSize());
            }
        },
        OUTPUT_GUARD(0, RecipeSlotUiModel.MAX_GUARD, RecipeSlotUiModel.MAX_GUARD) {

            @Override
            int value(RecipeSlot slot) {
                return slot.outputGuard();
            }

            @Override
            RecipeSlot updated(RecipeSlot slot, int value) {
                return new RecipeSlot(
                    slot.recipe(),
                    slot.enabled(),
                    slot.inputGuard(),
                    value,
                    slot.priority(),
                    slot.orderSize());
            }
        },
        PRIORITY(0, RecipeSlotUiModel.MAX_BYTE_SETTING, 1) {

            @Override
            int value(RecipeSlot slot) {
                return slot.priority();
            }

            @Override
            RecipeSlot updated(RecipeSlot slot, int value) {
                return new RecipeSlot(
                    slot.recipe(),
                    slot.enabled(),
                    slot.inputGuard(),
                    slot.outputGuard(),
                    (byte) value,
                    slot.orderSize());
            }
        },
        ORDER_SIZE(1, RecipeSlotUiModel.MAX_BYTE_SETTING, 1) {

            @Override
            int value(RecipeSlot slot) {
                return slot.orderSize();
            }

            @Override
            RecipeSlot updated(RecipeSlot slot, int value) {
                return new RecipeSlot(
                    slot.recipe(),
                    slot.enabled(),
                    slot.inputGuard(),
                    slot.outputGuard(),
                    slot.priority(),
                    (byte) value);
            }
        };

        final int min;
        final int max;
        final int defaultValue;

        Field(int min, int max, int defaultValue) {
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }

        abstract int value(RecipeSlot slot);

        abstract RecipeSlot updated(RecipeSlot slot, int value);
    }
}
