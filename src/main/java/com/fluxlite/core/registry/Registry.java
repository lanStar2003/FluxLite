package com.fluxlite.core.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.fluxlite.backend.GTWirelessBackend;

/**
 * Global (all dimensions) store of connector records and team data, saved with the overworld. This is what the control
 * center reads, so it keeps showing connectors whose chunks are unloaded.
 */
public final class Registry extends WorldSavedData {

    public static final String NAME = "fluxlite_registry";

    private static Registry instance;

    private long nextId = 1;
    private final Map<Long, ConnectorRecord> connectors = new LinkedHashMap<>();
    private final Map<UUID, TeamData> teams = new HashMap<>();

    public Registry(String name) {
        super(name);
    }

    /** Null while no server world is loaded. */
    public static Registry get() {
        if (instance != null) return instance;
        World w = DimensionManager.getWorld(0);
        if (w == null || w.mapStorage == null) return null;
        Registry r = (Registry) w.mapStorage.loadData(Registry.class, NAME);
        if (r == null) {
            r = new Registry(NAME);
            w.mapStorage.setData(NAME, r);
        }
        instance = r;
        return r;
    }

    public static void reset() {
        instance = null;
    }

    public ConnectorRecord create() {
        ConnectorRecord r = new ConnectorRecord(nextId++);
        connectors.put(r.id, r);
        markDirty();
        return r;
    }

    /** Re-creates a record with a known id (e.g. a connector whose record was lost). */
    public ConnectorRecord restore(long id) {
        ConnectorRecord r = new ConnectorRecord(id);
        connectors.put(id, r);
        if (id >= nextId) nextId = id + 1;
        markDirty();
        return r;
    }

    public ConnectorRecord byId(long id) {
        return connectors.get(id);
    }

    public void remove(long id) {
        if (connectors.remove(id) != null) markDirty();
    }

    public Collection<ConnectorRecord> all() {
        return connectors.values();
    }

    public List<ConnectorRecord> forTeam(UUID leader) {
        List<ConnectorRecord> l = new ArrayList<>();
        for (ConnectorRecord r : connectors.values()) {
            if (r.owner != null && Objects.equals(GTWirelessBackend.INSTANCE.resolveTeam(r.owner), leader)) l.add(r);
        }
        return l;
    }

    public int countForTeam(UUID leader) {
        int n = 0;
        for (ConnectorRecord r : connectors.values()) {
            if (r.owner != null && Objects.equals(GTWirelessBackend.INSTANCE.resolveTeam(r.owner), leader)) n++;
        }
        return n;
    }

    public TeamData team(UUID leader) {
        return teams.computeIfAbsent(leader, k -> {
            markDirty();
            return new TeamData(k);
        });
    }

    public TeamData teamIfPresent(UUID leader) {
        return teams.get(leader);
    }

    public Collection<TeamData> teams() {
        return teams.values();
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        nextId = Math.max(1, t.getLong("next"));
        connectors.clear();
        NBTTagList cl = t.getTagList("c", 10);
        for (int i = 0; i < cl.tagCount(); i++) {
            ConnectorRecord r = ConnectorRecord.read(cl.getCompoundTagAt(i));
            connectors.put(r.id, r);
            if (r.id >= nextId) nextId = r.id + 1;
        }
        teams.clear();
        NBTTagList tl = t.getTagList("t", 10);
        for (int i = 0; i < tl.tagCount(); i++) {
            TeamData d = TeamData.read(tl.getCompoundTagAt(i));
            teams.put(d.leader, d);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        t.setLong("next", nextId);
        NBTTagList cl = new NBTTagList();
        for (ConnectorRecord r : connectors.values()) cl.appendTag(r.write());
        t.setTag("c", cl);
        NBTTagList tl = new NBTTagList();
        for (TeamData d : teams.values()) tl.appendTag(d.write());
        t.setTag("t", tl);
    }
}
