package com.gtnewhorizons.galaxia.registry.outpost;

import com.gtnewhorizons.galaxia.registry.block.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

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
}
