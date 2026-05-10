package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeInputScreen;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotBounds;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

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
    private static final int BOUNDS_X = 232;
    private static final int PRIORITY_X = 304;
    private static final int ORDER_X = 350;
    private static final int REMOVE_X = 394;
    private static final int ENABLE_WIDTH = 40;
    private static final int RECIPE_WIDTH = 148;
    private static final int BOUNDS_WIDTH = 64;
    private static final int SMALL_FIELD_WIDTH = 46;
    private static final int REMOVE_WIDTH = 38;
    private static final int PAGE_BUTTON_WIDTH = 28;
    private static final int MODE_BUTTON_WIDTH = 96;
    private static final int ADD_BUTTON_WIDTH = 52;
    private static final int CLOSE_BUTTON_WIDTH = 54;
    private static final int DETAIL_ITEM_INPUT_X = 24;
    private static final int DETAIL_ITEM_OUTPUT_X = 260;
    private static final int DETAIL_ITEM_Y = 62;
    private static final int DETAIL_ITEM_SLOT_SIZE = 20;
    private static final int DETAIL_ITEM_GAP = 24;
    private static final int DETAIL_FLUID_INPUT_X = 24;
    private static final int DETAIL_FLUID_OUTPUT_X = 260;
    private static final int DETAIL_FLUID_Y = 142;
    private static final int DETAIL_FLUID_WIDTH = 116;
    private static final int DETAIL_FLUID_HEIGHT = 20;
    private static final int DETAIL_CONTROL_Y = 198;
    private static final int DETAIL_AMOUNT_X = 130;
    private static final int DETAIL_AMOUNT_WIDTH = 72;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]*");

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private int page;
    private int boundsSlotIndex = -1;
    private @Nullable BoundTarget selectedBoundTarget;
    private String boundAmountInput = "";

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
                ModuleConfigModalSupport.button(() -> canUseRow(rowIndex), "Bounds", () -> openBounds(rowIndex))
                    .pos(BOUNDS_X, rowY)
                    .size(BOUNDS_WIDTH, BUTTON_HEIGHT));
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

        for (int i = 0; i < 9; i++) {
            int slot = i;
            child(
                boundSlotButton(new BoundTarget(BoundSide.INPUT, BoundResource.ITEM, slot))
                    .pos(DETAIL_ITEM_INPUT_X + (i % 3) * DETAIL_ITEM_GAP, DETAIL_ITEM_Y + (i / 3) * DETAIL_ITEM_GAP)
                    .size(DETAIL_ITEM_SLOT_SIZE, DETAIL_ITEM_SLOT_SIZE));
            child(
                boundSlotButton(new BoundTarget(BoundSide.OUTPUT, BoundResource.ITEM, slot))
                    .pos(DETAIL_ITEM_OUTPUT_X + (i % 3) * DETAIL_ITEM_GAP, DETAIL_ITEM_Y + (i / 3) * DETAIL_ITEM_GAP)
                    .size(DETAIL_ITEM_SLOT_SIZE, DETAIL_ITEM_SLOT_SIZE));
        }
        for (int i = 0; i < 4; i++) {
            int slot = i;
            child(
                boundSlotButton(new BoundTarget(BoundSide.INPUT, BoundResource.FLUID, slot))
                    .pos(DETAIL_FLUID_INPUT_X, DETAIL_FLUID_Y + i * DETAIL_ITEM_GAP)
                    .size(DETAIL_FLUID_WIDTH, DETAIL_FLUID_HEIGHT));
            child(
                boundSlotButton(new BoundTarget(BoundSide.OUTPUT, BoundResource.FLUID, slot))
                    .pos(DETAIL_FLUID_OUTPUT_X, DETAIL_FLUID_Y + i * DETAIL_ITEM_GAP)
                    .size(DETAIL_FLUID_WIDTH, DETAIL_FLUID_HEIGHT));
        }

        child(
            boundAmountField().pos(DETAIL_AMOUNT_X, DETAIL_CONTROL_Y)
                .size(DETAIL_AMOUNT_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canApplySelectedBound, "Set", this::applySelectedBound)
                .pos(DETAIL_AMOUNT_X + DETAIL_AMOUNT_WIDTH + 6, DETAIL_CONTROL_Y)
                .size(48, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canClearSelectedBound, "Clear", this::clearSelectedBound)
                .pos(DETAIL_AMOUNT_X + DETAIL_AMOUNT_WIDTH + 58, DETAIL_CONTROL_Y)
                .size(54, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::isBoundsOpen, "Back", this::closeBounds)
                .pos(WIDTH - CLOSE_BUTTON_WIDTH - ModuleConfigModalSupport.PANEL_PADDING, FOOTER_Y)
                .size(CLOSE_BUTTON_WIDTH, BUTTON_HEIGHT));

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
            ModuleConfigModalSupport
                .button(() -> controller.isRecipeConfigOpen() && !isBoundsOpen(), "Close", controller::close)
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

        if (isBoundsOpen()) {
            drawBoundsView();
            return;
        }

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
        ModuleConfigModalSupport.drawLine("Bounds", BOUNDS_X + 8, y, color);
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
        return controller.isRecipeConfigOpen() && selectedRecipeModule() != null && !isBoundsOpen();
    }

    private boolean canUseRow(int rowIndex) {
        return canConfigureRecipes() && !isBoundsOpen() && slotAtRow(rowIndex) != null;
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
            new RecipeSlot(slot.recipe(), !slot.enabled(), slot.bounds(), slot.priority(), slot.orderSize()));
    }

    private void openBounds(int rowIndex) {
        int slotIndex = slotIndexForRow(rowIndex);
        if (slotIndex < 0 || slotIndex >= slots().size()) return;
        boundsSlotIndex = slotIndex;
        selectFirstBoundTarget();
    }

    private void closeBounds() {
        boundsSlotIndex = -1;
        selectedBoundTarget = null;
        boundAmountInput = "";
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
        updateSlotIndex(slotIndex, slot);
    }

    private void updateSlotIndex(int slotIndex, RecipeSlot slot) {
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
        return !isBoundsOpen() && page > 0;
    }

    private boolean hasNextPage() {
        return !isBoundsOpen() && (page + 1) * ROWS_PER_PAGE < slots().size();
    }

    private int maxPageAfterRemoval() {
        int remaining = Math.max(0, slots().size() - 1);
        return remaining == 0 ? 0 : (remaining - 1) / ROWS_PER_PAGE;
    }

    private boolean isBoundsOpen() {
        return boundsSlotIndex >= 0 && boundsSlotIndex < slots().size();
    }

    private @Nullable RecipeSlot boundsSlot() {
        List<RecipeSlot> slots = slots();
        return boundsSlotIndex >= 0 && boundsSlotIndex < slots.size() ? slots.get(boundsSlotIndex) : null;
    }

    private ButtonWidget<?> boundSlotButton(BoundTarget target) {
        return new ButtonWidget<>()
            .background(
                ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> drawBoundSlotButton(target, x, y, w, h, false)))
            .hoverBackground(
                ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> drawBoundSlotButton(target, x, y, w, h, true)))
            .onMousePressed(mouseButton -> {
                if (mouseButton != 0 || !canUseBoundTarget(target)) return false;
                selectedBoundTarget = target;
                boundAmountInput = currentBoundText(target);
                return true;
            })
            .setEnabledIf(w -> canUseBoundTarget(target));
    }

    private TextFieldWidget boundAmountField() {
        return new TextFieldWidget().setMaxLength(9)
            .setPattern(INTEGER_PATTERN)
            .setDefaultNumber(0)
            .setNumbers(0, Integer.MAX_VALUE)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .background(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> {
                if (selectedBoundTarget == null || !isBoundsOpen()) return;
                BorderedRect.draw(
                    x,
                    y,
                    w,
                    h,
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
            }))
            .value(new StringValue.Dynamic(() -> boundAmountInput, text -> boundAmountInput = text == null ? "" : text))
            .setFocusOnGuiOpen(false)
            .setEnabledIf(w -> selectedBoundTarget != null && isBoundsOpen());
    }

    private void drawBoundsView() {
        RecipeSlot slot = boundsSlot();
        if (slot == null) return;
        int color = EnumColors.MAP_COLOR_TEXT_BODY.getColor();
        ModuleConfigModalSupport.drawTrimmedLine(
            RecipeSlotUiModel.slotTitle(slot),
            ModuleConfigModalSupport.PANEL_PADDING,
            BODY_TOP,
            WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2,
            color);
        ModuleConfigModalSupport.drawLine("Inputs", DETAIL_ITEM_INPUT_X, DETAIL_ITEM_Y - 14, color);
        ModuleConfigModalSupport.drawLine("Outputs", DETAIL_ITEM_OUTPUT_X, DETAIL_ITEM_Y - 14, color);
        ModuleConfigModalSupport
            .drawLine("Items", DETAIL_ITEM_INPUT_X, DETAIL_ITEM_Y + 70, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
        ModuleConfigModalSupport
            .drawLine("Fluids", DETAIL_FLUID_INPUT_X, DETAIL_FLUID_Y - 14, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());

        String selected = selectedBoundTarget == null ? "Select a slot"
            : boundDescription(selectedBoundTarget) + ": " + currentBoundText(selectedBoundTarget);
        ModuleConfigModalSupport.drawTrimmedLine(
            selected,
            ModuleConfigModalSupport.PANEL_PADDING,
            DETAIL_CONTROL_Y - 18,
            WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2,
            EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
    }

    private void drawBoundSlotButton(BoundTarget target, int x, int y, int w, int h, boolean hovered) {
        if (!canUseBoundTarget(target)) return;
        boolean selected = target.equals(selectedBoundTarget);
        int bg = selected || hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
            : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor();
        BorderedRect.draw(x, y, w, h, bg, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
        if (target.resource() == BoundResource.ITEM) {
            ItemStack stack = itemStack(target);
            if (stack != null) {
                renderItemIcon(stack, x + 2, y + 2);
            }
        } else {
            FluidStack stack = fluidStack(target);
            String label = stack == null ? "" : fluidLabel(stack);
            ModuleConfigModalSupport
                .drawTrimmedLine(label, x + 3, y + 6, w - 6, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
        drawBoundMarker(target, x + w - 7, y + 1);
    }

    private void drawBoundMarker(BoundTarget target, int x, int y) {
        if (!hasBound(target)) return;
        int color = isBoundBlocking(target) ? EnumColors.MAP_COLOR_TEXT_DANGER.getColor()
            : EnumColors.MAP_COLOR_TEXT_WARNING.getColor();
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow("*", x, y, color);
    }

    private boolean canUseBoundTarget(BoundTarget target) {
        if (!isBoundsOpen()) return false;
        return target.resource() == BoundResource.ITEM ? itemStack(target) != null : fluidStack(target) != null;
    }

    private void selectFirstBoundTarget() {
        selectedBoundTarget = null;
        for (BoundSide side : BoundSide.values()) {
            for (BoundResource resource : BoundResource.values()) {
                int limit = resource == BoundResource.ITEM ? 9 : 4;
                for (int i = 0; i < limit; i++) {
                    BoundTarget candidate = new BoundTarget(side, resource, i);
                    if (canUseBoundTarget(candidate)) {
                        selectedBoundTarget = candidate;
                        boundAmountInput = currentBoundText(candidate);
                        return;
                    }
                }
            }
        }
        boundAmountInput = "";
    }

    private boolean canApplySelectedBound() {
        return selectedBoundTarget != null && canUseBoundTarget(selectedBoundTarget);
    }

    private boolean canClearSelectedBound() {
        return canApplySelectedBound() && hasBound(selectedBoundTarget);
    }

    private void applySelectedBound() {
        if (!canApplySelectedBound()) return;
        long amount;
        try {
            amount = Long.parseLong(boundAmountInput);
        } catch (NumberFormatException ignored) {
            return;
        }
        amount = Math.max(0L, amount);
        RecipeSlot slot = boundsSlot();
        if (slot == null) return;
        RecipeSlotBounds bounds = updateBound(slot.bounds(), selectedBoundTarget, amount);
        updateSlotIndex(
            boundsSlotIndex,
            new RecipeSlot(slot.recipe(), slot.enabled(), bounds, slot.priority(), slot.orderSize()));
    }

    private void clearSelectedBound() {
        if (!canClearSelectedBound()) return;
        RecipeSlot slot = boundsSlot();
        if (slot == null) return;
        RecipeSlotBounds bounds = clearBound(slot.bounds(), selectedBoundTarget);
        boundAmountInput = "";
        updateSlotIndex(
            boundsSlotIndex,
            new RecipeSlot(slot.recipe(), slot.enabled(), bounds, slot.priority(), slot.orderSize()));
    }

    private String currentBoundText(BoundTarget target) {
        RecipeSlot slot = boundsSlot();
        if (slot == null) return "";
        RecipeSlotBounds bounds = slot.bounds();
        return switch (target.resource()) {
            case ITEM -> {
                ItemStackWrapper item = itemKey(target);
                if (item == null) yield "";
                if (target.side() == BoundSide.INPUT && bounds.hasInputItemLowerBound(item)) {
                    yield Long.toString(bounds.inputItemLowerBound(item));
                }
                if (target.side() == BoundSide.OUTPUT && bounds.hasOutputItemUpperBound(item)) {
                    yield Long.toString(bounds.outputItemUpperBound(item));
                }
                yield "";
            }
            case FLUID -> {
                String fluid = fluidName(target);
                if (fluid == null) yield "";
                if (target.side() == BoundSide.INPUT && bounds.hasInputFluidLowerBound(fluid)) {
                    yield Long.toString(bounds.inputFluidLowerBound(fluid));
                }
                if (target.side() == BoundSide.OUTPUT && bounds.hasOutputFluidUpperBound(fluid)) {
                    yield Long.toString(bounds.outputFluidUpperBound(fluid));
                }
                yield "";
            }
        };
    }

    private boolean hasBound(BoundTarget target) {
        RecipeSlot slot = boundsSlot();
        if (slot == null) return false;
        RecipeSlotBounds bounds = slot.bounds();
        if (target.resource() == BoundResource.ITEM) {
            ItemStackWrapper item = itemKey(target);
            return item != null && (target.side() == BoundSide.INPUT ? bounds.hasInputItemLowerBound(item)
                : bounds.hasOutputItemUpperBound(item));
        }
        String fluid = fluidName(target);
        return fluid != null && (target.side() == BoundSide.INPUT ? bounds.hasInputFluidLowerBound(fluid)
            : bounds.hasOutputFluidUpperBound(fluid));
    }

    private boolean isBoundBlocking(BoundTarget target) {
        RecipeSlot slot = boundsSlot();
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        if (slot == null || facility == null || !hasBound(target)) return false;
        if (target.resource() == BoundResource.ITEM) {
            ItemStackWrapper item = itemKey(target);
            if (item == null) return false;
            long current = facility.inventory.getAmount(item);
            long recipeAmount = target.side() == BoundSide.INPUT ? itemInputAmount(slot.recipe(), item)
                : itemOutputAmount(slot.recipe(), item);
            long bound = target.side() == BoundSide.INPUT ? slot.bounds()
                .inputItemLowerBound(item)
                : slot.bounds()
                    .outputItemUpperBound(item);
            return target.side() == BoundSide.INPUT ? current - recipeAmount < bound : current + recipeAmount > bound;
        }
        String fluid = fluidName(target);
        if (fluid == null) return false;
        long current = facility.inventory.getFluidAmount(fluid);
        long recipeAmount = target.side() == BoundSide.INPUT ? fluidInputAmount(slot.recipe(), fluid)
            : fluidOutputAmount(slot.recipe(), fluid);
        long bound = target.side() == BoundSide.INPUT ? slot.bounds()
            .inputFluidLowerBound(fluid)
            : slot.bounds()
                .outputFluidUpperBound(fluid);
        return target.side() == BoundSide.INPUT ? current - recipeAmount < bound : current + recipeAmount > bound;
    }

    private RecipeSlotBounds updateBound(RecipeSlotBounds bounds, BoundTarget target, long amount) {
        if (target.resource() == BoundResource.ITEM) {
            ItemStackWrapper item = itemKey(target);
            if (item == null) return bounds;
            return target.side() == BoundSide.INPUT ? bounds.withInputItemLowerBound(item, amount)
                : bounds.withOutputItemUpperBound(item, amount);
        }
        String fluid = fluidName(target);
        if (fluid == null) return bounds;
        return target.side() == BoundSide.INPUT ? bounds.withInputFluidLowerBound(fluid, amount)
            : bounds.withOutputFluidUpperBound(fluid, amount);
    }

    private RecipeSlotBounds clearBound(RecipeSlotBounds bounds, BoundTarget target) {
        if (target.resource() == BoundResource.ITEM) {
            ItemStackWrapper item = itemKey(target);
            if (item == null) return bounds;
            return target.side() == BoundSide.INPUT ? bounds.withoutInputItemLowerBound(item)
                : bounds.withoutOutputItemUpperBound(item);
        }
        String fluid = fluidName(target);
        if (fluid == null) return bounds;
        return target.side() == BoundSide.INPUT ? bounds.withoutInputFluidLowerBound(fluid)
            : bounds.withoutOutputFluidUpperBound(fluid);
    }

    private String boundDescription(BoundTarget target) {
        String side = target.side() == BoundSide.INPUT ? "Input lower" : "Output upper";
        return side + " " + resourceName(target);
    }

    private String resourceName(BoundTarget target) {
        if (target.resource() == BoundResource.ITEM) {
            ItemStack stack = itemStack(target);
            return stack != null ? stack.getDisplayName() : "?";
        }
        FluidStack stack = fluidStack(target);
        return stack != null ? fluidLabel(stack) : "?";
    }

    private @Nullable ItemStack itemStack(BoundTarget target) {
        RecipeSlot slot = boundsSlot();
        if (slot == null || target.resource() != BoundResource.ITEM) return null;
        ItemStack[] stacks = target.side() == BoundSide.INPUT ? slot.recipe()
            .inputs()
            : slot.recipe()
                .outputs();
        return target.index() >= 0 && stacks != null && target.index() < stacks.length ? stacks[target.index()] : null;
    }

    private @Nullable ItemStackWrapper itemKey(BoundTarget target) {
        return ItemStackWrapper.of(itemStack(target));
    }

    private @Nullable FluidStack fluidStack(BoundTarget target) {
        RecipeSlot slot = boundsSlot();
        if (slot == null || target.resource() != BoundResource.FLUID) return null;
        FluidStack[] stacks = target.side() == BoundSide.INPUT ? slot.recipe()
            .fluidInputs()
            : slot.recipe()
                .fluidOutputs();
        return target.index() >= 0 && stacks != null && target.index() < stacks.length ? stacks[target.index()] : null;
    }

    private @Nullable String fluidName(BoundTarget target) {
        FluidStack stack = fluidStack(target);
        if (stack == null) return null;
        try {
            Fluid fluid = stack.getFluid();
            return fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String fluidLabel(FluidStack stack) {
        String name = null;
        try {
            Fluid fluid = stack.getFluid();
            name = fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {}
        return (name != null ? name : "Fluid") + " " + stack.amount + "L";
    }

    private long itemInputAmount(RecipeSnapshot recipe, ItemStackWrapper item) {
        return itemAmount(recipe.inputs(), item);
    }

    private long itemOutputAmount(RecipeSnapshot recipe, ItemStackWrapper item) {
        return itemAmount(recipe.outputs(), item);
    }

    private long itemAmount(@Nullable ItemStack[] stacks, ItemStackWrapper item) {
        if (stacks == null) return 0L;
        long total = 0L;
        for (ItemStack stack : stacks) {
            ItemStackWrapper key = ItemStackWrapper.of(stack);
            if (item.equals(key)) total += stack.stackSize;
        }
        return total;
    }

    private long fluidInputAmount(RecipeSnapshot recipe, String fluid) {
        return fluidAmount(recipe.fluidInputs(), fluid);
    }

    private long fluidOutputAmount(RecipeSnapshot recipe, String fluid) {
        return fluidAmount(recipe.fluidOutputs(), fluid);
    }

    private long fluidAmount(@Nullable FluidStack[] stacks, String fluid) {
        if (stacks == null) return 0L;
        long total = 0L;
        for (FluidStack stack : stacks) {
            if (stack == null) continue;
            String name;
            try {
                Fluid fluidType = stack.getFluid();
                name = fluidType != null ? fluidType.getName() : null;
            } catch (RuntimeException ignored) {
                name = null;
            }
            if (fluid.equals(name)) total += stack.amount;
        }
        return total;
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

    private static void renderItemIcon(ItemStack stack, int x, int y) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        RenderItem renderItem = RenderItem.getInstance();
        float previousZ = renderItem.zLevel;
        renderItem.zLevel = 200f;
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        renderItem.zLevel = previousZ;
        RenderHelper.disableStandardItemLighting();
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private record BoundTarget(BoundSide side, BoundResource resource, int index) {}

    private enum BoundSide {
        INPUT,
        OUTPUT
    }

    private enum BoundResource {
        ITEM,
        FLUID
    }

    private enum Field {

        PRIORITY(0, RecipeSlotUiModel.MAX_BYTE_SETTING, 1) {

            @Override
            int value(RecipeSlot slot) {
                return slot.priority();
            }

            @Override
            RecipeSlot updated(RecipeSlot slot, int value) {
                return new RecipeSlot(slot.recipe(), slot.enabled(), slot.bounds(), (byte) value, slot.orderSize());
            }
        },
        ORDER_SIZE(1, RecipeSlotUiModel.MAX_BYTE_SETTING, 1) {

            @Override
            int value(RecipeSlot slot) {
                return slot.orderSize();
            }

            @Override
            RecipeSlot updated(RecipeSlot slot, int value) {
                return new RecipeSlot(slot.recipe(), slot.enabled(), slot.bounds(), slot.priority(), (byte) value);
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
