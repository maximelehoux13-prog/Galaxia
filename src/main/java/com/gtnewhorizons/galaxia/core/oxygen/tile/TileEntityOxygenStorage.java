package com.gtnewhorizons.galaxia.core.oxygen.tile;

import net.minecraft.item.ItemStack;
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
import com.gtnewhorizons.galaxia.core.oxygen.api.IOxygenStorage;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

public class TileEntityOxygenStorage extends TileEntity implements IOxygenStorage, IGuiHolder<PosGuiData> {

    ItemStackHandler oxygenSlot = new LimitingItemStackHandler(1);

    private static final String CURRENT_OXYGEN = "current oxygen";
    private static final String MAX_OXYGEN = "max oxygen";

    private int oxygenStored = 0;
    private final int capacity;

    public TileEntityOxygenStorage() {
        this.capacity = 10000; // maybe make bigger ones later idk :p
    }

    @Override
    public int tankSize() {
        return this.capacity;
    }

    @Override
    public int transferAmount() {
        return 300;
    }

    @Override
    public int currentOxygen() {
        return this.oxygenStored;
    }

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

    @Override
    public boolean drain(int amount) {
        if (amount <= 0) return false;
        int toDrain = Math.min(this.oxygenStored, amount);

        if (toDrain > 0) {
            this.oxygenStored -= toDrain;
            this.markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void fill(int amount) {
        if (amount <= 0) return;


        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        int current = nbt.getInteger(CURRENT_OXYGEN);
        int newAmount = Math.min(current + amount, this.capacity);

        nbt.setInteger(CURRENT_OXYGEN, newAmount);
        this.oxygenStored = newAmount;

        readFromNBT(nbt);
        markDirty();
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;

        ItemStack tankStack = oxygenSlot.getStackInSlot(0);
        if (tankStack == null || !(tankStack.getItem() instanceof IOxygenStorage)) return;

        IOxygenStorage storage = (IOxygenStorage) tankStack.getItem();

        // Calculate how much we can take from the item
        int spaceInMachine = this.tankSize() - this.currentOxygen();
        int availableInItem = storage.currentOxygenFromStack(tankStack); // <-- use the stack-specific method
        int toTransfer = Math.min(this.transferAmount(), Math.min(spaceInMachine, availableInItem));

        if (toTransfer > 0) {
            // Drain from the item stack, not the item class
            if (storage.drainStack(tankStack, toTransfer)) {
                this.fill(toTransfer);
            }
        }
    }

}
