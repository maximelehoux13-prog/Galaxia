package com.gtnewhorizons.galaxia.registry.outpost;

import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.block.tile.TileStationController;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

public class Station extends CelestialAsset {

    private BlockPos controller;

    public Station(ID assetId, CelestialObjectId celestialObjectId, Status status) {
        super(assetId, celestialObjectId, Kind.STATION, status, null);
    }

    public BlockPos getController() {
        return controller;
    }

    public void setController(BlockPos controller) {
        this.controller = controller;
    }

    @Override
    public void tick() {
        if (this.isDisabled()) return;
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) return;

        TileStationController teController = controller.getTE(world);
        if (teController == null) return; // TODO: Figure out what to do, it should never happen though

        teController.tick();
    }
}
