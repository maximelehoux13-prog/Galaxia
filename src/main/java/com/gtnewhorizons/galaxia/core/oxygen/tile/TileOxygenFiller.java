package com.gtnewhorizons.galaxia.core.oxygen.tile;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.utils.item.LimitingItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.galaxia.core.oxygen.api.IOxygenStorage;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class TileOxygenFiller extends TileEntity implements IGuiHolder<PosGuiData>  {

    ItemStackHandler oxygenSlot = new LimitingItemStackHandler(1);

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {

        syncManager.registerSlotGroup("oxygen", 1);
        return new ModularPanel("filler").bindPlayerInventory()
            .child(new Column()
                .heightRel(.5f).child(new ItemSlot()
                        .slot(new ModularSlot(oxygenSlot, 0)
                            .filter((a) -> a.getItem() instanceof ItemOxygenTank)
                            .slotGroup("oxygen")
                        )

                    .align(Alignment.CENTER)
                )
            );
    }

    @Override
    public void updateEntity() {
        if(worldObj.isRemote){
            return;
        }

        //cant cast something if it doesnt exist :Clueless:
        if(oxygenSlot.getStackInSlot(0) == null){
            return;
        }
        // need the stack sometimes :/
        ItemStack tankStack = oxygenSlot.getStackInSlot(0);
        IOxygenStorage tank = (IOxygenStorage) oxygenSlot.getStackInSlot(0).getItem();

        int currentOxygen = tank.currentOxygenFromStack(tankStack);
        //this should just work
        if (currentOxygen < tank.tankSize()) {
            tank.fillStack(tankStack, 30);
        }
    }


}
