package com.gtnewhorizons.galaxia.quests;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

public class TileEntityResearchOutpost extends TileEntity implements IGuiHolder<PosGuiData> {

    ResearchQuest activeQuest;

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("researchOutpost").size(300, 250);

        Flow mainColumn = Flow.column()
            .top(4);
        Flow questPanels = Flow.row()
            .left(4)
            .width(292)
            .childPadding(4);
        Flow questSelectColumn = Flow.column()
            .childPadding(1)
            .size(144, 242);

        // This dynamic will return an empty column if there is no active quest. If there is an active quest
        // the column will be populated - this means that in the second half of the dynamic you can write widgets
        // with the assumption that there is a non-null active quest.
        DynamicSyncHandler questSyncHandler = new DynamicSyncHandler()
            .widgetProvider((syncManager1, packet) -> makeActiveQuestColumn());

        mainColumn.child(
            IKey.str("Research Outpost: " + worldObj.provider.getDimensionName())
                .asWidget()
                .paddingBottom(8));

        questSelectColumn.child(
            IKey.str("Available Research Tasks")
                .asWidget()
                .style(EnumChatFormatting.UNDERLINE)
                .paddingBottom(4));
        for (ResearchQuest quest : QuestRegistry.researchQuests) {
            if (quest.planet.getId() == worldObj.provider.dimensionId) {
                questSelectColumn.child(
                    new ButtonWidget<>().syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        activeQuest = quest;
                        questSyncHandler.notifyUpdate(buffer -> {});
                    }))
                        .size(120, 20)
                        .child(
                            Flow.row()
                                .height(20)
                                .childPadding(4)
                                .left(2)
                                .child(quest.icon.asWidget())
                                .child(
                                    IKey.str(quest.name)
                                        .asWidget())));
            }
        }

        panel.child(mainColumn);
        mainColumn.child(questPanels);
        questPanels.child(questSelectColumn);
        questPanels.child(
            new DynamicSyncedWidget<>().syncHandler(questSyncHandler)
                .top(4));
        // Update the questSyncHandler immediately so that cached activeQuest loads into the gui
        questSyncHandler.notifyUpdate(buffer -> {});
        return panel;
    }

    protected Flow makeActiveQuestColumn() {
        Flow activeQuestColumn = Flow.column()
            .childPadding(1)
            .size(144, 242);
        if (activeQuest == null) return activeQuestColumn;
        activeQuestColumn.child(
            IKey.str(activeQuest.name)
                .asWidget()
                .style(EnumChatFormatting.UNDERLINE)
                .paddingBottom(4));
        activeQuestColumn.child(
            IKey.str(activeQuest.desc)
                .asWidget()
                .paddingBottom(4));
        for (ItemStack input : activeQuest.inputs) {
            activeQuestColumn.child(
                Flow.row()
                    .height(25)
                    .childPadding(2)
                    .child(
                        new ItemDisplayWidget().item(input)
                            .displayAmount(false))
                    .child(
                        IKey.str("0/" + input.stackSize + " " + input.getDisplayName())
                            .asWidget()
                            .width(120)));
        }
        return activeQuestColumn;
    }
}
