package com.gtnewhorizons.galaxia.core.oxygen.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

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
import com.gtnewhorizons.galaxia.core.oxygen.api.IOxygenTile;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

public class TileEntityOxygenFiller extends TileEntity implements IGuiHolder<PosGuiData>, IOxygenTile {

    private static final String CURRENT_OXYGEN = "current oxygen";
    private static final String MAX_OXYGEN = "max oxygen";

    private int oxygenStored = 0;
    private final int capacity;

    ItemStackHandler oxygenSlot = new LimitingItemStackHandler(1);

    public TileEntityOxygenFiller() {
        capacity = 10000;
    }

    // mui2 ui
    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {

        syncManager.registerSlotGroup("oxygen", 1);
        return new ModularPanel("filler").bindPlayerInventory()
            .child(
                new Column().heightRel(.5f)
                    .child(
                        new ItemSlot()
                            .slot(
                                new ModularSlot(oxygenSlot, 0).filter((a) -> a.getItem() instanceof ItemOxygenTank)
                                    .slotGroup("oxygen"))

                            .align(Alignment.CENTER)));
    }

    // syncing :rollingeyes

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.oxygenStored = nbt.getInteger(CURRENT_OXYGEN);
        if (nbt.hasKey("Inventory")) {
            oxygenSlot.deserializeNBT(nbt.getCompoundTag("Inventory"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger(CURRENT_OXYGEN, this.oxygenStored);
        nbt.setInteger(MAX_OXYGEN, this.capacity);
        nbt.setTag("Inventory", oxygenSlot.serializeNBT());
    }

    // oxygen interfacing
    @Override
    public int tankSize() {
        return this.capacity;
    }

    @Override
    public int transferAmount() {
        return 20;
    }

    @Override
    public int currentOxygen() {
        return this.oxygenStored;
    }

    @Override
    public void fill(int amount) {
        if (amount <= 0) {
            return;
        }

        this.oxygenStored = Math.min(this.oxygenStored + amount, this.capacity);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("CurrentOxygen", this.oxygenStored);
        nbt.setInteger("MaxOxygen", this.capacity);
        this.writeToNBT(nbt);
        this.markDirty();
    }

    @Override
    public boolean drain(int amount) {
        int toDrain = Math.min(this.oxygenStored, amount);
        if (toDrain <= 0) {
            return false;
        }

        this.oxygenStored -= toDrain;
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("CurrentOxygen", this.oxygenStored);
        nbt.setInteger("MaxOxygen", this.capacity);
        this.writeToNBT(nbt);
        this.markDirty();
        return toDrain == amount;
    }

}
