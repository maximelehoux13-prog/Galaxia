package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.galaxia.compat.GalaxiaStructureUtility;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeTile;
import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;

public class TileStationMonitor extends TileStationBase<TileStationMonitor>
    implements ArbitraryShapeTile<TileStationMonitor> {

    private @Nullable BlockPos mainController;

    public final ArbitraryShapeDefinition<TileStationMonitor> STRUCTURE_DEFINITION = ArbitraryShapeDefinition
        .<TileStationMonitor>builder()
        .addControllerBlock(GalaxiaBlocksEnum.STATION_MONITOR.get())
        .addElements(
            BASE_VALID_BLOCKS.stream()
                .map(b -> StructureUtility.ofBlock(b, 0)))
        .addElement(GalaxiaStructureUtility.ofTileAdderCheckHints((_, tileEntity) -> {
            if (tileEntity instanceof TileEntityAirlock airlock) {
                if (!airlock.isStructureValid()) return false;

                BlockPos airlockPos = new BlockPos(airlock.xCoord, airlock.yCoord, airlock.zCoord);
                if (!this.airlocks.contains(airlockPos)) {
                    this.airlocks.add(airlockPos);
                }
                return true;
            }
            return false;
        }, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0))
        .embedDefinition(TileEntityAirlock.STRUCTURE_PIECE_MAIN, TileEntityAirlock.STRUCTURE_DEFINITION)
        .build();

    @Override
    public boolean isStructureValid() {
        return structureValid;
    }

    @Override
    public IStructureDefinition<TileStationMonitor> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
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
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (!worldObj.isRemote) {
            markStructureDirty();
        }
        return new ModularPanel("galaxia:station_monitor").size(210, 130)
            .child(
                IKey.str(StatCollector.translateToLocal("galaxia.gui.station_monitor.title"))
                    .asWidget()
                    .pos(8, 8))
            .child(
                new ButtonWidget<>().size(190, 30)
                    .pos(10, 85)
                    .overlay(
                        IKey.str(
                            (structureValid ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                                + StatCollector.translateToLocal("galaxia.gui.station_monitor.status")
                                + EnumChatFormatting.RESET))
                    .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                        if (mouseData.mouseButton != 0 || worldObj.isRemote) return;
                        markStructureDirty();
                    })));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
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

    public void collectGraph(TileStationController controller, List<BlockPos> monitors) {
        mainController = controller.here;

        for (BlockPos pos : airlocks) {
            TileEntityAirlock airlock = pos.getTE(worldObj);
            if (airlock == null) continue;

            airlock.collectGraph(controller, monitors);
        }
    }

    public boolean tryRebuildMonitorGraph() {
        if (mainController != null) {
            TileStationController controller = mainController.getTE(worldObj);
            if (controller == null) return false;

            return controller.tryRebuildMonitorGraph();
        }

        return false;
    }
}
