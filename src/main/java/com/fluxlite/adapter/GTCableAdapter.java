package com.fluxlite.adapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.compat.GTPower;
import com.fluxlite.compat.GTSinks;
import com.fluxlite.core.CableScanner;
import com.fluxlite.core.FeedGovernor;
import com.fluxlite.core.MachineSample;
import com.fluxlite.core.PortRole;
import com.fluxlite.core.ServerEvents;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.util.Names;

import cofh.api.energy.IEnergyReceiver;
import gregtech.api.graphs.GenerateNodeMapPower;
import gregtech.api.graphs.Node;
import gregtech.api.graphs.paths.PowerNodePath;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import ic2.api.energy.tile.IEnergySink;

/**
 * A GT cable next to the connector. The whole cable network behind it is scanned (with a node cap) and cached; a
 * block placed or broken next to it, or a device on it disappearing, makes it scan again.
 * <p>
 * Feeding voltage is the minimum of the weakest cable and the lowest consumer input voltage, so neither the cable nor
 * any machine on it can be overloaded by our injection. Amperage is capped by the weakest cable and the sum of the
 * consumers, and the connector stops feeding while any stretch of the cable runs hot (see {@link GTPower#heat}).
 * Collecting is passive: generators on the cable push into the connector like into any other consumer.
 * <p>
 * A cable with machines and generators (or batteries) on it is both fed and drained, but never both at once, and its
 * generators come first: the connector only takes energy no machine on the cable can take right now (surplus), only
 * tops up machines that ran below half, and never charges the cable's batteries (they would hand it straight back). A
 * face that fed the cable this tick or the last refuses energy from it, and the other way round. Topping up never
 * takes a generator's place: the face sends no more than the demand, and as soon as a generator cannot get its energy
 * out (the machines are full of the connector's packets, and the connector refuses its own cable's energy) the face
 * pauses and learns a smaller share, see {@link FeedGovernor}.
 */
public final class GTCableAdapter implements EnergyAdapter {

    /** The connector stops feeding at this cable heat (ticks of overload, GT burns at 40) and resumes below COOL. */
    static final double HOT = 20, COOL = 10;

    private final BaseMetaPipeEntity pipe;
    private final TileConnector connector;
    private final ForgeDirection side;
    /** Face of the cable that touches the connector. */
    private final ForgeDirection face;
    private CableScanner.Result scan;
    private List<TileEntity> endpoints = new ArrayList<>();
    private long scannedAt = Long.MIN_VALUE;
    private int signature;
    private boolean topologyChanged;
    /** Power paths of the network, collected again whenever GT rebuilt its graph. */
    private Set<PowerNodePath> paths;
    private Node pathsOf;
    private boolean cooling;
    /** The face's share next to the cable's generators; learnt again when the devices on the cable change. */
    private FeedGovernor governor = new FeedGovernor();
    /** Amperes the face may feed this tick, worked out with the demand. */
    private long allowed;
    private long allowedAt = Long.MIN_VALUE;

    public GTCableAdapter(BaseMetaPipeEntity pipe, TileConnector connector, ForgeDirection side) {
        this.pipe = pipe;
        this.connector = connector;
        this.side = side;
        this.face = side.getOpposite();
        refresh();
    }

    public CableScanner.Result scan() {
        return scan;
    }

    @Override
    public void refresh() {
        long now = connector.getWorldObj()
            .getTotalWorldTime();
        if (scan != null && now - scannedAt < Config.cableRescanInterval && !stale()) return;
        scannedAt = now;
        scan = CableScanner.scan(pipe, connector, side);
        endpoints = endpoints();
        paths = null;
        int sig = signature(scan);
        if (sig != signature) {
            signature = sig;
            topologyChanged = true;
            governor = new FeedGovernor();
        }
    }

    @Override
    public void invalidateScan() {
        scannedAt = Long.MIN_VALUE;
    }

    /** A cable or a device of the last scan is gone. */
    private boolean stale() {
        for (BaseMetaPipeEntity c : scan.pipes) if (c.isInvalid()) return true;
        for (TileEntity te : endpoints) if (te.isInvalid()) return true;
        return false;
    }

    /** Which devices are on the cable, and which way; changes when one comes or goes. */
    private static int signature(CableScanner.Result r) {
        int h = 1;
        for (CableScanner.Endpoint e : r.consumers) h = h * 31 + Long.hashCode(MachineSample.posKey(e.tile)) * 2;
        for (CableScanner.Endpoint e : r.producers) h = h * 31 + Long.hashCode(MachineSample.posKey(e.tile)) * 2 + 1;
        return h * 31 + r.cables;
    }

    /**
     * True once after a scan found other devices than the one before. GT only rebuilds its cable graph for its own
     * machines, so the connector rebuilds it for the others (an AE2 controller placed on the cable, for one).
     */
    public boolean takeTopologyChange() {
        boolean t = topologyChanged;
        topologyChanged = false;
        return t;
    }

    @Override
    public boolean covers(long pos) {
        return scan.positions.contains(pos);
    }

    /** Every device on the cable once: consumers first, then pure producers. */
    private List<TileEntity> endpoints() {
        List<TileEntity> l = new ArrayList<>();
        Set<TileEntity> seen = new HashSet<>();
        for (CableScanner.Endpoint e : scan.consumers) if (seen.add(e.tile)) l.add(e.tile);
        for (CableScanner.Endpoint e : scan.producers) if (seen.add(e.tile)) l.add(e.tile);
        return l;
    }

    private PortRole role() {
        return connector.ports[side.ordinal()].role;
    }

    /**
     * What the machines on the cable take this tick, worked out the way GT accepts packets: a machine whose buffer is
     * not full takes one more packet than fits, up to what is left of its amperage until its next tick. Computed
     * every tick: a snapshot taken once per second would count a full packet of demand for every tick until the next
     * snapshot, although the machine only takes one every few ticks, and the face would look under-supplied.
     * <p>
     * With generators or batteries on the cable only machines below half are counted (they keep the rest topped up),
     * batteries are left out when the face also takes energy from the cable, and the face takes no more than its
     * share next to the generators (see {@link FeedGovernor}). What it works out is also what {@link #inject} may
     * send this tick.
     */
    private long currentDemand() {
        long tick = ServerEvents.tick();
        long v = inputVoltage();
        if (v <= 0) return allow(tick, 0);
        boolean topUp = !scan.producers.isEmpty(), takesBack = role().collects();
        long amps = 0;
        for (CableScanner.Endpoint e : scan.consumers) {
            if (e.tile.isInvalid() || takesBack && e.producer) continue;
            amps = safeAdd(amps, packets(e, v, topUp));
        }
        amps = Math.min(amps, inputAmperage());
        if (topUp) amps = governor.allow(tick, generatorsHeldBack(), amps);
        return safeMul(allow(tick, amps), v);
    }

    private long allow(long tick, long amps) {
        allowed = amps;
        allowedAt = tick;
        return amps;
    }

    /** A generator or battery on the cable could not get its energy out (see {@link GTPower#heldBack}). */
    private boolean generatorsHeldBack() {
        for (CableScanner.Endpoint e : scan.producers)
            if (e.tile instanceof BaseMetaTileEntity bm && GTPower.heldBack(bm)) return true;
        return false;
    }

    /** Packets of {@code v} the consumer takes now; with {@code topUp} only when it ran low. */
    private static long packets(CableScanner.Endpoint e, long v, boolean topUp) {
        TileEntity te = e.tile;
        if (te instanceof BaseMetaTileEntity bm) {
            return topUp && !GTPower.low(bm) ? 0 : GTPower.acceptAmps(bm, e.face, v);
        }
        if (te instanceof IBasicEnergyContainer c) {
            long in = c.getInputVoltage(), cap = c.getEUCapacity(), stored = c.getStoredEU();
            if (in <= 0 || in >= Integer.MAX_VALUE || stored >= cap || topUp && stored * 2 >= cap) return 0;
            return Math.min(c.getInputAmperage(), 1 + (cap - stored) / v);
        }
        if (te instanceof IEnergyConnected) {
            long d = GTSinks.demand(te, e.face, v);
            return d >= 0 ? d / v : Math.max(0, e.inAmperage);
        }
        if (te instanceof IEnergySink s) return s.getDemandedEnergy() >= 1 ? 1 : 0;
        if (te instanceof IEnergyReceiver r) {
            // GT cables hand RF machines whole packets only
            int rf = RF.clampInt(RF.euToRf(v));
            return rf > 0 && r.receiveEnergy(e.face, rf, true) >= rf ? 1 : 0;
        }
        return Math.max(0, e.inAmperage);
    }

    /**
     * A machine on the cable (not a battery) would take a packet of {@code voltage} right now. Generators feed the
     * machines first: the connector refuses such packets, and GT hands them to the next consumer.
     */
    @Override
    public boolean othersTake(long voltage) {
        for (CableScanner.Endpoint e : scan.consumers) {
            if (e.producer || e.tile.isInvalid() || e.inVoltage < voltage) continue;
            if (packets(e, voltage, false) > 0) return true;
        }
        return false;
    }

    /** Stops feeding while the cable runs hot, until it has cooled down well below burning. */
    private boolean overheated() {
        if (!GTPower.canReadCables()) return false;
        Node node = pipe.getNode();
        if (paths == null || node == null || node != pathsOf) {
            paths = GTPower.paths(scan.pipes);
            pathsOf = node;
        }
        double heat = GTPower.heat(paths);
        cooling = cooling ? heat > COOL : heat >= HOT;
        return cooling;
    }

    private static long safeMul(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    @Override
    public TileEntity target() {
        return pipe;
    }

    @Override
    public Kind kind() {
        return Kind.GT_CABLE;
    }

    @Override
    public boolean isValid() {
        return !pipe.isInvalid() && CableScanner.isCable(pipe);
    }

    @Override
    public boolean isCable() {
        return true;
    }

    @Override
    public boolean attached() {
        return isConnectedToUs();
    }

    public boolean isConnectedToUs() {
        return (pipe.getConnections() & face.flag) != 0;
    }

    @Override
    public boolean canReceive() {
        return isConnectedToUs() && !scan.consumers.isEmpty();
    }

    @Override
    public boolean canSend() {
        return isConnectedToUs() && !scan.producers.isEmpty();
    }

    /**
     * Machines on the cable: fed, and with generators or batteries on it too, also drained (see the class comment).
     * Only generators or batteries: drained.
     */
    @Override
    public PortRole autoRole() {
        if (!isConnectedToUs()) return PortRole.NONE;
        boolean sources = !scan.producers.isEmpty();
        if (scan.machines() > 0) return sources ? PortRole.BOTH : PortRole.OUTPUT;
        return sources ? PortRole.INPUT : PortRole.NONE;
    }

    @Override
    public boolean hasInputSpec() {
        if (scan.unknownConsumer || scan.consumers.isEmpty()) return false;
        long v = inputVoltage();
        return v > 0 && v < Integer.MAX_VALUE && inputAmperage() > 0;
    }

    @Override
    public long inputVoltage() {
        if (scan.minCableVoltage == Long.MAX_VALUE) return 0;
        return Math.min(scan.minCableVoltage, scan.minConsumerVoltage());
    }

    /**
     * The weakest cable and what the consumers take together. Without GT's cable heat to watch, what the generators
     * on the cable can push is kept free too.
     */
    @Override
    public long inputAmperage() {
        if (scan.minCableAmperage == Long.MAX_VALUE) return 0;
        long cable = scan.minCableAmperage;
        if (!GTPower.canReadCables()) cable -= scan.sumProducerAmperage();
        return Math.max(0, Math.min(cable, scan.sumConsumerAmperage()));
    }

    @Override
    public long outputVoltage() {
        if (scan.minCableVoltage == Long.MAX_VALUE) return 0;
        return Math.min(scan.minCableVoltage, scan.maxProducerVoltage());
    }

    @Override
    public long outputAmperage() {
        if (scan.minCableAmperage == Long.MAX_VALUE) return 0;
        return Math.min(scan.minCableAmperage, scan.sumProducerAmperage());
    }

    @Override
    public long demand() {
        return currentDemand();
    }

    @Override
    public long inject(long maxEU) {
        if (!hasInputSpec() || !isConnectedToUs()) return 0;
        long v = inputVoltage();
        long tick = ServerEvents.tick();
        long amps = Math.min(inputAmperage(), maxEU / v);
        // never more than the demand: GT would hand the rest to machines the generators are about to feed
        if (allowedAt == tick) amps = Math.min(amps, allowed);
        if (amps <= 0) return 0;
        // a cable network nobody has powered yet has no node graph, and injecting into it would do nothing
        if (pipe.getNode() == null) {
            try {
                new GenerateNodeMapPower(pipe);
            } catch (Throwable ignored) {}
        }
        if (overheated()) return 0;
        long used = Math.max(0, Math.min(pipe.injectEnergyUnits(face, v, amps), amps));
        governor.fed(tick, used);
        return used * v;
    }

    @Override
    public boolean isPassiveSender() {
        return true;
    }

    @Override
    public long extract(long maxEU) {
        return 0;
    }

    @Override
    public String displayName() {
        List<String> names = new ArrayList<>();
        for (TileEntity te : endpoints) if (!te.isInvalid()) names.add(Names.of(te));
        return Names.summarize(names);
    }

    @Override
    public int deviceCount() {
        int n = 0;
        for (TileEntity te : endpoints) if (!te.isInvalid()) n++;
        return n;
    }

    @Override
    public int[] devicePos() {
        TileEntity only = null;
        for (TileEntity te : endpoints) {
            if (te.isInvalid()) continue;
            if (only != null) return null;
            only = te;
        }
        return only == null ? null : new int[] { only.xCoord, only.yCoord, only.zCoord };
    }

    @Override
    public void collectSamples(List<MachineSample> out) {
        int max = Config.maxSampledMachinesPerConnector;
        for (CableScanner.Endpoint e : scan.consumers) {
            if (out.size() >= max) return;
            if (e.tile instanceof BaseMetaTileEntity bm && !bm.isInvalid())
                out.add(MachineSample.of(bm, true, e.producer));
        }
        for (CableScanner.Endpoint e : scan.producers) {
            if (out.size() >= max) return;
            if (e.consumer) continue;
            if (e.tile instanceof BaseMetaTileEntity bm && !bm.isInvalid()) out.add(MachineSample.of(bm, false, true));
        }
    }

}
