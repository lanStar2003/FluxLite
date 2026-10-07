package com.fluxlite.net;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.view.ConnectorView;
import com.fluxlite.core.view.ControlCenterView;
import com.fluxlite.item.ItemFluxTerminal;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.tile.TileControlCenter;

/** Server side packet handling. Every request is checked for distance and team membership (OPs see everything). */
public final class ServerPackets {

    private static final double MAX_DIST_SQ = 64 * 64;

    private ServerPackets() {}

    public static boolean isOp(EntityPlayerMP p) {
        return p.canCommandSenderUseCommand(2, "");
    }

    public static boolean sameTeam(EntityPlayerMP p, UUID owner) {
        if (owner == null) return false;
        GTWirelessBackend b = GTWirelessBackend.INSTANCE;
        return Objects.equals(b.resolveTeam(p.getUniqueID()), b.resolveTeam(owner));
    }

    private static TileEntity near(EntityPlayerMP p, NBTTagCompound d) {
        if (p.worldObj.provider.dimensionId != d.getInteger("dim")) return null;
        int x = d.getInteger("x"), y = d.getInteger("y"), z = d.getInteger("z");
        if (p.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) > MAX_DIST_SQ) return null;
        World w = p.worldObj;
        if (!w.blockExists(x, y, z)) return null;
        return w.getTileEntity(x, y, z);
    }

    public static void handle(EntityPlayerMP player, int kind, NBTTagCompound d) {
        if (player == null || player.playerNetServerHandler == null) return;
        TileEntity te = near(player, d);
        switch (kind) {
            case Kinds.CONNECTOR_REQUEST -> {
                if (!(te instanceof TileConnector c)) return;
                Net.toClient(
                    player,
                    Kinds.CONNECTOR_DATA,
                    sameTeam(player, c.owner) || isOp(player) ? ConnectorView.build(c) : ConnectorView.denied(c));
            }
            case Kinds.CONNECTOR_EDIT -> {
                if (!(te instanceof TileConnector c) || !(sameTeam(player, c.owner) || isOp(player))) return;
                ConnectorView.applyEdit(c, d);
                if (d.getInteger("op") == Kinds.OP_TOGGLE && c.isLive()) c.resolvePorts();
                Net.toClient(player, Kinds.CONNECTOR_DATA, ConnectorView.build(c));
            }
            case Kinds.CC_REQUEST -> {
                // the handheld terminal works anywhere, as long as the player carries one
                if (d.getBoolean("hand")) {
                    if (ItemFluxTerminal.carries(player))
                        Net.toClient(player, Kinds.CC_DATA, ControlCenterView.build(player, null, d));
                } else if (te instanceof TileControlCenter cc)
                    Net.toClient(player, Kinds.CC_DATA, ControlCenterView.build(player, cc, d));
            }
            case Kinds.CC_ACTION -> {
                if (d.getBoolean("hand")) {
                    if (ItemFluxTerminal.carries(player)) ControlCenterView.action(player, null, d);
                } else if (te instanceof TileControlCenter cc) ControlCenterView.action(player, cc, d);
            }
            default -> {}
        }
    }
}
