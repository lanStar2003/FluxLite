package com.fluxlite.backend;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * FluxLite's own steam network: one steam store per GT team (the same teams as the wireless EU network), without a
 * size limit, saved with the overworld in {@code data/fluxlite_steam.dat}. Only steam goes in or out.
 * <p>
 * It is a plain map in memory, so connectors move steam straight in and out of it every tick instead of buffering.
 */
public final class SteamNetwork extends WorldSavedData implements WirelessBackend {

    public static final String NAME = "fluxlite_steam";

    private static SteamNetwork instance;

    private final Map<UUID, BigInteger> balances = new HashMap<>();

    public SteamNetwork(String name) {
        super(name);
    }

    /** Null while no server world is loaded. */
    public static SteamNetwork get() {
        if (instance != null) return instance;
        World w = DimensionManager.getWorld(0);
        if (w == null || w.mapStorage == null) return null;
        SteamNetwork n = (SteamNetwork) w.mapStorage.loadData(SteamNetwork.class, NAME);
        if (n == null) {
            n = new SteamNetwork(NAME);
            w.mapStorage.setData(NAME, n);
        }
        instance = n;
        return n;
    }

    public static void reset() {
        instance = null;
    }

    /** Steam of a team (keyed by its leader, as {@link GTWirelessBackend#resolveTeam} returns it). */
    public BigInteger balanceOf(UUID team) {
        BigInteger b = team == null ? null : balances.get(team);
        return b != null ? b : BigInteger.ZERO;
    }

    /** Stores litres a boiler handed in. */
    public void give(UUID team, long litres) {
        if (team == null || litres <= 0) return;
        balances.merge(team, BigInteger.valueOf(litres), BigInteger::add);
        markDirty();
    }

    /** Takes up to {@code max} litres out; returns how much there was. */
    public long take(UUID team, long max) {
        if (team == null || max <= 0) return 0;
        BigInteger b = balances.get(team);
        if (b == null || b.signum() <= 0) return 0;
        long got = b.compareTo(BigInteger.valueOf(max)) >= 0 ? max : b.longValue();
        balances.put(team, b.subtract(BigInteger.valueOf(got)));
        markDirty();
        return got;
    }

    // ------------------------------------------------------------------ WirelessBackend (commands)

    @Override
    public UUID resolveTeam(UUID member) {
        return GTWirelessBackend.INSTANCE.resolveTeam(member);
    }

    @Override
    public void ensureUser(UUID member) {}

    @Override
    public BigInteger getBalance(UUID member) {
        return balanceOf(resolveTeam(member));
    }

    @Override
    public boolean add(UUID member, BigInteger delta) {
        UUID team = resolveTeam(member);
        if (team == null) return false;
        BigInteger next = balanceOf(team).add(delta);
        if (next.signum() < 0) return false;
        balances.put(team, next);
        markDirty();
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String lastError() {
        return "";
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void readFromNBT(NBTTagCompound t) {
        balances.clear();
        NBTTagList l = t.getTagList("b", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound e = l.getCompoundTagAt(i);
            try {
                balances.put(new UUID(e.getLong("m"), e.getLong("l")), new BigInteger(e.getString("v")));
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Map.Entry<UUID, BigInteger> e : balances.entrySet()) {
            if (e.getValue()
                .signum() == 0) continue;
            NBTTagCompound c = new NBTTagCompound();
            c.setLong(
                "m",
                e.getKey()
                    .getMostSignificantBits());
            c.setLong(
                "l",
                e.getKey()
                    .getLeastSignificantBits());
            c.setString(
                "v",
                e.getValue()
                    .toString());
            l.appendTag(c);
        }
        t.setTag("b", l);
    }
}
