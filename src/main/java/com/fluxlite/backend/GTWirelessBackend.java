package com.fluxlite.backend;

import java.math.BigInteger;
import java.util.UUID;

import com.fluxlite.FluxLite;

import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/**
 * Backend over {@link WirelessNetworkManager} (GT5U 5.09.51.x). Team resolution goes through
 * {@link SpaceProjectManager#getLeader(UUID)}, the same resolution the wireless hatches use, so team members share one
 * balance automatically.
 */
public final class GTWirelessBackend implements WirelessBackend {

    public static final GTWirelessBackend INSTANCE = new GTWirelessBackend();

    private boolean available = true;
    private String lastError = "";
    private long lastFailure;

    private GTWirelessBackend() {}

    @Override
    public UUID resolveTeam(UUID member) {
        if (member == null) return null;
        try {
            UUID leader = SpaceProjectManager.getLeader(member);
            return leader != null ? leader : member;
        } catch (Throwable t) {
            fail(t);
            return member;
        }
    }

    @Override
    public void ensureUser(UUID member) {
        if (member == null) return;
        try {
            WirelessNetworkManager.strongCheckOrAddUser(member);
            ok();
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public BigInteger getBalance(UUID member) {
        if (member == null) return BigInteger.ZERO;
        try {
            BigInteger b = WirelessNetworkManager.getUserEU(member);
            ok();
            return b != null ? b : BigInteger.ZERO;
        } catch (Throwable t) {
            fail(t);
            return BigInteger.ZERO;
        }
    }

    @Override
    public boolean add(UUID member, BigInteger delta) {
        if (member == null) return false;
        if (delta.signum() == 0) return true;
        try {
            boolean r = WirelessNetworkManager.addEUToGlobalEnergyMap(member, delta);
            ok();
            return r;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        // retry once a minute after a failure
        if (!available && System.currentTimeMillis() - lastFailure > 60_000L) available = true;
        return available;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    private void ok() {
        available = true;
    }

    private void fail(Throwable t) {
        if (available) FluxLite.LOG.error("GT wireless network call failed", t);
        available = false;
        lastFailure = System.currentTimeMillis();
        lastError = t.getClass()
            .getSimpleName() + ": "
            + t.getMessage();
    }
}
