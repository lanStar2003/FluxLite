package com.fluxlite.item;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.fluxlite.FluxLite;
import com.fluxlite.block.ModBlocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Handheld control center, like an AE wireless terminal but without linking: right-click anywhere (any distance, any
 * dimension) to open the dashboard of your team's network.
 */
public class ItemFluxTerminal extends Item {

    public ItemFluxTerminal() {
        setUnlocalizedName("fluxlite.terminal");
        setTextureName(FluxLite.MODID + ":terminal");
        setMaxStackSize(1);
        setCreativeTab(ModBlocks.TAB);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) FluxLite.proxy.openTerminalGui(player);
        return stack;
    }

    /** Is the player carrying a terminal (held, or anywhere in the inventory)? */
    public static boolean carries(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemFluxTerminal) return true;
        for (ItemStack s : player.inventory.mainInventory)
            if (s != null && s.getItem() instanceof ItemFluxTerminal) return true;
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String base = getUnlocalizedName() + ".desc.";
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(base + "0"));
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
