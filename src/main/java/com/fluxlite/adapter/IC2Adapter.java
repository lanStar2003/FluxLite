package com.fluxlite.adapter;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;

/**
 * An IC2 machine or generator next to the connector. Energy moves through the IC2 energy net (the connector is
 * registered there as sink and source), so this adapter only decides the direction and the safe tier.
 */
public final class IC2Adapter implements EnergyAdapter {

    private final TileEntity tile;
    private final TileEntity connector;
    private final ForgeDirection face;

    public IC2Adapter(TileEntity tile, TileEntity connector, ForgeDirection face) {
        this.tile = tile;
        this.connector = connector;
        this.face = face;
    }

    public static boolean handles(TileEntity te) {
        return te instanceof IEnergySink || te instanceof IEnergySource;
    }

    /** EU/packet of an IC2 tier: 1 = 32, 2 = 128, ... */
    public static long tierVoltage(int tier) {
        if (tier < 0) return 0;
        if (tier >= 14) return Integer.MAX_VALUE;
        return 8L << (2 * tier);
    }

    @Override
    public TileEntity target() {
        return tile;
    }

    @Override
    public Kind kind() {
        return Kind.IC2;
    }

    @Override
    public boolean isValid() {
        return !tile.isInvalid();
    }

    @Override
    public boolean canReceive() {
        return tile instanceof IEnergySink s && s.acceptsEnergyFrom(connector, face);
    }

    @Override
    public boolean canSend() {
        return tile instanceof IEnergySource s && s.emitsEnergyTo(connector, face);
    }

    @Override
    public boolean hasInputSpec() {
        return ic2SafeTier() >= 0;
    }

    @Override
    public int ic2SafeTier() {
        return tile instanceof IEnergySink s ? Math.min(13, s.getSinkTier()) : -1;
    }

    @Override
    public long inputVoltage() {
        return tierVoltage(ic2SafeTier());
    }

    @Override
    public long inputAmperage() {
        return 4;
    }

    @Override
    public long outputVoltage() {
        return tile instanceof IEnergySource s ? tierVoltage(s.getSourceTier()) : 0;
    }

    @Override
    public long outputAmperage() {
        return 1;
    }

    @Override
    public long demand() {
        if (!(tile instanceof IEnergySink s)) return 0;
        double d = s.getDemandedEnergy();
        return d <= 0 ? 0 : (long) Math.min(d, (double) Long.MAX_VALUE / 4);
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
        return tile.getBlockType() != null ? tile.getBlockType()
            .getLocalizedName() : "IC2";
    }
}
