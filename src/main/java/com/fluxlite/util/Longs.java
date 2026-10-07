package com.fluxlite.util;

/** NBT in 1.7.10 has no long arrays; pack them into int arrays (high, low). */
public final class Longs {

    private Longs() {}

    public static int[] pack(long[] a) {
        int[] r = new int[a.length * 2];
        for (int i = 0; i < a.length; i++) {
            r[2 * i] = (int) (a[i] >>> 32);
            r[2 * i + 1] = (int) a[i];
        }
        return r;
    }

    public static long[] unpack(int[] a) {
        long[] r = new long[a.length / 2];
        for (int i = 0; i < r.length; i++) r[i] = ((long) a[2 * i] << 32) | (a[2 * i + 1] & 0xFFFFFFFFL);
        return r;
    }
}
