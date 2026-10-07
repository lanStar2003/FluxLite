package com.fluxlite.core.registry;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxlite.core.PortMode;
import com.fluxlite.core.PortRole;
import com.fluxlite.core.PortStatus;

/** Snapshot of a face, kept in the registry so offline connectors can still be shown. */
public final class PortInfo {

    public PortMode mode = PortMode.AUTO;
    public PortRole role = PortRole.NONE;
    public PortStatus status = PortStatus.NO_TARGET;
    /** Spec used to feed the device / spec of what the device emits. */
    public long supplyVoltage, supplyAmperage, collectVoltage, collectAmperage;
    public String target = "";
    public boolean cable;

    public boolean isWorking() {
        return status == PortStatus.OK && role != PortRole.NONE;
    }

    /** Voltage shown for this face: what it feeds, or what it receives. */
    public long voltage() {
        return role.supplies() ? supplyVoltage : collectVoltage;
    }

    public void write(NBTTagCompound t) {
        t.setByte("m", (byte) mode.ordinal());
        t.setByte("r", (byte) role.ordinal());
        t.setByte("s", (byte) status.ordinal());
        t.setLong("sv", supplyVoltage);
        t.setLong("sa", supplyAmperage);
        t.setLong("cv", collectVoltage);
        t.setLong("ca", collectAmperage);
        t.setString("t", target == null ? "" : target);
        t.setBoolean("c", cable);
    }

    public void read(NBTTagCompound t) {
        mode = PortMode.byId(t.getByte("m"));
        role = PortRole.byId(t.getByte("r"));
        status = PortStatus.byId(t.getByte("s"));
        supplyVoltage = t.getLong("sv");
        supplyAmperage = t.getLong("sa");
        collectVoltage = t.getLong("cv");
        collectAmperage = t.getLong("ca");
        target = t.getString("t");
        cable = t.getBoolean("c");
    }
}
