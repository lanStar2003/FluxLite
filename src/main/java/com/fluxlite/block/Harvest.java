package com.fluxlite.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.fluxlite.compat.Wrenches;

/**
 * Taking the blocks down like GT machines: their harvest tool is the wrench, so a wrench (left click) breaks them
 * quickly and they drop as an item on the ground. A pickaxe still works. The dropped item keeps the block's settings
 * either way.
 */
final class Harvest {

    /** Break speed of a wrench that does not speed up mining by itself (GT wrenches are faster). */
    private static final float WRENCH_SPEED = 8f;

    private Harvest() {}

    static boolean canHarvest(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        return held != null && (Wrenches.isWrench(held) || held.getItem()
            .getToolClasses(held)
            .contains("pickaxe"));
    }

    /** Same as Forge's block strength, with the wrench and the pickaxe both counting as the right tool. */
    static float strength(Block b, EntityPlayer player, World w, int x, int y, int z) {
        float hardness = b.getBlockHardness(w, x, y, z);
        if (hardness < 0) return 0;
        int meta = w.getBlockMetadata(x, y, z);
        boolean ok = b.canHarvestBlock(player, meta);
        float speed = player.getBreakSpeed(b, !ok, meta, x, y, z);
        if (Wrenches.isWrench(player.getHeldItem())) speed = Math.max(speed, WRENCH_SPEED);
        return speed / hardness / (ok ? 30F : 100F);
    }

    /** The block as an item, with its settings when they differ from a new one. */
    static ArrayList<ItemStack> drops(Block b, World w, int x, int y, int z) {
        ArrayList<ItemStack> l = new ArrayList<>();
        ItemStack s = new ItemStack(b, 1, 0);
        NBTTagCompound kept = WrenchActions.settings(w.getTileEntity(x, y, z));
        if (kept != null) {
            NBTTagCompound root = new NBTTagCompound();
            root.setTag(WrenchActions.TAG, kept);
            s.setTagCompound(root);
        }
        l.add(s);
        return l;
    }
}
