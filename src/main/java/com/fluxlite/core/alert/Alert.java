package com.fluxlite.core.alert;

import net.minecraft.nbt.NBTTagCompound;

/** One active alert. {@code args} are already formatted strings, inserted into the localized message. */
public final class Alert {

    public enum Type {

        LOW_BALANCE,
        ETA_SHORT,
        UNDER_SUPPLY,
        IDLE,
        OVERLOAD,
        CHUNK_FAIL,
        BACKEND_DOWN;

        public String langKey() {
            return "fluxlite.alert." + name().toLowerCase();
        }
    }

    public final Type type;
    public final long connectorId;
    public final int side;
    public final String[] args;

    public Alert(Type type, long connectorId, int side, String... args) {
        this.type = type;
        this.connectorId = connectorId;
        this.side = side;
        this.args = args;
    }

    public String key() {
        return type.name() + ":" + connectorId + ":" + side;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setByte("t", (byte) type.ordinal());
        t.setLong("c", connectorId);
        t.setByte("s", (byte) side);
        t.setString("a", String.join("\u0001", args));
        return t;
    }

    public static Alert read(NBTTagCompound t) {
        Type[] types = Type.values();
        int i = t.getByte("t");
        String a = t.getString("a");
        return new Alert(
            types[Math.max(0, Math.min(types.length - 1, i))],
            t.getLong("c"),
            t.getByte("s"),
            a.isEmpty() ? new String[0] : a.split("\u0001", -1));
    }
}
