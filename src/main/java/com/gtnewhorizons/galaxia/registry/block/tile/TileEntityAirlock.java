package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

public class TileEntityAirlock extends GalaxiaMultiblockBase<TileEntityAirlock> {

    public TileEntityAirlock() {
        super();
    }

    public enum AirlockState {
        CLOSED,
        OPEN,
    }

    private AirlockState state = AirlockState.CLOSED;

    public static final int MAX_CONNECTIONS = 2;
    private List<BlockPos> stationControllers = new ArrayList<>(MAX_CONNECTIONS);

    /**
     * Controller is now on the BOTTOM layer of the structure.
     */
    public static final int CONTROLLER_OFFSET_X = 1;
    public static final int CONTROLLER_OFFSET_Y = 3;
    public static final int CONTROLLER_OFFSET_Z = 0;

    public static final String STRUCTURE_PIECE_MAIN = "main";

    public static final List<Block> VALID_BLOCKS = List.of(
        GalaxiaBlocksEnum.AIRLOCK_CASING.get(),
        GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(),
        GalaxiaBlocksEnum.AIRLOCK_DOOR.get());

    public static final IStructureDefinition<TileEntityAirlock> STRUCTURE_DEFINITION = StructureDefinition
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
        .addElement('D', StructureUtility.ofBlockAnyMeta(GalaxiaBlocksEnum.AIRLOCK_DOOR.get()))
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

    public boolean isExternalConnection() { return stationControllers.size() < MAX_CONNECTIONS; }
    public boolean isInternalConnection() { return !isExternalConnection(); }

    public List<BlockPos> getStationControllers() {
        return new ArrayList<>(stationControllers);
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
        if (stationControllers.size() >= MAX_CONNECTIONS) {
            Galaxia.LOG.error("Too many station controllers to track");
            return;
        }

        if (stationControllers.contains(pos)) return;
        stationControllers.add(pos);
        markDirty();

        if (stationControllers.size() >= MAX_CONNECTIONS) {
            for (BlockPos controllerPos : stationControllers) {
                TileStationBase base = controllerPos.getTE(worldObj);
                if (base == null) continue;
                if (base.tryRebuildMonitorGraph()) return;
            }
        }
    }

    public void untrackStationController(BlockPos pos) {
        if (!stationControllers.remove(pos)) {
            Galaxia.LOG.error("Invalid station controller to untrack");
        }
        markDirty();
    }

    public void collectGraph(TileStationController controller, List<BlockPos> monitors) {
        for (BlockPos b : stationControllers) {
            if (monitors.contains(b)) continue;

            TileStationBase te = b.getTE(worldObj);
            if (te instanceof TileStationMonitor monitor) {
                monitors.add(b);
                monitor.collectGraph(controller, monitors);
            }
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
                if (currentFacing != facing) {
                    // This forces a call to onStructureFormed
                    structureValid = false;

                    currentFacing = facing;
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onStructureFormed() {
        closeDoor();
    }

    @Override
    protected void onStructureDisformed() {
        closeDoor();
    }

    @Override
    protected boolean needsFormationOnReload() {
        return true;
    }

    @Override
    public void validate() {
        super.validate();
        if (!worldObj.isRemote) {
            markStructureDirty();
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        this.writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
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
        nbt.setTag("stationControllers", blockPosListToNBT(stationControllers));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        int s = nbt.getInteger("state");
        if (s >= 0 && s < AirlockState.values().length) {
            state = AirlockState.values()[s];
        }

        if (nbt.hasKey("stationControllers")) {
            stationControllers = blockPosListFromNBT(nbt.getTagList("stationControllers", Constants.NBT.TAG_COMPOUND));
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

    @Override
    public void invalidate() {
        super.invalidate();
        if (isChunkUnloading) {
            setDoorState(false);
        }
    }

    protected static NBTTagList blockPosListToNBT(List<BlockPos> positions) {
        NBTTagList tagList = new NBTTagList();
        for (BlockPos pos : positions) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", pos.x());
            tag.setInteger("y", pos.y());
            tag.setInteger("z", pos.z());
            tagList.appendTag(tag);
        }
        return tagList;
    }

    protected static List<BlockPos> blockPosListFromNBT(NBTTagList tagList) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            positions.add(new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
        }
        return positions;
    }
}
