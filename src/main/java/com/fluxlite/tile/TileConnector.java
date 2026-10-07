package com.fluxlite.tile;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.fluxlite.Config;
import com.fluxlite.FluxLite;
import com.fluxlite.adapter.Adapters;
import com.fluxlite.adapter.EnergyAdapter;
import com.fluxlite.adapter.GTCableAdapter;
import com.fluxlite.adapter.SteamAdapter;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.backend.SteamNetwork;
import com.fluxlite.chunk.ChunkLoadManager;
import com.fluxlite.core.MachineSample;
import com.fluxlite.core.Port;
import com.fluxlite.core.PortMode;
import com.fluxlite.core.PortRole;
import com.fluxlite.core.PortStatus;
import com.fluxlite.core.ServerEvents;
import com.fluxlite.core.Settlement;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.PortInfo;
import com.fluxlite.core.registry.Registry;

import cofh.api.energy.IEnergyHandler;
import gregtech.api.GregTechAPI;
import gregtech.api.graphs.GenerateNodeMap;
import gregtech.api.graphs.GenerateNodeMapPower;
import gregtech.api.graphs.Node;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;

/**
 * Flux connector. Every face connects by itself: generators and dynamo hatches feed the wireless network (input),
 * machines and energy hatches are fed from it (output), cables with both get both. Speaks GT EU directly, IC2 EU
 * through the IC2 energy net and RF through the CoFH API.
 * <p>
 * Each face also has a steam channel ({@code ports[6..11]}): boilers fill the team's steam network, steam machines are
 * fed from it. Steam goes straight in and out of the network, through Forge's fluid interface.
 */
public class TileConnector extends TileEntity
    implements IEnergyConnected, IEnergySink, IEnergySource, IEnergyHandler, IFluidHandler {

    /** Client visual of a face: what the arm looks like. */
    public static final byte VIS_NONE = 0, VIS_IN = 1, VIS_OUT = 2, VIS_BOTH = 3, VIS_IDLE = 4, VIS_OFF = 5,
        VIS_ERROR = 6;

    public UUID owner;
    public String ownerName = "";
    public long recordId;
    /** 0-5: EU per face, 6-11: steam per face. */
    public final Port[] ports = new Port[Port.COUNT];
    /** Name kept by a wrench-dismantled item; given to the record when the connector goes live. */
    public String pendingName;

    // server runtime
    private ConnectorRecord record;
    private boolean live;
    private boolean released;
    private boolean needsResolve = true;
    private boolean chunkSynced;
    private UUID teamCache;
    private int stagger;
    private boolean ic2Registered;
    private int ic2Signature = -1;
    private final byte[] sentVisual = new byte[6];
    private final List<MachineSample> sampleScratch = new ArrayList<>();

    // client
    public final byte[] visual = new byte[6];
    /** Faces that moved energy during the last second (client, for the glow). */
    public byte activeMask;

    public TileConnector() {
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            ports[d.ordinal()] = new Port(d);
            ports[d.ordinal() + 6] = new Port(d, true);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || owner == null || released) return;
        if (!live) goLive();
        if (record == null) return;

        long now = System.currentTimeMillis();
        long tick = ServerEvents.tick();
        commitTick(now);

        if (needsResolve || (tick + stagger) % 20 == 0) resolvePorts();
        for (Port p : ports) work(p);

        if ((tick + stagger) % 20 == 0) {
            teamCache = GTWirelessBackend.INSTANCE.resolveTeam(owner);
            sampleMachines(tick);
            syncRecord();
            syncVisual(true);
        }
        if (tick % 20 == 0) rollSecond();
    }

    private void goLive() {
        Registry reg = Registry.get();
        if (reg == null) return;
        live = true;
        released = false;
        stagger = (int) Math.floorMod(xCoord * 31L + zCoord * 17L + yCoord, 20L);
        ConnectorRecord r = recordId > 0 ? reg.byId(recordId) : null;
        if (r != null && (r.dim != worldObj.provider.dimensionId || r.x != xCoord || r.y != yCoord || r.z != zCoord)) {
            // record belongs to another block (copied structure); this one gets its own
            if (r.online) r = null;
        }
        if (r == null) r = recordId > 0 && reg.byId(recordId) == null ? reg.restore(recordId) : reg.create();
        recordId = r.id;
        r.owner = owner;
        r.ownerName = ownerName;
        r.dim = worldObj.provider.dimensionId;
        r.x = xCoord;
        r.y = yCoord;
        r.z = zCoord;
        r.online = true;
        if (pendingName != null) {
            r.name = pendingName;
            pendingName = null;
            reg.markDirty();
        }
        record = r;
        teamCache = GTWirelessBackend.INSTANCE.resolveTeam(owner);
        GTWirelessBackend.INSTANCE.ensureUser(owner);
        Settlement.register(this);
        needsResolve = true;
        chunkSynced = false;
        markDirty();
    }

    /** Refund buffers and leave the settlement. Safe to call more than once. */
    public void release() {
        if (released || worldObj == null || worldObj.isRemote) return;
        released = true;
        live = false;
        Settlement.unregister(this);
        setIc2Registered(false);
        refundBuffers();
        if (record != null) {
            syncRecord();
            record.online = false;
        }
        record = null;
    }

    /** Called by the block when broken: drop everything that refers to this connector. */
    public void onBroken() {
        release();
        if (recordId > 0) {
            ChunkLoadManager.INSTANCE.release(worldObj, recordId);
            Registry reg = Registry.get();
            if (reg != null) reg.remove(recordId);
        }
        for (Port p : ports) {
            p.role = PortRole.NONE;
            p.status = PortStatus.DISABLED;
            if (!p.steam) refreshCableGraph(p);
        }
    }

    @Override
    public void validate() {
        super.validate();
        released = false;
    }

    @Override
    public void invalidate() {
        release();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        release();
        super.onChunkUnload();
    }

    public void refundBuffers() {
        long supply = 0, collected = 0;
        for (Port p : ports) {
            if (p.steam) continue;
            supply += Math.max(0, p.supply);
            collected += Math.max(0, p.collected);
            p.supply = p.collected = 0;
        }
        if (supply + collected > 0 && owner != null) {
            BigInteger total = BigInteger.valueOf(supply)
                .add(Settlement.applyLoss(BigInteger.valueOf(collected)));
            GTWirelessBackend.INSTANCE.add(owner, total);
            markDirty();
        }
    }

    public ConnectorRecord record() {
        return record;
    }

    public UUID team() {
        return teamCache != null ? teamCache : owner;
    }

    public boolean isLive() {
        return live && record != null;
    }

    public void markNeighborChanged() {
        needsResolve = true;
        for (Port p : ports) {
            if (p.adapter instanceof GTCableAdapter c) c.invalidateScan();
            else if (p.adapter instanceof SteamAdapter s) s.invalidateScan();
        }
    }

    /** The only setting of a face: on (automatic) or off. Switches its EU and steam channel together. */
    public void toggleSide(int side) {
        if (side < 0 || side > 5) return;
        PortMode next = ports[side].mode == PortMode.OFF ? PortMode.AUTO : PortMode.OFF;
        ports[side].mode = ports[side + 6].mode = next;
        needsResolve = true;
        markDirty();
    }

    // ------------------------------------------------------------------ per tick work

    private void commitTick(long now) {
        long sumIn = 0, sumOut = 0, sumDemand = 0, steamIn = 0, steamOut = 0, steamDemand = 0;
        boolean anyActive = false, steamActive = false;
        for (Port p : ports) {
            long out = p.tickOut - p.outDebt;
            if (out < 0) {
                p.outDebt = -out;
                out = 0;
            } else p.outDebt = 0;
            long in = Math.max(0, p.tickIn);
            boolean active = in > 0 || out > 0;
            int s = p.index;
            if (active || p.role != PortRole.NONE || record.portSeries[s] != null) {
                record.portSeries(s)
                    .tick(in, out, p.tickDemand, active, now);
            }
            if (p.steam) {
                steamIn += in;
                steamOut += out;
                steamDemand += p.tickDemand;
                steamActive |= active;
            } else {
                sumIn += in;
                sumOut += out;
                sumDemand += p.tickDemand;
                anyActive |= active;
            }
            p.tickIn = p.tickOut = p.tickDemand = 0;
        }
        record.total.tick(sumIn, sumOut, sumDemand, anyActive, now);
        if (steamActive || record.steamTotal != null) record.steamTotal()
            .tick(steamIn, steamOut, steamDemand, steamActive, now);
        ServerEvents.addTeamTick(team(), sumIn, sumOut, sumDemand, anyActive);
        ServerEvents.addTeamSteamTick(team(), steamIn, steamOut, steamDemand, steamActive);
    }

    private void work(Port p) {
        EnergyAdapter a = p.adapter;
        if (a == null || !p.isWorking()) return;
        if (p.steam) {
            workSteam(p, a);
            return;
        }
        if (p.supplies() && !a.viaIc2()) {
            long demand = a.demand();
            p.tickDemand += demand;
            if (p.supply > 0 && demand > 0) {
                long used;
                Settlement.supplying = a.isCable();
                try {
                    used = a.inject(p.supply);
                } finally {
                    Settlement.supplying = false;
                }
                if (used > 0) {
                    p.supply -= used;
                    p.tickOut += used;
                }
            }
        } else if (p.supplies()) {
            p.tickDemand += a.demand();
        }
        if (p.collects() && !a.isPassiveSender()) {
            long room = p.collectRoom();
            // a GT emitter may also have pushed this tick; together never more than its rated output
            if (a.kind() == EnergyAdapter.Kind.GT_MACHINE)
                room = Math.min(room, Math.max(0, Port.mul(p.collectVoltage, p.collectAmperage) - p.tickIn));
            long got = a.extract(room);
            if (got > 0) {
                p.collected += got;
                p.tickIn += got;
            }
        }
    }

    /** Steam goes straight from and to the team's steam network; whatever the device did not take goes back. */
    private void workSteam(Port p, EnergyAdapter a) {
        SteamNetwork net = SteamNetwork.get();
        UUID team = team();
        if (net == null || team == null) return;
        if (p.supplies()) {
            long demand = a.demand();
            p.tickDemand += demand;
            long got = demand > 0 ? net.take(team, demand) : 0;
            if (got > 0) {
                long used = Math.min(got, a.inject(got));
                if (used < got) net.give(team, got - used);
                p.tickOut += used;
            }
        }
        if (p.collects()) {
            long got = a.extract(Config.steamMaxPerTick);
            if (got > 0) {
                net.give(team, got);
                p.tickIn += got;
            }
        }
    }

    // ------------------------------------------------------------------ face resolution

    public void resolvePorts() {
        needsResolve = false;
        boolean changed = false;
        for (Port p : ports) {
            PortRole oldRole = p.role;
            PortStatus oldStatus = p.status;
            resolve(p);
            if (p.role != oldRole || p.status != oldStatus) {
                if (!p.steam) refreshCableGraph(p);
                changed = true;
            }
        }
        if (changed) markDirty();
        if (changed || !chunkSynced) {
            chunkSynced = true;
            ChunkLoadManager.INSTANCE.update(this);
        }
        updateIc2Registration();
        syncVisual(false);
    }

    private void resolve(Port p) {
        ForgeDirection side = p.side;
        int nx = xCoord + side.offsetX, ny = yCoord + side.offsetY, nz = zCoord + side.offsetZ;
        if (ny < 0 || ny >= worldObj.getHeight()
            || !worldObj.getChunkProvider()
                .chunkExists(nx >> 4, nz >> 4)) {
            set(p, null, PortRole.NONE, PortStatus.NO_TARGET);
            return;
        }
        TileEntity te = worldObj.getTileEntity(nx, ny, nz);
        if (te instanceof TileConnector) {
            set(p, null, PortRole.NONE, PortStatus.CONNECTOR_NEIGHBOUR);
            return;
        }
        if (p.steam) {
            resolveSteam(p, te);
            return;
        }
        EnergyAdapter a = Adapters.matches(p.adapter, te) ? p.adapter : Adapters.create(this, side, te);
        if (a == null) {
            set(p, null, PortRole.NONE, PortStatus.NO_TARGET);
            return;
        }
        if (p.mode == PortMode.OFF) {
            set(p, a, PortRole.NONE, PortStatus.DISABLED);
            return;
        }
        a.refresh();
        boolean recv = a.canReceive();
        boolean send = a.canSend();
        PortRole role;
        if (recv && send) {
            // a GT cable carrying both can be fed and drained at once (see Settlement#supplying); a single device
            // that does both (an energy storage) is drained into the network
            role = a.isCable() && !a.viaIc2() ? PortRole.BOTH : PortRole.INPUT;
        } else if (recv) role = PortRole.OUTPUT;
        else if (send) role = PortRole.INPUT;
        else role = PortRole.NONE;

        PortStatus status = role == PortRole.NONE ? PortStatus.NO_TARGET : PortStatus.OK;
        if (role.supplies() && !a.hasInputSpec()) {
            if (role == PortRole.BOTH) role = PortRole.INPUT;
            else {
                role = PortRole.NONE;
                status = PortStatus.UNKNOWN_SPEC;
            }
        }
        set(p, a, role, status);
    }

    private void resolveSteam(Port p, TileEntity te) {
        EnergyAdapter a = SteamAdapter.matches(p.adapter, te) ? p.adapter : SteamAdapter.create(this, p.side, te);
        if (a == null) {
            set(p, null, PortRole.NONE, PortStatus.NO_TARGET);
            return;
        }
        if (p.mode == PortMode.OFF) {
            set(p, a, PortRole.NONE, PortStatus.DISABLED);
            return;
        }
        a.refresh();
        // a pipe with machines on it is fed, even when boilers push into it too
        PortRole role = a.canReceive() ? PortRole.OUTPUT : a.canSend() ? PortRole.INPUT : PortRole.NONE;
        set(p, a, role, role == PortRole.NONE ? PortStatus.NO_TARGET : PortStatus.OK);
    }

    private void set(Port p, EnergyAdapter a, PortRole role, PortStatus status) {
        p.adapter = a;
        p.role = role;
        p.status = status;
        p.targetName = a == null ? "" : a.displayName();
        p.supplyVoltage = a != null && role.supplies() ? a.inputVoltage() : 0;
        p.supplyAmperage = a != null && role.supplies() ? a.inputAmperage() : 0;
        p.collectVoltage = a != null && role.collects() ? a.outputVoltage() : 0;
        p.collectAmperage = a != null && role.collects() ? a.outputAmperage() : 0;
        p.recomputeCapacity();
    }

    /** GT cable graphs cache their consumers; rebuild them so a direction change takes effect. */
    private void refreshCableGraph(Port p) {
        ForgeDirection side = p.side;
        TileEntity te = worldObj.getTileEntity(xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ);
        if (!(te instanceof BaseMetaPipeEntity pipe)) return;
        if ((pipe.getConnections() & side.getOpposite().flag) == 0) return;
        try {
            Node node = pipe.getNode();
            if (node != null) GenerateNodeMap.clearNodeMap(node, -1);
            new GenerateNodeMapPower(pipe);
        } catch (Throwable ignored) {}
    }

    public byte visualFor(Port p) {
        if (p.adapter == null || !p.adapter.attached()) return VIS_NONE;
        if (p.mode == PortMode.OFF) return VIS_OFF;
        if (p.status == PortStatus.UNKNOWN_SPEC) return VIS_ERROR;
        return switch (p.status == PortStatus.OK ? p.role : PortRole.NONE) {
            case INPUT -> VIS_IN;
            case OUTPUT -> VIS_OUT;
            case BOTH -> VIS_BOTH;
            default -> VIS_IDLE;
        };
    }

    /** What the arm of a face looks like: its EU and steam channel together. */
    public byte faceVisual(int side) {
        return combine(visualFor(ports[side]), visualFor(ports[side + 6]));
    }

    static byte combine(byte eu, byte steam) {
        if (steam == VIS_NONE) return eu;
        if (eu == VIS_NONE || eu == VIS_IDLE && steam != VIS_IDLE) return steam;
        if (steam == VIS_IDLE || eu == VIS_OFF) return eu;
        boolean in = eu == VIS_IN || eu == VIS_BOTH || steam == VIS_IN || steam == VIS_BOTH;
        boolean out = eu == VIS_OUT || eu == VIS_BOTH || steam == VIS_OUT || steam == VIS_BOTH;
        return in && out ? VIS_BOTH : in ? VIS_IN : out ? VIS_OUT : eu;
    }

    private byte activeMaskNow() {
        byte mask = 0;
        if (record == null) return 0;
        for (int i = 0; i < Port.COUNT; i++) {
            if (record.portSeries[i] != null && record.portSeries[i].window(1).activeTicks > 0) mask |= 1 << (i % 6);
        }
        return mask;
    }

    private byte sentActive;

    private void syncVisual(boolean withActivity) {
        boolean dirty = false;
        for (int i = 0; i < 6; i++) {
            byte v = faceVisual(i);
            if (v != sentVisual[i]) {
                sentVisual[i] = v;
                dirty = true;
            }
        }
        if (withActivity) {
            byte a = activeMaskNow();
            if (a != sentActive) {
                sentActive = a;
                dirty = true;
            }
        }
        if (dirty) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    // ------------------------------------------------------------------ statistics & registry mirror

    private void rollSecond() {
        int day = ServerEvents.dayKey();
        record.total.rollSecond(day);
        if (record.steamTotal != null) record.steamTotal.rollSecond(day);
        for (int i = 0; i < Port.COUNT; i++) if (record.portSeries[i] != null) record.portSeries[i].rollSecond(day);
    }

    private void sampleMachines(long tick) {
        sampleScratch.clear();
        Map<Long, ConnectorRecord.Sampled> map = record.samples;
        Set<Long> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        int day = ServerEvents.dayKey();
        for (Port p : ports) {
            if (!(p.adapter instanceof GTCableAdapter c) || !p.isWorking()) continue;
            int before = sampleScratch.size();
            c.collectSamples(sampleScratch);
            for (int i = before; i < sampleScratch.size(); i++) {
                MachineSample s = sampleScratch.get(i);
                long key = s.posKey();
                if (!seen.add(key)) continue;
                ConnectorRecord.Sampled e = map.get(key);
                if (e == null) {
                    if (map.size() >= com.fluxlite.Config.maxSampledMachinesPerConnector) continue;
                    e = new ConnectorRecord.Sampled();
                    map.put(key, e);
                }
                e.last = s;
                e.side = p.side.ordinal();
                e.seenTick = tick;
                // a GT machine's input is the network's output and vice versa
                e.series.addSecond(s.avgOut, s.avgIn, s.active, now);
                e.series.rollSecond(day);
            }
        }
        for (Iterator<Map.Entry<Long, ConnectorRecord.Sampled>> it = map.entrySet()
            .iterator(); it.hasNext();) {
            if (!seen.contains(
                it.next()
                    .getKey()))
                it.remove();
        }
    }

    private void syncRecord() {
        ConnectorRecord r = record;
        if (r == null) return;
        r.online = live;
        r.lastSeen = System.currentTimeMillis();
        r.ownerName = ownerName;
        for (int i = 0; i < Port.COUNT; i++) {
            Port p = ports[i];
            PortInfo info = r.ports[i];
            info.mode = p.mode;
            info.role = p.role;
            info.status = p.status;
            info.supplyVoltage = p.supplyVoltage;
            info.supplyAmperage = p.supplyAmperage;
            info.collectVoltage = p.collectVoltage;
            info.collectAmperage = p.collectAmperage;
            info.target = p.targetName;
            info.cable = p.adapter != null && p.adapter.isCable();
            info.devices = p.adapter != null ? p.adapter.deviceCount() : 0;
            info.at = p.adapter != null ? p.adapter.devicePos() : null;
        }
    }

    /** True when at least one face moves energy; only such connectors keep chunks loaded. */
    public boolean hasActivePort() {
        for (Port p : ports) if (p.isWorking()) return true;
        return false;
    }

    // ------------------------------------------------------------------ GT EU

    @Override
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        if (worldObj == null || worldObj.isRemote || side == ForgeDirection.UNKNOWN || !live) return 0;
        if (voltage <= 0 || amperage <= 0 || Settlement.supplying) return 0;
        Port p = ports[side.ordinal()];
        if (!p.collects()) return 0;
        p.ensureCollectCapacityFor(Port.mul(voltage, amperage));
        long amps = Math.min(amperage, p.collectRoom() / voltage);
        if (amps <= 0) return 0;
        long eu = amps * voltage;
        p.collected += eu;
        p.tickIn += eu;
        return amps;
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        return side != ForgeDirection.UNKNOWN && ports[side.ordinal()].collects();
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side, boolean waitForActive) {
        return inputEnergyFrom(side);
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side) {
        return side != ForgeDirection.UNKNOWN && ports[side.ordinal()].supplies();
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side, boolean waitForActive) {
        if (side == ForgeDirection.UNKNOWN) return false;
        // lets GT cables connect to any face that is switched on, so auto detection has something to look at
        return waitForActive ? outputsEnergyTo(side) : ports[side.ordinal()].mode != PortMode.OFF;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(byte color) {
        return -1;
    }

    // ------------------------------------------------------------------ IC2 EU (through the IC2 energy net)

    private boolean ic2Face(Port p) {
        return p.adapter != null && p.adapter.viaIc2();
    }

    private void updateIc2Registration() {
        int sig = 0;
        boolean want = false;
        for (Port p : ports) {
            if (!ic2Face(p)) continue;
            want = true;
            int s = p.side.ordinal();
            if (p.collects()) sig |= 1 << s;
            if (p.supplies()) sig |= 1 << (s + 6);
        }
        if (want && ic2Registered && sig != ic2Signature) setIc2Registered(false); // IC2 caches the sides
        ic2Signature = sig;
        setIc2Registered(want);
    }

    private void setIc2Registered(boolean on) {
        if (on == ic2Registered || worldObj == null || worldObj.isRemote) return;
        try {
            MinecraftForge.EVENT_BUS.post(on ? new EnergyTileLoadEvent(this) : new EnergyTileUnloadEvent(this));
            ic2Registered = on;
        } catch (Throwable t) {
            FluxLite.LOG.warn("IC2 energy net registration failed", t);
        }
    }

    @Override
    public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection dir) {
        if (dir == ForgeDirection.UNKNOWN) return false;
        Port p = ports[dir.ordinal()];
        return ic2Face(p) && p.role.collects() && p.status == PortStatus.OK;
    }

    @Override
    public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection dir) {
        if (dir == ForgeDirection.UNKNOWN) return false;
        Port p = ports[dir.ordinal()];
        return ic2Face(p) && p.role.supplies() && p.status == PortStatus.OK;
    }

    @Override
    public double getDemandedEnergy() {
        if (!live) return 0;
        long room = 0;
        for (Port p : ports) if (ic2Face(p) && p.collects()) room += p.collectRoom();
        return room;
    }

    @Override
    public int getSinkTier() {
        return Integer.MAX_VALUE;
    }

    @Override
    public double injectEnergy(ForgeDirection dir, double amount, double voltage) {
        if (!live || amount <= 0) return amount;
        Port target = null;
        if (dir != ForgeDirection.UNKNOWN && ic2Face(ports[dir.ordinal()]) && ports[dir.ordinal()].collects())
            target = ports[dir.ordinal()];
        if (target == null) for (Port p : ports) if (ic2Face(p) && p.collects()) {
            target = p;
            break;
        }
        if (target == null) return amount;
        long take = (long) Math.min(Math.floor(amount), target.collectRoom());
        if (take <= 0) return amount;
        target.collected += take;
        target.tickIn += take;
        return amount - take;
    }

    @Override
    public double getOfferedEnergy() {
        if (!live) return 0;
        long offer = 0;
        for (Port p : ports) if (ic2Face(p) && p.supplies()) offer += Math.max(0, p.supply);
        return offer;
    }

    @Override
    public void drawEnergy(double amount) {
        if (amount > 0) {
            long left = (long) Math.ceil(amount);
            for (Port p : ports) {
                if (left <= 0) break;
                if (!ic2Face(p) || !p.supplies()) continue;
                long take = Math.min(left, Math.max(0, p.supply));
                p.supply -= take;
                p.tickOut += take;
                left -= take;
            }
        } else if (amount < 0) {
            // IC2 hands back what it could not deliver
            long back = (long) Math.floor(-amount);
            for (Port p : ports) {
                if (!ic2Face(p)) continue;
                p.supply += back;
                p.tickOut -= back;
                return;
            }
            ports[0].supply += back;
        }
    }

    @Override
    public int getSourceTier() {
        int tier = 13;
        boolean any = false;
        for (Port p : ports) {
            if (!ic2Face(p) || !p.supplies()) continue;
            any = true;
            tier = Math.min(tier, p.adapter.ic2SafeTier());
        }
        return any ? Math.max(0, tier) : 0;
    }

    // ------------------------------------------------------------------ RF (CoFH API)

    private boolean rfFace(Port p) {
        return p.adapter != null && p.adapter.kind() == EnergyAdapter.Kind.RF;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection side) {
        return side != ForgeDirection.UNKNOWN && ports[side.ordinal()].mode != PortMode.OFF;
    }

    @Override
    public int receiveEnergy(ForgeDirection side, int maxRF, boolean simulate) {
        if (!live || side == ForgeDirection.UNKNOWN || maxRF <= 0) return 0;
        Port p = ports[side.ordinal()];
        if (!rfFace(p) || !p.collects()) return 0;
        long rate = Math.max(1, GregTechAPI.mRFtoEU);
        long acceptRf = Math.min(maxRF, p.collectRoom() * 100 / rate);
        long eu = acceptRf * rate / 100;
        if (eu <= 0) return 0;
        if (!simulate) {
            p.collected += eu;
            p.tickIn += eu;
        }
        return (int) acceptRf;
    }

    @Override
    public int extractEnergy(ForgeDirection side, int maxRF, boolean simulate) {
        if (!live || side == ForgeDirection.UNKNOWN || maxRF <= 0) return 0;
        Port p = ports[side.ordinal()];
        if (!rfFace(p) || !p.supplies()) return 0;
        long rate = Math.max(1, GregTechAPI.mEUtoRF);
        long giveRf = Math.min(maxRF, Math.max(0, p.supply) * rate / 100);
        long eu = (giveRf * 100 + rate - 1) / rate;
        if (giveRf <= 0) return 0;
        if (!simulate) {
            p.supply = Math.max(0, p.supply - eu);
            p.tickOut += eu;
        }
        return (int) giveRf;
    }

    @Override
    public int getEnergyStored(ForgeDirection side) {
        if (side == ForgeDirection.UNKNOWN) return 0;
        return (int) Math.min(Integer.MAX_VALUE, ports[side.ordinal()].supply * Math.max(1, GregTechAPI.mEUtoRF) / 100);
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection side) {
        if (side == ForgeDirection.UNKNOWN) return 0;
        Port p = ports[side.ordinal()];
        return (int) Math
            .min(Integer.MAX_VALUE, Math.max(p.supplyCap, p.collectCap) * Math.max(1, GregTechAPI.mEUtoRF) / 100);
    }

    // ------------------------------------------------------------------ steam (Forge fluids)

    private Port steamPort(ForgeDirection from) {
        return from == null || from == ForgeDirection.UNKNOWN ? null : ports[from.ordinal() + 6];
    }

    /** Boilers and pipes push steam into input faces; it goes straight into the steam network. */
    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (!live || resource == null || resource.amount <= 0 || !SteamAdapter.isSteam(resource)) return 0;
        Port p = steamPort(from);
        if (p == null || !p.collects()) return 0;
        SteamNetwork net = SteamNetwork.get();
        UUID team = team();
        if (net == null || team == null) return 0;
        int take = Math.min(resource.amount, Config.steamMaxPerTick);
        if (doFill) {
            net.give(team, take);
            p.tickIn += take;
        }
        return take;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        Port p = steamPort(from);
        return live && p != null && p.collects() && fluid != null && SteamAdapter.isSteam(new FluidStack(fluid, 1));
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    /** One (always empty) tank on every face that is switched on, so pipes connect to it. */
    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        Port p = steamPort(from);
        if (!Config.steamEnabled || p == null || p.mode == PortMode.OFF) return new FluidTankInfo[0];
        return new FluidTankInfo[] { new FluidTankInfo(null, Config.steamMaxPerTick) };
    }

    // ------------------------------------------------------------------ NBT & client sync

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (owner != null) {
            t.setLong("ownerM", owner.getMostSignificantBits());
            t.setLong("ownerL", owner.getLeastSignificantBits());
        }
        t.setString("ownerName", ownerName == null ? "" : ownerName);
        t.setLong("record", recordId);
        NBTTagList l = new NBTTagList();
        for (Port p : ports) {
            NBTTagCompound pt = new NBTTagCompound();
            p.write(pt);
            l.appendTag(pt);
        }
        t.setTag("ports", l);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        owner = t.hasKey("ownerM") ? new UUID(t.getLong("ownerM"), t.getLong("ownerL")) : null;
        ownerName = t.getString("ownerName");
        recordId = t.getLong("record");
        NBTTagList l = t.getTagList("ports", 10);
        for (int i = 0; i < Math.min(Port.COUNT, l.tagCount()); i++) ports[i].read(l.getCompoundTagAt(i));
        // the switch is per face (saves from before steam only have the EU channels)
        for (int i = 0; i < 6; i++) ports[i + 6].mode = ports[i].mode;
        needsResolve = true;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        byte[] v = new byte[6];
        for (int i = 0; i < 6; i++) v[i] = faceVisual(i);
        t.setByteArray("v", v);
        t.setByte("a", activeMaskNow());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        byte[] v = t.getByteArray("v");
        if (v.length == 6) System.arraycopy(v, 0, visual, 0, 6);
        activeMask = t.getByte("a");
        if (worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    /** Is there an arm towards this face? (server: live state, client: last synced visual) */
    public boolean hasArm(int side) {
        if (worldObj != null && !worldObj.isRemote) return faceVisual(side) != VIS_NONE;
        return visual[side] != VIS_NONE;
    }
}
