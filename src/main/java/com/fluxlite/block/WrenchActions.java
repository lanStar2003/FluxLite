package com.fluxlite.block;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.fluxlite.compat.Wrenches;
import com.fluxlite.core.Port;
import com.fluxlite.core.PortMode;
import com.fluxlite.core.PortRole;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.net.ServerPackets;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.tile.TileControlCenter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Right-clicking a FluxLite block with a wrench (handled on the server, before the wrench's own logic):
 * <ul>
 * <li>connector: switch the clicked face on or off; sneaking, dismantle it like a conduit: it drops on the ground and
 * keeps its name and switched off faces</li>
 * <li>control center: turn it (towards the clicked side, or by 90 degrees). It is taken down like a GT machine, by
 * breaking it with the wrench (see {@link Harvest}).</li>
 * </ul>
 * Only the owner's team (or an OP) may do this.
 */
public final class WrenchActions {

    /** Key of the kept settings in a dismantled block's item. */
    public static final String TAG = "fluxlite";

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer p = e.entityPlayer;
        World w = p.worldObj;
        if (w == null || w.isRemote) return;
        Block b = w.getBlock(e.x, e.y, e.z);
        if (b != ModBlocks.connector && b != ModBlocks.controlCenter) return;
        ItemStack held = p.getHeldItem();
        if (!Wrenches.usable(held, p, e.x, e.y, e.z)) return;
        e.setCanceled(true);
        if (!(p instanceof EntityPlayerMP mp) || p instanceof FakePlayer) return;

        TileEntity te = w.getTileEntity(e.x, e.y, e.z);
        UUID owner = te instanceof TileConnector c ? c.owner : te instanceof TileControlCenter cc ? cc.owner : null;
        if (owner != null && !ServerPackets.sameTeam(mp, owner) && !ServerPackets.isOp(mp)) {
            mp.addChatComponentMessage(new ChatComponentTranslation("fluxlite.msg.not_owner"));
            return;
        }
        if (te instanceof TileConnector c) {
            if (p.isSneaking()) dismantle(w, e.x, e.y, e.z);
            else toggle(c, e.face, mp);
        } else ModBlocks.controlCenter.rotateBlock(w, e.x, e.y, e.z, ForgeDirection.getOrientation(e.face));
        Wrenches.use(held, p, w, e.x, e.y, e.z);
    }

    private static void toggle(TileConnector c, int face, EntityPlayerMP p) {
        if (face < 0 || face > 5) return;
        c.toggleSide(face);
        if (c.isLive()) c.resolvePorts();
        boolean off = c.ports[face].mode == PortMode.OFF;
        p.addChatComponentMessage(
            new ChatComponentTranslation(
                off ? "fluxlite.msg.face_off" : "fluxlite.msg.face_on",
                new ChatComponentTranslation("fluxlite.side." + face)));
    }

    /**
     * Breaks the block with its drop on the ground (the drop keeps the settings, see {@link Harvest#drops}).
     * breakBlock still runs: buffers go back to the network, the connector leaves the registry.
     */
    private static void dismantle(World w, int x, int y, int z) {
        w.func_147480_a(x, y, z, true);
    }

    /** What a dismantled block remembers; null when everything is default, so such items still stack. */
    static NBTTagCompound settings(TileEntity te) {
        NBTTagCompound t = new NBTTagCompound();
        if (te instanceof TileConnector c) {
            ConnectorRecord r = c.record();
            Registry reg = Registry.get();
            if (r == null && reg != null && c.recordId > 0) r = reg.byId(c.recordId);
            if (r != null && r.name != null && !r.name.isEmpty()) t.setString("name", r.name);
            byte off = 0;
            for (int i = 0; i < 6; i++) if (c.ports[i].mode == PortMode.OFF) off |= (byte) (1 << i);
            if (off != 0) t.setByte("off", off);
            // fixed directions, two bits per channel: 1 input, 2 output
            int dir = 0;
            for (int i = 0; i < Port.COUNT; i++) {
                PortRole f = c.ports[i].fixed;
                if (f != PortRole.NONE) dir |= (f == PortRole.INPUT ? 1 : 2) << (2 * i);
            }
            if (dir != 0) t.setInteger("dir", dir);
        } else if (te instanceof TileControlCenter cc) {
            if (cc.redstoneOnAlert) t.setBoolean("rs", true);
            if (!cc.hologram) t.setBoolean("noHolo", true);
            if (cc.holoSize != 0) t.setByte("holoSize", (byte) cc.holoSize);
            if (cc.holoOpaque) t.setBoolean("holoOpaque", true);
        }
        return t.hasNoTags() ? null : t;
    }

    /** Applies the settings kept in a dismantled block's item to the block just placed from it. */
    public static void restore(TileEntity te, ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()
            || !stack.getTagCompound()
                .hasKey(TAG))
            return;
        NBTTagCompound t = stack.getTagCompound()
            .getCompoundTag(TAG);
        if (te instanceof TileConnector c) {
            if (t.hasKey("name")) c.pendingName = t.getString("name");
            byte off = t.getByte("off");
            for (int i = 0; i < 6; i++) if ((off >> i & 1) != 0) c.ports[i].mode = c.ports[i + 6].mode = PortMode.OFF;
            int dir = t.getInteger("dir");
            for (int i = 0; i < Port.COUNT; i++) {
                int f = dir >> (2 * i) & 3;
                c.ports[i].fixed = f == 1 ? PortRole.INPUT : f == 2 ? PortRole.OUTPUT : PortRole.NONE;
            }
        } else if (te instanceof TileControlCenter cc) {
            cc.redstoneOnAlert = t.getBoolean("rs");
            cc.hologram = !t.getBoolean("noHolo");
            cc.holoSize = Math.max(0, Math.min(TileControlCenter.HOLO_SIZES - 1, t.getByte("holoSize")));
            cc.holoOpaque = t.getBoolean("holoOpaque");
        }
        te.markDirty();
    }
}
