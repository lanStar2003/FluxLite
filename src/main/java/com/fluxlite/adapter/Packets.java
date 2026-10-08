package com.fluxlite.adapter;

/** Splitting EU into GT packets for a device that takes up to {@code amps} packets of {@code voltage} per tick. */
public final class Packets {

    private Packets() {}

    /** The most EU such a device takes in one tick. */
    public static long most(long voltage, long amps) {
        if (voltage <= 0 || amps <= 0) return 0;
        return amps > Long.MAX_VALUE / voltage ? Long.MAX_VALUE : voltage * amps;
    }

    /** Whole packets of {@code voltage} in {@code eu}, at most {@code amps}. */
    public static long whole(long eu, long voltage, long amps) {
        if (eu <= 0 || voltage <= 0 || amps <= 0) return 0;
        return Math.min(amps, eu / voltage);
    }

    /** What is left over after whole packets, as one smaller packet (0 when it would be a whole one). */
    public static long rest(long eu, long voltage) {
        if (eu <= 0 || voltage <= 0) return 0;
        return eu < voltage ? eu : 0;
    }
}
