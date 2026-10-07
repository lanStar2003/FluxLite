package com.fluxlite.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GTPowerTest {

    @Test
    void theCableBucketDrainsByItsRatingEveryTick() {
        assertEquals(10, GTPower.bucket(10, 2, 500, 500), "touched this tick");
        assertEquals(4, GTPower.bucket(10, 2, 500, 503));
        assertEquals(0, GTPower.bucket(10, 2, 500, 520), "never below empty");
    }

    @Test
    void anOldOrFutureBucketIsEmpty() {
        // GT resets a path that was not used for more than 100 ticks, or whose clock ran backwards
        assertEquals(0, GTPower.bucket(1000, 1, 0, 101));
        assertEquals(0, GTPower.bucket(1000, 1, 50, 10));
    }

    @Test
    void aGeneratorIsHeldBackOnlyWhenItHadEnergyAndDidNotSendIt() {
        assertFalse(GTPower.heldBack(33, 2560, 32, 1), "sent its packet (GT adds line loss)");
        assertTrue(GTPower.heldBack(0, 2560, 32, 1), "full buffer, nothing sent");
        assertFalse(GTPower.heldBack(0, 10, 32, 1), "out of fuel");
        assertFalse(GTPower.heldBack(64, 70, 32, 4), "a dynamo sending all it has");
        assertTrue(GTPower.heldBack(64, 200, 32, 4), "a dynamo with four packets ready that sent two");
    }

    @Test
    void theFieldsGtKeepsPrivateAreThere() {
        // when GT renames them the connector falls back to guessing; this test says so first
        assertTrue(GTPower.canReadCables());
        assertTrue(GTPower.canReadMachines());
        assertTrue(GTPower.canReadOutput());
    }
}
