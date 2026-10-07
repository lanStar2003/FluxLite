package com.fluxlite.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Item form of both blocks; adds the (shift-expandable) description from the lang file. */
public class ItemBlockFlux extends ItemBlock {

    public ItemBlockFlux(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String base = field_150939_a.getUnlocalizedName() + ".desc.";
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(base + "0"));
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(WrenchActions.TAG)) {
            NBTTagCompound t = stack.getTagCompound()
                .getCompoundTag(WrenchActions.TAG);
            if (t.hasKey("name")) list.add(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocalFormatted("fluxlite.tooltip.saved_name", t.getString("name")));
            int off = Integer.bitCount(t.getByte("off") & 0x3F);
            if (off > 0) list.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("fluxlite.tooltip.saved_off", off));
            if (t.hasKey("rs") || t.hasKey("noHolo") || t.hasKey("holoSize") || t.hasKey("holoOpaque"))
                list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("fluxlite.tooltip.saved_settings"));
        }
        if (!GuiScreen.isShiftKeyDown()) {
            list.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("fluxlite.tooltip.shift"));
            return;
        }
        for (int i = 1; i < 12; i++) {
            String key = base + i;
            if (!StatCollector.canTranslate(key)) break;
            list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(key));
        }
    }
}
