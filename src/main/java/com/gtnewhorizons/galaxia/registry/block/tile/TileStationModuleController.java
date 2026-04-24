package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.UUID;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
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
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
public class TileStationModuleController extends GalaxiaMultiblockBase<TileStationModuleController>
    implements IGuiHolder<PosGuiData> {

    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_DIAMETER = SEARCH_RADIUS * 2;
    private static final int MAX_BLOCKS_TO_CHECK = SEARCH_RADIUS * SEARCH_RADIUS * SEARCH_RADIUS;

    private final HashSet<BlockPos> structureBlocks = new HashSet<>(SEARCH_DIAMETER * SEARCH_DIAMETER * 6);
    private UUID owner;
    private boolean inValidDimension = false;
    private CelestialAsset.ID backingStation;

    private ForgeDirection placedFacing = ForgeDirection.NORTH;
    private ExtendedFacing currentFacing = ExtendedFacing.DEFAULT;

    private enum CleanroomBlockType {
        CASING,
        GLASS,
        OTHER,
        INVALID
    }

    // Specify which blocks are allowed where. This skips checks for other blocks.
    private static final int MASK_CASING = 1;
    private static final int MASK_GLASS = 1 << 1;
    private static final int MASK_OTHER = 1 << 2;

    private static final int MASK_WALL_INTERNAL = MASK_CASING | MASK_GLASS | MASK_OTHER;
    private static final int MASK_WALL_EDGE = MASK_CASING | MASK_GLASS | MASK_OTHER;

    public static boolean isBlockAllowed(Block block) {
        return block == GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get();
    }

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
        // On reload, recompute but keep structure valid to not mess anything up
        if (structureValid && !this.structureBlocks.isEmpty()) return true;

        World world = this.worldObj;
        CelestialObjectId objectId =  GalaxiaCelestialAPI.getObjectFromDimension(world.provider.dimensionId);
        this.inValidDimension = objectId != CelestialObjectId.INVALID;
        if (!this.inValidDimension) return false;

        boolean enclosed = checkEnclosed(world);
        if (enclosed != structureValid) {
            structureValid = enclosed;
            if (enclosed) onStructureFormed();
            else onStructureDisformed();
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

            Station station = (Station) CelestialAsset.create(objectId, CelestialAsset.Kind.STATION, Buildable.Status.OPERATIONAL);
            station.setController(new BlockPos(xCoord, yCoord, zCoord));
            backingStation = station.assetId;

            CelestialAssetStore.add(owner, station);
        }
        return enclosed;
    }

    private boolean checkEnclosed(World world) {

        ForgeDirection dir = placedFacing;

        int startX = xCoord + dir.offsetX;
        int startY = yCoord + dir.offsetY;
        int startZ = zCoord + dir.offsetZ;

        BlockPos start = new BlockPos(startX, startY, startZ);

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> airPocket = new HashSet<>();

        queue.add(start);
        airPocket.add(start);

        int blocksChecked = 0;
        while (!queue.isEmpty() && blocksChecked++ < MAX_BLOCKS_TO_CHECK) {
            BlockPos current = queue.poll();

            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                BlockPos n = new BlockPos(
                    current.x() + d.offsetX,
                    current.y() + d.offsetY,
                    current.z() + d.offsetZ
                );

                Block b = world.getBlock(n.x(), n.y(), n.z());

                boolean isAir = b.isAir(world, n.x(), n.y(), n.z());
                if (!isAir) continue;

                if (airPocket.add(n)) {
                    queue.add(n);
                }
            }
        }

        if (!queue.isEmpty()) return false;

        structureBlocks.clear();
        for (BlockPos current: airPocket) {
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                BlockPos n = new BlockPos(
                    current.x() + d.offsetX,
                    current.y() + d.offsetY,
                    current.z() + d.offsetZ
                );

                Block b = world.getBlock(n.x(), n.y(), n.z());

                if (b == GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get()) {
                    structureBlocks.add(n);
                }
            }
        }

        return !structureBlocks.isEmpty() && structureBlocks.size() >= 6;
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
            owner = new UUID(
                nbt.getLong("ownerMost"),
                nbt.getLong("ownerLeast")
            );
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

        boolean hitUp = false;
        boolean hitDown = false;

        for (int d = 1; d <= SEARCH_RADIUS; d++) {
            if (!hitUp && structureBlocks.contains(new BlockPos(x, y + d, z))) {
                hitUp = true;
            }

            if (!hitDown && structureBlocks.contains(new BlockPos(x, y - d, z))) {
                hitDown = true;
            }

            if (hitUp && hitDown) {
                return true;
            }
        }

        return false;
    }
}
