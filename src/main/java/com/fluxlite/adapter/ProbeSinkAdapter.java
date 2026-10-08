package com.fluxlite.adapter;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.util.Names;

import gregtech.api.interfaces.tileentity.IEnergyConnected;

/**
 * A GT EU device that is neither a GT machine nor one of the known sinks: some other mod's block that takes EU through
 * GT's {@link IEnergyConnected}. What it can do is detected from the tile itself:
 * <ul>
 * <li>Public {@code getInputVoltage()}, and optionally {@code getInputAmperage()}, {@code getStoredEU()} and
 * {@code getEUCapacity()} (the names GT's own energy containers use): the device says what it takes, and is fed
 * exactly that.</li>
 * <li>Otherwise it is fed small packets at {@link Config#pendingVoltage}, as many as it accepts; the face shows
 * "awaiting adaptation" ({@link #pending}) until FluxLite learns the device properly.</li>
 * </ul>
 * Packets it does not take bounce back: {@code injectEnergyUnits} answers how many amperes it accepted.
 */
public final class ProbeSinkAdapter implements EnergyAdapter {

    /** What a tile class tells about itself; one lookup per class. */
    private static final class Spec {

        final Method voltage, amperage, stored, capacity;

        Spec(Class<?> c) {
            voltage = getter(c, "getInputVoltage");
            amperage = getter(c, "getInputAmperage");
            stored = getter(c, "getStoredEU");
            capacity = getter(c, "getEUCapacity");
        }

        private static Method getter(Class<?> c, String name) {
            try {
                Method m = c.getMethod(name);
                Class<?> r = m.getReturnType();
                return r == long.class || r == int.class ? m : null;
            } catch (NoSuchMethodException | SecurityException e) {
                return null;
            }
        }
    }

    private static final Map<Class<?>, Spec> SPECS = new ConcurrentHashMap<>();

    private final TileEntity tile;
    private final IEnergyConnected target;
    private final ForgeDirection face;
    private final Spec spec;

    public ProbeSinkAdapter(TileEntity tile, ForgeDirection face) {
        this.tile = tile;
        this.target = (IEnergyConnected) tile;
        this.face = face;
        this.spec = SPECS.computeIfAbsent(tile.getClass(), Spec::new);
    }

    private long read(Method m, long fallback) {
        if (m == null) return fallback;
        try {
            return ((Number) m.invoke(tile)).longValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    @Override
    public Kind kind() {
        return Kind.GT_SINK;
    }

    @Override
    public TileEntity target() {
        return tile;
    }

    @Override
    public boolean isValid() {
        return !tile.isInvalid();
    }

    @Override
    public boolean canReceive() {
        return target.inputEnergyFrom(face);
    }

    @Override
    public boolean canSend() {
        return target.outputsEnergyTo(face);
    }

    /** True while the device has not told what it takes: fed at a safe voltage, shown as awaiting adaptation. */
    @Override
    public boolean pending() {
        return spec.voltage == null || read(spec.voltage, 0) <= 0;
    }

    @Override
    public boolean hasInputSpec() {
        // a device that does not tell gets packets small enough for any GT machine behind it
        return true;
    }

    @Override
    public long inputVoltage() {
        return pending() ? Math.max(1, Config.pendingVoltage) : read(spec.voltage, Config.pendingVoltage);
    }

    @Override
    public long inputAmperage() {
        long a = pending() ? Config.pendingAmperage : read(spec.amperage, Config.pendingAmperage);
        return Math.max(1, a);
    }

    @Override
    public long outputVoltage() {
        return 0;
    }

    @Override
    public long outputAmperage() {
        return 0;
    }

    @Override
    public long demand() {
        long most = Packets.most(inputVoltage(), inputAmperage());
        if (spec.stored == null || spec.capacity == null) return most;
        long room = read(spec.capacity, 0) - read(spec.stored, 0);
        return Math.max(0, Math.min(most, room));
    }

    @Override
    public long inject(long maxEU) {
        long eu = Math.min(maxEU, demand()), v = inputVoltage(), used = 0;
        long amps = Packets.whole(eu, v, inputAmperage());
        if (amps > 0) used += target.injectEnergyUnits(face, v, amps) * v;
        long rest = Packets.rest(eu - used, v);
        if (rest > 0 && used + rest <= eu) used += target.injectEnergyUnits(face, rest, 1) * rest;
        return used;
    }

    @Override
    public boolean isPassiveSender() {
        return true;
    }

    @Override
    public long extract(long maxEU) {
        return 0;
    }

    @Override
    public String displayName() {
        String n = Names.of(tile);
        return n.isEmpty() ? tile.getClass()
            .getSimpleName() : n;
    }
}
