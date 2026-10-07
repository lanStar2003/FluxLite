package com.fluxlite.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.SoundResource;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

/**
 * Recognises wrenches of the common mods without depending on them: GT's wrench list, anything with the Forge tool
 * class "wrench", and items implementing one of the usual wrench interfaces (BuildCraft, CoFH, AE2, EnderIO,
 * Mekanism, IC2).
 */
public final class Wrenches {

    private static final String BC_WRENCH = "buildcraft.api.tools.IToolWrench";
    private static final String[] INTERFACES = { BC_WRENCH, "cofh.api.item.IToolHammer",
        "appeng.api.implementations.items.IAEWrench", "crazypants.enderio.api.tool.ITool", "mekanism.api.IMekWrench",
        "ic2.api.item.IWrench" };
    private static final String[] CLASS_NAMES = { "ic2.core.item.tool.ItemToolWrench" };

    /** Per item class: which of the known wrench types it is (cached, items never change their class). */
    private static final Map<Class<?>, String> KIND = new ConcurrentHashMap<>();

    private Wrenches() {}

    private static String kind(Item item) {
        return KIND.computeIfAbsent(item.getClass(), Wrenches::findKind);
    }

    private static String findKind(Class<?> c) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (String n : CLASS_NAMES) if (k.getName()
                .equals(n)) return n;
            String found = interfaceOf(k);
            if (found != null) return found;
        }
        return "";
    }

    private static String interfaceOf(Class<?> c) {
        for (Class<?> i : c.getInterfaces()) {
            for (String n : INTERFACES) if (i.getName()
                .equals(n)) return n;
            String deeper = interfaceOf(i);
            if (deeper != null) return deeper;
        }
        return null;
    }

    private static boolean gtWrench(ItemStack s) {
        try {
            return GTUtility.isStackInList(s, GregTechAPI.sWrenchList);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Is this item a wrench of any of the known kinds? */
    public static boolean isWrench(ItemStack s) {
        if (s == null || s.getItem() == null) return false;
        return !kind(s.getItem()).isEmpty() || gtWrench(s)
            || s.getItem()
                .getToolClasses(s)
                .contains("wrench");
    }

    /** Is the held item a wrench that can be used on this block right now (charged, not broken)? */
    public static boolean usable(ItemStack s, EntityPlayer player, int x, int y, int z) {
        if (s == null || s.getItem() == null) return false;
        String kind = kind(s.getItem());
        if (kind.equals(BC_WRENCH)) {
            Boolean ok = call(s.getItem(), "canWrench", player, x, y, z);
            return ok == null || ok;
        }
        if (!kind.isEmpty() || gtWrench(s)) return true;
        return s.getItem()
            .getToolClasses(s)
            .contains("wrench");
    }

    /** Wears the wrench a little and plays the wrench sound. */
    public static void use(ItemStack s, EntityPlayer player, World world, int x, int y, int z) {
        if (s == null || s.getItem() == null) return;
        if (kind(s.getItem()).equals(BC_WRENCH)) call(s.getItem(), "wrenchUsed", player, x, y, z);
        else if (gtWrench(s)) {
            try {
                GTModHandler.damageOrDechargeItem(s, 1, 1000, player);
            } catch (Throwable ignored) {}
        }
        if (s.stackSize <= 0) player.destroyCurrentEquippedItem();
        try {
            GTUtility.sendSoundToPlayers(world, SoundResource.IC2_TOOLS_WRENCH, 1.0F, -1.0F, x + 0.5, y + 0.5, z + 0.5);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Item item, String name, EntityPlayer player, int x, int y, int z) {
        try {
            Method m = item.getClass()
                .getMethod(name, EntityPlayer.class, int.class, int.class, int.class);
            return (T) m.invoke(item, player, x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }
}
