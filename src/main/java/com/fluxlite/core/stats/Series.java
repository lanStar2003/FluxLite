package com.fluxlite.core.stats;

import java.math.BigInteger;
import java.util.Calendar;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Multi-level rolling store (RRD style): 60 one-second buckets, 60 one-minute buckets, 72 one-hour buckets, plus
 * cumulative counters. "Second" means 20 server ticks. Only the minute and hour levels are persisted.
 */
public final class Series {

    public static final int SEC = 60, MIN = 60, HOUR = 72;

    private final Bucket[] sec = new Bucket[SEC];
    private final Bucket[] min = new Bucket[MIN];
    private final Bucket[] hour = new Bucket[HOUR];
    private int secHead, secCount, minHead, minCount, hourHead, hourCount;

    // current second, kept in longs because it is touched every tick
    private long cIn, cOut, cDemand, cPeakIn, cPeakOut, cPeakInAt, cPeakOutAt, cMinIn, cMinOut, cActive, cTicks, cStart;
    /** Overflow of the long accumulators above (only reached with absurd per-tick values). */
    private BigInteger spillIn = BigInteger.ZERO, spillOut = BigInteger.ZERO, spillDemand = BigInteger.ZERO;
    private Bucket curMin = new Bucket();
    private Bucket curHour = new Bucket();
    private int secondsInMinute, minutesInHour;

    public long lastIn, lastOut, lastDemand;

    /**
     * The "now" values. GT moves energy in whole packets, so a machine on a cable takes one packet, then nothing for a
     * few ticks, then the next one; a single tick swings between a burst and zero. The rate is therefore measured from
     * packet to packet: the energy of the packets over the ticks between the first and the last one, looking back at
     * least {@link #SMOOTH} ticks. A steady packet rhythm gives its exact average (no jitter), a flow that starts shows
     * up at once, and one that stops drops to zero after a few missed packets.
     */
    public static final int SMOOTH = 20;
    /** Ticks of history kept for the rate. */
    public static final int WINDOW = 64;
    private long[] wIn, wOut;
    private int wHead;
    private long wTicks, flowInAt = -WINDOW * 2, flowOutAt = -WINDOW * 2, flowInSince, flowOutSince;

    /** Per-tick history for live charts; allocated only while someone looks at it (or always, when kept). */
    public static final int TICKS = 200;
    private long[] ringIn, ringOut;
    private int ringHead, ringCount;
    private long ringUntil;
    private boolean keepTicks;

    public BigInteger totalIn = BigInteger.ZERO, totalOut = BigInteger.ZERO;
    public BigInteger todayIn = BigInteger.ZERO, todayOut = BigInteger.ZERO;
    public BigInteger sessionIn = BigInteger.ZERO, sessionOut = BigInteger.ZERO;
    public int todayKey;
    public long createdAt = System.currentTimeMillis();

    public void tick(long in, long out, long demand, boolean active, long now) {
        lastIn = in;
        lastOut = out;
        lastDemand = demand;
        wTicks++;
        if (wIn == null && (in > 0 || out > 0)) {
            // series that never move energy (most faces of most connectors) keep no history
            wIn = new long[WINDOW];
            wOut = new long[WINDOW];
        }
        if (wIn != null) {
            wIn[wHead] = in;
            wOut[wHead] = out;
        }
        wHead = (wHead + 1) % WINDOW;
        if (in > 0) {
            if (wTicks - flowInAt > WINDOW) flowInSince = wTicks;
            flowInAt = wTicks;
        }
        if (out > 0) {
            if (wTicks - flowOutAt > WINDOW) flowOutSince = wTicks;
            flowOutAt = wTicks;
        }
        if (ringIn != null) {
            if (!keepTicks && now > ringUntil) {
                ringIn = ringOut = null;
            } else {
                ringIn[ringHead] = rateIn();
                ringOut[ringHead] = rateOut();
                ringHead = (ringHead + 1) % TICKS;
                if (ringCount < TICKS) ringCount++;
            }
        }
        if (cTicks == 0) cStart = now;
        long r = cIn + in;
        if (((cIn ^ r) & (in ^ r)) < 0) {
            spillIn = spillIn.add(BigInteger.valueOf(cIn))
                .add(BigInteger.valueOf(in));
            r = 0;
        }
        cIn = r;
        r = cOut + out;
        if (((cOut ^ r) & (out ^ r)) < 0) {
            spillOut = spillOut.add(BigInteger.valueOf(cOut))
                .add(BigInteger.valueOf(out));
            r = 0;
        }
        cOut = r;
        r = cDemand + demand;
        if (((cDemand ^ r) & (demand ^ r)) < 0) {
            spillDemand = spillDemand.add(BigInteger.valueOf(cDemand))
                .add(BigInteger.valueOf(demand));
            r = 0;
        }
        cDemand = r;
        if (in > cPeakIn) {
            cPeakIn = in;
            cPeakInAt = now;
        }
        if (out > cPeakOut) {
            cPeakOut = out;
            cPeakOutAt = now;
        }
        if (in > 0) cMinIn = cMinIn == 0 ? in : Math.min(cMinIn, in);
        if (out > 0) cMinOut = cMinOut == 0 ? out : Math.min(cMinOut, out);
        if (active) cActive++;
        cTicks++;
    }

    /** Current input in EU/t (see {@link #SMOOTH}). */
    public long rateIn() {
        return rate(wIn, wTicks - flowInSince + 1);
    }

    /** Current output in EU/t (see {@link #SMOOTH}). */
    public long rateOut() {
        return rate(wOut, wTicks - flowOutSince + 1);
    }

    private long rate(long[] ring, long sinceStart) {
        if (ring == null) return 0;
        long avail = Math.min(Math.min(WINDOW, wTicks), sinceStart);
        int newest = -1, oldest = -1, count = 0;
        long sum = 0, newestValue = 0, oldestValue = 0;
        for (int age = 0; age < avail; age++) {
            long v = ring[((wHead - 1 - age) % WINDOW + WINDOW) % WINDOW];
            if (v <= 0) continue;
            if (newest < 0) {
                newest = age;
                newestValue = v;
            }
            oldest = age;
            oldestValue = v;
            count++;
            sum += v;
            if (sum < 0) sum = Long.MAX_VALUE; // only with absurd values
            if (count >= 2 && oldest - newest >= SMOOTH) break;
        }
        if (count == 0) return 0;
        // a single packet: spread over the time since the flow started (or the whole window)
        if (count == 1) return newestValue / Math.max(1, avail);
        long gap = oldest - newest;
        // no packet for a few times the usual spacing: the flow has stopped
        if (newest > 3 * gap / (count - 1) + 2) return 0;
        return (sum - oldestValue) / gap;
    }

    /** Start (or keep) recording per-tick values for the next minute. */
    public void watchTicks(long now) {
        if (ringIn == null) {
            ringIn = new long[TICKS];
            ringOut = new long[TICKS];
            ringHead = ringCount = 0;
        }
        ringUntil = now + 60_000L;
    }

    /** Always record per-tick values (team totals). */
    public void keepTicks() {
        keepTicks = true;
        watchTicks(System.currentTimeMillis());
    }

    /** Per-tick "now" values, oldest first: {in[], out[]}. Empty until {@link #watchTicks} was called. */
    public long[][] tickCurve() {
        if (ringIn == null) return new long[][] { new long[0], new long[0] };
        long[] in = new long[ringCount], out = new long[ringCount];
        for (int i = 0; i < ringCount; i++) {
            int idx = ((ringHead - ringCount + i) % TICKS + TICKS) % TICKS;
            in[i] = ringIn[idx];
            out[i] = ringOut[idx];
        }
        return new long[][] { in, out };
    }

    /** Feed one whole second at once (used for sampled machines, which only have GT's averages). */
    public void addSecond(long avgIn, long avgOut, boolean active, long now) {
        for (int i = 0; i < 20; i++) tick(avgIn, avgOut, 0, active, now);
    }

    /** Close the current second. Called every 20 ticks by the stats engine. */
    public void rollSecond(int dayKey) {
        Bucket b = new Bucket(cStart);
        b.in = BigInteger.valueOf(cIn)
            .add(spillIn);
        b.out = BigInteger.valueOf(cOut)
            .add(spillOut);
        b.demand = BigInteger.valueOf(cDemand)
            .add(spillDemand);
        spillIn = spillOut = spillDemand = BigInteger.ZERO;
        b.peakIn = cPeakIn;
        b.peakOut = cPeakOut;
        b.peakInAt = cPeakInAt;
        b.peakOutAt = cPeakOutAt;
        b.minIn = cMinIn;
        b.minOut = cMinOut;
        b.activeTicks = cActive;
        b.ticks = cTicks;

        if (dayKey != todayKey) {
            todayKey = dayKey;
            todayIn = BigInteger.ZERO;
            todayOut = BigInteger.ZERO;
        }
        if (b.in.signum() != 0) {
            totalIn = totalIn.add(b.in);
            todayIn = todayIn.add(b.in);
            sessionIn = sessionIn.add(b.in);
        }
        if (b.out.signum() != 0) {
            totalOut = totalOut.add(b.out);
            todayOut = todayOut.add(b.out);
            sessionOut = sessionOut.add(b.out);
        }

        sec[secHead] = b;
        secHead = (secHead + 1) % SEC;
        if (secCount < SEC) secCount++;
        curMin.merge(b);

        cIn = cOut = cDemand = cPeakIn = cPeakOut = cPeakInAt = cPeakOutAt = cMinIn = cMinOut = cActive = cTicks = 0;

        if (++secondsInMinute >= 60) {
            secondsInMinute = 0;
            min[minHead] = curMin;
            minHead = (minHead + 1) % MIN;
            if (minCount < MIN) minCount++;
            curHour.merge(curMin);
            curMin = new Bucket();
            if (++minutesInHour >= 60) {
                minutesInHour = 0;
                hour[hourHead] = curHour;
                hourHead = (hourHead + 1) % HOUR;
                if (hourCount < HOUR) hourCount++;
                curHour = new Bucket();
            }
        }
    }

    private static Bucket at(Bucket[] ring, int head, int count, int back) {
        // back = 0 is the newest closed bucket
        if (back >= count) return null;
        int len = ring.length;
        return ring[((head - 1 - back) % len + len) % len];
    }

    /** Merged data of roughly the last {@code seconds} seconds. */
    public Bucket window(int seconds) {
        Bucket r = new Bucket();
        if (seconds <= SEC) {
            for (int i = 0; i < seconds; i++) {
                Bucket b = at(sec, secHead, secCount, i);
                if (b == null) break;
                r.merge(b);
            }
            return r;
        }
        if (seconds <= MIN * 60) {
            int minutes = (seconds + 59) / 60;
            r.merge(curMin);
            for (int i = 0; i < minutes - 1; i++) {
                Bucket b = at(min, minHead, minCount, i);
                if (b == null) break;
                r.merge(b);
            }
            if (r.ticks == 0) return window(SEC);
            return r;
        }
        int hours = (seconds + 3599) / 3600;
        r.merge(curHour);
        r.merge(curMin);
        for (int i = 0; i < hours - 1; i++) {
            Bucket b = at(hour, hourHead, hourCount, i);
            if (b == null) break;
            r.merge(b);
        }
        // curHour already holds every closed minute of the running hour, so a young series is still covered
        return r;
    }

    public long avgIn(int seconds) {
        return window(seconds).avgIn();
    }

    public long avgOut(int seconds) {
        return window(seconds).avgOut();
    }

    /**
     * Curve for the detail page, oldest first. level 0 = seconds (1 min), 1 = minutes (1 h), 2 = hours (3 d).
     *
     * @return {avgIn[], avgOut[], startMs[]}
     */
    public long[][] curve(int level) {
        Bucket[] ring = level == 0 ? sec : level == 1 ? min : hour;
        int head = level == 0 ? secHead : level == 1 ? minHead : hourHead;
        int count = level == 0 ? secCount : level == 1 ? minCount : hourCount;
        long[] in = new long[count], out = new long[count], start = new long[count];
        for (int i = 0; i < count; i++) {
            Bucket b = at(ring, head, count, count - 1 - i);
            in[i] = b.avgIn();
            out[i] = b.avgOut();
            start[i] = b.start;
        }
        return new long[][] { in, out, start };
    }

    /** Average EU/t per hour of the (server local) day, from the hour ring. {in[24], out[24]} */
    public long[][] hourOfDay() {
        BigInteger[] in = new BigInteger[24], out = new BigInteger[24];
        long[] ticks = new long[24];
        for (int i = 0; i < 24; i++) in[i] = out[i] = BigInteger.ZERO;
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < hourCount + 1; i++) {
            Bucket b = i < hourCount ? at(hour, hourHead, hourCount, i) : curHour;
            if (b == null || b.ticks == 0) continue;
            cal.setTimeInMillis(b.start);
            int h = cal.get(Calendar.HOUR_OF_DAY);
            in[h] = in[h].add(b.in);
            out[h] = out[h].add(b.out);
            ticks[h] += b.ticks;
        }
        long[] ai = new long[24], ao = new long[24];
        for (int h = 0; h < 24; h++) {
            if (ticks[h] == 0) continue;
            ai[h] = in[h].divide(BigInteger.valueOf(ticks[h]))
                .longValue();
            ao[h] = out[h].divide(BigInteger.valueOf(ticks[h]))
                .longValue();
        }
        return new long[][] { ai, ao };
    }

    public boolean hasHistory() {
        return secCount > 0 || minCount > 0 || hourCount > 0;
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("min", writeRing(min, minHead, minCount));
        t.setTag("hour", writeRing(hour, hourHead, hourCount));
        t.setTag("cm", curMin.write());
        t.setTag("ch", curHour.write());
        t.setInteger("sm", secondsInMinute);
        t.setInteger("mh", minutesInHour);
        t.setByteArray("ti", totalIn.toByteArray());
        t.setByteArray("to", totalOut.toByteArray());
        t.setByteArray("di", todayIn.toByteArray());
        t.setByteArray("do", todayOut.toByteArray());
        t.setInteger("dk", todayKey);
        t.setLong("c", createdAt);
        return t;
    }

    public static Series read(NBTTagCompound t) {
        Series s = new Series();
        s.minCount = readRing(t.getTagList("min", 10), s.min);
        s.minHead = s.minCount % MIN;
        s.hourCount = readRing(t.getTagList("hour", 10), s.hour);
        s.hourHead = s.hourCount % HOUR;
        if (t.hasKey("cm")) s.curMin = Bucket.read(t.getCompoundTag("cm"));
        if (t.hasKey("ch")) s.curHour = Bucket.read(t.getCompoundTag("ch"));
        s.secondsInMinute = t.getInteger("sm");
        s.minutesInHour = t.getInteger("mh");
        s.totalIn = Bucket.big(t.getByteArray("ti"));
        s.totalOut = Bucket.big(t.getByteArray("to"));
        s.todayIn = Bucket.big(t.getByteArray("di"));
        s.todayOut = Bucket.big(t.getByteArray("do"));
        s.todayKey = t.getInteger("dk");
        if (t.hasKey("c")) s.createdAt = t.getLong("c");
        return s;
    }

    private static NBTTagList writeRing(Bucket[] ring, int head, int count) {
        NBTTagList l = new NBTTagList();
        for (int i = count - 1; i >= 0; i--) l.appendTag(at(ring, head, count, i).write());
        return l;
    }

    /** Reads oldest-first into ring[0..n). */
    private static int readRing(NBTTagList l, Bucket[] ring) {
        int n = Math.min(l.tagCount(), ring.length);
        int skip = l.tagCount() - n;
        for (int i = 0; i < n; i++) ring[i] = Bucket.read(l.getCompoundTagAt(skip + i));
        return n;
    }
}
