package com.fluxlite.core.view;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

/**
 * One device: a directly metered face, or a machine sampled behind a cable. "Now" values are the smoothed current
 * EU/t (see {@code Series#SMOOTH}).
 */
public final class DeviceRow {

    /** Status codes, also the order of the status filter. */
    public static final int ST_RUNNING = 0, ST_IDLE = 1, ST_OFFLINE = 2, ST_OFF = 3, ST_ERROR = 4;
    /** Kind codes: a device right at a face, a cable at a face, a machine found behind a cable. */
    public static final int KIND_DIRECT = 0, KIND_CABLE = 1, KIND_BEHIND = 2;

    public String key;
    public String name;
    public String conn;
    public long connId;
    public int dim, x, y, z;
    /** Face of the connector, -1 when unknown. */
    public int side = -1;
    /** 0 = none, 1 = input (feeds the network), 2 = output (fed by it), 3 = both. */
    public int role;
    public long voltage;
    public int tier;
    public long nowIn, nowOut, peak;
    public BigInteger total = BigInteger.ZERO;
    public boolean sampled, online, cable;
    public int status, kind;
    /** Buffer fill of a sampled machine in per mille, -1 when unknown. */
    public int fill = -1;

    public long now() {
        return role == 1 ? nowIn : role == 2 ? nowOut : nowIn + nowOut;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("k", key);
        t.setString("n", name == null ? "" : name);
        t.setString("c", conn == null ? "" : conn);
        t.setByte("r", (byte) role);
        t.setLong("v", voltage);
        t.setLong("ni", nowIn);
        t.setLong("no", nowOut);
        t.setLong("pk", peak);
        t.setString("tot", total.toString());
        t.setBoolean("s", sampled);
        t.setBoolean("on", online);
        t.setByte("st", (byte) status);
        t.setByte("kd", (byte) kind);
        t.setByte("sd", (byte) side);
        t.setInteger("d", dim);
        t.setIntArray("p", new int[] { x, y, z });
        t.setShort("f", (short) fill);
        return t;
    }
}
