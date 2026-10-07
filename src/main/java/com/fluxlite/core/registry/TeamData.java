package com.fluxlite.core.registry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxlite.core.alert.Alert;
import com.fluxlite.core.stats.Series;

/** Per-team totals and alert state. Keyed by the GT team leader. Thresholds live in the config file. */
public final class TeamData {

    public final UUID leader;
    public Series series = new Series();
    /** Steam moved by the team's connectors, in litres. */
    public Series steamSeries = new Series();
    /** Alerts are worked out at all. Off until the team switches them on, like the chat messages. */
    public boolean alertsOn;
    public boolean chatAlerts;

    // runtime
    public BigInteger balance = BigInteger.ZERO;
    public final transient List<Alert> alerts = new ArrayList<>();
    public final transient Map<String, Long> lastChat = new HashMap<>();
    public transient boolean backendDown;

    public TeamData(UUID leader) {
        this.leader = leader;
        series.keepTicks();
        steamSeries.keepTicks();
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("lm", leader.getMostSignificantBits());
        t.setLong("ll", leader.getLeastSignificantBits());
        t.setTag("s", series.write());
        t.setTag("ss", steamSeries.write());
        t.setBoolean("al", alertsOn);
        t.setBoolean("ch", chatAlerts);
        return t;
    }

    public static TeamData read(NBTTagCompound t) {
        TeamData d = new TeamData(new UUID(t.getLong("lm"), t.getLong("ll")));
        if (t.hasKey("s")) {
            d.series = Series.read(t.getCompoundTag("s"));
            d.series.keepTicks();
        }
        if (t.hasKey("ss")) {
            d.steamSeries = Series.read(t.getCompoundTag("ss"));
            d.steamSeries.keepTicks();
        }
        // "chat" of 0.5.0 and older defaulted to on; both start off now
        d.alertsOn = t.getBoolean("al");
        d.chatAlerts = t.getBoolean("ch");
        return d;
    }
}
