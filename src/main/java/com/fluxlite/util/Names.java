package com.fluxlite.util;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Names of devices for the GUIs. A face that reaches its devices through a cable or a pipe is named after those
 * devices, not after the cable (GT cables don't even have a usable name: they report "Unknown").
 */
public final class Names {

    private Names() {}

    /** Localized name of the block a tile belongs to; GT machines by their own name. */
    public static String of(TileEntity te) {
        if (te == null) return "";
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte != null) {
                try {
                    String n = mte.getLocalName();
                    if (usable(n)) return n;
                } catch (Throwable ignored) {}
            }
        }
        Block b = te.getBlockType();
        if (b == null) return "";
        try {
            Item item = Item.getItemFromBlock(b);
            if (item != null) {
                String n = new ItemStack(item, 1, b.damageDropped(te.getBlockMetadata())).getDisplayName();
                if (usable(n)) return n;
            }
        } catch (Throwable ignored) {}
        return b.getLocalizedName();
    }

    private static boolean usable(String n) {
        return n != null && !n.isEmpty() && !n.equals("Unknown") && !n.startsWith("gt.") && !n.startsWith("tile.");
    }

    /**
     * One name for several devices: the name itself for one, "name ×3" when all are the same, otherwise the first one
     * and how many others ("name +2"). Language neutral, because it is built on the server.
     */
    public static String summarize(List<String> names) {
        if (names.isEmpty()) return "";
        String first = names.get(0);
        if (names.size() == 1) return first;
        for (String n : names) if (!n.equals(first)) return first + " +" + (names.size() - 1);
        return first + " ×" + names.size();
    }
}
