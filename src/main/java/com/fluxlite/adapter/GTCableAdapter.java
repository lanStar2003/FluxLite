package com.fluxlite.adapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.core.CableScanner;
import com.fluxlite.core.MachineSample;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.util.Names;

import gregtech.api.graphs.GenerateNodeMapPower;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * A GT cable next to the connector. The whole cable network behind it is scanned (with a node cap) and cached.
 * <p>
 * Feeding voltage is the minimum of the weakest cable and the lowest consumer input voltage, so neither the cable nor
 * any machine on it can be overloaded by our injection. Amperage is capped by the weakest cable and the sum of the
 * consumers. Collecting is passive: generators on the cable push into the connector like into any other consumer.
 */
public final class GTCableAdapter implements EnergyAdapter {

    private final BaseMetaPipeEntity pipe;
    private final TileConnector connector;
    private final ForgeDirection side;
    /** Face of the cable that touches the connector. */
    private final ForgeDirection face;
    private CableScanner.Result scan;
    private List<TileEntity> endpoints = new ArrayList<>();
    private long scannedAt = Long.MIN_VALUE;

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
        if (scan != null && now - scannedAt < Config.cableRescanInterval) return;
        scannedAt = now;
        scan = CableScanner.scan(pipe, connector, side);
        endpoints = endpoints();
    }

    public void invalidateScan() {
        scannedAt = Long.MIN_VALUE;
    }

    /** Every device on the cable once: consumers first, then pure producers. */
    private List<TileEntity> endpoints() {
        List<TileEntity> l = new ArrayList<>();
        Set<TileEntity> seen = new HashSet<>();
        for (CableScanner.Endpoint e : scan.consumers) if (seen.add(e.tile)) l.add(e.tile);
        for (CableScanner.Endpoint e : scan.producers) if (seen.add(e.tile)) l.add(e.tile);
        return l;
    }

    /**
     * What the machines on the cable take this tick, worked out the way GT accepts packets: a machine whose buffer is
     * not full takes one more packet than fits (up to its amperage). Computed every tick: a snapshot taken once per
     * second would count a full packet of demand for every tick until the next snapshot, although the machine only
     * takes one every few ticks, and the face would look under-supplied.
     */
    private long currentDemand() {
        long v = inputVoltage();
        if (v <= 0) return 0;
        long sum = 0;
        for (CableScanner.Endpoint e : scan.consumers) {
            if (e.tile.isInvalid()) continue;
            if (e.tile instanceof IBasicEnergyContainer c) {
                long in = c.getInputVoltage();
                if (in <= 0 || in >= Integer.MAX_VALUE) continue;
                long free = c.getEUCapacity() - c.getStoredEU();
                if (free > 0) sum += Math.min(c.getInputAmperage(), 1 + free / v) * v;
            } else {
                sum += Math.max(0, e.inAmperage) * v;
            }
        }
        return Math.min(sum, safeMul(v, inputAmperage()));
    }

    private static long safeMul(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
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

    @Override
    public long inputAmperage() {
        if (scan.minCableAmperage == Long.MAX_VALUE) return 0;
        return Math.min(scan.minCableAmperage, scan.sumConsumerAmperage());
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
        long amps = Math.min(inputAmperage(), maxEU / v);
        if (amps <= 0) return 0;
        // a cable network nobody has powered yet has no node graph, and injecting into it would do nothing
        if (pipe.getNode() == null) {
            try {
                new GenerateNodeMapPower(pipe);
            } catch (Throwable ignored) {}
        }
        long used = pipe.injectEnergyUnits(face, v, amps);
        return Math.max(0, Math.min(used, amps)) * v;
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
        for (TileEntity te : endpoints) names.add(Names.of(te));
        return Names.summarize(names);
    }

    @Override
    public int deviceCount() {
        return endpoints.size();
    }

    @Override
    public int[] devicePos() {
        if (endpoints.size() != 1) return null;
        TileEntity te = endpoints.get(0);
        return new int[] { te.xCoord, te.yCoord, te.zCoord };
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
