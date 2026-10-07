package com.fluxlite.net;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.fluxlite.gui.ConnectorScreen;
import com.fluxlite.gui.ControlCenterScreen;
import com.fluxlite.gui.GuiHost;
import com.fluxlite.tile.TileControlCenter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientPackets {

    private ClientPackets() {}

    public static void handle(int kind, NBTTagCompound data) {
        if (kind == Kinds.HOLO_DATA) {
            World w = Minecraft.getMinecraft().theWorld;
            if (w != null && w.getTileEntity(
                data.getInteger("x"),
                data.getInteger("y"),
                data.getInteger("z")) instanceof TileControlCenter cc) cc.onHoloData(data, Minecraft.getSystemTime());
            return;
        }
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiHost host)) return;
        if (kind == Kinds.CONNECTOR_DATA && host.screen() instanceof ConnectorScreen s) s.onData(data);
        else if (kind == Kinds.CC_DATA && host.screen() instanceof ControlCenterScreen s) s.onData(data);
    }
}
