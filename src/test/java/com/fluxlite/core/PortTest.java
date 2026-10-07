package com.fluxlite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxlite.Config;

class PortTest {

    @Test
    void supplyBufferHoldsTheBufferedTicks() {
        Port p = new Port(ForgeDirection.UP);
        p.role = PortRole.OUTPUT;
        p.supplyVoltage = 32;
        p.supplyAmperage = 4;
        p.recomputeCapacity();
        assertEquals(32L * 4 * Config.settlementPeriod * Config.bufferPeriods, p.supplyCap);
        assertEquals(0, p.collectCap);
    }

    @Test
    void supplyBufferAlwaysFitsOnePacket() {
        Port p = new Port(ForgeDirection.UP);
        p.role = PortRole.OUTPUT;
        p.supplyVoltage = 2048;
        p.supplyAmperage = 0;
        p.recomputeCapacity();
        assertTrue(p.supplyCap >= 2048);
    }

    @Test
    void bothDirectionsGetBothBuffers() {
        Port p = new Port(ForgeDirection.UP);
        p.role = PortRole.BOTH;
        p.status = PortStatus.OK;
        p.supplyVoltage = 512;
        p.supplyAmperage = 2;
        p.collectVoltage = 128;
        p.collectAmperage = 1;
        p.recomputeCapacity();
        assertTrue(p.supplies());
        assertTrue(p.collects());
        assertTrue(p.supplyCap > 0);
        assertTrue(p.collectCap > 0);
    }

    @Test
    void passiveSendersGrowTheCollectBuffer() {
        Port p = new Port(ForgeDirection.UP);
        p.role = PortRole.INPUT;
        p.recomputeCapacity();
        long before = p.collectCap;
        p.ensureCollectCapacityFor(2_097_152L * 16);
        assertTrue(p.collectCap > before);
        assertTrue(p.collectRoom() >= 2_097_152L * 16);
    }

    @Test
    void offPortsDoNothing() {
        Port p = new Port(ForgeDirection.UP);
        p.role = PortRole.BOTH;
        p.status = PortStatus.DISABLED;
        assertFalse(p.supplies());
        assertFalse(p.collects());
    }

    @Test
    void multiplicationSaturates() {
        assertEquals(Long.MAX_VALUE, Port.mul(Long.MAX_VALUE / 2, 3));
        assertEquals(0, Port.mul(-1, 5));
    }
}
