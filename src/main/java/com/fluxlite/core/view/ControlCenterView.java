package com.fluxlite.core.view;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxlite.Config;
import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.backend.SteamNetwork;
import com.fluxlite.core.MachineSample;
import com.fluxlite.core.Port;
import com.fluxlite.core.PortRole;
import com.fluxlite.core.PortStatus;
import com.fluxlite.core.alert.Alert;
import com.fluxlite.core.registry.ConnectorRecord;
import com.fluxlite.core.registry.PortInfo;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.core.registry.TeamData;
import com.fluxlite.core.stats.Bucket;
import com.fluxlite.core.stats.Series;
import com.fluxlite.net.Kinds;
import com.fluxlite.net.ServerPackets;
import com.fluxlite.tile.TileControlCenter;
import com.fluxlite.util.Fmt;
import com.fluxlite.util.Longs;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/**
 * Server side of the control center and the handheld terminal (which has no block: {@code cc} is null). The client
 * asks for one tab at a time and only gets what it shows.
 */
public final class ControlCenterView {

    public static final int PAGE_OVERVIEW = 0, PAGE_DEVICES = 1, PAGE_ALERTS = 2, PAGE_SETTINGS = 3;
    public static final int SORT_NAME = 0, SORT_ROLE = 1, SORT_TIER = 2, SORT_NOW = 3, SORT_PEAK = 4, SORT_TOTAL = 5,
        SORT_STATUS = 6;

    private ControlCenterView() {}

    private static UUID viewedTeam(EntityPlayerMP player, NBTTagCompound q) {
        if (q.hasKey("team") && ServerPackets.isOp(player)) {
            try {
                return UUID.fromString(q.getString("team"));
            } catch (IllegalArgumentException ignored) {}
        }
        return GTWirelessBackend.INSTANCE.resolveTeam(player.getUniqueID());
    }

    /** @param cc the control center, or null for the handheld terminal */
    public static NBTTagCompound build(EntityPlayerMP player, TileControlCenter cc, NBTTagCompound q) {
        NBTTagCompound out = new NBTTagCompound();
        Registry reg = Registry.get();
        if (reg == null) return out;
        UUID team = viewedTeam(player, q);
        TeamData td = reg.team(team);
        List<ConnectorRecord> records = reg.forTeam(team);
        int page = q.getInteger("page");
        String detail = q.getString("detail");

        out.setBoolean("ok", true);
        out.setBoolean("hand", cc == null);
        out.setInteger("page", page);
        out.setString("detail", detail);
        out.setString("team", team.toString());
        out.setString("teamName", teamName(team, records));
        int online = 0;
        for (ConnectorRecord r : records) if (r.online) online++;
        out.setInteger("online", online);
        out.setInteger("connectors", records.size());
        out.setInteger("alerts", td.alerts.size());
        boolean op = ServerPackets.isOp(player);
        out.setBoolean("op", op);
        if (op) out.setTag("teams", teamList(reg));

        if (!detail.isEmpty()) {
            detail(out, td, reg, records, detail, q.getInteger("drange"));
            return out;
        }
        switch (page) {
            case PAGE_DEVICES -> devices(out, records, q);
            case PAGE_ALERTS -> alerts(out, td);
            case PAGE_SETTINGS -> settings(out, td, player, cc);
            default -> overview(out, td, records, q.getInteger("range"));
        }
        return out;
    }

    /** @param cc the control center, or null for the handheld terminal (then only team settings can change) */
    public static void action(EntityPlayerMP player, TileControlCenter cc, NBTTagCompound q) {
        Registry reg = Registry.get();
        if (reg == null) return;
        UUID team = viewedTeam(player, q);
        boolean member = Objects.equals(GTWirelessBackend.INSTANCE.resolveTeam(player.getUniqueID()), team)
            || ServerPackets.isOp(player);
        if (!member) return;
        int act = q.getInteger("action");
        if (act == Kinds.ACT_CHAT || act == Kinds.ACT_ALERTS) {
            TeamData td = reg.team(team);
            if (act == Kinds.ACT_CHAT) td.chatAlerts = !td.chatAlerts;
            else {
                td.alertsOn = !td.alertsOn;
                // gone at once, not with the next evaluation
                if (!td.alertsOn) td.alerts.clear();
            }
            reg.markDirty();
            return;
        }
        // everything else belongs to the block
        if (cc == null || !(ServerPackets.sameTeam(player, cc.owner) || ServerPackets.isOp(player))) return;
        switch (act) {
            case Kinds.ACT_REDSTONE -> cc.setRedstoneOnAlert(!cc.redstoneOnAlert);
            case Kinds.ACT_HOLOGRAM -> cc.setHologram(!cc.hologram);
            case Kinds.ACT_HOLO_SIZE -> cc.setHoloSize(q.getInteger("value"));
            case Kinds.ACT_HOLO_OPAQUE -> cc.setHoloOpaque(!cc.holoOpaque);
            default -> {}
        }
    }

    static String teamName(UUID team, List<ConnectorRecord> records) {
        for (ConnectorRecord r : records)
            if (team.equals(r.owner) && r.ownerName != null && !r.ownerName.isEmpty()) return r.ownerName;
        try {
            String n = SpaceProjectManager.getPlayerNameFromUUID(team);
            if (n != null && !n.startsWith("ERROR")) return n;
        } catch (Throwable ignored) {}
        return team.toString()
            .substring(0, 8);
    }

    private static NBTTagList teamList(Registry reg) {
        Set<UUID> seen = new HashSet<>();
        NBTTagList l = new NBTTagList();
        for (ConnectorRecord r : reg.all()) {
            if (r.owner == null) continue;
            UUID t = GTWirelessBackend.INSTANCE.resolveTeam(r.owner);
            if (!seen.add(t)) continue;
            NBTTagCompound e = new NBTTagCompound();
            e.setString("id", t.toString());
            e.setString("name", teamName(t, reg.forTeam(t)));
            l.appendTag(e);
        }
        return l;
    }

    // ------------------------------------------------------------------ overview

    private static void overview(NBTTagCompound out, TeamData td, List<ConnectorRecord> records, int range) {
        Series s = td.series;
        s.keepTicks();
        out.setLong("in", s.rateIn());
        out.setLong("out", s.rateOut());
        BigInteger balance = GTWirelessBackend.INSTANCE.getBalance(td.leader);
        out.setString("balance", balance.toString());
        // time to empty is a forecast, so it uses the last minute rather than one noisy tick
        Bucket m = s.window(60);
        long net = m.avgIn() - m.avgOut();
        long eta = -1;
        if (net < 0) eta = balance.divide(BigInteger.valueOf(-net * 20))
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
        out.setLong("eta", eta);
        curve(out, s, range, 24);
        steam(out, td, records);

        List<DeviceRow> rows = collect(records);
        out.setTag("topOut", top(rows, false));
        out.setTag("topIn", top(rows, true));
    }

    /** The team's steam network: shown when it holds steam or a connector moves steam. */
    static void steam(NBTTagCompound out, TeamData td, List<ConnectorRecord> records) {
        SteamNetwork net = SteamNetwork.get();
        BigInteger steam = net != null ? net.balanceOf(td.leader) : BigInteger.ZERO;
        long in = td.steamSeries.rateIn(), o = td.steamSeries.rateOut();
        boolean on = steam.signum() > 0 || in > 0 || o > 0;
        for (ConnectorRecord r : records)
            for (int i = 6; i < Port.COUNT && !on; i++) on = r.ports[i].role != PortRole.NONE;
        out.setBoolean("steamOn", on);
        out.setString("steam", steam.toString());
        out.setLong("sin", in);
        out.setLong("sout", o);
    }

    /** range 0 = per tick, 1 = per minute (1 h), 2 = per hour (last {@code hours}). */
    private static void curve(NBTTagCompound out, Series s, int range, int hours) {
        long[][] c = range == 0 ? s.tickCurve() : s.curve(range == 1 ? 1 : 2);
        long[] in = c[0], o = c[1];
        if (range == 2 && in.length > hours) {
            in = java.util.Arrays.copyOfRange(in, in.length - hours, in.length);
            o = java.util.Arrays.copyOfRange(o, o.length - hours, o.length);
        }
        out.setIntArray("cin", Longs.pack(in));
        out.setIntArray("cout", Longs.pack(o));
    }

    private static NBTTagList top(List<DeviceRow> rows, boolean input) {
        List<DeviceRow> l = new ArrayList<>();
        for (DeviceRow d : rows) {
            // the machines on a cable are listed individually; steam is not EU
            if (d.group || d.steam) continue;
            long v = input ? d.nowIn : d.nowOut;
            if (v > 0) l.add(d);
        }
        l.sort(
            Comparator.comparingLong((DeviceRow d) -> input ? d.nowIn : d.nowOut)
                .reversed());
        NBTTagList t = new NBTTagList();
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            DeviceRow d = l.get(i);
            NBTTagCompound e = new NBTTagCompound();
            e.setString("k", d.key);
            e.setString("n", d.name);
            e.setLong("v", input ? d.nowIn : d.nowOut);
            t.appendTag(e);
        }
        return t;
    }

    // ------------------------------------------------------------------ devices

    private static int roleCode(PortRole role) {
        return switch (role) {
            case INPUT -> 1;
            case OUTPUT -> 2;
            case BOTH -> 3;
            default -> 0;
        };
    }

    public static List<DeviceRow> collect(List<ConnectorRecord> records) {
        List<DeviceRow> rows = new ArrayList<>();
        for (ConnectorRecord r : records) {
            // a cable face with a single machine on it stands for that machine (metered exactly, not sampled)
            Set<Long> shownByFace = new HashSet<>();
            for (int idx = 0; idx < Port.COUNT; idx++) {
                PortInfo p = r.ports[idx];
                // working faces, plus faces with a device that are switched off or cannot be fed safely
                boolean problem = (p.status == PortStatus.DISABLED || p.status == PortStatus.UNKNOWN_SPEC)
                    && p.target != null
                    && !p.target.isEmpty();
                if (p.role == PortRole.NONE && !problem) continue;
                boolean steam = idx >= 6;
                int side = idx % 6;
                Series s = r.portSeries[idx];
                ForgeDirection dir = ForgeDirection.getOrientation(side);
                DeviceRow d = new DeviceRow();
                d.key = "p:" + r.id + ":" + idx;
                d.name = p.target == null || p.target.isEmpty() ? "?" : p.target;
                d.conn = r.displayName();
                d.connId = r.id;
                d.dim = r.dim;
                if (p.at != null) {
                    d.x = p.at[0];
                    d.y = p.at[1];
                    d.z = p.at[2];
                    if (!steam) shownByFace.add(MachineSample.posKey(d.x, d.y, d.z));
                } else {
                    d.x = r.x + dir.offsetX;
                    d.y = r.y + dir.offsetY;
                    d.z = r.z + dir.offsetZ;
                }
                d.side = side;
                d.role = roleCode(p.role);
                d.steam = steam;
                d.voltage = steam ? 0 : p.voltage();
                d.unit = steam ? null : p.unitTag;
                d.tier = steam ? -1 : Fmt.tierIndex(d.voltage);
                d.online = r.online && p.isWorking();
                d.cable = p.cable;
                d.group = p.cable && p.devices != 1;
                d.kind = steam ? DeviceRow.KIND_STEAM : p.cable ? DeviceRow.KIND_CABLE : DeviceRow.KIND_DIRECT;
                fill(d, s);
                if (p.status == PortStatus.DISABLED) d.status = DeviceRow.ST_OFF;
                else if (!r.online) d.status = DeviceRow.ST_OFFLINE;
                else if (p.status == PortStatus.UNKNOWN_SPEC) d.status = DeviceRow.ST_ERROR;
                else d.status = d.now() > 0 ? DeviceRow.ST_RUNNING : DeviceRow.ST_IDLE;
                rows.add(d);
            }
            for (Map.Entry<Long, ConnectorRecord.Sampled> e : r.samples.entrySet()) {
                MachineSample m = e.getValue().last;
                if (m == null || shownByFace.contains(e.getKey())) continue;
                DeviceRow d = new DeviceRow();
                d.key = "m:" + r.id + ":" + e.getKey();
                d.name = m.name;
                d.conn = r.displayName();
                d.connId = r.id;
                d.dim = r.dim;
                d.x = m.x;
                d.y = m.y;
                d.z = m.z;
                d.side = e.getValue().side;
                // a machine that consumes is fed by the network (output); a generator feeds it (input)
                d.role = m.consumer ? 2 : 1;
                d.voltage = m.voltage;
                d.tier = m.tier;
                d.sampled = true;
                d.online = r.online;
                d.kind = DeviceRow.KIND_BEHIND;
                if (m.capacity > 0) d.fill = (int) Math.max(0, Math.min(1000, m.stored * 1000 / m.capacity));
                fill(d, e.getValue().series);
                d.status = !r.online ? DeviceRow.ST_OFFLINE : d.now() > 0 ? DeviceRow.ST_RUNNING : DeviceRow.ST_IDLE;
                rows.add(d);
            }
        }
        return rows;
    }

    private static void fill(DeviceRow d, Series s) {
        if (s == null) return;
        d.nowIn = s.rateIn();
        d.nowOut = s.rateOut();
        Bucket hour = s.window(3600);
        d.peak = d.role == 1 ? hour.peakIn : d.role == 2 ? hour.peakOut : Math.max(hour.peakIn, hour.peakOut);
        d.total = d.role == 2 ? s.totalOut : s.totalIn;
    }

    private static Comparator<DeviceRow> comparator(int col) {
        return switch (col) {
            case SORT_ROLE -> Comparator.comparingInt(d -> d.role);
            case SORT_TIER -> Comparator.comparingInt(d -> d.tier);
            case SORT_NOW -> Comparator.comparingLong(DeviceRow::now);
            case SORT_PEAK -> Comparator.comparingLong(d -> d.peak);
            case SORT_TOTAL -> Comparator.comparing(d -> d.total);
            case SORT_STATUS -> Comparator.comparingInt(d -> d.status);
            default -> Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT));
        };
    }

    /** The device filters of a query; -1 (0 for role and connector) means "any". */
    static final class Filter {

        int role, status = -1, tier = -1, kind = -1;
        long conn;
        String search = "";

        static Filter of(NBTTagCompound q) {
            Filter f = new Filter();
            f.role = q.getInteger("filter");
            if (q.hasKey("status")) f.status = q.getInteger("status");
            if (q.hasKey("tier")) f.tier = q.getInteger("tier");
            if (q.hasKey("kind")) f.kind = q.getInteger("kind");
            f.conn = q.getLong("conn");
            f.search = q.getString("search")
                .toLowerCase(Locale.ROOT)
                .trim();
            return f;
        }

        boolean keeps(DeviceRow d) {
            if (role != 0 && d.role != role) return false;
            if (status >= 0 && d.status != status) return false;
            if (tier >= 0 && d.tier != tier) return false;
            if (kind >= 0 && d.kind != kind) return false;
            if (conn != 0 && d.connId != conn) return false;
            if (search.isEmpty()) return true;
            return d.name.toLowerCase(Locale.ROOT)
                .contains(search)
                || d.conn.toLowerCase(Locale.ROOT)
                    .contains(search);
        }
    }

    static void devices(NBTTagCompound out, List<ConnectorRecord> records, NBTTagCompound q) {
        List<DeviceRow> rows = collect(records);
        Filter f = Filter.of(q);
        List<DeviceRow> kept = new ArrayList<>();
        for (DeviceRow d : rows) if (f.keeps(d)) kept.add(d);
        Comparator<DeviceRow> cmp = comparator(q.getInteger("sort"));
        if (q.getBoolean("desc")) cmp = cmp.reversed();
        kept.sort(cmp.thenComparing(d -> d.key));
        int rowsWanted = Math.max(1, Math.min(60, q.getInteger("rows")));
        int offset = Math.max(0, Math.min(q.getInteger("offset"), Math.max(0, kept.size() - rowsWanted)));
        NBTTagList l = new NBTTagList();
        for (int i = offset; i < Math.min(kept.size(), offset + rowsWanted); i++) l.appendTag(
            kept.get(i)
                .write());
        out.setTag("rows", l);
        out.setInteger("count", kept.size());
        out.setInteger("all", rows.size());
        out.setInteger("offset", offset);

        // totals of what is shown; machines behind a cable are already part of their cable's flow; steam apart
        boolean behind = f.kind == DeviceRow.KIND_BEHIND;
        long sumIn = 0, sumOut = 0, steamIn = 0, steamOut = 0;
        boolean anySteam = false;
        for (DeviceRow d : kept) {
            if (d.steam) {
                anySteam = true;
                steamIn = sat(steamIn, d.nowIn);
                steamOut = sat(steamOut, d.nowOut);
                continue;
            }
            if ((d.kind == DeviceRow.KIND_BEHIND) != behind) continue;
            sumIn = sat(sumIn, d.nowIn);
            sumOut = sat(sumOut, d.nowOut);
        }
        out.setLong("sumIn", sumIn);
        out.setLong("sumOut", sumOut);
        out.setBoolean("hasSteam", anySteam);
        out.setLong("sumSin", steamIn);
        out.setLong("sumSout", steamOut);

        // what the filter menus offer: count per status, tiers present, connectors
        int[] statusCount = new int[5];
        TreeSet<Integer> tiers = new TreeSet<>();
        for (DeviceRow d : rows) {
            if (d.status >= 0 && d.status < statusCount.length) statusCount[d.status]++;
            if (d.tier >= 0) tiers.add(d.tier);
        }
        out.setIntArray("statusCount", statusCount);
        int[] tierArr = new int[tiers.size()];
        int i = 0;
        for (int t : tiers) tierArr[i++] = t;
        out.setIntArray("tiers", tierArr);
        NBTTagList conns = new NBTTagList();
        List<ConnectorRecord> sorted = new ArrayList<>(records);
        sorted.sort(
            Comparator.comparing(
                (ConnectorRecord r) -> r.displayName()
                    .toLowerCase(Locale.ROOT))
                .thenComparingLong(r -> r.id));
        for (ConnectorRecord r : sorted) {
            NBTTagCompound e = new NBTTagCompound();
            e.setLong("id", r.id);
            e.setString("n", r.displayName());
            e.setBoolean("on", r.online);
            conns.appendTag(e);
        }
        out.setTag("conns", conns);
    }

    private static long sat(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }

    // ------------------------------------------------------------------ detail

    private static void detail(NBTTagCompound out, TeamData td, Registry reg, List<ConnectorRecord> records, String key,
        int range) {
        Series s = null;
        int role = 1;
        boolean steam = false;
        String[] parts = key.split(":");
        long nowMs = System.currentTimeMillis();
        try {
            if (key.equals("t")) {
                s = td.series;
                role = 3;
                out.setString("name", "@team");
            } else if (key.equals("ts")) {
                s = td.steamSeries;
                role = 3;
                steam = true;
                out.setString("name", "@steam");
            } else if (parts.length == 3) {
                ConnectorRecord r = reg.byId(Long.parseLong(parts[1]));
                if (r == null || !records.contains(r)) return;
                out.setString("conn", r.displayName());
                out.setInteger("dim", r.dim);
                out.setBoolean("online", r.online);
                if (parts[0].equals("p")) {
                    int idx = Integer.parseInt(parts[2]);
                    if (idx < 0 || idx >= Port.COUNT) return;
                    int side = idx % 6;
                    steam = idx >= 6;
                    PortInfo p = r.ports[idx];
                    s = r.portSeries(idx);
                    role = p.role == PortRole.INPUT ? 1 : p.role == PortRole.OUTPUT ? 2 : 3;
                    ForgeDirection dir = ForgeDirection.getOrientation(side);
                    out.setString("name", p.target);
                    out.setInteger("side", side);
                    out.setIntArray(
                        "pos",
                        p.at != null ? p.at : new int[] { r.x + dir.offsetX, r.y + dir.offsetY, r.z + dir.offsetZ });
                    if (!steam) {
                        out.setLong("v", p.voltage());
                        out.setLong("a", p.role.supplies() ? p.supplyAmperage : p.collectAmperage);
                    }
                } else if (parts[0].equals("m")) {
                    ConnectorRecord.Sampled sm = r.samples.get(Long.parseLong(parts[2]));
                    if (sm == null || sm.last == null) return;
                    s = sm.series;
                    role = sm.last.consumer ? 2 : 1;
                    out.setString("name", sm.last.name);
                    out.setBoolean("sampled", true);
                    out.setInteger("side", sm.side);
                    out.setIntArray("pos", new int[] { sm.last.x, sm.last.y, sm.last.z });
                    out.setLong("v", sm.last.voltage);
                    out.setLong("a", sm.last.amperage);
                    if (sm.last.capacity > 0) {
                        out.setLong("stored", sm.last.stored);
                        out.setLong("cap", sm.last.capacity);
                    }
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return;
        }
        if (s == null) return;
        if (!out.hasKey("online")) out.setBoolean("online", true);
        out.setBoolean("steam", steam);
        out.setByte("role", (byte) role);
        out.setLong("ni", s.rateIn());
        out.setLong("no", s.rateOut());
        if (range == 0) s.watchTicks(nowMs);
        curve(out, s, range, 72);

        Bucket hour = s.window(3600);
        boolean in = role == 1 || role == 3 && hour.peakIn >= hour.peakOut;
        long peak = in ? hour.peakIn : hour.peakOut;
        long at = in ? hour.peakInAt : hour.peakOutAt;
        out.setLong("peak", peak);
        out.setLong("peakAgo", at > 0 && peak > 0 ? Math.max(0, (nowMs - at) / 1000) : -1);
        Bucket m = s.window(60);
        out.setLong("duty", m.ticks > 0 ? m.activeTicks * 1000 / m.ticks : -1);
        long sat = -1;
        if (role != 1 && m.demand.signum() > 0) sat = m.out.multiply(BigInteger.valueOf(1000))
            .divide(m.demand)
            .min(BigInteger.valueOf(1000))
            .longValue();
        out.setLong("sat", sat);
        out.setString("total", (role == 2 ? s.totalOut : s.totalIn).toString());
        out.setString("today", (role == 2 ? s.todayOut : s.todayIn).toString());
        long[][] hod = s.hourOfDay();
        out.setIntArray("hod", Longs.pack(role == 2 ? hod[1] : hod[0]));
    }

    // ------------------------------------------------------------------ alerts & settings

    private static void alerts(NBTTagCompound out, TeamData td) {
        out.setBoolean("alertsOn", td.alertsOn);
        NBTTagList l = new NBTTagList();
        for (Alert a : td.alerts) l.appendTag(a.write());
        out.setTag("alertList", l);
    }

    /** Team settings, plus the block's own ones when opened from a control center. */
    private static void settings(NBTTagCompound out, TeamData td, EntityPlayerMP player, TileControlCenter cc) {
        out.setBoolean("alertsOn", td.alertsOn);
        out.setBoolean("chat", td.chatAlerts);
        if (cc == null) return;
        out.setBoolean("redstone", cc.redstoneOnAlert);
        out.setBoolean("holo", cc.hologram);
        out.setInteger("holoSize", cc.holoSize);
        out.setBoolean("holoOpaque", cc.holoOpaque);
        out.setBoolean("holoAllowed", Config.hologramRange > 0);
        out.setBoolean("ccOwner", ServerPackets.sameTeam(player, cc.owner) || ServerPackets.isOp(player));
    }
}
