package com.fluxlite.core.view;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.core.registry.TeamData;
import com.fluxlite.core.stats.Bucket;
import com.fluxlite.core.stats.Series;
import com.fluxlite.tile.TileControlCenter;
import com.fluxlite.util.Longs;

/** What the floating display above a control center shows: its team's balance and flow. */
public final class HoloView {

    /** Points of the live curve (the last 10 s, every 4th tick). */
    public static final int POINTS = 50;

    private HoloView() {}

    public static NBTTagCompound build(TileControlCenter cc) {
        Registry reg = Registry.get();
        if (reg == null || cc.owner == null) return null;
        UUID team = GTWirelessBackend.INSTANCE.resolveTeam(cc.owner);
        TeamData td = reg.teamIfPresent(team);
        List<ConnectorRecord> records = reg.forTeam(team);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("team", ControlCenterView.teamName(team, records));
        BigInteger balance = GTWirelessBackend.INSTANCE.getBalance(team);
        t.setString("balance", balance.toString());
        int online = 0;
        for (ConnectorRecord r : records) if (r.online) online++;
        t.setInteger("online", online);
        t.setInteger("connectors", records.size());
        t.setBoolean("down", !GTWirelessBackend.INSTANCE.isAvailable());
        if (td == null) return t;

        Series s = td.series;
        t.setLong("in", s.rateIn());
        t.setLong("out", s.rateOut());
        t.setInteger("alerts", td.alerts.size());
        Bucket m = s.window(60);
        long net = m.avgIn() - m.avgOut();
        long eta = -1;
        if (net < 0) eta = balance.divide(BigInteger.valueOf(-net * 20))
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
        t.setLong("eta", eta);

        long[][] c = s.tickCurve();
        t.setIntArray("cin", Longs.pack(sample(c[0])));
        t.setIntArray("cout", Longs.pack(sample(c[1])));
        return t;
    }

    /** Every 4th value, newest last. */
    private static long[] sample(long[] v) {
        int n = Math.min(POINTS, (v.length + 3) / 4);
        long[] r = new long[n];
        for (int i = 0; i < n; i++) r[n - 1 - i] = v[v.length - 1 - i * 4];
        return r;
    }
}
