package com.fluxlite.adapter;

import gregtech.api.GregTechAPI;

/**
 * RF and EU. Feeding uses GT's own rate ({@link GregTechAPI#mEUtoRF}, RF per 100 EU), so an RF machine gets what a GT
 * cable would give it. Collecting never pays more EU for RF than that rate charges: GTNH ships 100 EU -> 360 RF but
 * 100 RF -> 100 EU (GT itself takes no RF at all), and taking that second rate as is would turn every 100 EU into 360
 * by sending it out as RF and back in.
 */
public final class RF {

    private RF() {}

    /** RF per 100 EU when the connector feeds RF devices. */
    public static long outPer100() {
        return outPer100(GregTechAPI.mEUtoRF);
    }

    /** RF per 100 EU when the connector collects RF; never cheaper than {@link #outPer100()}. */
    public static long inPer100() {
        return inPer100(GregTechAPI.mEUtoRF, GregTechAPI.mRFtoEU);
    }

    static long outPer100(int euToRf) {
        return Math.max(1, euToRf);
    }

    static long inPer100(int euToRf, int rfToEu) {
        // GT's own collecting rate as RF per 100 EU, rounded so it never pays more
        long gt = rfToEu > 0 ? (10_000L + rfToEu - 1) / rfToEu : Long.MAX_VALUE;
        return Math.max(outPer100(euToRf), gt);
    }

    /** RF that {@code eu} buys when feeding. */
    public static long euToRf(long eu) {
        return toRf(eu, outPer100());
    }

    /** EU it costs to feed {@code rf}, rounded up. */
    public static long rfCost(long rf) {
        return rfCost(rf, outPer100());
    }

    /** RF to ask a device for when the connector wants {@code eu}. */
    public static long rfFor(long eu) {
        return toRf(eu, inPer100());
    }

    static long toRf(long eu, long per100) {
        if (eu <= 0) return 0;
        return eu > Long.MAX_VALUE / per100 ? Long.MAX_VALUE : eu * per100 / 100;
    }

    static long rfCost(long rf, long per100) {
        if (rf <= 0) return 0;
        return (Math.min(rf, Integer.MAX_VALUE) * 100 + per100 - 1) / per100;
    }

    /** {@code value} clamped to an int, for the CoFH API. */
    public static int clampInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    /**
     * RF collected bit by bit: a solar panel's 1 RF/t is worth less than one EU, so what does not make a whole EU yet
     * is carried over instead of being lost.
     */
    public static final class Carry {

        /** Leftover RF, times 100. */
        private long rest;

        /** Adds {@code rf} and returns the whole EU it completes. */
        public long toEu(long rf) {
            return toEu(rf, inPer100());
        }

        long toEu(long rf, long per100) {
            if (rf <= 0) return 0;
            long total = rest + Math.min(rf, Long.MAX_VALUE / 200) * 100;
            long eu = total / per100;
            rest = total - eu * per100;
            return eu;
        }
    }
}
