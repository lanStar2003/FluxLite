package com.fluxlite.gui;

import java.math.BigInteger;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxlite.core.alert.Alert;
import com.fluxlite.gui.ui.Canvas;
import com.fluxlite.gui.ui.Theme;
import com.fluxlite.gui.ui.UiScreen;
import com.fluxlite.net.Kinds;
import com.fluxlite.util.Fmt;
import com.fluxlite.util.Longs;

/**
 * Team dashboard. Three tabs (overview, devices, alerts) and a detail sheet for one device. Live numbers are the
 * current EU/t (the last second, shown at once when a flow starts); only the 1 h / 24 h charts are per-minute /
 * per-hour averages. The device list can be narrowed by direction, status, voltage, connector and kind.
 */
public class ControlCenterScreen extends UiScreen {

    public static final int TAB_OVERVIEW = 0, TAB_DEVICES = 1, TAB_ALERTS = 2;
    public static final int SORT_NAME = 0, SORT_ROLE = 1, SORT_TIER = 2, SORT_NOW = 3, SORT_PEAK = 4, SORT_TOTAL = 5;
    private static final int ROW = 22;

    private final int dim, bx, by, bz;
    private NBTTagCompound data;
    private int tab = TAB_OVERVIEW;
    private int range; // overview chart: 0 live, 1 hour, 2 day
    private int filter; // 0 all, 1 input, 2 output, 3 both
    private int statusFilter = -1, tierFilter = -1, kindFilter = -1;
    private long connFilter;
    private int sort = SORT_NOW;
    private boolean desc = true;
    private int offset;
    private int visibleRows = 8;
    private String detailKey = "";
    private int detailRange;
    private String team;
    private final TextInput search = new TextInput(40, s -> {
        offset = 0;
        request();
    });

    public ControlCenterScreen(int dim, int x, int y, int z) {
        this.dim = dim;
        this.bx = x;
        this.by = y;
        this.bz = z;
    }

    // ------------------------------------------------------------------ networking

    private NBTTagCompound pos() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("dim", dim);
        t.setInteger("x", bx);
        t.setInteger("y", by);
        t.setInteger("z", bz);
        if (team != null) t.setString("team", team);
        return t;
    }

    private void request() {
        NBTTagCompound q = pos();
        q.setInteger("page", tab);
        q.setInteger("range", range);
        q.setInteger("filter", filter);
        if (statusFilter >= 0) q.setInteger("status", statusFilter);
        if (tierFilter >= 0) q.setInteger("tier", tierFilter);
        if (kindFilter >= 0) q.setInteger("kind", kindFilter);
        if (connFilter != 0) q.setLong("conn", connFilter);
        q.setString("search", search.text);
        q.setInteger("sort", sort);
        q.setBoolean("desc", desc);
        q.setInteger("offset", offset);
        q.setInteger("rows", visibleRows);
        q.setString("detail", detailKey);
        q.setInteger("drange", detailRange);
        host.send(Kinds.CC_REQUEST, q);
    }

    private void action(int action) {
        NBTTagCompound t = pos();
        t.setInteger("action", action);
        host.send(Kinds.CC_ACTION, t);
        request();
    }

    @Override
    public void tick() {
        if (ticks % 5 == 0) request();
        super.tick();
    }

    @Override
    public void onData(NBTTagCompound d) {
        if (d.getInteger("page") != tab || !d.getString("detail")
            .equals(detailKey)) return;
        data = d;
    }

    private void go(int newTab) {
        if (tab == newTab && detailKey.isEmpty()) return;
        tab = newTab;
        detailKey = "";
        offset = 0;
        data = null;
        request();
    }

    private void openDetail(String key) {
        detailKey = key;
        detailRange = 0;
        data = null;
        request();
    }

    private void refilter() {
        offset = 0;
        request();
    }

    @Override
    public void scroll(int direction) {
        if (tab != TAB_DEVICES || !detailKey.isEmpty() || data == null) return;
        int count = data.getInteger("count");
        int max = Math.max(0, count - visibleRows);
        int next = Math.max(0, Math.min(max, offset - direction * 3));
        if (next != offset) {
            offset = next;
            request();
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void render(Canvas c) {
        scrim(c);
        float w = Math.min(width - 16, 480);
        float h = Math.min(height - 16, 300);
        float x = (width - w) / 2f, y = (height - h) / 2f;
        window(c, x, y, w, h);
        closeButton(c, x + w - 13, y + 13);

        bold(c, tr("tile.fluxlite.control_center.name"), x + 14, y + 10, Theme.LABEL);
        if (data != null && data.getBoolean("ok")) {
            String sub = tr(
                "fluxlite.gui.cc.subtitle",
                data.getString("teamName"),
                data.getInteger("online"),
                data.getInteger("connectors"));
            float sw = text(c, sub, x + 14, y + 22, Theme.LABEL2);
            if (data.getBoolean("op") && data.getTagList("teams", 10)
                .tagCount() > 1) {
                String label = tr("fluxlite.gui.switch_team");
                float lx = x + 14 + sw + 8, lw = c.width(label, 1);
                text(c, label, lx, y + 22, hover(lx, y + 20, lw, 11) ? Theme.LABEL : Theme.BLUE);
                onClick(lx, y + 20, lw, 11, this::nextTeam);
            }
        }
        String[] tabs = { tr("fluxlite.gui.tab.overview"), tr("fluxlite.gui.tab.devices"),
            tr("fluxlite.gui.tab.alerts") };
        int[] badges = { 0, 0, data == null ? 0 : data.getInteger("alerts") };
        float segW = segmentedWidth(c, tabs, badges);
        segmented(c, x + w - 28 - segW, y + 12, tabs, tab, this::go, badges);

        float cx = x + 14, cy = y + 38, cw = w - 28, ch = h - 38 - 12;
        if (data == null) {
            textCenter(c, tr("fluxlite.gui.loading"), cx + cw / 2, cy + ch / 2 - 4, Theme.LABEL2, 1);
            return;
        }
        if (!detailKey.isEmpty()) {
            detail(c, cx, cy, cw, ch);
            return;
        }
        switch (tab) {
            case TAB_DEVICES -> devices(c, cx, cy, cw, ch);
            case TAB_ALERTS -> alerts(c, cx, cy, cw, ch);
            default -> overview(c, cx, cy, cw, ch);
        }
        menus(c);
    }

    private void nextTeam() {
        NBTTagList teams = data.getTagList("teams", 10);
        String cur = data.getString("team");
        int idx = -1;
        for (int i = 0; i < teams.tagCount(); i++) if (teams.getCompoundTagAt(i)
            .getString("id")
            .equals(cur)) idx = i;
        team = teams.getCompoundTagAt((idx + 1) % teams.tagCount())
            .getString("id");
        data = null;
        request();
    }

    private static BigInteger big(String s) {
        try {
            return new BigInteger(s);
        } catch (NumberFormatException e) {
            return BigInteger.ZERO;
        }
    }

    // ------------------------------------------------------------------ shared pieces

    private void stat(Canvas c, float x, float y, float w, float h, String label, int dot, String value, String unit,
        int valueColor, String sub, int subColor) {
        card(c, x, y, w, h);
        float lx = x + 9;
        if (dot != 0) {
            c.circle(x + 11.5f, y + 9.5f, 2.5f, dot);
            lx = x + 17;
        }
        text(c, fit(c, label, w - (lx - x) - 8), lx, y + 6, Theme.LABEL2);
        value(c, value, unit, x + 8, y + 16, valueColor, 2);
        if (sub != null) text(c, fit(c, sub, w - 16), x + 9, y + h - 11, subColor);
    }

    private float legend(Canvas c, float x, float y, int color, String label) {
        c.circle(x + 2.5f, y + 3.5f, 2.5f, color);
        return x + 8 + text(c, label, x + 8, y, Theme.LABEL2);
    }

    private void rolePill(Canvas c, float x, float y, int role) {
        rolePill(c, x, y, role, -1);
    }

    private void rolePill(Canvas c, float x, float y, int role, int status) {
        switch (role) {
            case 1 -> pill(c, x, y, tr("fluxlite.role.input"), Theme.INPUT);
            case 2 -> pill(c, x, y, tr("fluxlite.role.output"), Theme.OUTPUT);
            case 3 -> pill(c, x, y, tr("fluxlite.role.both"), Theme.BOTH);
            default -> {
                if (status == ST_OFF) pill(c, x, y, tr("fluxlite.gui.st.3"), Theme.GRAY);
                else if (status == ST_ERROR) pill(c, x, y, tr("fluxlite.gui.st.4"), Theme.YELLOW);
                else pill(c, x, y, tr("fluxlite.role.none"), Theme.GRAY);
            }
        }
    }

    // same codes as the server's DeviceRow
    private static final int ST_RUNNING = 0, ST_IDLE = 1, ST_OFFLINE = 2, ST_OFF = 3, ST_ERROR = 4;

    private static int statusColor(int st) {
        return switch (st) {
            case ST_RUNNING -> Theme.GREEN;
            case ST_OFFLINE -> Theme.RED;
            case ST_ERROR -> Theme.YELLOW;
            case ST_OFF -> Theme.LABEL4;
            default -> Theme.GRAY;
        };
    }

    // ------------------------------------------------------------------ overview

    private void overview(Canvas c, float x, float y, float w, float h) {
        long in = data.getLong("in"), out = data.getLong("out"), net = in - out;
        BigInteger bal = big(data.getString("balance"));
        long eta = data.getLong("eta");
        float gap = 6, sw = (w - gap * 3) / 4, sh = 46;
        stat(
            c,
            x,
            y,
            sw,
            sh,
            tr("fluxlite.gui.input"),
            Theme.INPUT,
            Fmt.si(in),
            "EU/t",
            Theme.LABEL,
            tr("fluxlite.gui.input.sub"),
            Theme.LABEL3);
        stat(
            c,
            x + (sw + gap),
            y,
            sw,
            sh,
            tr("fluxlite.gui.output"),
            Theme.OUTPUT,
            Fmt.si(out),
            "EU/t",
            Theme.LABEL,
            tr("fluxlite.gui.output.sub"),
            Theme.LABEL3);
        stat(
            c,
            x + 2 * (sw + gap),
            y,
            sw,
            sh,
            tr("fluxlite.gui.net"),
            0,
            (net > 0 ? "+" : "") + Fmt.si(net),
            "EU/t",
            net > 0 ? Theme.GREEN : net < 0 ? Theme.RED : Theme.LABEL,
            tr(net >= 0 ? "fluxlite.gui.surplus" : "fluxlite.gui.deficit"),
            Theme.LABEL3);
        String etaText = eta < 0 ? tr("fluxlite.gui.eta.none") : tr("fluxlite.gui.eta.left", Fmt.duration(eta));
        stat(
            c,
            x + 3 * (sw + gap),
            y,
            sw,
            sh,
            tr("fluxlite.gui.balance"),
            0,
            Fmt.si(bal),
            "EU",
            Theme.LABEL,
            etaText,
            eta >= 0 && eta < 600 ? Theme.RED : Theme.LABEL3);

        // top lists get three rows when there is room, two otherwise
        int topRows = h >= 230 ? 3 : 2;
        float listH = 24 + topRows * 15;
        float chy = y + sh + gap, chh = h - sh - gap - listH - gap;
        card(c, x, chy, w, chh);
        float lx = x + 10 + bold(c, tr("fluxlite.gui.flow"), x + 10, chy + 8, Theme.LABEL) + 10;
        lx = legend(c, lx, chy + 8, Theme.INPUT, tr("fluxlite.gui.input"));
        legend(c, lx + 8, chy + 8, Theme.OUTPUT, tr("fluxlite.gui.output"));
        String[] ranges = { tr("fluxlite.gui.range.live"), tr("fluxlite.gui.range.hour"),
            tr("fluxlite.gui.range.day") };
        float rw = segmentedWidth(c, ranges);
        segmented(c, x + w - 7 - rw, chy + 5, ranges, range, i -> {
            range = i;
            request();
        });
        long[] cin = Longs.unpack(data.getIntArray("cin")), cout = Longs.unpack(data.getIntArray("cout"));
        boolean note = chh >= 100;
        float plotY = chy + 30, plotH = chh - 30 - (note ? 18 : 9);
        if (cin.length == 0) textCenter(c, tr("fluxlite.gui.no_data"), x + w / 2, chy + chh / 2, Theme.LABEL3, 1);
        else chart(c, x + 10, plotY, w - 18, plotH, cin, Theme.INPUT, cout, Theme.OUTPUT);
        if (note) text(c, tr("fluxlite.gui.range.note." + range), x + 10, chy + chh - 12, Theme.LABEL3);

        float ly = chy + chh + gap, lw = (w - gap) / 2;
        top(c, x, ly, lw, listH, topRows, tr("fluxlite.gui.top_out"), data.getTagList("topOut", 10), Theme.OUTPUT);
        top(
            c,
            x + lw + gap,
            ly,
            lw,
            listH,
            topRows,
            tr("fluxlite.gui.top_in"),
            data.getTagList("topIn", 10),
            Theme.INPUT);
    }

    private void top(Canvas c, float x, float y, float w, float h, int maxRows, String title, NBTTagList rows,
        int color) {
        card(c, x, y, w, h);
        bold(c, title, x + 10, y + 7, Theme.LABEL);
        if (rows.tagCount() == 0) {
            text(c, tr("fluxlite.gui.nothing_running"), x + 10, y + 24, Theme.LABEL3);
            return;
        }
        long max = 1;
        for (int i = 0; i < rows.tagCount(); i++) max = Math.max(
            max,
            rows.getCompoundTagAt(i)
                .getLong("v"));
        for (int i = 0; i < Math.min(maxRows, rows.tagCount()); i++) {
            NBTTagCompound r = rows.getCompoundTagAt(i);
            float ry = y + 22 + i * 15;
            if (hover(x + 4, ry - 3, w - 8, 14)) c.round(x + 4, ry - 3, w - 8, 14, 4, Theme.CARD_HOVER);
            String val = Fmt.si(r.getLong("v"));
            float vw = c.width(val, 1);
            text(c, fit(c, r.getString("n"), w * 0.45f), x + 10, ry, Theme.LABEL);
            float bx0 = x + 10 + w * 0.47f, bw = w - 20 - w * 0.47f - vw - 8;
            c.round(bx0, ry + 2.5f, bw, 3, 1.5f, Theme.LABEL4);
            c.round(bx0, ry + 2.5f, Math.max(3, bw * r.getLong("v") / max), 3, 1.5f, color);
            textRight(c, val, x + w - 10, ry, Theme.LABEL2);
            String key = r.getString("k");
            onClick(x + 4, ry - 3, w - 8, 14, () -> openDetail(key));
        }
    }

    // ------------------------------------------------------------------ devices

    private boolean filtered() {
        return filter != 0 || statusFilter >= 0
            || tierFilter >= 0
            || kindFilter >= 0
            || connFilter != 0
            || !search.text.isEmpty();
    }

    private void devices(Canvas c, float x, float y, float w, float h) {
        String[] roles = { tr("fluxlite.gui.filter.all"), tr("fluxlite.gui.input"), tr("fluxlite.gui.output"),
            tr("fluxlite.role.both") };
        segmented(c, x, y, roles, filter, i -> {
            filter = i;
            refilter();
        });
        input(c, search, x + w - 124, y, 124, 14, tr("fluxlite.gui.search"));

        // drop-down filters
        String any = tr("fluxlite.gui.filter.any");
        float fy = y + 19, fx = x;
        int[] sc = data.getIntArray("statusCount");
        String[] statusOpts = new String[6];
        statusOpts[0] = any;
        for (int i = 0; i < 5; i++)
            statusOpts[i + 1] = tr("fluxlite.gui.st." + i) + (i < sc.length ? "  " + sc[i] : "");
        fx += chip(
            c,
            fx,
            fy,
            "status",
            tr("fluxlite.gui.chip.status", statusFilter < 0 ? any : tr("fluxlite.gui.st." + statusFilter)),
            statusFilter >= 0,
            statusOpts,
            statusFilter + 1,
            i -> {
                statusFilter = i - 1;
                refilter();
            }) + 5;

        int[] tiers = data.getIntArray("tiers");
        String[] tierOpts = new String[tiers.length + 1];
        tierOpts[0] = any;
        int tierSel = 0;
        for (int i = 0; i < tiers.length; i++) {
            tierOpts[i + 1] = Fmt.tierName(tiers[i]);
            if (tiers[i] == tierFilter) tierSel = i + 1;
        }
        fx += chip(
            c,
            fx,
            fy,
            "tier",
            tr("fluxlite.gui.chip.tier", tierFilter < 0 ? any : Fmt.tierName(tierFilter)),
            tierFilter >= 0,
            tierOpts,
            tierSel,
            i -> {
                tierFilter = i == 0 ? -1 : tiers[i - 1];
                refilter();
            }) + 5;

        NBTTagList conns = data.getTagList("conns", 10);
        String[] connOpts = new String[conns.tagCount() + 1];
        long[] connIds = new long[conns.tagCount() + 1];
        connOpts[0] = any;
        int connSel = 0;
        String connName = any;
        for (int i = 0; i < conns.tagCount(); i++) {
            NBTTagCompound e = conns.getCompoundTagAt(i);
            connIds[i + 1] = e.getLong("id");
            connOpts[i + 1] = e.getString("n") + (e.getBoolean("on") ? "" : "  · " + tr("fluxlite.gui.offline"));
            if (connIds[i + 1] == connFilter) {
                connSel = i + 1;
                connName = e.getString("n");
            }
        }
        fx += chip(
            c,
            fx,
            fy,
            "conn",
            tr("fluxlite.gui.chip.conn", fit(c, connName, 64)),
            connFilter != 0,
            connOpts,
            connSel,
            i -> {
                connFilter = connIds[i];
                refilter();
            }) + 5;

        String[] kindOpts = { any, tr("fluxlite.gui.kind.0"), tr("fluxlite.gui.kind.1"), tr("fluxlite.gui.kind.2") };
        chip(
            c,
            fx,
            fy,
            "kind",
            tr("fluxlite.gui.chip.kind", kindFilter < 0 ? any : tr("fluxlite.gui.kind." + kindFilter)),
            kindFilter >= 0,
            kindOpts,
            kindFilter + 1,
            i -> {
                kindFilter = i - 1;
                refilter();
            });

        if (filtered()) {
            String clear = tr("fluxlite.gui.clear_filters");
            float cw = c.width(clear, 1);
            boolean hov = hover(x + w - cw - 4, fy, cw + 4, 14);
            text(c, clear, x + w - cw - 2, fy + 3.5f, hov ? Theme.LABEL : Theme.BLUE);
            onClick(x + w - cw - 4, fy, cw + 4, 14, () -> {
                filter = 0;
                statusFilter = tierFilter = kindFilter = -1;
                connFilter = 0;
                search.clear();
                refilter();
            });
        }

        float ty = y + 38, th = h - 38 - 13;
        card(c, x, ty, w, th);
        float[] col = columns(w);
        float hy = ty + 6;
        header(c, x + col[0], hy, tr("fluxlite.gui.col.device"), SORT_NAME, false);
        header(c, x + col[1], hy, tr("fluxlite.gui.col.role"), SORT_ROLE, false);
        header(c, x + col[2], hy, tr("fluxlite.gui.col.tier"), SORT_TIER, false);
        header(c, x + col[3], hy, tr("fluxlite.gui.col.now"), SORT_NOW, true);
        header(c, x + col[4], hy, tr("fluxlite.gui.col.peak"), SORT_PEAK, true);
        header(c, x + col[5], hy, tr("fluxlite.gui.col.total"), SORT_TOTAL, true);
        c.fill(x + 8, ty + 18, w - 16, 0.5f, Theme.SEPARATOR);

        float ry0 = ty + 19;
        int fit = Math.max(1, (int) ((th - 21) / ROW));
        if (fit != visibleRows) {
            visibleRows = fit;
            request();
        }
        NBTTagList rows = data.getTagList("rows", 10);
        c.clip(x, ry0, w, th - 20);
        for (int i = 0; i < rows.tagCount() && i < fit; i++) {
            row(c, rows.getCompoundTagAt(i), x, ry0 + i * ROW, w, col, i < rows.tagCount() - 1 && i < fit - 1);
        }
        c.unclip();
        int count = data.getInteger("count");
        if (count == 0) {
            String empty = tr(filtered() ? "fluxlite.gui.no_match" : "fluxlite.gui.no_devices");
            textCenter(c, empty, x + w / 2, ty + th / 2, Theme.LABEL3, 1);
        }
        if (count > fit) {
            float trackH = th - 26, barH = Math.max(12, trackH * fit / count);
            float barY = ry0 + 3 + (trackH - barH) * offset / Math.max(1, count - fit);
            c.round(x + w - 5, barY, 2.5f, barH, 1.25f, Theme.LABEL3);
        }

        // what is shown, and its total flow
        float by = y + h - 9;
        int all = data.getInteger("all");
        float bx = x + 2;
        bx += text(
            c,
            count == all ? tr("fluxlite.gui.count", count) : tr("fluxlite.gui.count_of", count, all),
            bx,
            by,
            Theme.LABEL2) + 10;
        c.circle(bx + 2, by + 3.5f, 2, Theme.INPUT);
        bx += 7 + text(c, Fmt.si(data.getLong("sumIn")) + " EU/t", bx + 7, by, Theme.LABEL2) + 10;
        c.circle(bx + 2, by + 3.5f, 2, Theme.OUTPUT);
        bx += 7 + text(c, Fmt.si(data.getLong("sumOut")) + " EU/t", bx + 7, by, Theme.LABEL2);
        String hint = tr("fluxlite.gui.devices_hint");
        if (bx + 12 + c.width(hint, 1) < x + w) textRight(c, hint, x + w - 2, by, Theme.LABEL3);
    }

    private static float[] columns(float w) {
        return new float[] { 20, w * 0.45f, w * 0.57f, w * 0.75f, w * 0.865f, w - 12 };
    }

    private void header(Canvas c, float x, float y, String label, int key, boolean right) {
        boolean active = sort == key;
        String s = label + (active ? desc ? " ▾" : " ▴" : "");
        float tw = c.width(s, 1);
        float tx = right ? x - tw : x;
        boolean hov = hover(tx - 2, y - 2, tw + 4, 12);
        text(c, s, tx, y, active ? Theme.LABEL : hov ? Theme.LABEL2 : Theme.LABEL3);
        onClick(tx - 2, y - 2, tw + 4, 12, () -> {
            if (sort == key) desc = !desc;
            else {
                sort = key;
                desc = key != SORT_NAME;
            }
            offset = 0;
            request();
        });
    }

    private void row(Canvas c, NBTTagCompound r, float x, float y, float w, float[] col, boolean sep) {
        boolean hov = hover(x + 4, y, w - 8, ROW);
        if (hov) c.round(x + 4, y + 1, w - 8, ROW - 2, 5, Theme.CARD_HOVER);
        else if (sep) c.fill(x + 10, y + ROW - 0.5f, w - 20, 0.5f, Theme.SEPARATOR);
        int st = r.getByte("st");
        boolean sampled = r.getBoolean("s");
        boolean dim = st == ST_OFFLINE || st == ST_OFF;
        c.circle(x + 12.5f, y + 6.5f, 2.2f, statusColor(st));
        float nameW = col[1] - col[0] - 8;
        text(
            c,
            fit(c, (sampled ? "~ " : "") + r.getString("n"), nameW),
            x + col[0],
            y + 2.5f,
            dim ? Theme.LABEL3 : Theme.LABEL);
        StringBuilder sub = new StringBuilder(r.getString("c"));
        int kind = r.getByte("kd"), side = r.getByte("sd");
        if (kind == 2) sub.append(" · ")
            .append(tr("fluxlite.gui.kind.short.2"));
        else if (side >= 0 && side < 6) sub.append(" · ")
            .append(tr("fluxlite.gui.face", tr("fluxlite.side." + side)));
        if (st >= ST_OFFLINE) sub.append(" · ")
            .append(tr("fluxlite.gui.st." + st));
        text(c, fit(c, sub.toString(), nameW), x + col[0], y + 12, Theme.LABEL3);
        int role = r.getByte("r");
        rolePill(c, x + col[1], y + 5.5f, role, st);
        text(c, Fmt.tier(r.getLong("v")), x + col[2], y + 7, Theme.LABEL2);
        String now;
        int nowColor;
        if (role == 3) {
            now = "+" + Fmt.si(r.getLong("ni")) + " −" + Fmt.si(r.getLong("no"));
            nowColor = Theme.LABEL;
        } else if (role == 0) {
            now = "-";
            nowColor = Theme.LABEL3;
        } else {
            long v = role == 1 ? r.getLong("ni") : r.getLong("no");
            now = Fmt.si(v);
            nowColor = v > 0 ? role == 1 ? Theme.INPUT : Theme.OUTPUT : Theme.LABEL3;
        }
        textRight(c, now, x + col[3], y + 7, nowColor);
        textRight(c, Fmt.si(r.getLong("pk")), x + col[4], y + 7, Theme.LABEL2);
        textRight(c, Fmt.si(big(r.getString("tot"))), x + col[5], y + 7, Theme.LABEL2);
        String key = r.getString("k");
        onClick(x + 4, y, w - 8, ROW, () -> openDetail(key));
    }

    // ------------------------------------------------------------------ detail

    private void detail(Canvas c, float x, float y, float w, float h) {
        // back chevron, title, pill, locate button: one row
        boolean hovBack = hover(x - 3, y - 2, 16, 14);
        c.round(x - 3, y - 2, 14, 14, 7, hovBack ? Theme.FILL_SELECTED : Theme.FILL);
        chevronLeft(c, x + 2.5f, y + 1.5f, Theme.LABEL);
        onClick(x - 3, y - 2, 16, 14, () -> {
            detailKey = "";
            data = null;
            request();
        });
        if (!data.hasKey("name")) {
            textCenter(c, tr("fluxlite.gui.no_data"), x + w / 2, y + h / 2, Theme.LABEL3, 1);
            return;
        }
        boolean sampled = data.getBoolean("sampled");
        String name = data.getString("name");
        if (name.equals("@team")) name = tr("fluxlite.gui.team_total");
        boolean canLocate = data.hasKey("pos");
        float titleMax = w - 18 - (canLocate ? 70 : 0) - 40;
        float tw = bold(c, fit(c, (sampled ? "~ " : "") + name, titleMax), x + 18, y + 1, Theme.LABEL);
        int role = data.getByte("role");
        rolePill(c, x + 18 + tw + 6, y, role);
        if (canLocate) {
            button(c, x + w - 56, y - 2, 56, 14, tr("fluxlite.gui.locate"), Theme.BLUE, () -> {
                int[] p = data.getIntArray("pos");
                host.highlight(data.getInteger("dim"), p[0], p[1], p[2]);
                host.close();
            });
        }
        StringBuilder sub = new StringBuilder();
        if (data.hasKey("conn")) sub.append(data.getString("conn"));
        if (data.hasKey("side")) sub.append("  ·  ")
            .append(tr("fluxlite.side." + data.getInteger("side")));
        if (data.hasKey("pos")) {
            int[] p = data.getIntArray("pos");
            sub.append("  ·  ")
                .append(p[0])
                .append(", ")
                .append(p[1])
                .append(", ")
                .append(p[2]);
        }
        if (!data.getBoolean("online")) sub.append("  ·  §c")
            .append(tr("fluxlite.gui.offline"));
        if (sampled) sub.append("  ·  ")
            .append(tr("fluxlite.gui.sampled"));
        if (data.hasKey("cap")) sub.append("  ·  ")
            .append(
                tr(
                    "fluxlite.gui.d.buffer",
                    Fmt.percent(Math.min(1000, data.getLong("stored") * 1000 / Math.max(1, data.getLong("cap"))))));
        text(c, fit(c, sub.toString(), w - 18), x + 18, y + 13, Theme.LABEL2);

        float gap = 6, sy = y + 27, sw = (w - gap * 3) / 4, sh = 46;
        int color = role == 2 ? Theme.OUTPUT : role == 1 ? Theme.INPUT : Theme.BOTH;
        long nowV = role == 2 ? data.getLong("no")
            : role == 1 ? data.getLong("ni") : data.getLong("ni") - data.getLong("no");
        String nowS = role == 3 && nowV > 0 ? "+" + Fmt.si(nowV) : Fmt.si(nowV);
        stat(c, x, sy, sw, sh, tr("fluxlite.gui.d.now"), color, nowS, "EU/t", Theme.LABEL, specLine(), Theme.LABEL3);
        long ago = data.getLong("peakAgo");
        stat(
            c,
            x + sw + gap,
            sy,
            sw,
            sh,
            tr("fluxlite.gui.d.peak"),
            0,
            Fmt.si(data.getLong("peak")),
            "EU/t",
            Theme.LABEL,
            ago >= 0 ? tr("fluxlite.gui.ago", Fmt.duration(ago)) : "-",
            Theme.LABEL3);
        long duty = data.getLong("duty");
        long sat = data.getLong("sat");
        if (sat >= 0) stat(
            c,
            x + 2 * (sw + gap),
            sy,
            sw,
            sh,
            tr("fluxlite.gui.d.satisfaction"),
            0,
            Fmt.percent(sat),
            null,
            sat < 950 ? Theme.ORANGE : Theme.LABEL,
            tr("fluxlite.gui.d.duty", duty < 0 ? "-" : Fmt.percent(duty)),
            Theme.LABEL3);
        else stat(
            c,
            x + 2 * (sw + gap),
            sy,
            sw,
            sh,
            tr("fluxlite.gui.d.duty_title"),
            0,
            duty < 0 ? "-" : Fmt.percent(duty),
            null,
            Theme.LABEL,
            tr("fluxlite.gui.d.duty_sub"),
            Theme.LABEL3);
        stat(
            c,
            x + 3 * (sw + gap),
            sy,
            sw,
            sh,
            tr("fluxlite.gui.d.total"),
            0,
            Fmt.si(big(data.getString("total"))),
            "EU",
            Theme.LABEL,
            tr("fluxlite.gui.d.today", Fmt.si(big(data.getString("today")))),
            Theme.LABEL3);

        // the hour-of-day card only when the chart keeps a useful height
        float hodH = 46;
        float chy = sy + sh + gap;
        boolean showHod = h - (chy - y) - hodH - gap >= 70;
        float chh = h - (chy - y) - (showHod ? hodH + gap : 0);
        card(c, x, chy, w, chh);
        bold(c, tr("fluxlite.gui.flow"), x + 10, chy + 8, Theme.LABEL);
        String[] ranges = { tr("fluxlite.gui.range.live"), tr("fluxlite.gui.range.hour"), tr("fluxlite.gui.range.3d") };
        float rw = segmentedWidth(c, ranges);
        segmented(c, x + w - 7 - rw, chy + 5, ranges, detailRange, i -> {
            detailRange = i;
            request();
        });
        long[] cin = Longs.unpack(data.getIntArray("cin")), cout = Longs.unpack(data.getIntArray("cout"));
        boolean showIn = role != 2, showOut = role != 1;
        if (cin.length == 0) textCenter(c, tr("fluxlite.gui.d.collecting"), x + w / 2, chy + chh / 2, Theme.LABEL3, 1);
        else chart(
            c,
            x + 10,
            chy + 30,
            w - 18,
            chh - 39,
            showIn ? cin : cout,
            showIn ? Theme.INPUT : Theme.OUTPUT,
            showIn && showOut ? cout : null,
            Theme.OUTPUT);
        if (!showHod) return;

        float hy = chy + chh + gap;
        card(c, x, hy, w, hodH);
        long[] hod = Longs.unpack(data.getIntArray("hod"));
        int peakHour = -1;
        long best = 0;
        for (int i = 0; i < hod.length; i++) if (hod[i] > best) {
            best = hod[i];
            peakHour = i;
        }
        bold(c, tr("fluxlite.gui.hod"), x + 10, hy + 7, Theme.LABEL);
        if (peakHour >= 0) textRight(c, tr("fluxlite.gui.hod.peak", peakHour), x + w - 10, hy + 7, Theme.LABEL2);
        if (hod.length == 24 && best > 0) {
            bars(c, x + 10, hy + 20, w - 20, hodH - 26, hod, color, peakHour);
        } else text(c, tr("fluxlite.gui.hod.empty"), x + 10, hy + 24, Theme.LABEL3);
    }

    private String specLine() {
        long v = data.getLong("v");
        if (v <= 0) return null;
        return Fmt.tier(v) + " · " + data.getLong("a") + "A";
    }

    // ------------------------------------------------------------------ alerts

    private void alerts(Canvas c, float x, float y, float w, float h) {
        NBTTagList list = data.getTagList("alertList", 10);
        float settingsH = 3 * 22;
        float listH = h - settingsH - 24;
        if (list.tagCount() == 0) {
            float cyy = y + listH / 2 - 12;
            c.circle(x + w / 2, cyy, 11, Theme.withAlpha(Theme.GREEN, 0x33));
            c.line(
                new float[] { x + w / 2 - 5, x + w / 2 - 1, x + w / 2 + 5.5f },
                new float[] { cyy, cyy + 4, cyy - 4 },
                1.8f,
                Theme.GREEN);
            String t = tr("fluxlite.gui.no_alerts");
            bold(c, t, x + w / 2 - c.width(t, 1) / 2, cyy + 18, Theme.LABEL);
            textCenter(c, tr("fluxlite.gui.no_alerts.sub"), x + w / 2, cyy + 30, Theme.LABEL3, 1);
        } else {
            int fit = (int) ((listH - 4) / 22);
            int shown = Math.min(fit, list.tagCount());
            card(c, x, y, w, shown * 22 + 4);
            for (int i = 0; i < shown; i++) {
                Alert a = Alert.read(list.getCompoundTagAt(i));
                float ry = y + 2 + i * 22;
                int col = severe(a.type) ? Theme.RED : Theme.ORANGE;
                c.circle(x + 14, ry + 11, 3.5f, col);
                Object[] args = new Object[a.args.length];
                System.arraycopy(a.args, 0, args, 0, args.length);
                text(c, fit(c, tr(a.type.langKey(), args), w - 40), x + 26, ry + 7, Theme.LABEL);
                if (i < shown - 1) c.fill(x + 26, ry + 21.5f, w - 36, 0.5f, Theme.SEPARATOR);
            }
            if (list.tagCount() > fit) text(
                c,
                tr("fluxlite.gui.more_alerts", list.tagCount() - fit),
                x + 4,
                y + shown * 22 + 10,
                Theme.LABEL3);
        }
        float sy = y + h - settingsH;
        text(c, tr("fluxlite.gui.notifications"), x + 10, sy - 12, Theme.LABEL3);
        card(c, x, sy, w, settingsH);
        text(c, tr("fluxlite.gui.chat"), x + 10, sy + 7, Theme.LABEL);
        toggle(c, x + w - 32, sy + 4.5f, data.getBoolean("chat"), () -> action(Kinds.ACT_CHAT));
        c.fill(x + 10, sy + 21.75f, w - 20, 0.5f, Theme.SEPARATOR);
        boolean owner = data.getBoolean("ccOwner");
        text(c, tr("fluxlite.gui.redstone"), x + 10, sy + 29, owner ? Theme.LABEL : Theme.LABEL3);
        if (owner) toggle(c, x + w - 32, sy + 26.5f, data.getBoolean("redstone"), () -> action(Kinds.ACT_REDSTONE));
        else textRight(c, tr("fluxlite.gui.owner_only"), x + w - 10, sy + 29, Theme.LABEL3);
        c.fill(x + 10, sy + 43.75f, w - 20, 0.5f, Theme.SEPARATOR);
        boolean allowed = data.getBoolean("holoAllowed");
        float lw = text(c, tr("fluxlite.gui.hologram"), x + 10, sy + 51, owner && allowed ? Theme.LABEL : Theme.LABEL3);
        text(c, tr("fluxlite.gui.hologram.sub"), x + 10 + lw + 6, sy + 51, Theme.LABEL3);
        if (!allowed) textRight(c, tr("fluxlite.gui.hologram.off"), x + w - 10, sy + 51, Theme.LABEL3);
        else if (owner) toggle(c, x + w - 32, sy + 48.5f, data.getBoolean("holo"), () -> action(Kinds.ACT_HOLOGRAM));
        else textRight(c, tr("fluxlite.gui.owner_only"), x + w - 10, sy + 51, Theme.LABEL3);
    }

    private static boolean severe(Alert.Type t) {
        return t == Alert.Type.BACKEND_DOWN || t == Alert.Type.ETA_SHORT
            || t == Alert.Type.LOW_BALANCE
            || t == Alert.Type.CHUNK_FAIL;
    }
}
