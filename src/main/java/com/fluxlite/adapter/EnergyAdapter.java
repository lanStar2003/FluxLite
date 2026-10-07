package com.fluxlite.adapter;

import java.util.List;

import net.minecraft.tileentity.TileEntity;

import com.fluxlite.core.MachineSample;

/**
 * Uniform view of whatever sits next to a connector face. All amounts are EU; RF adapters convert internally. Steam
 * adapters use the same interface with litres instead of EU (and no voltage).
 */
public interface EnergyAdapter {

    enum Kind {
        GT_MACHINE,
        GT_CABLE,
        IC2,
        RF,
        STEAM
    }

    Kind kind();

    /** The neighbour tile this adapter wraps. */
    TileEntity target();

    /** The neighbour tile is still the one this adapter was made for. */
    boolean isValid();

    /** False when the neighbour is there but not joined to this face (e.g. a GT cable not connected yet). */
    default boolean attached() {
        return true;
    }

    /** True for cables (several devices behind one face). */
    default boolean isCable() {
        return false;
    }

    /** The device can accept energy through this face. */
    boolean canReceive();

    /** The device can give energy through this face. */
    boolean canSend();

    /** True when the voltage/amperage needed to feed the device safely could be read. */
    boolean hasInputSpec();

    /** Max voltage that may be injected without exploding the target. */
    long inputVoltage();

    /** Max amperes per tick the target accepts through this face. */
    long inputAmperage();

    /** Output voltage of the device (for buffer sizing / tier display). */
    long outputVoltage();

    long outputAmperage();

    /** EU the device would take right now (one tick). */
    long demand();

    /**
     * Push up to {@code maxEU} into the device, packetised at {@link #inputVoltage()}.
     *
     * @return EU actually taken out of the supply buffer
     */
    long inject(long maxEU);

    /**
     * True when energy only arrives by the device pushing it (IC2 energy net, generators on a GT cable); false when
     * {@link #extract} is called every tick (GT machines next to the connector, RF).
     */
    boolean isPassiveSender();

    /** Pull up to {@code maxEU} out of the device. */
    long extract(long maxEU);

    /** True when energy is exchanged through the IC2 energy net instead of direct calls. */
    default boolean viaIc2() {
        return false;
    }

    /** Highest IC2 tier everything on the far side survives (IC2 adapters only). */
    default int ic2SafeTier() {
        return -1;
    }

    /** For a cable: the devices on it ("name", "name ×3", "name +2"); otherwise the neighbour's name. */
    String displayName();

    /** How many devices the face reaches: 1 for a device next to it, the number of endpoints for a cable. */
    default int deviceCount() {
        return 1;
    }

    /** Where the single device behind a cable is (x, y, z), for "locate"; null when there is not exactly one. */
    default int[] devicePos() {
        return null;
    }

    /** Called once per second; cable adapters report the machines behind the cable. */
    default void collectSamples(List<MachineSample> out) {}

    /** Called periodically to refresh cached data (cable scans). */
    default void refresh() {}
}
