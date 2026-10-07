package com.fluxlite.adapter;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * A GT machine, hatch, battery buffer or any other {@link IEnergyConnected} directly next to the connector.
 * <p>
 * Feeding works like GT's wireless energy hatch: the target's buffer is topped up to exactly full every tick. Whole
 * amperes go in at its input voltage; the last bit goes in as one smaller packet (GT accepts any voltage up to the
 * rated one). Plain GT feeding only sends whole packets once the buffer has room for one, so the flow would alternate
 * between a burst and nothing; topping up makes the per-tick flow equal to what the machine really uses.
 * <p>
 * Collecting: GT emitters push whole packets into the connector on their own (see
 * {@code TileConnector#injectEnergyUnits}); whatever is left above their minimum stored EU is pulled every tick, so a
 * generator's flow is steady as well. Both together never exceed the rated output per tick.
 */
public final class GTMachineAdapter implements EnergyAdapter {

    private final TileEntity tile;
    private final IEnergyConnected target;
    /** Face of the target that touches the connector. */
    private final ForgeDirection face;

    public GTMachineAdapter(TileEntity tile, ForgeDirection face) {
        this.tile = tile;
        this.target = (IEnergyConnected) tile;
        this.face = face;
    }

    @Override
    public TileEntity target() {
        return tile;
    }

    @Override
    public Kind kind() {
        return Kind.GT_MACHINE;
    }

    @Override
    public boolean isValid() {
        return !tile.isInvalid();
    }

    @Override
    public boolean canReceive() {
        return target.inputEnergyFrom(face, false);
    }

    @Override
    public boolean canSend() {
        return target.outputsEnergyTo(face, false);
    }

    @Override
    public boolean hasInputSpec() {
        if (!(target instanceof IBasicEnergyContainer c)) return false;
        if (target instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() == null) return false;
        // non-electric GT machines report Integer.MAX_VALUE here
        long v = c.getInputVoltage();
        return v > 0 && v < Integer.MAX_VALUE && c.getInputAmperage() > 0;
    }

    @Override
    public long inputVoltage() {
        return target instanceof IBasicEnergyContainer c ? c.getInputVoltage() : 0;
    }

    @Override
    public long inputAmperage() {
        return target instanceof IBasicEnergyContainer c ? c.getInputAmperage() : 0;
    }

    @Override
    public long outputVoltage() {
        return target instanceof IBasicEnergyContainer c ? c.getOutputVoltage() : 0;
    }

    @Override
    public long outputAmperage() {
        return target instanceof IBasicEnergyContainer c ? c.getOutputAmperage() : 0;
    }

    @Override
    public long demand() {
        if (!(target instanceof IBasicEnergyContainer c)) return 0;
        long v = c.getInputVoltage();
        if (v <= 0 || v >= Integer.MAX_VALUE) return 0;
        long free = c.getEUCapacity() - c.getStoredEU();
        if (free <= 0) return 0;
        return Math.min(free, mul(v, c.getInputAmperage()));
    }

    @Override
    public long inject(long maxEU) {
        if (!hasInputSpec() || !(target instanceof IBasicEnergyContainer c)) return 0;
        if (!target.inputEnergyFrom(face)) return 0;
        long v = inputVoltage();
        long maxAmps = inputAmperage();
        long budget = Math.min(maxEU, c.getEUCapacity() - c.getStoredEU());
        if (budget <= 0) return 0;
        long used = 0, amps = 0;
        long whole = Math.min(maxAmps, budget / v);
        if (whole > 0) {
            amps = Math.max(0, Math.min(target.injectEnergyUnits(face, v, whole), whole));
            used = amps * v;
        }
        // the remainder as one smaller packet, so the buffer ends up exactly full
        long rest = Math.min(budget - used, c.getEUCapacity() - c.getStoredEU());
        if (rest > 0 && rest < v && amps < maxAmps && target.injectEnergyUnits(face, rest, 1) > 0) used += rest;
        return used;
    }

    @Override
    public boolean isPassiveSender() {
        // GT emitters push by themselves; the leftovers are pulled (see #extract)
        return false;
    }

    @Override
    public long extract(long maxEU) {
        if (maxEU <= 0 || !(target instanceof IBasicEnergyContainer c)) return 0;
        if (!target.outputsEnergyTo(face)) return 0;
        long keep = 0;
        if (target instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MetaTileEntity mte)
            keep = Math.max(0, mte.getMinimumStoredEU());
        long take = Math.min(maxEU, c.getStoredEU() - keep);
        if (take <= 0) return 0;
        return c.decreaseStoredEnergyUnits(take, false) ? take : 0;
    }

    private static long mul(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    @Override
    public String displayName() {
        if (target instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte != null) {
                try {
                    String n = mte.getLocalName();
                    if (n != null && !n.isEmpty()) return n;
                } catch (Throwable ignored) {}
            }
        }
        return tile.getBlockType() != null ? tile.getBlockType()
            .getLocalizedName() : "GT";
    }
}
