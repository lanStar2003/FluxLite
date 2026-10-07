package com.fluxlite.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Test;

import com.fluxlite.core.Port;
import com.fluxlite.core.PortRole;
import com.fluxlite.tile.TileConnector;

import cofh.api.energy.IEnergyConnection;
import cofh.api.energy.IEnergyHandler;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;

class RFAdapterTest {

    private static final ForgeDirection FACE = ForgeDirection.WEST;

    /** An RF buffer with per-tick limits; what a device does is decided by the subclasses. */
    abstract static class Device extends TileEntity implements IEnergyConnection {

        int stored, capacity = 100_000, maxIn = 1000, maxOut = 1000;

        @Override
        public boolean canConnectEnergy(ForgeDirection from) {
            return true;
        }

        public int receiveEnergy(ForgeDirection from, int max, boolean simulate) {
            int n = Math.min(max, Math.min(maxIn, capacity - stored));
            if (!simulate) stored += n;
            return n;
        }

        public int extractEnergy(ForgeDirection from, int max, boolean simulate) {
            int n = Math.min(max, Math.min(maxOut, stored));
            if (!simulate) stored -= n;
            return n;
        }

        public int getEnergyStored(ForgeDirection from) {
            return stored;
        }

        public int getMaxEnergyStored(ForgeDirection from) {
            return capacity;
        }
    }

    /** An EnderIO style machine: receiver only. */
    static final class Machine extends Device implements IEnergyReceiver {
    }

    /** An EnderIO style generator: provider only. */
    static final class Generator extends Device implements IEnergyProvider {
    }

    /** A Forestry or Railcraft engine: no receiver, no provider, it pushes by itself. */
    static final class Engine extends Device {
    }

    /** BuildCraft, Forestry and most other machines: both interfaces, extraction does nothing. */
    static final class HandlerMachine extends Device implements IEnergyHandler {

        @Override
        public int extractEnergy(ForgeDirection from, int max, boolean simulate) {
            return 0;
        }
    }

    /** A BuildCraft engine: both interfaces, takes nothing, cannot be drained, pushes. */
    static final class HandlerEngine extends Device implements IEnergyHandler {

        @Override
        public int receiveEnergy(ForgeDirection from, int max, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(ForgeDirection from, int max, boolean simulate) {
            return 0;
        }
    }

    /** An Extra Utilities generator: both interfaces, takes nothing, can be drained. */
    static final class HandlerGenerator extends Device implements IEnergyHandler {

        @Override
        public int receiveEnergy(ForgeDirection from, int max, boolean simulate) {
            return 0;
        }
    }

    @Test
    void machinesAreFed() {
        assertEquals(PortRole.OUTPUT, new RFAdapter(new Machine(), FACE).autoRole());
        assertEquals(PortRole.OUTPUT, new RFAdapter(new HandlerMachine(), FACE).autoRole());
    }

    @Test
    void generatorsAndEnginesAreDrained() {
        assertEquals(PortRole.INPUT, new RFAdapter(new Generator(), FACE).autoRole());
        assertEquals(PortRole.INPUT, new RFAdapter(new Engine(), FACE).autoRole());
        HandlerGenerator g = new HandlerGenerator();
        g.stored = 5000;
        assertEquals(PortRole.INPUT, new RFAdapter(g, FACE).autoRole());
    }

    @Test
    void anEngineThatPushesTurnsToInput() {
        RFAdapter a = new RFAdapter(new HandlerEngine(), FACE);
        // nothing to tell yet: idle on the feeding side
        assertEquals(PortRole.OUTPUT, a.autoRole());
        assertTrue(a.notePush(), "the first push asks for a new look");
        assertEquals(PortRole.INPUT, a.autoRole());
        assertFalse(a.notePush(), "later pushes change nothing");
    }

    @Test
    void aFullMachineStaysFed() {
        HandlerMachine m = new HandlerMachine();
        RFAdapter a = new RFAdapter(m, FACE);
        assertEquals(PortRole.OUTPUT, a.autoRole());
        m.stored = m.capacity;
        assertEquals(PortRole.OUTPUT, a.autoRole());
    }

    @Test
    void decisionTable() {
        // receiver, provider, takes, gives, pushed, everTook, last
        assertEquals(PortRole.INPUT, RFAdapter.decide(false, false, false, false, false, false, PortRole.NONE));
        assertEquals(PortRole.INPUT, RFAdapter.decide(true, false, true, false, true, true, PortRole.OUTPUT));
        assertEquals(PortRole.OUTPUT, RFAdapter.decide(true, false, false, false, false, false, PortRole.NONE));
        // a storage that was charged and is full now is not drained
        assertEquals(PortRole.OUTPUT, RFAdapter.decide(true, true, false, true, false, true, PortRole.OUTPUT));
        // a storage found full is drained
        assertEquals(PortRole.INPUT, RFAdapter.decide(true, true, false, true, false, false, PortRole.NONE));
        // a device with nothing to say keeps its direction
        assertEquals(PortRole.INPUT, RFAdapter.decide(true, true, false, false, false, false, PortRole.INPUT));
        assertEquals(PortRole.OUTPUT, RFAdapter.decide(true, true, false, false, false, false, PortRole.NONE));
    }

    @Test
    void feedingConvertsAtGTsRate() {
        Machine m = new Machine();
        m.maxIn = 1_000_000;
        RFAdapter a = new RFAdapter(m, FACE);
        long used = a.inject(100);
        assertEquals(100, used);
        assertEquals(RF.euToRf(100), m.stored);
    }

    @Test
    void drainingNeverPaysMoreThanFeedingCosts() {
        Generator g = new Generator();
        g.stored = g.capacity;
        g.maxOut = 1_000_000;
        RFAdapter a = new RFAdapter(g, FACE);
        long eu = a.extract(1000);
        long rf = g.capacity - g.stored;
        assertTrue(eu > 0);
        assertTrue(RF.euToRf(eu) <= rf, eu + " EU for " + rf + " RF");
    }

    @Test
    void onlyTwoWayDevicesCanBeFixed() {
        TileConnector c = new TileConnector();
        Port p = c.ports[FACE.ordinal()];
        p.adapter = new RFAdapter(new Machine(), FACE);
        assertTrue(TileConnector.canChooseDirection(p));
        c.cycleDirection(p.index);
        assertEquals(PortRole.INPUT, p.fixed);
        c.cycleDirection(p.index);
        assertEquals(PortRole.OUTPUT, p.fixed);
        c.cycleDirection(p.index);
        assertEquals(PortRole.NONE, p.fixed);

        Port q = c.ports[ForgeDirection.UP.ordinal()];
        q.adapter = new RFAdapter(new Generator(), ForgeDirection.DOWN);
        assertFalse(TileConnector.canChooseDirection(q));
        c.cycleDirection(q.index);
        assertEquals(PortRole.NONE, q.fixed);
    }
}
