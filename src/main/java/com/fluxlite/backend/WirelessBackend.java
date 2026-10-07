package com.fluxlite.backend;

import java.math.BigInteger;
import java.util.UUID;

/**
 * The only place the mod touches the GT wireless EU network. Everything is called from the server main thread.
 */
public interface WirelessBackend {

    /** Team key the balance belongs to (the GT team leader). */
    UUID resolveTeam(UUID member);

    /** Make sure the member exists in GT's team and energy maps. */
    void ensureUser(UUID member);

    /** Balance of the member's team; ZERO when the team never stored anything. */
    BigInteger getBalance(UUID member);

    /** Adds (or subtracts, when negative) EU. Returns false and changes nothing when the result would go negative. */
    boolean add(UUID member, BigInteger delta);

    /** False after the GT API threw; settlement pauses and an alert is raised. */
    boolean isAvailable();

    String lastError();
}
