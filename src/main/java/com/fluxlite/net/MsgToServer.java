package com.fluxlite.net;

import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class MsgToServer implements IMessage {

    public int kind;
    public NBTTagCompound data;

    public MsgToServer() {}

    public MsgToServer(int kind, NBTTagCompound data) {
        this.kind = kind;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        kind = buf.readByte();
        data = ByteBufUtils.readTag(buf);
        if (data == null) data = new NBTTagCompound();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(kind);
        ByteBufUtils.writeTag(buf, data);
    }
}
