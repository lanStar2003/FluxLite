package com.fluxlite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeedGovernorTest {

    /**
     * A machine that takes {@code room} amperes per tick and stays hungry (an energy hatch of a running multiblock), a
     * generator of {@code gen} amperes with fuel to spare, and a connector face topping up. Returns {generator EU
     * share, machine fill share} over the second half of the run.
     */
    private static double[] run(long room, long gen, boolean connectorFirst) {
        FeedGovernor g = new FeedGovernor();
        boolean genBlocked = false;
        long genOut = 0, machineIn = 0, ticks = 0;
        for (long t = 0; t < 4000; t++) {
            long accepted = 0;
            if (!connectorFirst) {
                long gave = Math.min(gen, room - accepted);
                accepted += gave;
                genBlocked = gave < gen;
                if (t >= 2000) genOut += gave;
            }
            long want = room - accepted;
            long sent = Math.min(g.allow(t, genBlocked, want), want);
            g.fed(t, sent);
            accepted += sent;
            if (connectorFirst) {
                long gave = Math.min(gen, room - accepted);
                accepted += gave;
                genBlocked = gave < gen;
                if (t >= 2000) genOut += gave;
            }
            if (t >= 2000) {
                machineIn += accepted;
                ticks++;
            }
        }
        return new double[] { gen == 0 ? 1 : (double) genOut / (gen * ticks), (double) machineIn / (room * ticks) };
    }

    @Test
    void theGeneratorKeepsRunningWhenTheConnectorTicksFirst() {
        double[] r = run(2, 1, true);
        assertTrue(r[0] > 0.95, "generator ran " + r[0]);
        assertTrue(r[1] > 0.9, "machine filled " + r[1]);
    }

    @Test
    void theConnectorFillsWhatTheGeneratorLeaves() {
        double[] r = run(8, 3, false);
        assertEquals(1.0, r[0], 1e-9);
        assertEquals(1.0, r[1], 1e-9);
        double[] first = run(8, 3, true);
        assertTrue(first[0] > 0.95, "generator ran " + first[0]);
        assertTrue(first[1] > 0.9, "machine filled " + first[1]);
    }

    @Test
    void withoutFuelTheConnectorTakesOverFully() {
        double[] r = run(16, 0, true);
        assertTrue(r[1] > 0.99, "machine filled " + r[1]);
    }

    @Test
    void aSingleRefuelTickIsNotAHoldBack() {
        FeedGovernor g = new FeedGovernor();
        for (long t = 0; t < 20; t++) g.fed(t, g.allow(t, false, 4));
        assertEquals(4, g.allow(20, true, 4), "one held tick: keep feeding");
        g.fed(20, 4);
        assertEquals(4, g.allow(21, false, 4));
    }

    @Test
    void aGrowingDemandIsCaughtUpWithinSeconds() {
        FeedGovernor g = new FeedGovernor();
        long t = 0;
        for (; t < 10; t++) g.fed(t, g.allow(t, false, 4));
        // our 4 A held a generator back twice in a row: step back to 3
        g.fed(t, g.allow(t, true, 4));
        t++;
        assertEquals(0, g.allow(t, true, 4));
        assertEquals(3, g.share());
        long reached = -1;
        for (t++; t < 400 && reached < 0; t++) {
            long a = g.allow(t, false, 10);
            g.fed(t, a);
            if (a == 10) reached = t;
        }
        assertTrue(
            reached > 0 && reached < 11 + FeedGovernor.HOLD + FeedGovernor.SETTLE + 8 * FeedGovernor.STEP,
            "reached 10 A at " + reached);
    }

    @Test
    void aFullNetworkPausesTheFaceWithoutShrinkingItsShare() {
        FeedGovernor g = new FeedGovernor();
        for (long t = 0; t < 20; t++) g.fed(t, g.allow(t, false, 4));
        long share = g.share();
        // not feeding (the machines are full), and the generator cannot get rid of its energy either
        for (long t = 100; t < 110; t++) g.allow(t, true, 0);
        assertEquals(share, g.share());
        assertEquals(4, g.allow(200, false, 4));
    }
}
