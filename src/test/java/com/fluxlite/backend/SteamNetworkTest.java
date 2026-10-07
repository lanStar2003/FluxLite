package com.fluxlite.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class SteamNetworkTest {

    private static final UUID TEAM = new UUID(1, 2), OTHER = new UUID(3, 4);

    @Test
    void takesAtMostWhatIsStored() {
        SteamNetwork n = new SteamNetwork("t");
        n.give(TEAM, 1000);
        assertEquals(600, n.take(TEAM, 600));
        assertEquals(400, n.take(TEAM, 600));
        assertEquals(0, n.take(TEAM, 600));
        assertEquals(BigInteger.ZERO, n.balanceOf(TEAM));
    }

    @Test
    void teamsAreSeparate() {
        SteamNetwork n = new SteamNetwork("t");
        n.give(TEAM, 500);
        assertEquals(0, n.take(OTHER, 100));
        assertEquals(BigInteger.valueOf(500), n.balanceOf(TEAM));
    }

    @Test
    void hasNoUpperLimit() {
        SteamNetwork n = new SteamNetwork("t");
        for (int i = 0; i < 4; i++) n.give(TEAM, Long.MAX_VALUE);
        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(4)),
            n.balanceOf(TEAM));
        assertEquals(Long.MAX_VALUE, n.take(TEAM, Long.MAX_VALUE));
    }

    @Test
    void survivesSaving() {
        SteamNetwork n = new SteamNetwork("t");
        n.give(TEAM, 123_456);
        NBTTagCompound t = new NBTTagCompound();
        n.writeToNBT(t);
        SteamNetwork back = new SteamNetwork("t");
        back.readFromNBT(t);
        assertEquals(BigInteger.valueOf(123_456), back.balanceOf(TEAM));
        assertEquals(BigInteger.ZERO, back.balanceOf(OTHER));
    }
}
