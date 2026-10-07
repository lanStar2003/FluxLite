package com.fluxlite.core;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.adapter.EnergyAdapter;

/**
 * Runtime state of one channel of a connector face. Every face has two: EU (index 0-5) and steam (index 6-11).
 * <p>
 * EU has two small buffers: {@link #supply} holds energy taken from the network that is on its way to the device,
 * {@link #collected} holds energy from the device on its way into the network. Both are settled with the wireless
 * network by {@link Settlement}. Steam needs no buffers: it goes straight to and from the steam network, so the
 * amounts are litres instead of EU but the metering is the same.
 */
public final class Port {

    /** Number of channels per connector: six EU faces, then six steam faces. */
    public static final int COUNT = 12;

    public final ForgeDirection side;
    /** 0-5: EU on that face, 6-11: steam on face {@code index - 6}. */
    public final int index;
    public final boolean steam;

    public PortMode mode = PortMode.AUTO;
    public PortRole role = PortRole.NONE;
    public PortStatus status = PortStatus.NO_TARGET;
    public EnergyAdapter adapter;
    public String targetName = "";

    /** Spec used to feed the device (never above its rated input). */
    public long supplyVoltage, supplyAmperage;
    /** Spec of what the device emits (buffer sizing, display). */
    public long collectVoltage, collectAmperage;

    public long supply, supplyCap;
    public long collected, collectCap;

    // metering since the last commit
    public long tickIn, tickOut, tickDemand;
    public boolean tickActive;
    /** IC2 hands back unused energy a tick later; it is subtracted from the next ticks' output. */
    public long outDebt;

    public Port(ForgeDirection side) {
        this(side, false);
    }

    public Port(ForgeDirection side, boolean steam) {
        this.side = side;
        this.steam = steam;
        this.index = side.ordinal() + (steam ? 6 : 0);
    }

    public boolean collects() {
        return status == PortStatus.OK && role.collects();
    }

    public boolean supplies() {
        return status == PortStatus.OK && role.supplies();
    }

    public boolean isWorking() {
        return status == PortStatus.OK && role != PortRole.NONE;
    }

    public void recomputeCapacity() {
        if (steam) {
            supplyCap = collectCap = 0;
            return;
        }
        long periods = (long) Config.settlementPeriod * Config.bufferPeriods;
        supplyCap = role.supplies() ? Math.max(mul(mul(supplyVoltage, supplyAmperage), periods), supplyVoltage) : 0;
        collectCap = role.collects() ? Math.max(mul(mul(collectVoltage, collectAmperage), periods), 2048) : 0;
    }

    /** Passive senders may push at a voltage we did not anticipate; grow so one tick of it fits. */
    public void ensureCollectCapacityFor(long euPerTick) {
        long need = mul(euPerTick, (long) Config.settlementPeriod * Config.bufferPeriods);
        if (need > collectCap) collectCap = need;
    }

    public long collectRoom() {
        return Math.max(0, collectCap - collected);
    }

    public static long mul(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    public void write(NBTTagCompound t) {
        t.setByte("m", (byte) mode.ordinal());
        t.setByte("r", (byte) role.ordinal());
        t.setByte("s", (byte) status.ordinal());
        t.setLong("sb", supply);
        t.setLong("cb", collected);
    }

    public void read(NBTTagCompound t) {
        mode = PortMode.byId(t.getByte("m"));
        role = PortRole.byId(t.getByte("r"));
        status = PortStatus.byId(t.getByte("s"));
        supply = Math.max(0, t.getLong("sb"));
        collected = Math.max(0, t.getLong("cb"));
    }
}
