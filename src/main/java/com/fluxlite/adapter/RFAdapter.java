package com.fluxlite.adapter;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.compat.EnderIOCompat;
import com.fluxlite.compat.Mods;
import com.fluxlite.core.PortRole;
import com.fluxlite.util.Names;

import cofh.api.energy.IEnergyConnection;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;

/**
 * An RF device next to the connector. Rates: see {@link RF}.
 * <p>
 * The CoFH interfaces say little about the direction: many machines (BuildCraft, Forestry, the Galacticraft family)
 * implement both receiver and provider, engines and generators often implement neither and just push into their
 * neighbours. So the direction comes from what the device does: one that pushes energy into the connector, or can be
 * drained while it takes nothing, is a generator; one that takes energy is fed. Once it took energy it stays fed (a
 * full machine with an extractable buffer is not drained); a device with nothing to say keeps its last direction.
 * Capacitor banks and power buffers follow their own face setting (see {@link EnderIOCompat#storageRole}).
 */
public final class RFAdapter implements EnergyAdapter {

    /** How long (ticks) a device that pushed energy into the connector keeps counting as a generator. */
    static final long PUSH_MEMORY = 200;

    private final TileEntity tile;
    private final ForgeDirection face;
    private final RF.Carry carry = new RF.Carry();
    private long lastPush = Long.MIN_VALUE / 2;
    private boolean everTook;
    private PortRole lastRole = PortRole.NONE;

    public RFAdapter(TileEntity tile, ForgeDirection face) {
        this.tile = tile;
        this.face = face;
    }

    /** Receivers, providers, and engines that only implement the connection interface and push by themselves. */
    public static boolean handles(TileEntity te) {
        return te instanceof IEnergyConnection;
    }

    @Override
    public TileEntity target() {
        return tile;
    }

    @Override
    public Kind kind() {
        return Kind.RF;
    }

    @Override
    public boolean isValid() {
        return !tile.isInvalid();
    }

    private boolean connects() {
        return tile instanceof IEnergyConnection c && c.canConnectEnergy(face);
    }

    @Override
    public boolean attached() {
        return connects();
    }

    @Override
    public boolean canReceive() {
        return tile instanceof IEnergyReceiver && connects();
    }

    /** Any RF device may push energy out, whatever it implements. */
    @Override
    public boolean canSend() {
        return connects();
    }

    @Override
    public PortRole autoRole() {
        if (!connects()) return lastRole = PortRole.NONE;
        PortRole io = Mods.ENDERIO ? EnderIOCompat.storageRole(tile, face) : null;
        if (io != null) return lastRole = io;
        boolean receiver = tile instanceof IEnergyReceiver, provider = tile instanceof IEnergyProvider;
        int probe = RF.clampInt(RF.euToRf(Config.rfNominalVoltage));
        boolean takes = receiver && ((IEnergyReceiver) tile).receiveEnergy(face, probe, true) > 0;
        boolean gives = provider && ((IEnergyProvider) tile).extractEnergy(face, probe, true) > 0;
        everTook |= takes;
        return lastRole = decide(receiver, provider, takes, gives, pushedRecently(), everTook, lastRole);
    }

    /**
     * @param receiver implements {@link IEnergyReceiver}
     * @param provider implements {@link IEnergyProvider}
     * @param takes    would accept energy right now
     * @param gives    could be drained right now
     * @param pushed   pushed energy into the connector lately
     * @param everTook accepted energy at some point since the connector found it
     * @param last     the direction decided last time
     */
    static PortRole decide(boolean receiver, boolean provider, boolean takes, boolean gives, boolean pushed,
        boolean everTook, PortRole last) {
        if (pushed || !receiver) return PortRole.INPUT;
        if (!provider || takes) return PortRole.OUTPUT;
        if (gives && !everTook) return PortRole.INPUT;
        return last == PortRole.INPUT ? PortRole.INPUT : PortRole.OUTPUT;
    }

    private long now() {
        World w = tile.getWorldObj();
        return w != null ? w.getTotalWorldTime() : 0;
    }

    private boolean pushedRecently() {
        return now() - lastPush <= PUSH_MEMORY;
    }

    /**
     * The device tried to push energy into the connector while the face was not collecting.
     *
     * @return true when this changes its direction, so the face should be resolved again
     */
    public boolean notePush() {
        boolean news = lastRole != PortRole.INPUT && !pushedRecently();
        lastPush = now();
        return news;
    }

    @Override
    public boolean hasInputSpec() {
        return tile instanceof IEnergyReceiver;
    }

    @Override
    public long inputVoltage() {
        return Config.rfNominalVoltage;
    }

    @Override
    public long inputAmperage() {
        return 1;
    }

    @Override
    public long outputVoltage() {
        return Config.rfNominalVoltage;
    }

    @Override
    public long outputAmperage() {
        return 1;
    }

    @Override
    public String unitTag() {
        return "RF";
    }

    @Override
    public long demand() {
        if (!(tile instanceof IEnergyReceiver r)) return 0;
        int rf = RF.clampInt(RF.euToRf(Config.rfNominalVoltage));
        return Math.min(Config.rfNominalVoltage, RF.rfCost(r.receiveEnergy(face, rf, true)));
    }

    @Override
    public long inject(long maxEU) {
        if (!(tile instanceof IEnergyReceiver r)) return 0;
        return feed(r, face, maxEU);
    }

    /** Feeds an RF receiver at most {@code maxEU}; returns the EU used. */
    static long feed(IEnergyReceiver r, ForgeDirection face, long maxEU) {
        long eu = Math.min(maxEU, Config.rfNominalVoltage);
        int rf = RF.clampInt(RF.euToRf(eu));
        if (rf <= 0) return 0;
        int accepted = r.receiveEnergy(face, rf, false);
        return Math.min(eu, RF.rfCost(accepted));
    }

    /** Devices that push are collected in {@code TileConnector#receiveEnergy}; providers are drained as well. */
    @Override
    public boolean isPassiveSender() {
        return !(tile instanceof IEnergyProvider);
    }

    @Override
    public long extract(long maxEU) {
        if (!(tile instanceof IEnergyProvider p)) return 0;
        long eu = Math.min(maxEU, Config.rfNominalVoltage);
        int rf = RF.clampInt(RF.rfFor(eu));
        if (rf <= 0) return 0;
        return carry.toEu(p.extractEnergy(face, rf, false));
    }

    @Override
    public String displayName() {
        String n = Names.of(tile);
        return n.isEmpty() ? "RF" : n;
    }
}
