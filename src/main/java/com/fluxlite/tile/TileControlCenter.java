package com.fluxlite.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.fluxlite.Config;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.core.registry.TeamData;
import com.fluxlite.core.view.HoloView;
import com.fluxlite.gui.holo.HoloState;
import com.fluxlite.net.Kinds;
import com.fluxlite.net.Net;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Control center: only reads team data. Emits redstone while its team has alerts (if enabled), and projects a
 * floating display of the team's energy above itself for players nearby.
 */
public class TileControlCenter extends TileEntity {

    public UUID owner;
    public String ownerName = "";
    public boolean redstoneOnAlert;
    /** The floating display is switched on (by its owner). */
    public boolean hologram = true;
    /** Synced to the client for the red-dot front texture. */
    public boolean alertActive;

    /** Client: what the floating display shows. */
    public HoloState holo;

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || owner == null) return;
        long time = worldObj.getTotalWorldTime();
        int phase = xCoord + zCoord & 15;
        if (hologram && Config.hologramRange > 0 && time % 10 == phase % 10) pushHologram();
        if (time % 20 != phase) return;
        Registry reg = Registry.get();
        if (reg == null) return;
        TeamData team = reg.teamIfPresent(GTWirelessBackend.INSTANCE.resolveTeam(owner));
        boolean alert = team != null && !team.alerts.isEmpty();
        if (alert != alertActive) {
            alertActive = alert;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
        }
    }

    /** Sends the display data to the players close enough to see it. */
    private void pushHologram() {
        double r = Config.hologramRange + 4;
        List<EntityPlayerMP> near = new ArrayList<>();
        for (Object o : worldObj.playerEntities) {
            if (o instanceof EntityPlayerMP p && p.getDistanceSq(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5) <= r * r)
                near.add(p);
        }
        if (near.isEmpty()) return;
        NBTTagCompound d = HoloView.build(this);
        if (d == null) return;
        d.setInteger("x", xCoord);
        d.setInteger("y", yCoord);
        d.setInteger("z", zCoord);
        d.setFloat("r", Config.hologramRange);
        for (EntityPlayerMP p : near) Net.toClient(p, Kinds.HOLO_DATA, d);
    }

    public int redstoneLevel() {
        return redstoneOnAlert && alertActive ? 15 : 0;
    }

    public void setRedstoneOnAlert(boolean on) {
        redstoneOnAlert = on;
        markDirty();
        if (worldObj != null) worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType());
    }

    public void setHologram(boolean on) {
        hologram = on;
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /** Client: new display data arrived. */
    public void onHoloData(NBTTagCompound t, long now) {
        if (holo == null) holo = new HoloState();
        holo.accept(t, now);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        // the display floats above the block and is wider than it
        return AxisAlignedBB
            .getBoundingBox(xCoord - 1.5, yCoord, zCoord - 1.5, xCoord + 2.5, yCoord + 3.5, zCoord + 2.5);
    }

    @Override
    public void writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        if (owner != null) {
            t.setLong("ownerM", owner.getMostSignificantBits());
            t.setLong("ownerL", owner.getLeastSignificantBits());
        }
        t.setString("ownerName", ownerName == null ? "" : ownerName);
        t.setBoolean("rs", redstoneOnAlert);
        t.setBoolean("holo", hologram);
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        owner = t.hasKey("ownerM") ? new UUID(t.getLong("ownerM"), t.getLong("ownerL")) : null;
        ownerName = t.getString("ownerName");
        redstoneOnAlert = t.getBoolean("rs");
        hologram = !t.hasKey("holo") || t.getBoolean("holo");
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("a", alertActive);
        t.setBoolean("h", hologram);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, t);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound t = pkt.func_148857_g();
        alertActive = t.getBoolean("a");
        hologram = t.getBoolean("h");
        if (worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }
}
