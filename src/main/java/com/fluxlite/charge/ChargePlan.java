package com.fluxlite.charge;

/**
 * Shares out what one charging round may spend: each item in turn gets what it still needs until the budget (the
 * per-player cap, and never more than the team's balance) runs out. Items earlier in the inventory go first, so a
 * short balance tops up the hotbar before the backpack.
 */
public final class ChargePlan {

    private ChargePlan() {}

    /**
     * @param needs EU each item can still take
     * @param cap   per-round cap, 0 for none
     * @return EU each item gets
     */
    public static long[] share(long[] needs, long balance, long cap) {
        long budget = Math.max(0, balance);
        if (cap > 0) budget = Math.min(budget, cap);
        long[] out = new long[needs.length];
        for (int i = 0; i < needs.length && budget > 0; i++) {
            long give = Math.min(Math.max(0, needs[i]), budget);
            out[i] = give;
            budget -= give;
        }
        return out;
    }

    /** The per-round cap of a per-second cap: rounds of {@code interval} ticks, 0 staying "no cap". */
    public static long capPerRound(long perSecond, int interval) {
        if (perSecond <= 0) return 0;
        return Math.max(1, perSecond * Math.max(1, interval) / 20);
    }
}
