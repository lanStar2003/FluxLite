package com.fluxlite.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.FluxLite;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.compat.Wrenches;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.tile.TileConnector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A small core with an arm and flange towards every neighbour it is connected to (machines, hatches, cables of GT,
 * IC2 or RF mods). Shape, collision and selection box follow the arms.
 */
public class BlockConnector extends BlockContainer {

    /** Model geometry in 1/16 block. */
    public static final float CORE_MIN = 5 / 16f, CORE_MAX = 11 / 16f, ARM_MIN = 6 / 16f, ARM_MAX = 10 / 16f,
        PLATE_MIN = 4 / 16f, PLATE_MAX = 12 / 16f, PLATE_DEPTH = 1 / 16f;

    public static final int PART_CORE = 0, PART_ARM = 1, PART_PLATE = 2;
    public static int renderId = -1;

    /** Set by the renderer for the part it is drawing; picks the icon. */
    @SideOnly(Side.CLIENT)
    public static int renderPart, renderVis;
    @SideOnly(Side.CLIENT)
    public static boolean renderActive;

    @SideOnly(Side.CLIENT)
    private IIcon core, coreActive;
    @SideOnly(Side.CLIENT)
    private IIcon[] arms, plates;

    public BlockConnector() {
        super(Material.iron);
        setBlockName("fluxlite.connector");
        setHardness(3.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(ModBlocks.TAB);
        setLightOpacity(0);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileConnector();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isNormalCube() {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        String[] states = { "in", "out", "both", "idle", "off", "error" };
        core = reg.registerIcon(FluxLite.MODID + ":connector_core");
        coreActive = reg.registerIcon(FluxLite.MODID + ":connector_core_active");
        arms = new IIcon[states.length];
        plates = new IIcon[states.length];
        for (int i = 0; i < states.length; i++) {
            arms[i] = reg.registerIcon(FluxLite.MODID + ":connector_arm_" + states[i]);
            plates[i] = reg.registerIcon(FluxLite.MODID + ":connector_plate_" + states[i]);
        }
    }

    @SideOnly(Side.CLIENT)
    private IIcon current() {
        int i = Math.max(0, Math.min(arms.length - 1, renderVis - 1));
        return switch (renderPart) {
            case PART_ARM -> arms[i];
            case PART_PLATE -> plates[i];
            default -> renderActive ? coreActive : core;
        };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return current();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return current();
    }

    // ------------------------------------------------------------------ shape

    /** Box of the arm (including flange) towards {@code side}, in block coordinates. */
    public static float[] armBox(int side) {
        float a = ARM_MIN, b = ARM_MAX, lo = 0, hi = CORE_MIN;
        float lo2 = CORE_MAX, hi2 = 1;
        return switch (side) {
            case 0 -> new float[] { a, lo, a, b, hi, b };
            case 1 -> new float[] { a, lo2, a, b, hi2, b };
            case 2 -> new float[] { a, a, lo, b, b, hi };
            case 3 -> new float[] { a, a, lo2, b, b, hi2 };
            case 4 -> new float[] { lo, a, a, hi, b, b };
            default -> new float[] { lo2, a, a, hi2, b, b };
        };
    }

    public static float[] plateBox(int side) {
        float a = PLATE_MIN, b = PLATE_MAX, d = PLATE_DEPTH;
        return switch (side) {
            case 0 -> new float[] { a, 0, a, b, d, b };
            case 1 -> new float[] { a, 1 - d, a, b, 1, b };
            case 2 -> new float[] { a, a, 0, b, b, d };
            case 3 -> new float[] { a, a, 1 - d, b, b, 1 };
            case 4 -> new float[] { 0, a, a, d, b, b };
            default -> new float[] { 1 - d, a, a, 1, b, b };
        };
    }

    private static boolean arm(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileConnector c && c.hasArm(side);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float[] b = { CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX };
        for (int s = 0; s < 6; s++) {
            if (!arm(world, x, y, z, s)) continue;
            float[] a = plateBox(s);
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], a[i]);
                b[i + 3] = Math.max(b[i + 3], a[i + 3]);
            }
        }
        setBlockBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list, Entity e) {
        setBlockBounds(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX);
        super.addCollisionBoxesToList(world, x, y, z, mask, list, e);
        for (int s = 0; s < 6; s++) {
            if (!arm(world, x, y, z, s)) continue;
            float[] a = armBox(s);
            setBlockBounds(a[0], a[1], a[2], a[3], a[4], a[5]);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, e);
            float[] p = plateBox(s);
            setBlockBounds(p[0], p[1], p[2], p[3], p[4], p[5]);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, e);
        }
        setBlockBoundsBasedOnState(world, x, y, z);
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileConnector c)) return;
        if (!(placer instanceof EntityPlayerMP player) || placer instanceof FakePlayer) {
            // automation cannot own a connector: drop it again
            world.func_147480_a(x, y, z, true);
            return;
        }
        if (Config.maxConnectorsPerTeam > 0) {
            Registry reg = Registry.get();
            if (reg != null && reg.countForTeam(GTWirelessBackend.INSTANCE.resolveTeam(player.getUniqueID()))
                >= Config.maxConnectorsPerTeam) {
                player.addChatMessage(
                    new ChatComponentTranslation("fluxlite.msg.team_limit", Config.maxConnectorsPerTeam));
                world.func_147480_a(x, y, z, true);
                return;
            }
        }
        c.owner = player.getUniqueID();
        c.ownerName = player.getCommandSenderName();
        WrenchActions.restore(c, stack);
        c.markDirty();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        // a wrench is handled by WrenchActions on the server
        if (Wrenches.usable(player.getHeldItem(), player, x, y, z)) return true;
        if (player.isSneaking() && player.getHeldItem() != null) return false;
        if (world.isRemote) FluxLite.proxy.openConnectorGui(player, x, y, z);
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) return;
        if (world.getTileEntity(x, y, z) instanceof TileConnector c) c.markNeighborChanged();
    }

    @Override
    public void onNeighborChange(IBlockAccess world, int x, int y, int z, int tileX, int tileY, int tileZ) {
        if (world.getTileEntity(x, y, z) instanceof TileConnector c && c.getWorldObj() != null
            && !c.getWorldObj().isRemote) c.markNeighborChanged();
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileConnector c) c.onBroken();
        super.breakBlock(world, x, y, z, block, meta);
    }
}
