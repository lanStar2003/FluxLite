package com.fluxlite.gui.holo;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxlite.util.Longs;

/**
 * Client side state of one floating display: the last data from the server, the eased numbers that are shown and the
 * open/close animation. Plain Java, so the preview test can drive it too.
 */
public final class HoloState {

    /** Data older than this means the server stopped sending (player out of range); the display folds away. */
    public static final long STALE_MS = 2500;

    public String team = "";
    public BigInteger balance = BigInteger.ZERO;
    public long in, out, eta = -1;
    public int online, connectors, alerts;
    public boolean down;
    public long[] curveIn = new long[0], curveOut = new long[0];
    /** The team's steam network (shown when it is in use). */
    public boolean steamOn;
    public BigInteger steam = BigInteger.ZERO;
    public long steamIn, steamOut;
    /** Blocks; the display opens when the viewer is closer than this. */
    public float range = 12;
    public long receivedAt;

    /** Eased values that the panel shows. */
    public double shownIn, shownOut;
    /** 0 = folded away, 1 = fully open. */
    public float open;
    private long lastMs;
    private boolean fresh = true;

    public void accept(NBTTagCompound t, long now) {
        team = t.getString("team");
        balance = big(t.getString("balance"));
        in = t.getLong("in");
        out = t.getLong("out");
        eta = t.hasKey("eta") ? t.getLong("eta") : -1;
        online = t.getInteger("online");
        connectors = t.getInteger("connectors");
        alerts = t.getInteger("alerts");
        down = t.getBoolean("down");
        curveIn = Longs.unpack(t.getIntArray("cin"));
        curveOut = Longs.unpack(t.getIntArray("cout"));
        steamOn = t.getBoolean("steamOn");
        steam = big(t.getString("steam"));
        steamIn = t.getLong("sin");
        steamOut = t.getLong("sout");
        if (t.hasKey("r")) range = t.getFloat("r");
        receivedAt = now;
        if (fresh) {
            shownIn = in;
            shownOut = out;
            fresh = false;
        }
    }

    private static BigInteger big(String s) {
        try {
            return new BigInteger(s);
        } catch (NumberFormatException e) {
            return BigInteger.ZERO;
        }
    }

    public boolean stale(long now) {
        return receivedAt == 0 || now - receivedAt > STALE_MS;
    }

    /** Advances the animations to {@code now}; {@code visible} is whether the display should be open. */
    public void update(long now, boolean visible) {
        float dt = lastMs == 0 ? 0 : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        open = visible ? Math.min(1, open + dt / 0.7f) : Math.max(0, open - dt / 0.45f);
        double k = 1 - Math.exp(-dt * 8);
        shownIn += (in - shownIn) * k;
        shownOut += (out - shownOut) * k;
    }
}
