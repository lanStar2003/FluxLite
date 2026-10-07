package com.fluxlite.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class FmtTest {

    @Test
    void siSuffixes() {
        assertEquals("1234", Fmt.si(1234));
        assertEquals("12.3K", Fmt.si(12_345));
        assertEquals("-5.00M", Fmt.si(-5_000_000));
        assertEquals("9.22E", Fmt.si(Long.MAX_VALUE));
        assertEquals("100Y", Fmt.si(BigInteger.TEN.pow(26)));
    }

    @Test
    void tiers() {
        assertEquals("ULV", Fmt.tier(8));
        assertEquals("LV", Fmt.tier(32));
        assertEquals("MV", Fmt.tier(33));
        assertEquals("UV", Fmt.tier(524_288));
        assertEquals(8, Fmt.tierIndex(524_288));
    }

    @Test
    void durations() {
        assertEquals("45s", Fmt.duration(45));
        assertEquals("1h 2m", Fmt.duration(3725));
        assertEquals("2d 3h", Fmt.duration(2 * 86400 + 3 * 3600 + 5));
    }

    @Test
    void longsPackRoundTrip() {
        long[] v = { 0, -1, Long.MAX_VALUE, Long.MIN_VALUE, 123_456_789_012L };
        assertArrayEquals(v, Longs.unpack(Longs.pack(v)));
    }
}
