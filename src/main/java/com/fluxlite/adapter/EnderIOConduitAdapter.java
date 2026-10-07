package com.fluxlite.adapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.compat.EnderIOCompat;
import com.fluxlite.core.MachineSample;
import com.fluxlite.core.PortRole;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.util.Names;

import cofh.api.energy.IEnergyConnection;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import crazypants.enderio.conduit.ConnectionMode;
import crazypants.enderio.conduit.power.IPowerConduit;

/**
 * An EnderIO energy conduit next to the connector, handled like a cable: the conduit network is walked to see what is
 * on it.
 * <p>
 * Energy goes into the conduit's buffer and EnderIO hands it out to the machines on the network itself. The
 * connector is a receiver on that network too, so a face either feeds the conduit or takes what it pushes, never
 * both (that would send energy round in circles). The conduit's own setting for the side touching the connector
 * decides first (input: the conduit takes from the connector, output: it gives to it); on the default in/out
 * setting a network with machines on it is fed, one with only generators or capacitor banks is drained. Like IC2
 * cables, a network with both is only fed: its generators feed its machines first, the connector makes up the rest.
 */
public final class EnderIOConduitAdapter implements EnergyAdapter {

    private final TileEntity bundle;
    private final TileConnector connector;
    /** Side of the conduit that touches the connector. */
    private final ForgeDirection face;
    private int consumers, producers;
    private final List<TileEntity> devices = new ArrayList<>();
    /** Devices seen taking energy once; they count as machines from then on, also while full. */
    private final Set<Long> knownConsumers = new HashSet<>();
    /** The conduits walked and the devices on them. */
    private final Set<Long> positions = new HashSet<>();
    private long scannedAt = Long.MIN_VALUE;

    public EnderIOConduitAdapter(TileEntity bundle, TileConnector connector, ForgeDirection side) {
        this.bundle = bundle;
        this.connector = connector;
        this.face = side.getOpposite();
        refresh();
    }

    private IPowerConduit conduit() {
        return EnderIOCompat.powerConduit(bundle);
    }

    private ConnectionMode mode() {
        IPowerConduit c = conduit();
        return c == null || !c.containsExternalConnection(face) ? ConnectionMode.DISABLED : c.getConnectionMode(face);
    }

    @Override
    public void refresh() {
        long now = connector.getWorldObj()
            .getTotalWorldTime();
        if (now - scannedAt < Config.cableRescanInterval) return;
        scannedAt = now;
        scan();
    }

    @Override
    public void invalidateScan() {
        scannedAt = Long.MIN_VALUE;
    }

    @Override
    public boolean covers(long pos) {
        return positions.contains(pos);
    }

    private void scan() {
        consumers = producers = 0;
        devices.clear();
        positions.clear();
        if (conduit() == null) return;
        List<TileEntity> sources = new ArrayList<>();
        World w = bundle.getWorldObj();
        Set<Long> seen = new HashSet<>();
        Set<Long> seenDevices = new HashSet<>();
        ArrayDeque<TileEntity> queue = new ArrayDeque<>();
        queue.add(bundle);
        seen.add(MachineSample.posKey(bundle));
        while (!queue.isEmpty()) {
            TileEntity at = queue.poll();
            IPowerConduit c = EnderIOCompat.powerConduit(at);
            if (c == null) continue;
            for (ForgeDirection d : c.getConduitConnections()) {
                TileEntity n = neighbour(w, at, d);
                if (n == null || seen.size() >= Config.cableScanMaxNodes) continue;
                if (seen.add(MachineSample.posKey(n))) queue.add(n);
            }
            for (ForgeDirection d : c.getExternalConnections()) {
                ConnectionMode m = c.getConnectionMode(d);
                if (m == ConnectionMode.DISABLED) continue;
                TileEntity n = neighbour(w, at, d);
                if (n == null || n instanceof TileConnector || EnderIOCompat.isBundle(n)) continue;
                if (!seenDevices.add(MachineSample.posKey(n))) continue;
                ForgeDirection back = d.getOpposite();
                boolean out = m != ConnectionMode.INPUT, in = m != ConnectionMode.OUTPUT;
                PortRole io = EnderIOCompat.storageRole(n, back);
                boolean consumer = io != null ? io == PortRole.OUTPUT : isConsumer(n, back);
                boolean producer = io != null ? io == PortRole.INPUT : isProducer(n);
                if (out && consumer) {
                    consumers++;
                    devices.add(n);
                } else if (in && producer) {
                    producers++;
                    sources.add(n);
                }
            }
        }
        devices.addAll(sources);
        positions.addAll(seen);
        positions.addAll(seenDevices);
    }

    private static TileEntity neighbour(World w, TileEntity at, ForgeDirection d) {
        int x = at.xCoord + d.offsetX, y = at.yCoord + d.offsetY, z = at.zCoord + d.offsetZ;
        if (y < 0 || y >= w.getHeight()
            || !w.getChunkProvider()
                .chunkExists(x >> 4, z >> 4))
            return null;
        TileEntity te = w.getTileEntity(x, y, z);
        return te == null || te.isInvalid() ? null : te;
    }

    /** Generators and engines: providers, and devices that only push. */
    private static boolean isProducer(TileEntity te) {
        return te instanceof IEnergyProvider || te instanceof IEnergyConnection && !(te instanceof IEnergyReceiver);
    }

    /** Machines: receivers; one that is also a provider only once it was seen taking energy. */
    private boolean isConsumer(TileEntity te, ForgeDirection side) {
        if (!(te instanceof IEnergyReceiver r)) return false;
        if (!(te instanceof IEnergyProvider)) return true;
        long key = MachineSample.posKey(te);
        if (knownConsumers.contains(key)) return true;
        if (r.receiveEnergy(side, 1, true) <= 0) return false;
        knownConsumers.add(key);
        return true;
    }

    @Override
    public TileEntity target() {
        return bundle;
    }

    @Override
    public Kind kind() {
        return Kind.RF;
    }

    @Override
    public boolean isValid() {
        return !bundle.isInvalid() && conduit() != null;
    }

    @Override
    public boolean isCable() {
        return true;
    }

    @Override
    public boolean attached() {
        return mode() != ConnectionMode.DISABLED;
    }

    @Override
    public boolean canReceive() {
        ConnectionMode m = mode();
        return m != ConnectionMode.DISABLED && m != ConnectionMode.OUTPUT;
    }

    @Override
    public boolean canSend() {
        ConnectionMode m = mode();
        return m != ConnectionMode.DISABLED && m != ConnectionMode.INPUT;
    }

    @Override
    public PortRole autoRole() {
        ConnectionMode m = mode();
        if (m == ConnectionMode.DISABLED) return PortRole.NONE;
        if (m == ConnectionMode.INPUT) return PortRole.OUTPUT;
        if (m == ConnectionMode.OUTPUT) return PortRole.INPUT;
        return consumers > 0 ? PortRole.OUTPUT : producers > 0 ? PortRole.INPUT : PortRole.NONE;
    }

    @Override
    public boolean hasInputSpec() {
        return true;
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

    /** Room in the conduits' buffer, which EnderIO empties into the machines every tick. */
    @Override
    public long demand() {
        if (!(bundle instanceof IEnergyReceiver r)) return 0;
        int rf = RF.clampInt(RF.euToRf(Config.rfNominalVoltage));
        return Math.min(Config.rfNominalVoltage, RF.rfCost(r.receiveEnergy(face, rf, true)));
    }

    @Override
    public long inject(long maxEU) {
        return bundle instanceof IEnergyReceiver r ? RFAdapter.feed(r, face, maxEU) : 0;
    }

    /** Conduits push into the connector by themselves; they cannot be drained. */
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
        List<String> names = new ArrayList<>();
        for (TileEntity te : devices) names.add(Names.of(te));
        return Names.summarize(names);
    }

    @Override
    public int deviceCount() {
        return devices.size();
    }

    @Override
    public int[] devicePos() {
        if (devices.size() != 1) return null;
        TileEntity te = devices.get(0);
        return new int[] { te.xCoord, te.yCoord, te.zCoord };
    }
}
