package com.fluxlite.core.stats;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;

/**
 * One time slot of the RRD. Sums are BigInteger because an hour of UHV+ multi-amp traffic overflows a long once a few
 * ports are added together; per-tick extremes stay long.
 */
public final class Bucket {

    /** Wall clock (ms) of the first tick in this bucket. */
    public long start;
    public BigInteger in = BigInteger.ZERO;
    public BigInteger out = BigInteger.ZERO;
    public BigInteger demand = BigInteger.ZERO;
    /** Largest single-tick values and when they happened (ms). */
    public long peakIn, peakOut, peakInAt, peakOutAt;
    /** Smallest non-zero single-tick values, 0 when there was none. */
    public long minIn, minOut;
    public long activeTicks, ticks;

    public Bucket() {}

    public Bucket(long start) {
        this.start = start;
    }

    public boolean isEmpty() {
        return ticks == 0;
    }

    public void merge(Bucket b) {
        if (b.ticks == 0) return;
        if (ticks == 0) start = b.start;
        in = in.add(b.in);
        out = out.add(b.out);
        demand = demand.add(b.demand);
        if (b.peakIn > peakIn) {
            peakIn = b.peakIn;
            peakInAt = b.peakInAt;
        }
        if (b.peakOut > peakOut) {
            peakOut = b.peakOut;
            peakOutAt = b.peakOutAt;
        }
        minIn = minNonZero(minIn, b.minIn);
        minOut = minNonZero(minOut, b.minOut);
        activeTicks += b.activeTicks;
        ticks += b.ticks;
    }

    static long minNonZero(long a, long b) {
        if (a == 0) return b;
        if (b == 0) return a;
        return Math.min(a, b);
    }

    /** Average EU/t of the inbound sum. */
    public long avgIn() {
        return ticks == 0 ? 0
            : in.divide(BigInteger.valueOf(ticks))
                .longValue();
    }

    public long avgOut() {
        return ticks == 0 ? 0
            : out.divide(BigInteger.valueOf(ticks))
                .longValue();
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("s", start);
        t.setByteArray("i", in.toByteArray());
        t.setByteArray("o", out.toByteArray());
        t.setByteArray("d", demand.toByteArray());
        t.setLong("pi", peakIn);
        t.setLong("po", peakOut);
        t.setLong("pia", peakInAt);
        t.setLong("poa", peakOutAt);
        t.setLong("mi", minIn);
        t.setLong("mo", minOut);
        t.setLong("a", activeTicks);
        t.setLong("t", ticks);
        return t;
    }

    public static Bucket read(NBTTagCompound t) {
        Bucket b = new Bucket(t.getLong("s"));
        b.in = big(t.getByteArray("i"));
        b.out = big(t.getByteArray("o"));
        b.demand = big(t.getByteArray("d"));
        b.peakIn = t.getLong("pi");
        b.peakOut = t.getLong("po");
        b.peakInAt = t.getLong("pia");
        b.peakOutAt = t.getLong("poa");
        b.minIn = t.getLong("mi");
        b.minOut = t.getLong("mo");
        b.activeTicks = t.getLong("a");
        b.ticks = t.getLong("t");
        return b;
    }

    static BigInteger big(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? BigInteger.ZERO : new BigInteger(bytes);
    }
}
