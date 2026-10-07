package com.fluxlite.core;

/**
 * How many amperes a face may feed into a cable that has generators on it too. The generators come first; the face
 * only fills what they leave. It cannot see in which order GT ticks the machines, so it learns its share: it raises the
 * share while every generator gets its energy out, and steps back below the share that held one back.
 * <p>
 * A generator counts as held back after two ticks in a row with a packet ready that it did not get out; a single
 * such tick is a generator that just burnt new fuel after its output. A held-back generator always pauses the face for
 * a few ticks; when the face had just fed, its share also drops to one ampere less than it fed, and stays there for a
 * few seconds. Then the share is tried one ampere higher every few ticks until a generator is held back again, so a
 * growing demand is caught up quickly while a settled share costs the generators about one tick in a hundred.
 */
public final class FeedGovernor {

    /** Ticks without feeding after a step back. */
    static final int HOLD = 5;
    /** Ticks the share stays put after a step back before it is tried one higher. */
    static final int SETTLE = 100;
    /** Ticks between two tries one ampere higher, long enough to see whether the last one held a generator back. */
    static final int STEP = 5;

    private long share = 1;
    /** A generator was held back once: from then on the share grows one ampere at a time. */
    private boolean learned;
    private long seen = Long.MIN_VALUE, heldAt = Long.MIN_VALUE, holdUntil = Long.MIN_VALUE, growFrom = Long.MIN_VALUE;
    private long fed, fedAt = Long.MIN_VALUE;

    /**
     * Amperes the face may feed at {@code tick}, at most {@code want}. {@code held}: a generator on the cable had a
     * packet ready and did not get it out. Called any number of times per tick; the first call of a tick decides.
     */
    public long allow(long tick, boolean held, long want) {
        if (tick != seen) {
            seen = tick;
            if (held) {
                boolean twice = heldAt == tick - 1;
                heldAt = tick;
                if (twice && tick >= holdUntil) {
                    holdUntil = tick + HOLD;
                    // held back by our own packets: take less; otherwise it is the network that is full
                    if (fedAt >= tick - 2) {
                        share = Math.max(0, fed - 1);
                        growFrom = tick + HOLD + SETTLE;
                        learned = true;
                    }
                }
            } else if (tick >= growFrom && (share == 0 || fedAt == tick - 1 && fed >= share)) {
                if (learned) {
                    share++;
                    growFrom = tick + STEP;
                } else share += Math.max(1, share);
            }
        }
        return tick < holdUntil ? 0 : Math.max(0, Math.min(want, share));
    }

    /** The face fed {@code amps} at {@code tick}. */
    public void fed(long tick, long amps) {
        if (amps <= 0) return;
        fed = amps;
        fedAt = tick;
    }

    long share() {
        return share;
    }
}
