package com.fluxlite.adapter;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.core.MachineSample;
import com.fluxlite.tile.TileConnector;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;

/**
 * An IC2 cable. The cable network is walked to find its sinks and sources; energy itself moves through the IC2
 * energy net. The source tier offered to the net is the lowest tier any sink or cable on it can take.
 * <p>
 * A cable with both generators and machines is only fed (never drained): IC2 computes flows asynchronously, so the
 * connector could otherwise end up feeding itself.
 */
public final class IC2CableAdapter implements EnergyAdapter {

    private final TileEntity cable;
    private final TileConnector connector;
    private final ForgeDirection face;
    private int sinks, sources, cables, safeTier = 13, maxSourceTier;
    private long scannedAt = Long.MIN_VALUE;

    public IC2CableAdapter(TileEntity cable, TileConnector connector, ForgeDirection face) {
        this.cable = cable;
        this.connector = connector;
        this.face = face;
        refresh();
    }

    public static boolean handles(TileEntity te) {
        return te instanceof IEnergyConductor;
    }

    @Override
    public void refresh() {
        long now = connector.getWorldObj()
            .getTotalWorldTime();
        if (now - scannedAt < Config.cableRescanInterval) return;
        scannedAt = now;
        scan();
    }

    private void scan() {
        sinks = sources = cables = 0;
        safeTier = 13;
        maxSourceTier = 0;
        World w = cable.getWorldObj();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<TileEntity> queue = new ArrayDeque<>();
        queue.add(cable);
        seen.add(MachineSample.posKey(cable));
        while (!queue.isEmpty() && seen.size() <= Config.cableScanMaxNodes) {
            TileEntity te = queue.poll();
            IEnergyConductor c = (IEnergyConductor) te;
            cables++;
            safeTier = Math.min(safeTier, tierOf(c.getConductorBreakdownEnergy() - 1));
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int x = te.xCoord + d.offsetX, y = te.yCoord + d.offsetY, z = te.zCoord + d.offsetZ;
                if (y < 0 || y >= w.getHeight()
                    || !w.getChunkProvider()
                        .chunkExists(x >> 4, z >> 4))
                    continue;
                TileEntity n = w.getTileEntity(x, y, z);
                if (n == null || n instanceof TileConnector) continue;
                if (!seen.add(MachineSample.posKey(n))) continue;
                ForgeDirection back = d.getOpposite();
                if (n instanceof IEnergyConductor nc) {
                    if (c.emitsEnergyTo(n, d) && nc.acceptsEnergyFrom(te, back)) queue.add(n);
                    continue;
                }
                if (n instanceof IEnergySink s && s.acceptsEnergyFrom(te, back)) {
                    sinks++;
                    safeTier = Math.min(safeTier, Math.min(13, s.getSinkTier()));
                }
                if (n instanceof IEnergySource s && s.emitsEnergyTo(te, back)) {
                    sources++;
                    maxSourceTier = Math.max(maxSourceTier, s.getSourceTier());
                }
            }
        }
    }

    private static int tierOf(double eu) {
        try {
            return Math.max(0, EnergyNet.instance.getTierFromPower(eu));
        } catch (Throwable t) {
            int tier = 0;
            while (tier < 13 && IC2Adapter.tierVoltage(tier) < eu) tier++;
            return tier;
        }
    }

    @Override
    public TileEntity target() {
        return cable;
    }

    @Override
    public Kind kind() {
        return Kind.IC2;
    }

    @Override
    public boolean isValid() {
        return !cable.isInvalid();
    }

    @Override
    public boolean isCable() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return sinks > 0 && ((IEnergyConductor) cable).acceptsEnergyFrom(connector, face);
    }

    @Override
    public boolean canSend() {
        // mixed networks are only fed, see class comment
        return sources > 0 && sinks == 0 && ((IEnergyConductor) cable).emitsEnergyTo(connector, face);
    }

    @Override
    public boolean hasInputSpec() {
        return sinks > 0;
    }

    @Override
    public int ic2SafeTier() {
        return safeTier;
    }

    @Override
    public long inputVoltage() {
        return IC2Adapter.tierVoltage(safeTier);
    }

    @Override
    public long inputAmperage() {
        return Math.max(1, sinks);
    }

    @Override
    public long outputVoltage() {
        return IC2Adapter.tierVoltage(maxSourceTier);
    }

    @Override
    public long outputAmperage() {
        return Math.max(1, sources);
    }

    @Override
    public long demand() {
        return 0;
    }

    @Override
    public long inject(long maxEU) {
        return 0;
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
    public boolean viaIc2() {
        return true;
    }

    @Override
    public String displayName() {
        String n = cable.getBlockType() != null ? cable.getBlockType()
            .getLocalizedName() : "IC2";
        return n + " ×" + cables;
    }
}
