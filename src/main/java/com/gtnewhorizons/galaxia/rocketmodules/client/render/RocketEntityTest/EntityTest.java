package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class EntityTest extends Entity {
    public EntityTest(World world) {
        super(world);
        this.noClip = true;
        this.setSize(1.0F, 1.0F);
    }

    @Override
    protected void entityInit() {

    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompund) {

    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {

    }
}
