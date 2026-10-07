package com.fluxlite.compat;

import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.core.PortRole;

import crazypants.enderio.conduit.power.IPowerConduit;
import crazypants.enderio.machine.IIoConfigurable;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.power.IPowerStorage;

/**
 * EnderIO specifics. Only called when {@link Mods#ENDERIO} is true. The conduit bundle and the power buffer are
 * reached by name: their class hierarchy pulls in Mekanism, OpenComputers and EnderCore types FluxLite does not
 * compile against.
 */
public final class EnderIOCompat {

    private static final Class<?> BUNDLE = Mods.find("crazypants.enderio.conduit.IConduitBundle");
    private static final Class<?> BUFFER = Mods.find("crazypants.enderio.machine.buffer.TileBuffer");
    private static final Method GET_CONDUIT = method(BUNDLE, "getConduit", Class.class);

    private EnderIOCompat() {}

    private static Method method(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner == null ? null : owner.getMethod(name, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Any conduit bundle, with or without an energy conduit in it. */
    public static boolean isBundle(TileEntity te) {
        return Mods.isInstance(te, BUNDLE);
    }

    /** The energy conduit in a conduit bundle; null when there is none. */
    public static IPowerConduit powerConduit(TileEntity te) {
        if (GET_CONDUIT == null || !isBundle(te)) return null;
        try {
            return GET_CONDUIT.invoke(te, IPowerConduit.class) instanceof IPowerConduit c ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A conduit bundle that holds an energy conduit. */
    public static boolean isPowerConduit(TileEntity te) {
        return powerConduit(te) != null;
    }

    /** A capacitor bank or power buffer: devices that take energy in and push it out again, per face. */
    public static boolean isStorage(TileEntity te) {
        return (te instanceof IPowerStorage || Mods.isInstance(te, BUFFER)) && te instanceof IIoConfigurable;
    }

    /**
     * What a capacitor bank or power buffer is set to on {@code face} (the Yeta wrench cycles it), seen from the
     * network; null for other tiles. Input-only means the network charges it; output, both and the default drain it
     * into the network, like other storages. A bank next to a connector defaults to output.
     */
    public static PortRole storageRole(TileEntity te, ForgeDirection face) {
        if (!isStorage(te)) return null;
        IoMode mode = ((IIoConfigurable) te).getIoMode(face);
        if (mode == IoMode.DISABLED) return PortRole.NONE;
        return mode == IoMode.PULL ? PortRole.OUTPUT : PortRole.INPUT;
    }
}
