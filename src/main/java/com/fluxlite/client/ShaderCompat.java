package com.fluxlite.client;

import java.lang.reflect.Method;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Whether a shader pack is on (Angelica ships Iris and its API). Read by reflection, so this works without them.
 */
@SideOnly(Side.CLIENT)
public final class ShaderCompat {

    private static final Object API;
    private static final Method IN_USE, SHADOW_PASS;

    static {
        Object api = null;
        Method inUse = null, shadow = null;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = c.getMethod("getInstance")
                .invoke(null);
            inUse = c.getMethod("isShaderPackInUse");
            shadow = c.getMethod("isRenderingShadowPass");
        } catch (Throwable ignored) {}
        API = api;
        IN_USE = inUse;
        SHADOW_PASS = shadow;
    }

    private ShaderCompat() {}

    private static boolean call(Method m) {
        if (API == null || m == null) return false;
        try {
            return (Boolean) m.invoke(API);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean packInUse() {
        return call(IN_USE);
    }

    /** The world is being drawn into the shadow map: things of light like the hologram cast no shadow. */
    public static boolean shadowPass() {
        return call(SHADOW_PASS);
    }
}
