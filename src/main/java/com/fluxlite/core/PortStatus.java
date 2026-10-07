package com.fluxlite.core;

/** Why a face is (not) working. */
public enum PortStatus {

    OK,
    /** Switched off by the player. */
    DISABLED,
    /** Nothing energy related next to this face. */
    NO_TARGET,
    /**
     * Voltage / amperage of the target could not be read; the face refuses to feed it rather than risk an explosion.
     */
    UNKNOWN_SPEC,
    /** Two connectors touching each other. */
    CONNECTOR_NEIGHBOUR;

    private static final PortStatus[] VALUES = values();

    public static PortStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NO_TARGET;
    }

    public String langKey() {
        return "fluxlite.status." + name().toLowerCase();
    }
}
