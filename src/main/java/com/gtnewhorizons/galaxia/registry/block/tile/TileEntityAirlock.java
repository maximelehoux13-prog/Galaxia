package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;

public class TileEntityAirlock extends GalaxiaMultiblockBase<TileEntityAirlock> {

    public enum AirlockState {
        CLOSED,
        OPEN,
    }

    private AirlockState state = AirlockState.CLOSED;

    private BlockPos stationController1;
    private BlockPos stationController2;

    /**
     * Controller is now on the BOTTOM layer of the structure.
     */
    public static final int CONTROLLER_OFFSET_X = 1;
    public static final int CONTROLLER_OFFSET_Y = 3;
    public static final int CONTROLLER_OFFSET_Z = 0;

    private static final String STRUCTURE_PIECE_MAIN = "main";

    public static final List<Block> VALID_BLOCKS = List.of(
        GalaxiaBlocksEnum.AIRLOCK_CASING.get(),
        GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(),
        GalaxiaBlocksEnum.AIRLOCK_DOOR.get());

    /**
     * Structure updated:
     * Controller ('R') is on bottom layer now.
     */
    private static final IStructureDefinition<TileEntityAirlock> STRUCTURE_DEFINITION = StructureDefinition
        .<TileEntityAirlock>builder()
        .addShape(
            STRUCTURE_PIECE_MAIN,
            // spotless:off
            StructureUtility.transpose(new String[][] {
                { "CCC" },
                { "CDC" },
                { "CDC" },
                { "CRC" }
            }))
            // spotless:on
        .addElement('C', StructureUtility.ofBlock(GalaxiaBlocksEnum.AIRLOCK_CASING.get(), 0))
        .addElement('R', StructureUtility.ofBlock(GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .addElement(
            'D',
            StructureUtility.ofChain(
                StructureUtility.ofBlock(GalaxiaBlocksEnum.AIRLOCK_DOOR.get(), 0),
                StructureUtility.ofBlock(GalaxiaBlocksEnum.AIRLOCK_DOOR.get(), 1)))
        .build();

    @Override
    public IStructureDefinition<TileEntityAirlock> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected int getControllerOffsetX() {
        return CONTROLLER_OFFSET_X;
    }

    @Override
    protected int getControllerOffsetY() {
        return CONTROLLER_OFFSET_Y;
    }

    @Override
    protected int getControllerOffsetZ() {
        return CONTROLLER_OFFSET_Z;
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get();
    }

    public AirlockState getState() {
        return state;
    }

    public boolean isOpen() {
        return state == AirlockState.OPEN;
    }

    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }

    public void toggleState() {
        if (!structureValid) return;

        switch (state) {
            case CLOSED -> {
                state = AirlockState.OPEN;
                openDoor();
            }
            case OPEN -> {
                state = AirlockState.CLOSED;
                closeDoor();
            }
        }

        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public void trackStationController(BlockPos pos) {
        if (stationController1 == null) {
            stationController1 = pos;
        } else if (stationController2 == null) {
            stationController2 = pos;
        } else {
            Galaxia.LOG.error("Too many station controllers to track");
        }
    }

    public void untrackStationController(BlockPos pos) {
        if (stationController1 == pos) {
            stationController1 = null;
        } else if (stationController2 == pos) {
            stationController2 = null;
        } else {
            Galaxia.LOG.error("Invalid station controllers to track");
        }
    }

    @Override
    protected boolean checkStructure() {
        if (worldObj == null || worldObj.isRemote) return structureValid;
        for (ExtendedFacing facing : ExtendedFacing.values()) {
            boolean valid = getStructureDefinition().check(
                this,
                STRUCTURE_PIECE_MAIN,
                worldObj,
                facing,
                xCoord,
                yCoord,
                zCoord,
                getControllerOffsetX(),
                getControllerOffsetY(),
                getControllerOffsetZ(),
                false);

            if (valid) {
                if (!structureValid || currentFacing != facing) {
                    structureValid = true;
                    currentFacing = facing;

                    onStructureFormed();

                    markDirty();
                    worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                }
                return true;
            }
        }

        if (structureValid) {
            structureValid = false;
            onStructureDisformed();

            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }

        return false;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
    }

    @Override
    protected void onStructureFormed() {
        closeDoor();
    }

    @Override
    protected void onStructureDisformed() {
        closeDoor();
    }

    private void openDoor() {
        setDoorState(true);
    }

    private void closeDoor() {
        setDoorState(false);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 256 * 256;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        nbt.setInteger("state", state.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        int s = nbt.getInteger("state");
        if (s >= 0 && s < AirlockState.values().length) {
            state = AirlockState.values()[s];
        }
    }

    private void setDoorState(boolean open) {
        state = open ? AirlockState.OPEN : AirlockState.CLOSED;

        for (int x = xCoord - 1; x <= xCoord + 1; x++) {
            for (int y = yCoord; y <= yCoord + 3; y++) {
                for (int z = zCoord - 1; z <= zCoord + 1; z++) {

                    Block block = worldObj.getBlock(x, y, z);

                    if (block instanceof BlockAirlockDoor) {
                        ((BlockAirlockDoor) block).setOpen(worldObj, x, y, z, open);
                    }
                }
            }
        }

        this.markDirty();
    }
}
