package com.fluxlite.compat;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.fluxlite.block.BlockConnector;
import com.fluxlite.block.BlockControlCenter;
import com.fluxlite.core.Port;
import com.fluxlite.core.PortRole;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.stats.Series;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.tile.TileControlCenter;
import com.fluxlite.util.Fmt;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/** Waila: owner, and per connected face the direction and current EU/t. */
public final class WailaCompat implements IWailaDataProvider {

    private static final WailaCompat INSTANCE = new WailaCompat();

    public static void register(IWailaRegistrar r) {
        r.registerBodyProvider(INSTANCE, BlockConnector.class);
        r.registerNBTProvider(INSTANCE, BlockConnector.class);
        r.registerBodyProvider(INSTANCE, BlockControlCenter.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity te = accessor.getTileEntity();
        if (te instanceof TileControlCenter cc) {
            tip.add(StatCollector.translateToLocalFormatted("fluxlite.waila.owner", cc.ownerName));
            return tip;
        }
        NBTTagCompound t = accessor.getNBTData();
        if (t == null || !t.hasKey("fl")) return tip;
        NBTTagCompound d = t.getCompoundTag("fl");
        String name = d.getString("name");
        if (!name.isEmpty()) tip.add(EnumChatFormatting.AQUA + name);
        tip.add(StatCollector.translateToLocalFormatted("fluxlite.waila.owner", d.getString("owner")));
        NBTTagList l = d.getTagList("p", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound p = l.getCompoundTagAt(i);
            PortRole role = PortRole.byId(p.getByte("r"));
            if (role == PortRole.NONE) continue;
            String side = StatCollector.translateToLocal("fluxlite.side." + p.getByte("s"));
            String dir = StatCollector.translateToLocal(role.langKey());
            EnumChatFormatting color = role == PortRole.INPUT ? EnumChatFormatting.GREEN
                : role == PortRole.OUTPUT ? EnumChatFormatting.GOLD : EnumChatFormatting.AQUA;
            String value = role == PortRole.INPUT ? Fmt.eut(p.getLong("in"))
                : role == PortRole.OUTPUT ? Fmt.eut(p.getLong("out"))
                    : "+" + Fmt.si(p.getLong("in")) + " / -" + Fmt.si(p.getLong("out")) + " EU/t";
            tip.add(
                EnumChatFormatting.GRAY + side
                    + " "
                    + color
                    + dir
                    + EnumChatFormatting.WHITE
                    + "  "
                    + value
                    + EnumChatFormatting.DARK_GRAY
                    + "  "
                    + Fmt.tier(p.getLong("v")));
        }
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
        int y, int z) {
        if (!(te instanceof TileConnector c)) return tag;
        NBTTagCompound d = new NBTTagCompound();
        ConnectorRecord r = c.record();
        d.setString("owner", c.ownerName == null ? "" : c.ownerName);
        d.setString("name", r != null ? r.name : "");
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < 6; i++) {
            Port p = c.ports[i];
            NBTTagCompound pt = new NBTTagCompound();
            pt.setByte("s", (byte) i);
            pt.setByte("r", (byte) (p.isWorking() ? p.role.ordinal() : 0));
            pt.setLong("v", p.role.supplies() ? p.supplyVoltage : p.collectVoltage);
            Series s = r != null ? r.portSeries[i] : null;
            if (s != null) {
                pt.setLong("in", s.rateIn());
                pt.setLong("out", s.rateOut());
            }
            l.appendTag(pt);
        }
        d.setTag("p", l);
        tag.setTag("fl", d);
        return tag;
    }
}
