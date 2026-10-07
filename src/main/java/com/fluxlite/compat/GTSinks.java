package com.fluxlite.compat;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IEnergyConnected;

/**
 * GT EU sinks that are not GT machines (an {@link IEnergyConnected} without GT's energy container), from mods known to
 * take packets of any voltage: AE2 power tiles, Railcraft feeders, OpenModularTurrets bases. Other such sinks are not
 * fed, because nothing says what voltage they survive (an AE2 P2P tunnel, for one, passes it on to whatever machine
 * sits at its far end).
 */
public final class GTSinks {

    private static final Class<?> OMT_BASE = Mods.find("openmodularturrets.tileentity.turretbase.TurretBase");

    private GTSinks() {}

    public static boolean isAE(TileEntity te) {
        return Mods.AE2 && AECompat.isPowerSink(te);
    }

    public static boolean isRailcraft(TileEntity te) {
        return Mods.RAILCRAFT && RailcraftCompat.isPowerSink(te);
    }

    /** A known sink: feeding it at any voltage does no harm. */
    public static boolean anyVoltage(TileEntity te) {
        return te instanceof IEnergyConnected && (isAE(te) || isRailcraft(te) || Mods.isInstance(te, OMT_BASE));
    }

    /** Packets it may take per tick on a GT cable. */
    public static long amperage(TileEntity te) {
        return isAE(te) ? 1 << 16 : 1;
    }

    /**
     * EU it takes this tick from packets of {@code voltage} on a GT cable: AE2 takes every packet that fits whole,
     * Railcraft one packet while it needs more than that, a turret base one while it is not full.
     *
     * @return -1 when the sink is not a known one
     */
    public static long demand(TileEntity te, ForgeDirection face, long voltage) {
        if (voltage <= 0) return 0;
        if (isAE(te)) return AECompat.freeEU(te) / voltage * voltage;
        if (isRailcraft(te)) return RailcraftCompat.demanded(te) > voltage ? voltage : 0;
        if (Mods.isInstance(te, OMT_BASE)) return ((IEnergyConnected) te).inputEnergyFrom(face) ? voltage : 0;
        return -1;
    }
}
