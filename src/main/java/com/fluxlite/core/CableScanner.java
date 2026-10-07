package com.fluxlite.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.adapter.IC2Adapter;
import com.fluxlite.compat.GTSinks;
import com.fluxlite.tile.TileConnector;

import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTECable;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;

/**
 * Breadth first walk over a GT cable network, collecting its endpoints. Never loads chunks; unloaded parts are
 * reported as {@link Result#truncated}.
 */
public final class CableScanner {

    private CableScanner() {}

    public static final class Endpoint {

        public final TileEntity tile;
        /** Face of the endpoint that touches the cable. */
        public final ForgeDirection face;
        public final boolean consumer, producer;
        /** Max safe input voltage; Long.MAX_VALUE for RF receivers, 0 when unknown. */
        public final long inVoltage, inAmperage, outVoltage, outAmperage;

        Endpoint(TileEntity tile, ForgeDirection face, boolean consumer, boolean producer, long inVoltage,
            long inAmperage, long outVoltage, long outAmperage) {
            this.tile = tile;
            this.face = face;
            this.consumer = consumer;
            this.producer = producer;
            this.inVoltage = inVoltage;
            this.inAmperage = inAmperage;
            this.outVoltage = outVoltage;
            this.outAmperage = outAmperage;
        }
    }

    public static final class Result {

        public long minCableVoltage = Long.MAX_VALUE;
        public long minCableAmperage = Long.MAX_VALUE;
        public int cables;
        public boolean truncated;
        public final List<Endpoint> consumers = new ArrayList<>();
        public final List<Endpoint> producers = new ArrayList<>();
        public boolean unknownConsumer;
        /** The cable blocks walked. */
        public final List<BaseMetaPipeEntity> pipes = new ArrayList<>();
        /** Where the cables and the devices on them are, so a block placed or broken next to them is noticed. */
        public final Set<Long> positions = new HashSet<>();

        /** Machines: devices that only take energy. */
        public int machines() {
            int n = 0;
            for (Endpoint e : consumers) if (!e.producer) n++;
            return n;
        }

        /** Batteries and buffers: devices that take energy and give it back. */
        public int storages() {
            int n = 0;
            for (Endpoint e : consumers) if (e.producer) n++;
            return n;
        }

        public long minConsumerVoltage() {
            long v = Long.MAX_VALUE;
            for (Endpoint e : consumers) v = Math.min(v, e.inVoltage);
            return v;
        }

        public long sumConsumerAmperage() {
            long a = 0;
            for (Endpoint e : consumers) a += Math.max(0, e.inAmperage);
            return a;
        }

        public long maxProducerVoltage() {
            long v = 0;
            for (Endpoint e : producers) v = Math.max(v, e.outVoltage);
            return v;
        }

        public long sumProducerAmperage() {
            long a = 0;
            for (Endpoint e : producers) a += Math.max(0, e.outAmperage);
            return a;
        }
    }

    public static boolean isCable(TileEntity te) {
        return te instanceof BaseMetaPipeEntity pipe && pipe.getMetaTileEntity() instanceof MTECable;
    }

    /**
     * @param start  the cable next to the connector
     * @param origin the connector itself
     * @param side   connector face touching {@code start}
     */
    public static Result scan(BaseMetaPipeEntity start, TileConnector origin, ForgeDirection side) {
        Result r = new Result();
        World world = start.getWorld();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BaseMetaPipeEntity> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(MachineSample.posKey(start));
        int limit = Config.cableScanMaxNodes;

        while (!queue.isEmpty()) {
            BaseMetaPipeEntity pipe = queue.poll();
            if (!(pipe.getMetaTileEntity() instanceof MTECable cable)) continue;
            r.cables++;
            r.pipes.add(pipe);
            r.positions.add(MachineSample.posKey(pipe));
            r.minCableVoltage = Math.min(r.minCableVoltage, cable.mVoltage);
            r.minCableAmperage = Math.min(r.minCableAmperage, cable.mAmperage);
            byte connections = pipe.getConnections();
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if ((connections & dir.flag) == 0) continue;
                int nx = pipe.xCoord + dir.offsetX, ny = pipe.yCoord + dir.offsetY, nz = pipe.zCoord + dir.offsetZ;
                if (ny < 0 || ny >= world.getHeight()) continue;
                if (!world.getChunkProvider()
                    .chunkExists(nx >> 4, nz >> 4)) {
                    r.truncated = true;
                    continue;
                }
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te == null || te.isInvalid()) continue;
                ForgeDirection face = dir.getOpposite();
                if (te instanceof BaseMetaPipeEntity next) {
                    if (!(next.getMetaTileEntity() instanceof MTECable)) continue;
                    if (visited.add(MachineSample.posKey(next))) {
                        if (visited.size() > limit) {
                            r.truncated = true;
                            continue;
                        }
                        queue.add(next);
                    }
                    continue;
                }
                if (te == origin && face == side) continue;
                if (te instanceof TileConnector) continue;
                classify(te, face, pipe, r);
            }
        }
        return r;
    }

    private static void classify(TileEntity te, ForgeDirection face, TileEntity cable, Result r) {
        if (te instanceof BaseMetaTileEntity bm) {
            boolean in = bm.inputEnergyFrom(face, false);
            boolean out = bm.outputsEnergyTo(face, false);
            if (!in && !out) return;
            Endpoint e = new Endpoint(
                te,
                face,
                in,
                out,
                bm.getInputVoltage(),
                bm.getInputAmperage(),
                bm.getOutputVoltage(),
                bm.getOutputAmperage());
            add(e, r);
            return;
        }
        if (te instanceof IEnergyConnected ec) {
            boolean in = ec.inputEnergyFrom(face, false);
            boolean out = ec.outputsEnergyTo(face, false);
            if (!in && !out) return;
            if (te instanceof IBasicEnergyContainer c) {
                add(
                    new Endpoint(
                        te,
                        face,
                        in,
                        out,
                        c.getInputVoltage(),
                        c.getInputAmperage(),
                        c.getOutputVoltage(),
                        c.getOutputAmperage()),
                    r);
            } else if (GTSinks.anyVoltage(te)) {
                add(new Endpoint(te, face, in, out, Long.MAX_VALUE, GTSinks.amperage(te), 0, 0), r);
            } else {
                // nothing says what voltage it survives (an AE2 P2P tunnel passes it on to whatever is behind)
                if (in) r.unknownConsumer = true;
                add(new Endpoint(te, face, in, out, 0, 0, 0, 0), r);
            }
            return;
        }
        boolean ic2In = te instanceof IEnergySink sink && sink.acceptsEnergyFrom(cable, face);
        boolean ic2Out = te instanceof IEnergySource src && src.emitsEnergyTo(cable, face);
        if (ic2In || ic2Out) {
            long inV = ic2In ? IC2Adapter.tierVoltage(((IEnergySink) te).getSinkTier()) : 0;
            long outV = ic2Out ? IC2Adapter.tierVoltage(((IEnergySource) te).getSourceTier()) : 0;
            add(new Endpoint(te, face, ic2In, ic2Out, inV, 1, outV, 1), r);
            return;
        }
        boolean rfIn = GregTechAPI.mOutputRF && te instanceof IEnergyReceiver rec && rec.canConnectEnergy(face);
        boolean rfOut = GregTechAPI.mInputRF && te instanceof IEnergyProvider prov && prov.canConnectEnergy(face);
        if (rfIn || rfOut) add(new Endpoint(te, face, rfIn, rfOut, Long.MAX_VALUE, 1, 0, 0), r);
    }

    private static void add(Endpoint e, Result r) {
        r.positions.add(MachineSample.posKey(e.tile));
        if (e.consumer) r.consumers.add(e);
        if (e.producer) r.producers.add(e);
    }
}
