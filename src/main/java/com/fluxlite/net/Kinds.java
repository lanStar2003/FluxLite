package com.fluxlite.net;

/** Message kinds of the two generic NBT messages. */
public final class Kinds {

    // client -> server
    public static final int CONNECTOR_REQUEST = 0;
    public static final int CONNECTOR_EDIT = 1;
    public static final int CC_REQUEST = 2;
    public static final int CC_ACTION = 3;

    // server -> client
    public static final int CONNECTOR_DATA = 0;
    public static final int CC_DATA = 1;
    public static final int HOLO_DATA = 2;

    // connector edits
    public static final int OP_NAME = 0;
    public static final int OP_TOGGLE = 1;

    // control center actions
    public static final int ACT_CHAT = 0;
    public static final int ACT_REDSTONE = 1;
    public static final int ACT_HOLOGRAM = 2;
    /** Hologram size, 0 (small) to 3 (extra large), in "value". */
    public static final int ACT_HOLO_SIZE = 3;
    public static final int ACT_HOLO_OPAQUE = 4;

    private Kinds() {}
}
