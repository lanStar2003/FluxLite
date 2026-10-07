package com.fluxlite.adapter;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.compat.EnderIOCompat;
import com.fluxlite.compat.Mods;
import com.fluxlite.core.CableScanner;
import com.fluxlite.tile.TileConnector;

import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.metatileentity.BaseMetaPipeEntity;

/**
 * Picks the adapter for whatever is next to a connector face. Many devices speak several APIs; the order prefers the
 * one that tells exactly how much the device takes, without converting: GT machines, then cables, then the
 * mod-specific sinks, IC2, RF, and last other GT sinks (which are only drained, see {@link GTMachineAdapter}).
 */
public final class Adapters {

    private Adapters() {}

    /** @return a new adapter, or null when the neighbour has nothing to do with energy */
    public static EnergyAdapter create(TileConnector connector, ForgeDirection side, TileEntity te) {
        if (te == null || te.isInvalid() || te instanceof TileConnector) return null;
        ForgeDirection face = side.getOpposite();
        if (te instanceof BaseMetaPipeEntity pipe) {
            return CableScanner.isCable(te) ? new GTCableAdapter(pipe, connector, side) : null;
        }
        if (te instanceof IEnergyConnected && te instanceof IBasicEnergyContainer)
            return new GTMachineAdapter(te, face);
        if (IC2CableAdapter.handles(te)) return new IC2CableAdapter(te, connector, face);
        if (Mods.ENDERIO && EnderIOCompat.isPowerConduit(te)) return new EnderIOConduitAdapter(te, connector, side);
        if (te instanceof IEnergyConnected) {
            EnergyAdapter sink = GTSinkAdapter.create(te, face);
            if (sink != null) return sink;
        }
        if (IC2Adapter.handles(te)) return new IC2Adapter(te, connector, face);
        if (RFAdapter.handles(te)) return new RFAdapter(te, face);
        if (te instanceof IEnergyConnected) return new GTMachineAdapter(te, face);
        return null;
    }

    /** True when {@code adapter} was built for exactly this tile and the tile is still alive. */
    public static boolean matches(EnergyAdapter adapter, TileEntity te) {
        return adapter != null && te != null && adapter.target() == te && adapter.isValid();
    }
}
