package com.fluxlite.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fluxlite.Config;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.backend.WirelessBackend;
import com.fluxlite.tile.TileConnector;

/**
 * Batches every face of every loaded connector per team and touches the wireless balance once per settlement period
 * (every tick by default): what generators handed in is added first, then the supply buffers are refilled,
 * proportionally when the balance is short. Runs on the server thread at the end of a tick.
 */
public final class Settlement {

    private static final Set<TileConnector> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Set while any connector pushes energy into a GT cable. Connectors refuse incoming energy meanwhile, so a mixed
     * cable can be fed and drained by connectors without energy going round in circles.
     */
    public static boolean supplying;

    private Settlement() {}

    public static void register(TileConnector c) {
        LIVE.add(c);
    }

    public static void unregister(TileConnector c) {
        LIVE.remove(c);
    }

    public static Set<TileConnector> live() {
        return LIVE;
    }

    public static void reset() {
        LIVE.clear();
        supplying = false;
    }

    public static BigInteger applyLoss(BigInteger collected) {
        if (Config.wirelessLossPercent <= 0 || collected.signum() <= 0) return collected;
        long keepPpm = Math.round((100.0 - Config.wirelessLossPercent) * 10_000);
        return collected.multiply(BigInteger.valueOf(keepPpm))
            .divide(BigInteger.valueOf(1_000_000));
    }

    static final class Request {

        final Port port;
        final long amount;
        long granted;

        Request(Port port, long amount) {
            this.port = port;
            this.amount = amount;
        }
    }

    static final class Batch {

        /** Energy generators handed in (subject to loss). */
        long collected;
        /** Supply buffers of faces that stopped supplying; goes back as is. */
        long returned;
        final List<Request> requests = new ArrayList<>();
    }

    public static void settle() {
        WirelessBackend backend = GTWirelessBackend.INSTANCE;
        if (!backend.isAvailable() || LIVE.isEmpty()) return;

        Map<UUID, Batch> batches = new HashMap<>();
        for (TileConnector c : new ArrayList<>(LIVE)) {
            if (c.isInvalid() || !c.isLive()) continue;
            UUID team = c.team();
            if (team == null) continue;
            Batch b = batches.computeIfAbsent(team, k -> new Batch());
            for (Port p : c.ports) {
                if (p.steam) continue; // steam goes straight to the steam network
                if (p.collected > 0) {
                    b.collected = add(b.collected, p.collected);
                    p.collected = 0;
                }
                if (p.supplies()) {
                    long req = p.supplyCap - p.supply;
                    if (req > 0) b.requests.add(new Request(p, req));
                } else if (p.supply > 0) {
                    b.returned = add(b.returned, p.supply);
                    p.supply = 0;
                }
            }
        }
        for (Map.Entry<UUID, Batch> e : batches.entrySet()) settleTeam(backend, e.getKey(), e.getValue());
    }

    private static long add(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    static void settleTeam(WirelessBackend backend, UUID team, Batch b) {
        BigInteger offered = BigInteger.valueOf(b.returned)
            .add(applyLoss(BigInteger.valueOf(b.collected)));
        BigInteger available = backend.getBalance(team)
            .add(offered);

        BigInteger wanted = BigInteger.ZERO;
        for (Request r : b.requests) wanted = wanted.add(BigInteger.valueOf(r.amount));
        BigInteger granted = BigInteger.ZERO;
        if (available.signum() > 0 && wanted.signum() > 0) {
            boolean full = available.compareTo(wanted) >= 0;
            for (Request r : b.requests) {
                r.granted = full ? r.amount
                    : BigInteger.valueOf(r.amount)
                        .multiply(available)
                        .divide(wanted)
                        .longValue();
                granted = granted.add(BigInteger.valueOf(r.granted));
            }
        }

        BigInteger delta = offered.subtract(granted);
        if (backend.add(team, delta)) {
            for (Request r : b.requests) r.port.supply += r.granted;
        } else if (offered.signum() > 0) {
            // cannot happen on a single thread, but never lose what generators handed in
            backend.add(team, offered);
        }
    }
}
