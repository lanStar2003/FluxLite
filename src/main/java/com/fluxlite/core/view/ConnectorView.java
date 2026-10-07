package com.fluxlite.core.view;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxlite.Config;
import com.fluxlite.core.Port;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.core.stats.Series;
import com.fluxlite.net.Kinds;
import com.fluxlite.tile.TileConnector;

/** Builds the connector card payload and applies the two edits it allows (name, face on/off). */
public final class ConnectorView {

    private ConnectorView() {}

    public static NBTTagCompound denied(TileConnector c) {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("ok", false);
        t.setString("owner", c.ownerName == null ? "" : c.ownerName);
        return t;
    }

    public static NBTTagCompound build(TileConnector c) {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("ok", true);
        ConnectorRecord r = c.record();
        t.setLong("id", c.recordId);
        t.setString("name", r != null ? r.name : "");
        t.setString("owner", c.ownerName == null ? "" : c.ownerName);
        int chunk = !Config.chunkLoadingEnabled ? 3
            : r != null && r.chunkLoadFailed ? 2 : r != null && r.chunkLoaded ? 1 : 0;
        t.setInteger("chunk", chunk);
        t.setLong("in", r != null ? r.total.rateIn() : 0);
        t.setLong("out", r != null ? r.total.rateOut() : 0);
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < 6; i++) {
            Port p = c.ports[i];
            NBTTagCompound f = new NBTTagCompound();
            f.setByte("side", (byte) i);
            f.setByte("vis", c.visualFor(p));
            f.setByte("role", (byte) p.role.ordinal());
            f.setByte("status", (byte) p.status.ordinal());
            f.setString("target", p.targetName == null ? "" : p.targetName);
            f.setLong("v", p.role.supplies() ? p.supplyVoltage : p.collectVoltage);
            Series s = r != null ? r.portSeries[i] : null;
            if (s != null) {
                f.setLong("in", s.rateIn());
                f.setLong("out", s.rateOut());
            }
            l.appendTag(f);
        }
        t.setTag("faces", l);
        return t;
    }

    public static void applyEdit(TileConnector c, NBTTagCompound d) {
        switch (d.getInteger("op")) {
            case Kinds.OP_NAME -> {
                ConnectorRecord r = c.record();
                if (r == null) return;
                r.name = clean(d.getString("text"), 32);
                Registry reg = Registry.get();
                if (reg != null) reg.markDirty();
            }
            case Kinds.OP_TOGGLE -> c.toggleSide(d.getInteger("side"));
            default -> {}
        }
    }

    private static String clean(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}§]", "")
            .trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
