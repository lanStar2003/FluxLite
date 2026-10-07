package com.fluxlite.adapter;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.compat.AECompat;
import com.fluxlite.compat.GTSinks;
import com.fluxlite.compat.RailcraftCompat;
import com.fluxlite.util.Names;

import cofh.api.energy.IEnergyConnection;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

/**
 * A GT EU sink that is not a GT machine, next to the connector (see {@link GTSinks}). Packets go straight in, sized
 * to what the device still takes, so its buffer is topped up every tick like a GT machine's.
 * <ul>
 * <li>AE2 (controller, energy acceptor, charger, ...): one packet of exactly the free room in its buffer; AE2 takes
 * any voltage. Better than its RF side, which loses a tenth to GTNH's conversion rates.</li>
 * <li>Railcraft electric feeder and energy loader: packets at their IC2 tier voltage, one per call, each smaller than
 * what they still need (that is all they accept).</li>
 * </ul>
 */
public final class GTSinkAdapter implements EnergyAdapter {

    /** Railcraft feeders take one packet per call; at most this many calls per tick. */
    private static final int MAX_CALLS = 16;

    private final TileEntity tile;
    private final IEnergyConnected target;
    private final ForgeDirection face;
    private final boolean ae;

    private GTSinkAdapter(TileEntity tile, ForgeDirection face, boolean ae) {
        this.tile = tile;
        this.target = (IEnergyConnected) tile;
        this.face = face;
        this.ae = ae;
    }

    /** @return an adapter for a known sink, null for anything else */
    public static GTSinkAdapter create(TileEntity te, ForgeDirection face) {
        if (GTSinks.isAE(te)) return new GTSinkAdapter(te, face, true);
        if (GTSinks.isRailcraft(te)) return new GTSinkAdapter(te, face, false);
        return null;
    }

    @Override
    public TileEntity target() {
        return tile;
    }

    @Override
    public Kind kind() {
        return Kind.GT_SINK;
    }

    @Override
    public boolean isValid() {
        return !tile.isInvalid();
    }

    @Override
    public boolean canReceive() {
        // AE2 tiles only take power on some sides (its RF side knows which)
        if (ae && tile instanceof IEnergyConnection c && !c.canConnectEnergy(face)) return false;
        return target.inputEnergyFrom(face);
    }

    @Override
    public boolean canSend() {
        return false;
    }

    @Override
    public boolean hasInputSpec() {
        return true;
    }

    @Override
    public long inputVoltage() {
        return ae ? Config.rfNominalVoltage : Math.max(1, IC2Adapter.tierVoltage(RailcraftCompat.sinkTier(tile)));
    }

    @Override
    public long inputAmperage() {
        return ae ? 1 : MAX_CALLS;
    }

    @Override
    public long outputVoltage() {
        return 0;
    }

    @Override
    public long outputAmperage() {
        return 0;
    }

    @Override
    public String unitTag() {
        return ae ? "" : null;
    }

    @Override
    public long demand() {
        if (ae) return Math.min(Config.rfNominalVoltage, AECompat.freeEU(tile));
        double d = RailcraftCompat.demanded(tile);
        if (!(d > 0)) return 0;
        return (long) Math.min(Math.floor(d), (double) inputVoltage() * MAX_CALLS);
    }

    @Override
    public long inject(long maxEU) {
        if (ae) {
            long eu = Math.min(maxEU, demand());
            return eu > 0 && target.injectEnergyUnits(face, eu, 1) > 0 ? eu : 0;
        }
        long v = inputVoltage(), used = 0;
        for (int i = 0; i < MAX_CALLS; i++) {
            double need = RailcraftCompat.demanded(tile);
            // the feeder only takes a packet smaller than what it still needs
            long packet = Math.min(Math.min(v, maxEU - used), (long) Math.ceil(need) - 1);
            if (packet <= 0 || target.injectEnergyUnits(face, packet, 1) <= 0) break;
            used += packet;
        }
        return used;
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
        String n = Names.of(tile);
        return n.isEmpty() ? (ae ? "AE2" : "Railcraft") : n;
    }
}
