package com.fluxlite.adapter;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.core.CableScanner;
import com.fluxlite.core.MachineSample;
import com.fluxlite.tile.TileConnector;

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
    private long scannedAt = Long.MIN_VALUE;
    private long cachedDemand;

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
        if (scan != null && now - scannedAt < Config.cableRescanInterval) {
            updateDemand();
            return;
        }
        scannedAt = now;
        scan = CableScanner.scan(pipe, connector, side);
        updateDemand();
    }

    public void invalidateScan() {
        scannedAt = Long.MIN_VALUE;
    }

    private void updateDemand() {
        long sum = 0;
        for (CableScanner.Endpoint e : scan.consumers) {
            if (e.tile.isInvalid()) continue;
            if (e.tile instanceof IBasicEnergyContainer c) {
                long v = c.getInputVoltage();
                if (v <= 0 || v >= Integer.MAX_VALUE) continue;
                long free = c.getEUCapacity() - c.getStoredEU();
                if (free > 0) sum += Math.min(c.getInputAmperage(), (free + v - 1) / v) * v;
            } else {
                sum += Math.max(0, e.inAmperage) * Math.max(0, Math.min(e.inVoltage, Integer.MAX_VALUE));
            }
        }
        long cap = safeMul(inputVoltage(), inputAmperage());
        cachedDemand = Math.min(sum, cap);
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
        return cachedDemand;
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
        String base = pipe.getMetaTileEntity() != null ? pipe.getMetaTileEntity()
            .getLocalName() : "Cable";
        return base + " ×" + scan.cables;
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
