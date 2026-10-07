package com.fluxlite.compat;

import net.minecraft.tileentity.TileEntity;

import gregtech.api.interfaces.tileentity.IEnergyConnected;
import mods.railcraft.common.plugins.ic2.ISinkDelegate;

/** Railcraft specifics. Only called when {@link Mods#RAILCRAFT} is true. */
public final class RailcraftCompat {

    private RailcraftCompat() {}

    /**
     * Electric feeder and energy loader: they take GT EU one packet per call, and only while the packet is smaller
     * than what they still need. Their IC2 side is a separate delegate tile the connector never sees.
     */
    public static boolean isPowerSink(TileEntity te) {
        return te instanceof IEnergyConnected && te instanceof ISinkDelegate;
    }

    public static double demanded(TileEntity te) {
        return ((ISinkDelegate) te).getDemandedEnergy();
    }

    public static int sinkTier(TileEntity te) {
        return ((ISinkDelegate) te).getSinkTier();
    }
}
