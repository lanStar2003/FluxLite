package com.fluxlite.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RFTest {

    /** GTNH's GregTech config: 100 EU -> 360 RF, 100 RF -> 100 EU. */
    private static final int EU_TO_RF = 360, RF_TO_EU = 100;

    @Test
    void collectingNeverPaysMoreThanFeedingCosts() {
        assertEquals(360, RF.inPer100(EU_TO_RF, RF_TO_EU));
        // a stricter GT collecting rate is kept
        assertEquals(500, RF.inPer100(EU_TO_RF, 20));
        assertEquals(Long.MAX_VALUE, RF.inPer100(EU_TO_RF, 0));
    }

    @Test
    void aRoundTripGainsNothing() {
        long out = RF.outPer100(EU_TO_RF), in = RF.inPer100(EU_TO_RF, RF_TO_EU);
        for (long eu : new long[] { 1, 7, 32, 100, 8192, 1_000_003 }) {
            long rf = RF.toRf(eu, out);
            RF.Carry carry = new RF.Carry();
            assertTrue(carry.toEu(rf, in) <= eu, "EU back for " + eu);
            assertTrue(RF.rfCost(rf, out) <= eu, "cost of " + rf + " RF");
        }
    }

    @Test
    void feedingRates() {
        assertEquals(3600, RF.toRf(1000, 360));
        assertEquals(3, RF.toRf(1, 360));
        assertEquals(1, RF.rfCost(3, 360));
        assertEquals(1000, RF.rfCost(3600, 360));
        assertEquals(1001, RF.rfCost(3601, 360));
        assertEquals(0, RF.rfCost(0, 360));
    }

    @Test
    void smallPushesAddUp() {
        RF.Carry carry = new RF.Carry();
        long eu = 0;
        // a 1 RF/t panel for 3600 ticks is worth 1000 EU at 360 RF per 100 EU
        for (int i = 0; i < 3600; i++) eu += carry.toEu(1, 360);
        assertEquals(1000, eu);
    }

    @Test
    void clampsToTheCoFHRange() {
        assertEquals(Integer.MAX_VALUE, RF.clampInt(Long.MAX_VALUE));
        assertEquals(0, RF.clampInt(-5));
        assertEquals(12, RF.clampInt(12));
    }
}
