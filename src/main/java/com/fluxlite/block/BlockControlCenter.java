package com.fluxlite.block;

import java.util.ArrayList;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.FluxLite;
import com.fluxlite.compat.Wrenches;
import com.fluxlite.tile.TileControlCenter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockControlCenter extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon front, frontAlert, side, top;

    public BlockControlCenter() {
        super(Material.iron);
        setBlockName("fluxlite.control_center");
        setHardness(5.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("wrench", 0);
        setCreativeTab(ModBlocks.TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileControlCenter();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        front = reg.registerIcon(FluxLite.MODID + ":control_center_front");
        frontAlert = reg.registerIcon(FluxLite.MODID + ":control_center_front_alert");
        side = reg.registerIcon(FluxLite.MODID + ":control_center_side");
        top = reg.registerIcon(FluxLite.MODID + ":control_center_top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        int facing = meta < 2 || meta > 5 ? 3 : meta;
        if (s == facing) return front;
        return s < 2 ? top : side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int s) {
        int meta = world.getBlockMetadata(x, y, z);
        int facing = meta < 2 || meta > 5 ? 3 : meta;
        if (s == facing) {
            return world.getTileEntity(x, y, z) instanceof TileControlCenter cc && cc.alertActive ? frontAlert : front;
        }
        return s < 2 ? top : side;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int dir = MathHelper.floor_double(placer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int[] facing = { 2, 5, 3, 4 };
        world.setBlockMetadataWithNotify(x, y, z, facing[dir], 2);
        if (!world.isRemote && placer instanceof EntityPlayer player
            && world.getTileEntity(x, y, z) instanceof TileControlCenter cc) {
            cc.owner = player.getUniqueID();
            cc.ownerName = player.getCommandSenderName();
            WrenchActions.restore(cc, stack);
            cc.markDirty();
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int s, float hx, float hy,
        float hz) {
        // a wrench is handled by WrenchActions on the server
        if (Wrenches.usable(player.getHeldItem(), player, x, y, z)) return true;
        if (player.isSneaking() && player.getHeldItem() != null) return false;
        if (world.isRemote) FluxLite.proxy.openControlCenterGui(player, x, y, z);
        return true;
    }

    /** Wrenches turn it: towards the clicked side, or by 90 degrees when the front, top or bottom is clicked. */
    @Override
    public boolean rotateBlock(World world, int x, int y, int z, ForgeDirection axis) {
        int meta = world.getBlockMetadata(x, y, z);
        int facing = meta < 2 || meta > 5 ? 3 : meta;
        int next = axis != null && axis.offsetY == 0 && axis != ForgeDirection.UNKNOWN && axis.ordinal() != facing
            ? axis.ordinal()
            : ForgeDirection.getOrientation(facing)
                .getRotation(ForgeDirection.UP)
                .ordinal();
        world.setBlockMetadataWithNotify(x, y, z, next, 3);
        return true;
    }

    @Override
    public ForgeDirection[] getValidRotations(World world, int x, int y, int z) {
        return new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH,
            ForgeDirection.WEST };
    }

    // ------------------------------------------------------------------ harvesting (like a GT machine)

    @Override
    public boolean canHarvestBlock(EntityPlayer player, int meta) {
        return Harvest.canHarvest(player) || super.canHarvestBlock(player, meta);
    }

    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z) {
        return Harvest.strength(this, player, world, x, y, z);
    }

    /** Keeps the tile entity until {@link #harvestBlock} has dropped the item, so it can keep the settings. */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest) return true;
        return super.removedByPlayer(world, player, x, y, z, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return Harvest.drops(this, world, x, y, z);
    }

    @Override
    public boolean canProvidePower() {
        return true;
    }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int s) {
        return world.getTileEntity(x, y, z) instanceof TileControlCenter cc ? cc.redstoneLevel() : 0;
    }
}
