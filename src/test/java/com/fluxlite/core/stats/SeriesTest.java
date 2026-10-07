package com.fluxlite.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class SeriesTest {

    private static void second(Series s, long in, long out, int day) {
        for (int i = 0; i < 20; i++) s.tick(in, out, in, in > 0, 1000L + i);
        s.rollSecond(day);
    }

    @Test
    void oneSecondAverage() {
        Series s = new Series();
        second(s, 100, 40, 1);
        assertEquals(100, s.avgIn(1));
        assertEquals(40, s.avgOut(1));
        assertEquals(20, s.window(1).ticks);
    }

    @Test
    void peakAndNonZeroMinimum() {
        Series s = new Series();
        long[] v = { 0, 50, 200, 70 };
        for (int i = 0; i < 20; i++) s.tick(v[i % 4], 0, 0, v[i % 4] > 0, 5000L + i);
        s.rollSecond(1);
        Bucket b = s.window(1);
        assertEquals(200, b.peakIn);
        assertEquals(50, b.minIn);
        assertEquals(15, b.activeTicks);
    }

    @Test
    void minuteAndHourRollover() {
        Series s = new Series();
        for (int i = 0; i < 60; i++) second(s, 10, 0, 1);
        assertEquals(1, s.curve(1)[0].length);
        assertEquals(10, s.curve(1)[0][0]);
        assertEquals(10, s.avgIn(300));
        for (int i = 0; i < 59 * 60; i++) second(s, 10, 0, 1);
        assertEquals(1, s.curve(2)[0].length);
        assertEquals(10, s.avgIn(86400));
    }

    @Test
    void windowMixesClosedMinutesAndCurrentMinute() {
        Series s = new Series();
        for (int i = 0; i < 60; i++) second(s, 100, 0, 1); // one closed minute at 100
        for (int i = 0; i < 60; i++) second(s, 300, 0, 1); // second closed minute at 300
        assertEquals(200, s.avgIn(300));
    }

    @Test
    void totalsUseBigInteger() {
        Series s = new Series();
        long huge = Long.MAX_VALUE / 10;
        for (int i = 0; i < 3; i++) second(s, huge, 0, 1);
        BigInteger expected = BigInteger.valueOf(huge)
            .multiply(BigInteger.valueOf(60));
        assertEquals(expected, s.totalIn);
        assertTrue(s.totalIn.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test
    void todayResetsOnNewDay() {
        Series s = new Series();
        second(s, 5, 0, 20261007);
        assertEquals(BigInteger.valueOf(100), s.todayIn);
        second(s, 5, 0, 20261008);
        assertEquals(BigInteger.valueOf(100), s.todayIn);
        assertEquals(BigInteger.valueOf(200), s.totalIn);
    }

    @Test
    void nbtRoundTripKeepsMinutesHoursAndTotals() {
        Series s = new Series();
        for (int i = 0; i < 61; i++) second(s, 7 + i, 3, 1);
        Series r = Series.read(s.write());
        assertEquals(s.totalIn, r.totalIn);
        assertEquals(s.totalOut, r.totalOut);
        long[][] a = s.curve(1), b = r.curve(1);
        assertEquals(a[0].length, b[0].length);
        for (int i = 0; i < a[0].length; i++) assertEquals(a[0][i], b[0][i]);
        assertEquals(s.avgIn(300), r.avgIn(300));
    }

    @Test
    void ringKeepsOnlyNewestSeconds() {
        Series s = new Series();
        for (int i = 0; i < 70; i++) second(s, i, 0, 1);
        long[] c = s.curve(0)[0];
        assertEquals(60, c.length);
        assertEquals(10, c[0]);
        assertEquals(69, c[59]);
    }

    // ------------------------------------------------------------------ "now" rate

    private static void ticks(Series s, int n, int every, long packet) {
        for (int i = 0; i < n; i++) s.tick(0, i % every == 0 ? packet : 0, 0, false, 0);
    }

    @Test
    void steadyFlowShowsItsValue() {
        Series s = new Series();
        for (int i = 0; i < 100; i++) s.tick(500, 0, 0, true, 0);
        assertEquals(500, s.rateIn());
        assertEquals(0, s.rateOut());
    }

    @Test
    void packetRhythmHasNoJitter() {
        // a cable-fed machine using 2 EU/t takes one 32 EU packet every 16 ticks
        Series s = new Series();
        ticks(s, 64, 16, 32);
        for (int i = 0; i < 64; i++) {
            s.tick(0, i % 16 == 0 ? 32 : 0, 0, false, 0);
            assertEquals(2, s.rateOut(), "tick " + i);
        }
    }

    @Test
    void startShowsAtOnce() {
        Series s = new Series();
        for (int i = 0; i < 200; i++) s.tick(0, 0, 0, false, 0);
        s.tick(0, 8192, 0, true, 0);
        assertEquals(8192, s.rateOut());
        s.tick(0, 8192, 0, true, 0);
        assertEquals(8192, s.rateOut());
    }

    @Test
    void stopDropsToZeroQuickly() {
        Series s = new Series();
        for (int i = 0; i < 100; i++) s.tick(0, 1000, 0, true, 0);
        for (int i = 0; i < 6; i++) s.tick(0, 0, 0, false, 0);
        assertEquals(0, s.rateOut());
        // a packet rhythm counts as stopped after a few missed packets
        Series p = new Series();
        ticks(p, 128, 16, 32);
        for (int i = 0; i < 40; i++) p.tick(0, 0, 0, false, 0);
        assertEquals(0, p.rateOut());
    }

    @Test
    void rateFollowsAChange() {
        Series s = new Series();
        for (int i = 0; i < 100; i++) s.tick(0, 1000, 0, true, 0);
        for (int i = 0; i < 25; i++) s.tick(0, 3000, 0, true, 0);
        assertEquals(3000, s.rateOut());
    }
}
