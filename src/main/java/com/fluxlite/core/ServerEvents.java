package com.fluxlite.core;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxlite.Config;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.chunk.ChunkLoadManager;
import com.fluxlite.core.alert.AlertManager;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.core.registry.TeamData;
import com.fluxlite.tile.TileConnector;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server tick driver: settlement, team statistics, alerts, periodic saving. */
public final class ServerEvents {

    private static long tick;
    private static int dayKey;
    private static final Map<UUID, long[]> TEAM_TICK = new HashMap<>();

    public static long tick() {
        return tick;
    }

    public static int dayKey() {
        return dayKey;
    }

    public static void reset() {
        tick = 0;
        TEAM_TICK.clear();
        Settlement.reset();
    }

    /** Called by each connector once per tick with its committed totals. */
    public static void addTeamTick(UUID team, long in, long out, long demand, boolean active) {
        if (team == null) return;
        long[] a = TEAM_TICK.computeIfAbsent(team, k -> new long[4]);
        a[0] = sat(a[0], in);
        a[1] = sat(a[1], out);
        a[2] = sat(a[2], demand);
        if (active) a[3] = 1;
    }

    private static long sat(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        Settlement.supplying = false;
        Registry reg = Registry.get();
        if (reg == null) return;

        if (tick % Config.settlementPeriod == 0) Settlement.settle();

        long now = System.currentTimeMillis();
        for (TeamData team : reg.teams()) {
            long[] a = TEAM_TICK.get(team.leader);
            if (a != null) team.series.tick(a[0], a[1], a[2], a[3] != 0, now);
            else team.series.tick(0, 0, 0, false, now);
        }
        for (UUID leader : TEAM_TICK.keySet()) if (reg.teamIfPresent(leader) == null) reg.team(leader);
        TEAM_TICK.clear();

        if (tick % 20 == 0) {
            dayKey = computeDayKey();
            for (TeamData team : reg.teams()) {
                team.series.rollSecond(dayKey);
                team.backendDown = !GTWirelessBackend.INSTANCE.isAvailable();
                team.balance = GTWirelessBackend.INSTANCE.getBalance(team.leader);
            }
            AlertManager.evaluate(reg);
            ChunkLoadManager.INSTANCE.tickSecond();
        }
        if (tick % (Config.registrySaveIntervalMinutes * 1200L) == 0) reg.markDirty();
    }

    private static int computeDayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * Fired before the chunk is saved (unlike TileEntity#onChunkUnload, which runs after). Refunding here keeps the
     * saved NBT buffer at zero, so the energy can't be counted twice.
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload e) {
        if (e.world == null || e.world.isRemote) return;
        Chunk chunk = e.getChunk();
        for (Object o : chunk.chunkTileEntityMap.values()) {
            if (o instanceof TileConnector c) c.release();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        ChunkLoadManager.INSTANCE.onWorldUnload(e.world);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        ChunkLoadManager.INSTANCE.onPresenceChanged();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ChunkLoadManager.INSTANCE.onPresenceChanged();
    }

}
