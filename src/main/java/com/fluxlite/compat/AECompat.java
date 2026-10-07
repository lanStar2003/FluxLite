package com.fluxlite.compat;

import net.minecraft.tileentity.TileEntity;

import appeng.api.config.PowerUnits;
import appeng.api.networking.energy.IAEPowerStorage;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

/** Applied Energistics 2 specifics. Only called when {@link Mods#AE2} is true. */
public final class AECompat {

    private AECompat() {}

    /**
     * A powered AE2 tile that takes GT EU: controller, energy acceptor, charger, inscriber, ME chest. They take any
     * voltage and put it in a small internal buffer the ME network draws from.
     */
    public static boolean isPowerSink(TileEntity te) {
        return te instanceof IEnergyConnected && te instanceof IAEPowerStorage;
    }

    /** EU that fits into the tile's buffer right now. */
    public static long freeEU(TileEntity te) {
        IAEPowerStorage s = (IAEPowerStorage) te;
        double free = s.getAEMaxPower() - s.getAECurrentPower();
        if (!(free > 0)) return 0;
        double eu = PowerUnits.AE.convertTo(PowerUnits.EU, free);
        return eu >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(eu);
    }

    /** EU the whole buffer holds. */
    public static long capacityEU(TileEntity te) {
        double eu = PowerUnits.AE.convertTo(PowerUnits.EU, ((IAEPowerStorage) te).getAEMaxPower());
        return eu >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.max(0, eu);
    }
}
