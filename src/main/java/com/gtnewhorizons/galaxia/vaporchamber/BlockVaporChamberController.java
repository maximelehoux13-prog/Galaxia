package com.gtnewhorizons.galaxia.vaporchamber;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockVaporChamberController extends Block implements ITileEntityProvider {
    public BlockVaporChamberController() {
        super(Material.iron);
        this.setHardness(1.5F);
        this.setResistance(10.0f);
        this.setBlockTextureName("lapis_block");
    }

    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityVaporChamberController();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack itemIn) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityVaporChamberController tevcc)) {
            return;
        }

        tevcc.genGrid();
    }
}
