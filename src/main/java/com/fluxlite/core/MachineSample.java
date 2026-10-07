package com.fluxlite.core;

import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.util.GTUtility;

/**
 * One reading of a machine reached through a cable. These numbers come from GT's own averages, not from metering, so
 * the control center labels them as sampled.
 */
public final class MachineSample {

    public int x, y, z;
    public String name;
    public int tier;
    public long voltage, amperage;
    public long avgIn, avgOut;
    public long stored, capacity;
    public boolean consumer, producer, active;

    public static MachineSample of(BaseMetaTileEntity te, boolean consumer, boolean producer) {
        MachineSample s = new MachineSample();
        s.x = te.xCoord;
        s.y = te.yCoord;
        s.z = te.zCoord;
        IMetaTileEntity mte = te.getMetaTileEntity();
        String n = null;
        try {
            if (mte != null) n = mte.getLocalName();
        } catch (Throwable ignored) {}
        s.name = n != null ? n : te.getInventoryName();
        s.consumer = consumer;
        s.producer = producer;
        s.voltage = consumer ? te.getInputVoltage() : te.getOutputVoltage();
        s.amperage = consumer ? te.getInputAmperage() : te.getOutputAmperage();
        s.tier = s.voltage > 0 && s.voltage < Integer.MAX_VALUE ? GTUtility.getTier(s.voltage) : 0;
        s.avgIn = te.getAverageElectricInput();
        s.avgOut = te.getAverageElectricOutput();
        s.stored = te.getStoredEU();
        s.capacity = te.getEUCapacity();
        s.active = te.isActive();
        return s;
    }

    public static long posKey(TileEntity te) {
        return posKey(te.xCoord, te.yCoord, te.zCoord);
    }

    public static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public long posKey() {
        return posKey(x, y, z);
    }
}
