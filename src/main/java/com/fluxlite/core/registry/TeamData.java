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
    public boolean chatAlerts = true;

    // runtime
    public BigInteger balance = BigInteger.ZERO;
    public final transient List<Alert> alerts = new ArrayList<>();
    public final transient Map<String, Long> lastChat = new HashMap<>();
    public transient boolean backendDown;

    public TeamData(UUID leader) {
        this.leader = leader;
        series.keepTicks();
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("lm", leader.getMostSignificantBits());
        t.setLong("ll", leader.getLeastSignificantBits());
        t.setTag("s", series.write());
        t.setBoolean("chat", chatAlerts);
        return t;
    }

    public static TeamData read(NBTTagCompound t) {
        TeamData d = new TeamData(new UUID(t.getLong("lm"), t.getLong("ll")));
        if (t.hasKey("s")) {
            d.series = Series.read(t.getCompoundTag("s"));
            d.series.keepTicks();
        }
        d.chatAlerts = !t.hasKey("chat") || t.getBoolean("chat");
        return d;
    }
}
