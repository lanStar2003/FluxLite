package com.fluxlite.core.alert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.fluxlite.Config;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.core.Port;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.PortInfo;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.core.registry.TeamData;
import com.fluxlite.core.stats.Bucket;
import com.fluxlite.core.stats.Series;
import com.fluxlite.util.Fmt;

/**
 * Re-evaluates the alerts of every team that switched them on, once per second, and sends rate-limited chat
 * notifications.
 */
public final class AlertManager {

    private AlertManager() {}

    public static void evaluate(Registry reg) {
        Map<UUID, List<ConnectorRecord>> byTeam = new HashMap<>();
        for (ConnectorRecord r : reg.all()) {
            if (r.owner == null) continue;
            byTeam.computeIfAbsent(GTWirelessBackend.INSTANCE.resolveTeam(r.owner), k -> new ArrayList<>())
                .add(r);
        }
        long nowMs = System.currentTimeMillis();
        for (TeamData team : reg.teams()) {
            List<ConnectorRecord> records = byTeam.get(team.leader);
            team.alerts.clear();
            if (!team.alertsOn || records == null || records.isEmpty()) continue;
            teamAlerts(team, team.alerts);
            for (ConnectorRecord r : records) connectorAlerts(r, team.alerts);
            notifyChat(team, nowMs);
        }
    }

    private static void teamAlerts(TeamData team, List<Alert> out) {
        if (team.backendDown) {
            out.add(new Alert(Alert.Type.BACKEND_DOWN, 0, -1, GTWirelessBackend.INSTANCE.lastError()));
            return;
        }
        if (Config.alertLowBalance > 0 && team.balance.compareTo(BigInteger.valueOf(Config.alertLowBalance)) < 0) {
            out.add(new Alert(Alert.Type.LOW_BALANCE, 0, -1, Fmt.eu(team.balance), Fmt.eu(Config.alertLowBalance)));
        }
        if (Config.alertEtaMinutes > 0) {
            Bucket w = team.series.window(300);
            long net = w.avgIn() - w.avgOut();
            if (net < 0 && w.ticks > 0) {
                long etaTicks = team.balance.divide(BigInteger.valueOf(-net))
                    .min(BigInteger.valueOf(Long.MAX_VALUE))
                    .longValue();
                if (etaTicks < Config.alertEtaMinutes * 1200L)
                    out.add(new Alert(Alert.Type.ETA_SHORT, 0, -1, Fmt.duration(etaTicks / 20)));
            }
        }
    }

    private static void connectorAlerts(ConnectorRecord r, List<Alert> out) {
        if (r.chunkLoadFailed) out.add(new Alert(Alert.Type.CHUNK_FAIL, r.id, -1, r.displayName()));
        if (!r.online) return;
        for (int side = 0; side < 6; side++) {
            PortInfo p = r.ports[side];
            Series s = r.portSeries[side];
            if (!p.isWorking() || s == null) continue;
            Bucket minute = s.window(60);
            if (minute.ticks < 1000) continue; // not enough data yet

            if (p.role.supplies() && Config.alertUnderSupplyPercent > 0 && minute.demand.signum() > 0) {
                long pct = minute.out.multiply(BigInteger.valueOf(100))
                    .divide(minute.demand)
                    .longValue();
                if (pct < Config.alertUnderSupplyPercent) out.add(
                    new Alert(Alert.Type.UNDER_SUPPLY, r.id, side, p.target, Fmt.side(side), Math.min(100, pct) + "%"));
            }
            long rate = Port.mul(p.supplyVoltage, p.supplyAmperage);
            if (p.role.supplies() && Config.alertLoadPercent > 0 && rate > 0) {
                long pct = minute.avgOut() * 100 / rate;
                if (pct > Config.alertLoadPercent)
                    out.add(new Alert(Alert.Type.OVERLOAD, r.id, side, p.target, Fmt.side(side), pct + "%"));
            }
            if (Config.alertIdleMinutes > 0) {
                Bucket w = s.window(Config.alertIdleMinutes * 60);
                if (w.ticks >= Config.alertIdleMinutes * 1200L * 9 / 10 && w.in.signum() == 0 && w.out.signum() == 0)
                    out.add(
                        new Alert(Alert.Type.IDLE, r.id, side, p.target, Fmt.side(side), Config.alertIdleMinutes + ""));
            }
        }
    }

    private static void notifyChat(TeamData team, long nowMs) {
        if (!team.chatAlerts || team.alerts.isEmpty()) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        long cooldown = Config.alertChatCooldownSeconds * 1000L;
        List<EntityPlayerMP> members = new ArrayList<>();
        for (Object o : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP p = (EntityPlayerMP) o;
            if (Objects.equals(GTWirelessBackend.INSTANCE.resolveTeam(p.getUniqueID()), team.leader)) members.add(p);
        }
        if (members.isEmpty()) return;
        for (Alert a : team.alerts) {
            Long last = team.lastChat.get(a.key());
            if (last != null && nowMs - last < cooldown) continue;
            team.lastChat.put(a.key(), nowMs);
            Object[] args = new Object[a.args.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = a.args[i].startsWith(Fmt.LANG)
                    ? new ChatComponentTranslation(a.args[i].substring(Fmt.LANG.length()))
                    : a.args[i];
            }
            IChatComponent msg = new ChatComponentText("[FluxLite] ")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
            msg.appendSibling(
                new ChatComponentTranslation(a.type.langKey(), args)
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
            for (EntityPlayerMP p : members) p.addChatMessage(msg);
        }
        team.lastChat.values()
            .removeIf(t -> nowMs - t > cooldown * 4);
    }
}
