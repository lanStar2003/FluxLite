package com.fluxlite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fluxlite.Config;
import com.fluxlite.backend.WirelessBackend;

class SettlementTest {

    /** Same contract as GT's WirelessNetworkManager: refuses to go below zero. */
    static final class FakeBackend implements WirelessBackend {

        final Map<UUID, BigInteger> map = new HashMap<>();

        @Override
        public UUID resolveTeam(UUID member) {
            return member;
        }

        @Override
        public void ensureUser(UUID member) {}

        @Override
        public BigInteger getBalance(UUID member) {
            return map.getOrDefault(member, BigInteger.ZERO);
        }

        @Override
        public boolean add(UUID member, BigInteger delta) {
            BigInteger n = getBalance(member).add(delta);
            if (n.signum() < 0) return false;
            map.put(member, n);
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String lastError() {
            return "";
        }
    }

    private static final UUID TEAM = UUID.randomUUID();

    @AfterEach
    void resetLoss() {
        Config.wirelessLossPercent = 0;
    }

    private static Port output() {
        Port p = new Port(ForgeDirection.NORTH);
        p.role = PortRole.OUTPUT;
        p.status = PortStatus.OK;
        return p;
    }

    private static Settlement.Batch batch(long collected, Port[] ports, long[] wants) {
        Settlement.Batch b = new Settlement.Batch();
        b.collected = collected;
        for (int i = 0; i < ports.length; i++) b.requests.add(new Settlement.Request(ports[i], wants[i]));
        return b;
    }

    @Test
    void enoughBalanceFillsEveryone() {
        FakeBackend be = new FakeBackend();
        be.map.put(TEAM, BigInteger.valueOf(10_000));
        Port a = output(), b = output();
        Settlement.settleTeam(be, TEAM, batch(0, new Port[] { a, b }, new long[] { 1000, 1000 }));
        assertEquals(1000, a.supply);
        assertEquals(1000, b.supply);
        assertEquals(BigInteger.valueOf(8000), be.getBalance(TEAM));
    }

    @Test
    void shortBalanceIsSharedProportionally() {
        FakeBackend be = new FakeBackend();
        be.map.put(TEAM, BigInteger.valueOf(1000));
        Port a = output(), b = output();
        Settlement.settleTeam(be, TEAM, batch(0, new Port[] { a, b }, new long[] { 1000, 3000 }));
        assertEquals(250, a.supply);
        assertEquals(750, b.supply);
        assertEquals(BigInteger.ZERO, be.getBalance(TEAM));
    }

    @Test
    void inputOfTheSameTickCanFundOutput() {
        FakeBackend be = new FakeBackend();
        Port a = output();
        Settlement.settleTeam(be, TEAM, batch(2000, new Port[] { a }, new long[] { 1500 }));
        assertEquals(1500, a.supply);
        assertEquals(BigInteger.valueOf(500), be.getBalance(TEAM));
    }

    @Test
    void emptyNetworkGrantsNothingAndNeverGoesNegative() {
        FakeBackend be = new FakeBackend();
        Port a = output();
        Settlement.settleTeam(be, TEAM, batch(0, new Port[] { a }, new long[] { 1500 }));
        assertEquals(0, a.supply);
        assertEquals(BigInteger.ZERO, be.getBalance(TEAM));
    }

    @Test
    void lossAppliesToInput() {
        Config.wirelessLossPercent = 10;
        FakeBackend be = new FakeBackend();
        Settlement.settleTeam(be, TEAM, batch(1000, new Port[0], new long[0]));
        assertEquals(BigInteger.valueOf(900), be.getBalance(TEAM));
    }
}
