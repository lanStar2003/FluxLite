package com.fluxlite.charge;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

import com.fluxlite.Config;
import com.fluxlite.adapter.RF;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.ServerEvents;
import com.fluxlite.item.ItemFluxTerminal;

import cofh.api.energy.IEnergyContainerItem;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.util.GTModHandler;

/**
 * Wireless charging: a player carrying a Flux Terminal with charging switched on gets the electric items in their
 * inventory and armour slots topped up from their team's GT wireless network, once per round (a second by default).
 * GT and IC2 items at any voltage, and RF items at GT's EU to RF rate. What is charged counts as the team's output.
 */
public final class Charger {

    private static final int ANY_TIER = Integer.MAX_VALUE;

    private long tick;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Config.chargingEnabled) return;
        if (++tick % Math.max(1, Config.chargingInterval) != 0) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        GTWirelessBackend backend = GTWirelessBackend.INSTANCE;
        if (!backend.isAvailable()) return;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (!(o instanceof EntityPlayerMP p) || p instanceof FakePlayer || !ItemFluxTerminal.charging(p)) continue;
            try {
                charge(p, backend);
            } catch (RuntimeException ex) {
                // one odd item must not stop everyone's charging
            }
        }
    }

    /** The stacks a round looks at: armour first, then hotbar and backpack in slot order. */
    private static List<ItemStack> stacks(EntityPlayerMP p) {
        List<ItemStack> out = new ArrayList<>();
        if (Config.chargeArmor) for (ItemStack s : p.inventory.armorInventory) if (s != null) out.add(s);
        for (ItemStack s : p.inventory.mainInventory) if (s != null) out.add(s);
        return out;
    }

    private static boolean isRf(ItemStack s) {
        return Config.chargeRf && s.getItem() instanceof IEnergyContainerItem;
    }

    /** EU the stack can still take this round. */
    private static long need(ItemStack s) {
        if (s.stackSize != 1) return 0;
        if (GTModHandler.isElectricItem(s)) {
            if (!Config.chargeBatteries && GTModHandler.isChargerItem(s)) return 0;
            return Math.max(0, GTModHandler.chargeElectricItem(s, Integer.MAX_VALUE, ANY_TIER, true, true));
        }
        if (isRf(s)) {
            IEnergyContainerItem c = (IEnergyContainerItem) s.getItem();
            return RF.rfCost(c.receiveEnergy(s, Integer.MAX_VALUE, true));
        }
        return 0;
    }

    /** Puts up to {@code eu} into the stack; returns the EU it took. */
    private static long give(ItemStack s, long eu) {
        if (eu <= 0) return 0;
        if (GTModHandler.isElectricItem(s)) {
            return Math.max(0, GTModHandler.chargeElectricItem(s, RF.clampInt(eu), ANY_TIER, true, false));
        }
        IEnergyContainerItem c = (IEnergyContainerItem) s.getItem();
        int rf = c.receiveEnergy(s, RF.clampInt(RF.euToRf(eu)), false);
        return Math.min(eu, RF.rfCost(rf));
    }

    private static void charge(EntityPlayerMP p, GTWirelessBackend backend) {
        List<ItemStack> stacks = stacks(p);
        long[] needs = new long[stacks.size()];
        long total = 0;
        for (int i = 0; i < needs.length; i++) total += needs[i] = need(stacks.get(i));
        if (total <= 0) return;
        UUID team = backend.resolveTeam(p.getUniqueID());
        BigInteger balance = backend.getBalance(team);
        long bal = balance.min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
        long[] grant = ChargePlan
            .share(needs, bal, ChargePlan.capPerRound(Config.chargeMaxPerSecond, Config.chargingInterval));
        long reserved = 0;
        for (long g : grant) reserved += g;
        // take it first: should the balance have changed meanwhile, nothing is charged for free
        if (reserved <= 0 || !backend.add(team, BigInteger.valueOf(-reserved))) return;
        long spent = 0;
        for (int i = 0; i < grant.length; i++) spent += give(stacks.get(i), grant[i]);
        if (spent < reserved) backend.add(team, BigInteger.valueOf(reserved - spent));
        if (spent <= 0) return;
        ServerEvents.addTeamTick(team, 0, spent, spent, true);
        p.inventoryContainer.detectAndSendChanges();
    }
}
