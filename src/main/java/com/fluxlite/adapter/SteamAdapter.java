package com.fluxlite.adapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.fluxlite.Config;
import com.fluxlite.core.MachineSample;
import com.fluxlite.core.PortRole;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.util.Names;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;
import gregtech.common.tileentities.boilers.MTEBoiler;

/**
 * Steam next to a connector face; amounts are litres. Only steam is ever moved.
 * <ul>
 * <li>Producers (GT boilers, the output hatch of a large boiler, other mods' boilers) are drained into the steam
 * network. They also push into the connector on their own.</li>
 * <li>Consumers (GT steam machines, steam multiblock hatches, steam turbines, anything that takes steam but not water)
 * are filled from it.</li>
 * <li>A GT fluid pipe is walked like a cable: with consumers on it the network feeds the pipe; with only producers on
 * it the pipe feeds the network.</li>
 * </ul>
 * Tanks and other fluid handlers that take anything are left alone, so nothing gets flooded with steam.
 */
public final class SteamAdapter implements EnergyAdapter {

    public static final int NONE = 0, PRODUCER = 1, CONSUMER = 2, PIPE = 3;

    private final TileEntity tile;
    private final IFluidHandler handler;
    private final TileConnector connector;
    /** Face of the neighbour that touches the connector. */
    private final ForgeDirection face;
    private final int type;

    // pipes only
    private final List<TileEntity> producers = new ArrayList<>(), consumers = new ArrayList<>();
    private final List<ForgeDirection> consumerFaces = new ArrayList<>();
    /** The pipes walked and the devices on them. */
    private final Set<Long> positions = new HashSet<>();
    private long scannedAt = Long.MIN_VALUE;

    private SteamAdapter(TileEntity tile, TileConnector connector, ForgeDirection face, int type) {
        this.tile = tile;
        this.handler = (IFluidHandler) tile;
        this.connector = connector;
        this.face = face;
        this.type = type;
        refresh();
    }

    /** @return an adapter, or null when the neighbour has nothing to do with steam (yet) */
    public static SteamAdapter create(TileConnector connector, ForgeDirection side, TileEntity te) {
        if (!Config.steamEnabled || te == null || te.isInvalid() || te instanceof TileConnector) return null;
        if (!(te instanceof IFluidHandler)) return null;
        ForgeDirection face = side.getOpposite();
        if (isFluidPipe(te)) return new SteamAdapter(te, connector, face, PIPE);
        int type = classify(te, face);
        return type == NONE ? null : new SteamAdapter(te, connector, face, type);
    }

    public static boolean matches(EnergyAdapter adapter, TileEntity te) {
        return adapter instanceof SteamAdapter && adapter.target() == te && adapter.isValid();
    }

    // ------------------------------------------------------------------ steam helpers

    public static boolean isSteam(FluidStack s) {
        try {
            return s != null && GTModHandler.isSteam(s);
        } catch (Throwable t) {
            return false;
        }
    }

    static FluidStack steam(long litres) {
        return Materials.Steam.getGas(Math.max(1, Math.min(Integer.MAX_VALUE, litres)));
    }

    private static boolean isFluidPipe(TileEntity te) {
        return te instanceof BaseMetaPipeEntity p && p.getMetaTileEntity() instanceof MTEFluidPipe;
    }

    /** What a device is for steam; decided once per neighbour, so a filling tank can't flip it. */
    static int classify(TileEntity te, ForgeDirection face) {
        if (!(te instanceof IFluidHandler h)) return NONE;
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte == null) return NONE;
            if (mte instanceof MTEBoiler || mte instanceof MTEHatchOutput) return PRODUCER;
            // bronze / steel machines keep their steam apart from their fluid tanks
            if (te instanceof BaseMetaTileEntity bm && bm.isSteampowered()) return CONSUMER;
        }
        try {
            boolean takesSteam = h.fill(face, steam(1000), false) > 0;
            boolean takesWater = h.fill(face, Materials.Water.getFluid(1000), false) > 0;
            if (takesSteam && !takesWater) return CONSUMER;
            if (!takesSteam && drain(h, face, 1000, false) > 0) return PRODUCER;
        } catch (Throwable ignored) {}
        return NONE;
    }

    /**
     * Drains steam only. GT boilers keep water as their main fluid and steam as the drainable one, so a drain asking
     * for steam by type gets nothing from them; the untyped drain is tried first and used only when it gives steam.
     */
    static long drain(IFluidHandler h, ForgeDirection face, long max, boolean doDrain) {
        int amount = (int) Math.max(0, Math.min(Integer.MAX_VALUE, max));
        if (amount <= 0) return 0;
        FluidStack any = h.drain(face, amount, false);
        if (isSteam(any) && any.amount > 0) {
            if (!doDrain) return any.amount;
            FluidStack got = h.drain(face, any.amount, true);
            return isSteam(got) ? got.amount : 0;
        }
        FluidStack typed = h.drain(face, steam(amount), false);
        if (!isSteam(typed) || typed.amount <= 0) return 0;
        if (!doDrain) return typed.amount;
        FluidStack got = h.drain(face, steam(typed.amount), true);
        return isSteam(got) ? got.amount : 0;
    }

    // ------------------------------------------------------------------ pipe scan

    @Override
    public void refresh() {
        if (type != PIPE) return;
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
        producers.clear();
        consumers.clear();
        consumerFaces.clear();
        positions.clear();
        World w = tile.getWorldObj();
        Set<Long> seen = new HashSet<>();
        Set<TileEntity> devices = new HashSet<>();
        ArrayDeque<BaseMetaPipeEntity> queue = new ArrayDeque<>();
        queue.add((BaseMetaPipeEntity) tile);
        seen.add(MachineSample.posKey(tile));
        while (!queue.isEmpty()) {
            BaseMetaPipeEntity pipe = queue.poll();
            byte connections = pipe.getConnections();
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if ((connections & d.flag) == 0) continue;
                int x = pipe.xCoord + d.offsetX, y = pipe.yCoord + d.offsetY, z = pipe.zCoord + d.offsetZ;
                if (y < 0 || y >= w.getHeight()
                    || !w.getChunkProvider()
                        .chunkExists(x >> 4, z >> 4))
                    continue;
                TileEntity n = w.getTileEntity(x, y, z);
                if (n == null || n.isInvalid() || n instanceof TileConnector) continue;
                if (n instanceof BaseMetaPipeEntity next) {
                    if (isFluidPipe(next) && seen.size() < Config.cableScanMaxNodes
                        && seen.add(MachineSample.posKey(next))) queue.add(next);
                    continue;
                }
                if (!devices.add(n)) continue;
                positions.add(MachineSample.posKey(n));
                int k = classify(n, d.getOpposite());
                if (k == CONSUMER) {
                    consumers.add(n);
                    consumerFaces.add(d.getOpposite());
                } else if (k == PRODUCER) producers.add(n);
            }
        }
        positions.addAll(seen);
    }

    // ------------------------------------------------------------------ EnergyAdapter

    @Override
    public Kind kind() {
        return Kind.STEAM;
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
    public boolean attached() {
        return type != PIPE || (((BaseMetaPipeEntity) tile).getConnections() & face.flag) != 0;
    }

    @Override
    public boolean isCable() {
        return type == PIPE;
    }

    @Override
    public boolean canReceive() {
        return type == CONSUMER || type == PIPE && attached() && !consumers.isEmpty();
    }

    @Override
    public boolean canSend() {
        return type == PRODUCER || type == PIPE && attached() && !producers.isEmpty();
    }

    /** A pipe with machines on it is fed, even when boilers push into it too. */
    @Override
    public PortRole autoRole() {
        return canReceive() ? PortRole.OUTPUT : canSend() ? PortRole.INPUT : PortRole.NONE;
    }

    @Override
    public boolean hasInputSpec() {
        return true;
    }

    @Override
    public long inputVoltage() {
        return 0;
    }

    @Override
    public long inputAmperage() {
        return 0;
    }

    @Override
    public long outputVoltage() {
        return 0;
    }

    @Override
    public long outputAmperage() {
        return 0;
    }

    /** Litres the device (or the machines on the pipe) would take right now. */
    @Override
    public long demand() {
        try {
            long room = handler.fill(face, steam(Config.steamMaxPerTick), false);
            if (type != PIPE || room <= 0) return Math.max(0, room);
            // only what the machines on the pipe can take, so the pipe is not flooded
            long want = 0;
            for (int i = 0; i < consumers.size(); i++) {
                TileEntity c = consumers.get(i);
                if (c.isInvalid() || !(c instanceof IFluidHandler h)) continue;
                want += h.fill(consumerFaces.get(i), steam(Config.steamMaxPerTick), false);
                if (want >= room) return room;
            }
            return want;
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public long inject(long max) {
        if (max <= 0) return 0;
        try {
            return Math.max(0, handler.fill(face, steam(Math.min(max, Config.steamMaxPerTick)), true));
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public boolean isPassiveSender() {
        return false;
    }

    @Override
    public long extract(long max) {
        try {
            return drain(handler, face, Math.min(max, Config.steamMaxPerTick), true);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public String displayName() {
        if (type != PIPE) return Names.of(tile);
        List<String> names = new ArrayList<>();
        for (TileEntity te : consumers) names.add(Names.of(te));
        for (TileEntity te : producers) names.add(Names.of(te));
        return Names.summarize(names);
    }

    @Override
    public int deviceCount() {
        return type == PIPE ? consumers.size() + producers.size() : 1;
    }

    @Override
    public int[] devicePos() {
        if (type != PIPE || deviceCount() != 1) return null;
        TileEntity te = consumers.isEmpty() ? producers.get(0) : consumers.get(0);
        return new int[] { te.xCoord, te.yCoord, te.zCoord };
    }
}
