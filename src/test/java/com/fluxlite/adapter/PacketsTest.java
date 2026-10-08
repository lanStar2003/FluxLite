package com.fluxlite.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PacketsTest {

    @Test
    void mostIsAllPackets() {
        assertEquals(32 * 64, Packets.most(32, 64));
        assertEquals(0, Packets.most(0, 64));
        assertEquals(0, Packets.most(32, 0));
        assertEquals(Long.MAX_VALUE, Packets.most(Long.MAX_VALUE / 2, 4));
    }

    @Test
    void wholePacketsAreCappedByAmperage() {
        assertEquals(3, Packets.whole(100, 32, 64));
        assertEquals(64, Packets.whole(1_000_000, 32, 64));
        assertEquals(0, Packets.whole(31, 32, 64));
        assertEquals(0, Packets.whole(-5, 32, 64));
    }

    @Test
    void theRestGoesAsOneSmallerPacket() {
        assertEquals(4, Packets.rest(4, 32));
        assertEquals(0, Packets.rest(32, 32));
        assertEquals(0, Packets.rest(0, 32));
    }

    @Test
    void wholeAndRestNeverExceedTheEu() {
        for (long eu = 0; eu < 300; eu += 7) {
            long whole = Packets.whole(eu, 32, 4) * 32;
            long rest = Packets.rest(eu - whole, 32);
            assertEquals(true, whole + rest <= eu);
        }
    }
}
