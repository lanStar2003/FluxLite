package com.fluxlite.compat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.graphs.paths.NodePath;
import gregtech.api.graphs.paths.PowerNodePath;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * What GT's own energy code decides but does not expose: how many packets a machine still takes this tick, and how
 * close a cable is to burning. Read by reflection; when a field is missing the answers fall back to safe guesses.
 * <p>
 * A machine takes at most its amperage between two of its own ticks ({@code mAcceptedAmperes}). A cable path keeps a
 * leaky bucket of amperes ({@code mAmps}, drained by its rating every tick) and burns once it holds more than 40 ticks
 * worth of its rating.
 */
public final class GTPower {

    /** GT sets cables on fire above this many ticks worth of their rating. */
    public static final double BURN = 40;

    private static final Field ACCEPTED = field(BaseMetaTileEntity.class, "mAcceptedAmperes");
    private static final Field PATH_AMPS = field(PowerNodePath.class, "mAmps");
    private static final Field PATH_MAX = field(PowerNodePath.class, "mMaxAmps");
    private static final Field PATH_TICK = field(PowerNodePath.class, "mTick");

    private GTPower() {}

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when what a machine already took this tick can be read (otherwise it is taken as nothing). */
    public static boolean canReadMachines() {
        return ACCEPTED != null;
    }

    /** True when the cable bucket can be read, so {@link #heat} means something. */
    public static boolean canReadCables() {
        return PATH_AMPS != null && PATH_MAX != null && PATH_TICK != null;
    }

    /**
     * Packets of {@code voltage} the machine takes right now through {@code face}, the way its
     * {@code injectEnergyUnits} counts them; 0 when it would not take one (or would explode).
     */
    public static long acceptAmps(BaseMetaTileEntity bm, ForgeDirection face, long voltage) {
        if (voltage <= 0 || bm.isInvalid() || !bm.inputEnergyFrom(face)) return 0;
        long cap = bm.getEUCapacity(), stored = bm.getStoredEU();
        if (stored >= cap || voltage > bm.getInputVoltage()) return 0;
        long max = bm.getInputAmperage() - accepted(bm);
        return max <= 0 ? 0 : Math.min(max, 1 + (cap - stored) / voltage);
    }

    private static long accepted(BaseMetaTileEntity bm) {
        if (ACCEPTED == null) return 0;
        try {
            return ACCEPTED.getLong(bm);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Less than half full: worth topping up while generators on the same cable cannot keep up. */
    public static boolean low(BaseMetaTileEntity bm) {
        long cap = bm.getEUCapacity();
        return cap > 0 && bm.getStoredEU() * 2 < cap;
    }

    /** Every power path the cables belong to, once each. */
    public static Set<PowerNodePath> paths(List<BaseMetaPipeEntity> cables) {
        Set<PowerNodePath> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BaseMetaPipeEntity c : cables) {
            if (c.isInvalid()) continue;
            NodePath p = c.getNodePath();
            if (p instanceof PowerNodePath pp) out.add(pp);
        }
        return out;
    }

    /** The hottest path, in ticks of its own rating (0 = cold, {@link #BURN} = on fire). */
    public static double heat(Set<PowerNodePath> paths) {
        if (!canReadCables() || paths.isEmpty()) return 0;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return 0;
        int now = server.getTickCounter();
        double hottest = 0;
        try {
            for (PowerNodePath p : paths) {
                long max = PATH_MAX.getLong(p);
                if (max <= 0) continue;
                long amps = bucket(PATH_AMPS.getLong(p), max, PATH_TICK.getInt(p), now);
                hottest = Math.max(hottest, (double) amps / max);
            }
        } catch (Throwable t) {
            return 0;
        }
        return hottest;
    }

    /** What is left in a bucket last touched at {@code tick}: GT drains it by the rating per tick that passed. */
    static long bucket(long amps, long max, int tick, int now) {
        long passed = (long) now - tick;
        if (passed < 0 || passed > 100) return 0;
        return Math.max(0, amps - max * passed);
    }
}
