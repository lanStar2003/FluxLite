package com.fluxlite.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxlite.FluxLite;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * Two generic NBT messages, one per direction. In 1.7.10 handlers run on the Netty thread, so every message is queued
 * and handled on the main thread of its side.
 */
public final class Net {

    public static SimpleNetworkWrapper CHANNEL;

    private static final Queue<Runnable> SERVER_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> CLIENT_QUEUE = new ConcurrentLinkedQueue<>();

    private Net() {}

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(FluxLite.MODID);
        CHANNEL.registerMessage(ToServerHandler.class, MsgToServer.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ToClientHandler.class, MsgToClient.class, 1, Side.CLIENT);
        Pump pump = new Pump();
        FMLCommonHandler.instance()
            .bus()
            .register(pump);
    }

    public static void toServer(int kind, NBTTagCompound data) {
        CHANNEL.sendToServer(new MsgToServer(kind, data));
    }

    public static void toClient(EntityPlayerMP player, int kind, NBTTagCompound data) {
        CHANNEL.sendTo(new MsgToClient(kind, data), player);
    }

    public static final class ToServerHandler implements IMessageHandler<MsgToServer, IMessage> {

        @Override
        public IMessage onMessage(MsgToServer msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            SERVER_QUEUE.add(() -> ServerPackets.handle(player, msg.kind, msg.data));
            return null;
        }
    }

    public static final class ToClientHandler implements IMessageHandler<MsgToClient, IMessage> {

        @Override
        public IMessage onMessage(MsgToClient msg, MessageContext ctx) {
            CLIENT_QUEUE.add(() -> ClientPackets.handle(msg.kind, msg.data));
            return null;
        }
    }

    public static final class Pump {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Runnable r;
            int n = 0;
            while (n++ < 256 && (r = SERVER_QUEUE.poll()) != null) {
                try {
                    r.run();
                } catch (Throwable t) {
                    FluxLite.LOG.error("Error handling FluxLite packet", t);
                }
            }
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            Runnable r;
            while ((r = CLIENT_QUEUE.poll()) != null) {
                try {
                    r.run();
                } catch (Throwable t) {
                    FluxLite.LOG.error("Error handling FluxLite packet", t);
                }
            }
        }
    }
}
