package com.fluxlite.charge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChargePlanTest {

    @Test
    void enoughBalanceFillsEveryItem() {
        assertArrayEquals(new long[] { 100, 0, 50 }, ChargePlan.share(new long[] { 100, 0, 50 }, 1_000, 0));
    }

    @Test
    void aShortBalanceFillsTheFirstItemsFirst() {
        assertArrayEquals(new long[] { 100, 20, 0 }, ChargePlan.share(new long[] { 100, 80, 50 }, 120, 0));
    }

    @Test
    void theCapLimitsARoundAndNothingGoesBelowZero() {
        assertArrayEquals(new long[] { 30, 0 }, ChargePlan.share(new long[] { 100, 80 }, 1_000, 30));
        assertArrayEquals(new long[] { 0, 0 }, ChargePlan.share(new long[] { 100, -5 }, -10, 0));
    }

    @Test
    void aPerSecondCapIsSpreadOverRounds() {
        assertEquals(0, ChargePlan.capPerRound(0, 20));
        assertEquals(1000, ChargePlan.capPerRound(1000, 20));
        assertEquals(50, ChargePlan.capPerRound(1000, 1));
        assertEquals(1, ChargePlan.capPerRound(1, 1));
    }
}
