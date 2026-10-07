package com.fluxlite.compat;

import cpw.mods.fml.common.Loader;

/**
 * Optional mods FluxLite has dedicated adapters for. Classes that use a mod's own types are only touched when that mod
 * is loaded.
 */
public final class Mods {

    public static final boolean ENDERIO = loaded("EnderIO");
    public static final boolean AE2 = loaded("appliedenergistics2");
    public static final boolean RAILCRAFT = loaded("Railcraft");

    private Mods() {}

    private static boolean loaded(String id) {
        try {
            return Loader.isModLoaded(id);
        } catch (Throwable t) {
            // unit tests run without FML
            return false;
        }
    }

    /** {@code te} is an instance of the named class; false when that class does not exist. */
    static boolean isInstance(Object te, Class<?> cls) {
        return cls != null && cls.isInstance(te);
    }

    static Class<?> find(String name) {
        try {
            return Class.forName(name, false, Mods.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }
}
