package com.fluxlite.core;

/** The only per-face setting: connectors work automatically unless a face is switched off. */
public enum PortMode {

    AUTO,
    OFF;

    private static final PortMode[] VALUES = values();

    public static PortMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : AUTO;
    }
}
