package com.fluxlite.core;

/**
 * Direction of a face, seen from the wireless network (like a battery): energy coming from generators is the
 * network's input, energy handed to machines is its output.
 */
public enum PortRole {

    NONE,
    /** Device -> network (generators, dynamo hatches). */
    INPUT,
    /** Network -> device (machines, energy hatches). */
    OUTPUT,
    /** A cable with generators and machines on it: both at once. */
    BOTH;

    private static final PortRole[] VALUES = values();

    public static PortRole byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public boolean collects() {
        return this == INPUT || this == BOTH;
    }

    public boolean supplies() {
        return this == OUTPUT || this == BOTH;
    }

    public String langKey() {
        return "fluxlite.role." + name().toLowerCase();
    }
}
