package com.fluxlite.core.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxlite.core.MachineSample;
import com.fluxlite.core.stats.Series;

/** Registry entry of one connector. Survives chunk unloads; removed when the block is broken. */
public final class ConnectorRecord {

    public final long id;
    public UUID owner;
    public String ownerName = "";
    public int dim, x, y, z;
    public String name = "";
    public String group = "";

    public boolean online;
    public long lastSeen;
    public boolean chunkLoaded;
    public boolean chunkLoadFailed;

    public final PortInfo[] ports = new PortInfo[6];
    /** Per-port statistics, created when a port first becomes active. */
    public final Series[] portSeries = new Series[6];
    public Series total = new Series();

    /** Machines found behind cables; not persisted. */
    public final transient Map<Long, Sampled> samples = new LinkedHashMap<>();

    public static final class Sampled {

        public MachineSample last;
        public int side;
        public final Series series = new Series();
        public long seenTick;
    }

    public ConnectorRecord(long id) {
        this.id = id;
        for (int i = 0; i < 6; i++) ports[i] = new PortInfo();
    }

    public Series portSeries(int side) {
        if (portSeries[side] == null) portSeries[side] = new Series();
        return portSeries[side];
    }

    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        return "#" + id;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("id", id);
        if (owner != null) {
            t.setLong("om", owner.getMostSignificantBits());
            t.setLong("ol", owner.getLeastSignificantBits());
        }
        t.setString("on", ownerName == null ? "" : ownerName);
        t.setInteger("d", dim);
        t.setInteger("x", x);
        t.setInteger("y", y);
        t.setInteger("z", z);
        t.setString("n", name == null ? "" : name);
        t.setString("g", group == null ? "" : group);
        t.setLong("ls", lastSeen);
        NBTTagList pl = new NBTTagList();
        for (int i = 0; i < 6; i++) {
            NBTTagCompound p = new NBTTagCompound();
            ports[i].write(p);
            if (portSeries[i] != null) p.setTag("st", portSeries[i].write());
            pl.appendTag(p);
        }
        t.setTag("p", pl);
        t.setTag("tot", total.write());
        return t;
    }

    public static ConnectorRecord read(NBTTagCompound t) {
        ConnectorRecord r = new ConnectorRecord(t.getLong("id"));
        if (t.hasKey("om")) r.owner = new UUID(t.getLong("om"), t.getLong("ol"));
        r.ownerName = t.getString("on");
        r.dim = t.getInteger("d");
        r.x = t.getInteger("x");
        r.y = t.getInteger("y");
        r.z = t.getInteger("z");
        r.name = t.getString("n");
        r.group = t.getString("g");
        r.lastSeen = t.getLong("ls");
        NBTTagList pl = t.getTagList("p", 10);
        for (int i = 0; i < Math.min(6, pl.tagCount()); i++) {
            NBTTagCompound p = pl.getCompoundTagAt(i);
            r.ports[i].read(p);
            if (p.hasKey("st")) r.portSeries[i] = Series.read(p.getCompoundTag("st"));
        }
        if (t.hasKey("tot")) r.total = Series.read(t.getCompoundTag("tot"));
        return r;
    }
}
