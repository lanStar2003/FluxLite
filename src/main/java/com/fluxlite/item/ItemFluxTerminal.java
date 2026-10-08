package com.fluxlite.item;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.fluxlite.Config;
import com.fluxlite.FluxLite;
import com.fluxlite.block.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Handheld control center, like an AE wireless terminal but without linking: right-click anywhere (any distance, any
 * dimension) to open the dashboard of your team's network. Carried with charging on (sneak-right-click switches it),
 * it also charges the electric items in your inventory from the team's wireless network.
 */
public class ItemFluxTerminal extends Item {

    private static final String NO_CHARGE = "fluxliteNoCharge";

    public ItemFluxTerminal() {
        setUnlocalizedName("fluxlite.terminal");
        setTextureName(FluxLite.MODID + ":terminal");
        setMaxStackSize(1);
        setCreativeTab(ModBlocks.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (!world.isRemote) {
                boolean on = !chargingOn(stack);
                NBTTagCompound t = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
                if (on) t.removeTag(NO_CHARGE);
                else t.setBoolean(NO_CHARGE, true);
                stack.setTagCompound(t.hasNoTags() ? null : t);
                player.addChatMessage(
                    new ChatComponentTranslation(on ? "fluxlite.terminal.charge_on" : "fluxlite.terminal.charge_off"));
            }
            return stack;
        }
        if (world.isRemote) FluxLite.proxy.openTerminalGui(player);
        return stack;
    }

    /** Whether this terminal charges (on unless switched off). */
    public static boolean chargingOn(ItemStack s) {
        return s != null && s.getItem() instanceof ItemFluxTerminal
            && !(s.hasTagCompound() && s.getTagCompound()
                .getBoolean(NO_CHARGE));
    }

    /** Is the player carrying a terminal (held, or anywhere in the inventory)? */
    public static boolean carries(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemFluxTerminal) return true;
        for (ItemStack s : player.inventory.mainInventory)
            if (s != null && s.getItem() instanceof ItemFluxTerminal) return true;
        return false;
    }

    /** Is the player carrying a terminal with charging on? */
    public static boolean charging(EntityPlayer player) {
        for (ItemStack s : player.inventory.mainInventory) if (chargingOn(s)) return true;
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String base = getUnlocalizedName() + ".desc.";
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(base + "0"));
        if (Config.chargingEnabled) list.add(
            chargingOn(stack) ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("fluxlite.terminal.charging")
                : EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("fluxlite.terminal.not_charging"));
        if (!GuiScreen.isShiftKeyDown()) {
            list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("fluxlite.tooltip.shift"));
            return;
        }
        for (int i = 1; i < 8; i++) {
            String key = base + i;
            if (!StatCollector.canTranslate(key)) break;
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(key));
        }
    }
}
