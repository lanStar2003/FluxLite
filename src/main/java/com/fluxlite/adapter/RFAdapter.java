package com.fluxlite.adapter;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;

import cofh.api.energy.IEnergyConnection;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import gregtech.api.GregTechAPI;

/**
 * RF devices. Conversion rates come from GT's own config ({@link GregTechAPI#mEUtoRF} and
 * {@link GregTechAPI#mRFtoEU}, both in percent), so the numbers match GT cables and machines.
 */
public final class RFAdapter implements EnergyAdapter {

    private final TileEntity tile;
    private final ForgeDirection face;
    /** RF pulled but not yet worth a whole EU. */
    private long rfRemainder;

    public RFAdapter(TileEntity tile, ForgeDirection face) {
        this.tile = tile;
        this.face = face;
    }

    public static boolean handles(TileEntity te) {
        return te instanceof IEnergyReceiver || te instanceof IEnergyProvider;
    }

    private static long euToRf(long eu) {
        return eu * Math.max(1, GregTechAPI.mEUtoRF) / 100;
    }

    private static long rfToEuCeil(long rf) {
        long rate = Math.max(1, GregTechAPI.mEUtoRF);
        return (rf * 100 + rate - 1) / rate;
    }

    @Override
    public TileEntity target() {
        return tile;
    }

    @Override
    public Kind kind() {
        return Kind.RF;
    }

    @Override
    public boolean isValid() {
        return !tile.isInvalid();
    }

    private boolean connects() {
        return tile instanceof IEnergyConnection c && c.canConnectEnergy(face);
    }

    @Override
    public boolean canReceive() {
        return tile instanceof IEnergyReceiver && connects();
    }

    @Override
    public boolean canSend() {
        return tile instanceof IEnergyProvider && connects();
    }

    @Override
    public boolean hasInputSpec() {
        return tile instanceof IEnergyReceiver;
    }

    @Override
    public long inputVoltage() {
        return Config.rfNominalVoltage;
    }

    @Override
    public long inputAmperage() {
        return 1;
    }

    @Override
    public long outputVoltage() {
        return Config.rfNominalVoltage;
    }

    @Override
    public long outputAmperage() {
        return 1;
    }

    @Override
    public long demand() {
        if (!(tile instanceof IEnergyReceiver r)) return 0;
        int rf = (int) Math.min(Integer.MAX_VALUE, euToRf(Config.rfNominalVoltage));
        return rfToEuCeil(r.receiveEnergy(face, rf, true));
    }

    @Override
    public long inject(long maxEU) {
        if (!(tile instanceof IEnergyReceiver r)) return 0;
        long eu = Math.min(maxEU, Config.rfNominalVoltage);
        int rf = (int) Math.min(Integer.MAX_VALUE, euToRf(eu));
        if (rf <= 0) return 0;
        int accepted = r.receiveEnergy(face, rf, false);
        return Math.min(eu, rfToEuCeil(accepted));
    }

    @Override
    public boolean isPassiveSender() {
        return false;
    }

    @Override
    public long extract(long maxEU) {
        if (!(tile instanceof IEnergyProvider p)) return 0;
        long rate = Math.max(1, GregTechAPI.mRFtoEU);
        long eu = Math.min(maxEU, Config.rfNominalVoltage);
        long rfWanted = Math.max(0, eu * 100 / rate - rfRemainder);
        int rf = (int) Math.min(Integer.MAX_VALUE, rfWanted);
        if (rf > 0) rfRemainder += p.extractEnergy(face, rf, false);
        long got = rfRemainder * rate / 100;
        rfRemainder -= got * 100 / rate;
        return got;
    }

    @Override
    public String displayName() {
        return tile.getBlockType() != null ? tile.getBlockType()
            .getLocalizedName() : "RF";
    }
}
