package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class TileStationModuleController extends GalaxiaMultiblockBase<TileStationModuleController>
    implements IGuiHolder<PosGuiData> {

    public static final int SEARCH_RADIUS = 16;
    public static final int MAX_BLOCKS = SEARCH_RADIUS * SEARCH_RADIUS * SEARCH_RADIUS;

    private final IntSet structureBlocks = LocalCoord.newBlockSet();

    private UUID owner;
    private boolean inValidDimension = false;
    private CelestialAsset.ID backingStation;

    private ForgeDirection placedFacing = ForgeDirection.NORTH;
    private ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.MODULE_CONTROLLER.get();
    }

    @Override
    public IStructureDefinition<TileStationModuleController> getStructureDefinition() {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void construct(ItemStack trigger, boolean hintsOnly) {}

    @Override
    public int survivalConstruct(ItemStack trigger, int elementBudget, ISurvivalBuildEnvironment env) {
        return -2;
    }

    @Override
    protected int getControllerOffsetX() {
        return 0;
    }

    @Override
    protected int getControllerOffsetY() {
        return 0;
    }

    @Override
    protected int getControllerOffsetZ() {
        return 0;
    }

    @Override
    protected boolean checkStructure() {
        if (worldObj == null || worldObj.isRemote) return structureValid;
        if (structureValid && !this.structureBlocks.isEmpty()) return true;

        World world = this.worldObj;
        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(world.provider.dimensionId);
        this.inValidDimension = objectId != CelestialObjectId.INVALID;
        if (!this.inValidDimension) return false;

        IntSet validBoundary = floodStructure(world);
        boolean enclosed = checkEnclosed(world, validBoundary);
        if (enclosed != structureValid) {
            structureValid = enclosed;
            if (enclosed) onStructureFormed();
            else onStructureDisformed();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

            Station station = (Station) CelestialAsset
                .create(objectId, CelestialAsset.Kind.STATION, true);
            station.setController(new BlockPos(xCoord, yCoord, zCoord));
            backingStation = station.assetId;

            CelestialAssetStore.registerAsset(owner, station);
        }
        return enclosed;
    }

    public boolean isValidBoundaryBlock(Block b) {
        return b == GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get() || b == GalaxiaBlocksEnum.MODULE_CONTROLLER.get();
    }

    private IntSet floodStructure(World world) {
        IntQueue floodBFS = new IntQueue();
        final IntSet connectedStructure = LocalCoord.newBlockSet();

        int start = LocalCoord.pack(0, 0, 0);
        floodBFS.enqueue(start);
        connectedStructure.add(start);

        while (!floodBFS.isEmpty()) {
            int cur = floodBFS.dequeue();
            int lx = LocalCoord.unpackX(cur);
            int ly = LocalCoord.unpackY(cur);
            int lz = LocalCoord.unpackZ(cur);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz)) {
                    continue;
                }

                int np = LocalCoord.pack(nlx, nly, nlz);

                int wx = LocalCoord.worldX(nlx, xCoord);
                int wy = LocalCoord.worldY(nly, yCoord);
                int wz = LocalCoord.worldZ(nlz, zCoord);

                Block b = world.getBlock(wx, wy, wz);

                if (!isValidBoundaryBlock(b)) {
                    continue;
                }

                if (connectedStructure.add(np)) {
                    floodBFS.enqueue(np);
                }
            }
        }

        return connectedStructure;
    }

    boolean checkEnclosed(World world, IntSet validBoundary) {
        IntQueue bfs = new IntQueue();
        IntSet visited = LocalCoord.newBlockSet();

        ForgeDirection dir = placedFacing;
        int start = LocalCoord.pack(dir.offsetX, dir.offsetY, dir.offsetZ);

        bfs.enqueue(start);
        visited.add(start);

        int checked = 0;
        while (!bfs.isEmpty() && checked++ < MAX_BLOCKS) {
            int cur = bfs.dequeue();
            int lx = LocalCoord.unpackX(cur);
            int ly = LocalCoord.unpackY(cur);
            int lz = LocalCoord.unpackZ(cur);

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nlx = lx + d.offsetX;
                int nly = ly + d.offsetY;
                int nlz = lz + d.offsetZ;

                if (!LocalCoord.isInBounds(nlx, nly, nlz)) {
                    return false;
                }

                int np = LocalCoord.pack(nlx, nly, nlz);

                int nwx = LocalCoord.worldX(nlx, xCoord);
                int nwy = LocalCoord.worldY(nly, yCoord);
                int nwz = LocalCoord.worldZ(nlz, zCoord);

                Block b = world.getBlock(nwx, nwy, nwz);

                if (isValidBoundaryBlock(b)) {
                    if (validBoundary.contains(np)) {
                        this.structureBlocks.add(np);
                    }
                    continue;
                }

                if (!b.isAir(world, nwx, nwy, nwz)) {
                    continue;
                }

                if (visited.add(np)) {
                    bfs.enqueue(np);
                }
            }
        }

        return !this.structureBlocks.isEmpty() && this.structureBlocks.size() >= 6;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }
        return new ModularPanel("galaxia:module_controller").size(210, 130)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.module_controller.title"))
                    .asWidget()
                    .pos(8, 8))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
                    .overlay(
                        IKey.str(
                            (structureValid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                                + StatCollector.translateToLocal("galaxia.gui.module_controller.status")
                                + EnumChatFormatting.RESET))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                        markStructureDirty();
                    })));
    }

    public ForgeDirection getPlacedFacing() {
        return placedFacing;
    }

    public void setPlacedFacing(ForgeDirection dir) {
        placedFacing = dir;
    }

    public ExtendedFacing getCurrentFacing() {
        return currentFacing;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("placedFacing", placedFacing.ordinal());
        if (owner != null) {
            nbt.setLong("ownerMost", owner.getMostSignificantBits());
            nbt.setLong("ownerLeast", owner.getLeastSignificantBits());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        placedFacing = ForgeDirection.getOrientation(nbt.getInteger("placedFacing"));
        if (nbt.hasKey("ownerMost") && nbt.hasKey("ownerLeast")) {
            owner = new UUID(nbt.getLong("ownerMost"), nbt.getLong("ownerLeast"));
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

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void unregisterStation() {
        if (this.backingStation == null) return;
        CelestialAssetStore.destroyAsset(this.backingStation);
    }

    public boolean isInside(int x, int y, int z) {

        int packed = LocalCoord.packFromWorld(x, y, z, xCoord, yCoord, zCoord);
        int lx = LocalCoord.unpackX(packed);
        int ly = LocalCoord.unpackY(packed);
        int lz = LocalCoord.unpackZ(packed);

        boolean top = false, bottom = false;

        for (int d = 1; d <= SEARCH_RADIUS; d++) {
            if (structureBlocks.contains(LocalCoord.pack(lx, ly + d, lz))) top = true;
            if (structureBlocks.contains(LocalCoord.pack(lx, ly - d, lz))) bottom = true;
            if (top && bottom) return true;
        }

        return false;
    }

    private static class LocalCoord {

        private static int offset(int v) {
            return v + SEARCH_RADIUS;
        }

        private static int unoffset(int v) {
            return v - SEARCH_RADIUS;
        }

        public static int pack(int x, int y, int z) {
            return (offset(x) << 12) | (offset(y) << 6) | offset(z);
        }

        public static int packFromWorld(int wx, int wy, int wz, int xCoord, int yCoord, int zCoord) {
            return pack(wx - xCoord, wy - yCoord, wz - zCoord);
        }

        public static int unpackX(int v) {
            return unoffset((v >> 12) & 63);
        }

        public static int unpackY(int v) {
            return unoffset((v >> 6) & 63);
        }

        public static int unpackZ(int v) {
            return unoffset(v & 63);
        }

        public static int worldX(int localX, int xCoord) {
            return localX + xCoord;
        }

        public static int worldY(int localY, int yCoord) {
            return localY + yCoord;
        }

        public static int worldZ(int localZ, int zCoord) {
            return localZ + zCoord;
        }

        public static boolean isInBounds(int x, int y, int z) {
            return x >= -SEARCH_RADIUS && x <= SEARCH_RADIUS
                && y >= -SEARCH_RADIUS
                && y <= SEARCH_RADIUS
                && z >= -SEARCH_RADIUS
                && z <= SEARCH_RADIUS;
        }

        public static IntSet newBlockSet() {
            return new IntOpenHashSet();
        }
    }

    private static class IntQueue {

        private static final int INITIAL_QUEUE_SIZE = 4096;

        private int[] queue = new int[INITIAL_QUEUE_SIZE];
        private int head;
        private int tail;

        public void enqueue(int v) {
            if (tail == queue.length) {
                queue = java.util.Arrays.copyOf(queue, queue.length * 2);
            }
            queue[tail++] = v;
        }

        public int dequeue() {
            return queue[head++];
        }

        public boolean isEmpty() {
            return head >= tail;
        }

        public void reset() {
            head = 0;
            tail = 0;
        }

        public void clear() {
            reset();
            queue = new int[INITIAL_QUEUE_SIZE];
        }
    }

}
