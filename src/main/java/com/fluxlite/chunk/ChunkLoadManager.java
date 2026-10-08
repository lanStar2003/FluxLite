package com.fluxlite.chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.fluxlite.Config;
import com.fluxlite.FluxLite;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.block.ModBlocks;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.PortInfo;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.tile.TileConnector;

/**
 * Reference counted, ticket pooled chunk loading. Forge limits tickets per mod and chunks per ticket, so chunks of
 * all connectors share tickets, and a chunk wanted by several connectors is forced only once.
 * <p>
 * The registry is the source of truth: after a restart all old tickets are released and the forced set is rebuilt
 * from connector records whose block is still there.
 */
public final class ChunkLoadManager implements ForgeChunkManager.LoadingCallback {

    public static final ChunkLoadManager INSTANCE = new ChunkLoadManager();

    private static final class DimState {

        final Map<ChunkCoordIntPair, Set<Long>> refs = new HashMap<>();
        final Map<ChunkCoordIntPair, Ticket> chunkTicket = new HashMap<>();
        final List<Ticket> tickets = new ArrayList<>();
        final Map<Long, Set<ChunkCoordIntPair>> byConnector = new HashMap<>();
    }

    private final Map<Integer, DimState> dims = new HashMap<>();
    private final Set<Integer> pendingRebuild = new HashSet<>();
    private boolean warnedNoTicket;

    private ChunkLoadManager() {}

    public void reset() {
        dims.clear();
        pendingRebuild.clear();
    }

    // ------------------------------------------------------------------ public API

    /** Re-evaluates what a live connector should keep loaded. */
    public void update(TileConnector c) {
        World w = c.getWorldObj();
        if (w == null || w.isRemote || c.recordId <= 0) return;
        boolean want = Config.chunkLoadingEnabled && c.hasActivePort() && presenceAllows(c.team());
        apply(w, c.recordId, want ? area(c.xCoord, c.zCoord) : Collections.emptySet(), c.record());
    }

    public void release(World w, long id) {
        if (w == null || w.isRemote) return;
        apply(w, id, Collections.emptySet(), null);
    }

    public int forcedChunks(int dim) {
        DimState s = dims.get(dim);
        return s == null ? 0 : s.refs.size();
    }

    public int tickets(int dim) {
        DimState s = dims.get(dim);
        return s == null ? 0 : s.tickets.size();
    }

    // ------------------------------------------------------------------ Forge callbacks & lifecycle

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket t : tickets) ForgeChunkManager.releaseTicket(t);
        pendingRebuild.add(world.provider.dimensionId);
    }

    /** Bring up dimensions that hold active connectors, then rebuild everything. */
    public void onServerStarted() {
        Registry reg = Registry.get();
        if (reg == null || !Config.chunkLoadingEnabled) return;
        Set<Integer> wanted = new HashSet<>();
        for (ConnectorRecord r : reg.all()) if (wantsLoading(r)) wanted.add(r.dim);
        for (int dim : wanted) {
            if (DimensionManager.getWorld(dim) == null && DimensionManager.isDimensionRegistered(dim)) {
                try {
                    DimensionManager.initDimension(dim);
                } catch (Throwable t) {
                    FluxLite.LOG.warn("Could not load dimension {} for chunk loading", dim, t);
                }
            }
            pendingRebuild.add(dim);
        }
    }

    public void onPresenceChanged() {
        if (Config.keepLoadedWhenOffline) return;
        pendingRebuild.addAll(DimensionManager.getIDs() == null ? new HashSet<>() : toSet(DimensionManager.getIDs()));
    }

    public void onWorldUnload(World w) {
        if (w == null || w.isRemote) return;
        dims.remove(w.provider.dimensionId);
    }

    /** Once per second from the server tick. */
    public void tickSecond() {
        if (pendingRebuild.isEmpty()) return;
        Registry reg = Registry.get();
        if (reg == null) return;
        Set<Integer> todo = new HashSet<>(pendingRebuild);
        pendingRebuild.clear();
        for (int dim : todo) {
            World w = DimensionManager.getWorld(dim);
            if (w != null) rebuild(w, reg);
        }
    }

    // ------------------------------------------------------------------ internals

    private static Set<Integer> toSet(Integer[] ids) {
        Set<Integer> s = new HashSet<>();
        Collections.addAll(s, ids);
        return s;
    }

    private static boolean wantsLoading(ConnectorRecord r) {
        if (r.owner == null) return false;
        for (PortInfo p : r.ports) if (p.status.works() && p.role != com.fluxlite.core.PortRole.NONE) return true;
        return false;
    }

    private void rebuild(World w, Registry reg) {
        int dim = w.provider.dimensionId;
        for (ConnectorRecord r : reg.all()) {
            if (r.dim != dim) continue;
            boolean want = Config.chunkLoadingEnabled && wantsLoading(r)
                && presenceAllows(GTWirelessBackend.INSTANCE.resolveTeam(r.owner));
            if (want && w.getBlock(r.x, r.y, r.z) != ModBlocks.connector) {
                // the block is gone (e.g. edited world); forget it
                want = false;
            }
            apply(w, r.id, want ? area(r.x, r.z) : Collections.emptySet(), r);
        }
    }

    private static boolean presenceAllows(UUID team) {
        if (Config.keepLoadedWhenOffline) return true;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return false;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (Objects.equals(GTWirelessBackend.INSTANCE.resolveTeam(p.getUniqueID()), team)) return true;
        }
        return false;
    }

    private static Set<ChunkCoordIntPair> area(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        int r = Math.max(0, Config.loadRadius);
        Set<ChunkCoordIntPair> s = new HashSet<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) s.add(new ChunkCoordIntPair(cx + dx, cz + dz));
        return s;
    }

    private void apply(World w, long id, Set<ChunkCoordIntPair> desired, ConnectorRecord record) {
        DimState s = dims.computeIfAbsent(w.provider.dimensionId, k -> new DimState());
        Set<ChunkCoordIntPair> current = s.byConnector.getOrDefault(id, Collections.emptySet());
        if (current.equals(desired)) {
            if (record != null) {
                record.chunkLoaded = !desired.isEmpty();
            }
            return;
        }
        Set<ChunkCoordIntPair> next = new HashSet<>();
        for (ChunkCoordIntPair c : current) {
            if (desired.contains(c)) next.add(c);
            else decRef(s, c, id);
        }
        boolean failed = false;
        for (ChunkCoordIntPair c : desired) {
            if (next.contains(c)) continue;
            if (incRef(w, s, c, id)) next.add(c);
            else failed = true;
        }
        if (next.isEmpty()) s.byConnector.remove(id);
        else s.byConnector.put(id, next);
        if (record != null) {
            record.chunkLoaded = !next.isEmpty();
            record.chunkLoadFailed = failed;
        }
    }

    private boolean incRef(World w, DimState s, ChunkCoordIntPair c, long id) {
        Set<Long> owners = s.refs.get(c);
        if (owners != null) {
            owners.add(id);
            return true;
        }
        Ticket t = null;
        for (Ticket existing : s.tickets) {
            if (existing.getChunkList()
                .size() < existing.getChunkListDepth()) {
                t = existing;
                break;
            }
        }
        if (t == null) {
            t = ForgeChunkManager.requestTicket(FluxLite.instance, w, ForgeChunkManager.Type.NORMAL);
            if (t == null) {
                if (!warnedNoTicket) {
                    warnedNoTicket = true;
                    FluxLite.LOG.warn(
                        "Forge refused a chunk loading ticket; raise maximumTicketCount for 'fluxlite' in config/forgeChunkLoading.cfg");
                }
                return false;
            }
            s.tickets.add(t);
        }
        ForgeChunkManager.forceChunk(t, c);
        s.chunkTicket.put(c, t);
        owners = new HashSet<>();
        owners.add(id);
        s.refs.put(c, owners);
        return true;
    }

    private void decRef(DimState s, ChunkCoordIntPair c, long id) {
        Set<Long> owners = s.refs.get(c);
        if (owners == null) return;
        owners.remove(id);
        if (!owners.isEmpty()) return;
        s.refs.remove(c);
        Ticket t = s.chunkTicket.remove(c);
        if (t == null) return;
        ForgeChunkManager.unforceChunk(t, c);
        if (t.getChunkList()
            .isEmpty()) {
            ForgeChunkManager.releaseTicket(t);
            for (Iterator<Ticket> it = s.tickets.iterator(); it.hasNext();) if (it.next() == t) it.remove();
        }
    }
}
