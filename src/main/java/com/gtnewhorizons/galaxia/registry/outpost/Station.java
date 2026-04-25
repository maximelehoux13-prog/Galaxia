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

    public BlockPos toWorld(BlockPos local) {
        if (controller == null) return null;
        return new BlockPos(local.x() + controller.x(), local.y() + controller.y(), local.z() + controller.z());
    }

    public BlockPos toLocal(BlockPos world) {
        if (controller == null) return null;
        return new BlockPos(world.x() - controller.x(), world.y() - controller.y(), world.z() - controller.z());
    }

    public int toWorldX(int localX) {
        return controller == null ? localX : localX + controller.x();
    }

    public int toWorldY(int localY) {
        return controller == null ? localY : localY + controller.y();
    }

    public int toWorldZ(int localZ) {
        return controller == null ? localZ : localZ + controller.z();
    }

    public int toLocalX(int worldX) {
        return controller == null ? worldX : worldX - controller.x();
    }

    public int toLocalY(int worldY) {
        return controller == null ? worldY : worldY - controller.y();
    }

    public int toLocalZ(int worldZ) {
        return controller == null ? worldZ : worldZ - controller.z();
    }
}
