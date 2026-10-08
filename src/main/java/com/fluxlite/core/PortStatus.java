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
    CONNECTOR_NEIGHBOUR,
    /**
     * A device FluxLite does not know yet: it works, fed at a safe voltage (see
     * {@link com.fluxlite.adapter.ProbeSinkAdapter}), and says it awaits adaptation.
     */
    PENDING;

    private static final PortStatus[] VALUES = values();

    public static PortStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NO_TARGET;
    }

    /** The face moves energy: all is well, or a device that awaits adaptation is being fed. */
    public boolean works() {
        return this == OK || this == PENDING;
    }

    public String langKey() {
        return "fluxlite.status." + name().toLowerCase();
    }
}
