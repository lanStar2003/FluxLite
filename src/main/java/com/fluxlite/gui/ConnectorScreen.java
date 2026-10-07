package com.fluxlite.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxlite.core.PortRole;
import com.fluxlite.core.PortStatus;
import com.fluxlite.gui.ui.Canvas;
import com.fluxlite.gui.ui.Theme;
import com.fluxlite.gui.ui.UiScreen;
import com.fluxlite.net.Kinds;
import com.fluxlite.tile.TileConnector;
import com.fluxlite.util.Fmt;

/**
 * Connector card: name, live per-tick totals and the six faces. Directions are automatic; the only control is a
 * switch per face. A face that moves both EU and steam (a steam turbine) gets a row for each.
 */
public class ConnectorScreen extends UiScreen {

    private static final int W = 280, ROW = 19;

    private final int dim, bx, by, bz;
    private NBTTagCompound data;
    private final TextInput name = new TextInput(32, s -> edit(Kinds.OP_NAME, 0, s));

    public ConnectorScreen(int dim, int x, int y, int z) {
        this.dim = dim;
        this.bx = x;
        this.by = y;
        this.bz = z;
    }

    private NBTTagCompound pos() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("dim", dim);
        t.setInteger("x", bx);
        t.setInteger("y", by);
        t.setInteger("z", bz);
        return t;
    }

    private void edit(int op, int side, String text) {
        NBTTagCompound t = pos();
        t.setInteger("op", op);
        t.setInteger("side", side);
        t.setString("text", text == null ? "" : text);
        host.send(Kinds.CONNECTOR_EDIT, t);
    }

    @Override
    public void tick() {
        if (ticks % 5 == 0) host.send(Kinds.CONNECTOR_REQUEST, pos());
        super.tick();
    }

    @Override
    public void onData(NBTTagCompound d) {
        data = d;
        if (d.getBoolean("ok")) name.set(d.getString("name"));
    }

    /** One line of the face list: a face's EU or steam channel. */
    private static final class Row {

        final NBTTagCompound face;
        final boolean steam, first, lastOfFace;

        Row(NBTTagCompound face, boolean steam, boolean first, boolean lastOfFace) {
            this.face = face;
            this.steam = steam;
            this.first = first;
            this.lastOfFace = lastOfFace;
        }
    }

    /** EU and steam rows per face; an idle channel is left out when the other one has something to show. */
    private List<Row> rows() {
        NBTTagList faces = data.getTagList("faces", 10), steam = data.getTagList("steam", 10);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < faces.tagCount(); i++) {
            NBTTagCompound eu = faces.getCompoundTagAt(i);
            NBTTagCompound st = i < steam.tagCount() ? steam.getCompoundTagAt(i) : null;
            int ev = eu.getByte("vis"), sv = st == null ? TileConnector.VIS_NONE : st.getByte("vis");
            boolean euShown = ev != TileConnector.VIS_NONE
                && !(ev == TileConnector.VIS_IDLE && sv != TileConnector.VIS_NONE && sv != TileConnector.VIS_IDLE);
            boolean stShown = sv != TileConnector.VIS_NONE && !(sv == TileConnector.VIS_IDLE && euShown);
            if (euShown || !stShown) rows.add(new Row(eu, false, true, !stShown));
            if (stShown) rows.add(new Row(st, true, !euShown, true));
        }
        return rows;
    }

    @Override
    protected void render(Canvas c) {
        scrim(c);
        List<Row> rows = data != null && data.getBoolean("ok") ? rows() : new ArrayList<>();
        boolean steam = data != null && data.getBoolean("hasSteam");
        int n = Math.max(6, rows.size());
        float h = 12 + 30 + 10 + 40 + 10 + (steam ? 24 : 0) + n * ROW + 8 + 16;
        float x = (width - W) / 2f, y = (height - h) / 2f;
        window(c, x, y, W, h);
        closeButton(c, x + W - 14, y + 14);

        // header: icon, editable name, subtitle
        icon(c, x + 12, y + 12);
        if (data == null) {
            textCenter(c, tr("fluxlite.gui.loading"), x + W / 2f, y + h / 2 - 4, Theme.LABEL2, 1);
            return;
        }
        if (!data.getBoolean("ok")) {
            bold(c, tr("tile.fluxlite.connector.name"), x + 42, y + 14, Theme.LABEL);
            textCenter(c, tr("fluxlite.gui.denied", data.getString("owner")), x + W / 2f, y + h / 2 - 4, Theme.RED, 1);
            return;
        }
        input(c, name, x + 40, y + 10, W - 40 - 28, 14, tr("fluxlite.gui.unnamed"));
        String sub = data.getString("owner") + "  ·  " + chunkText(data.getInteger("chunk"));
        text(c, fit(c, sub, W - 52), x + 42, y + 29, Theme.LABEL2);

        // totals, this tick
        float cy = y + 52, cw = (W - 24 - 8) / 2f;
        stat(c, x + 12, cy, cw, tr("fluxlite.gui.input"), data.getLong("in"), Theme.INPUT);
        stat(c, x + 12 + cw + 8, cy, cw, tr("fluxlite.gui.output"), data.getLong("out"), Theme.OUTPUT);
        float ly = cy + 50;
        if (steam) {
            steamTotals(c, x + 12, ly, W - 24);
            ly += 24;
        }

        // faces
        float lw = W - 24;
        card(c, x + 12, ly, lw, n * ROW);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float ry = ly + i * ROW;
            if (i < rows.size() - 1) {
                // a full line between faces, a short one between the two channels of a face
                float inset = r.lastOfFace ? 28 : 60;
                c.fill(x + 12 + inset, ry + ROW - 0.5f, lw - inset, 0.5f, Theme.SEPARATOR);
            }
            face(c, r, x + 12, ry, lw);
        }
        textCenter(c, tr("fluxlite.gui.connector_hint"), x + W / 2f, ly + n * ROW + 7, Theme.LABEL3, 1);
    }

    private String chunkText(int state) {
        return switch (state) {
            case 1 -> tr("fluxlite.gui.chunk.on");
            case 2 -> "§c" + tr("fluxlite.gui.chunk.fail");
            case 3 -> tr("fluxlite.gui.chunk.disabled");
            default -> tr("fluxlite.gui.chunk.off");
        };
    }

    private void icon(Canvas c, float x, float y) {
        c.roundGradient(x, y, 22, 22, 6, 0xFF0A84FF, 0xFF64D2FF);
        // lightning bolt
        c.line(
            new float[] { x + 13, x + 8.5f, x + 12.5f, x + 9 },
            new float[] { y + 4.5f, y + 11.5f, y + 11.5f, y + 17.5f },
            1.6f,
            0xFFFFFFFF);
    }

    private void stat(Canvas c, float x, float y, float w, String label, long value, int color) {
        card(c, x, y, w, 40);
        c.circle(x + 10, y + 10.5f, 2.5f, color);
        text(c, label, x + 16, y + 7, Theme.LABEL2);
        value(c, Fmt.si(value), "EU/t", x + 9, y + 20, value > 0 ? Theme.LABEL : Theme.LABEL3, 2);
    }

    /** Steam in and out of this connector, one line. */
    private void steamTotals(Canvas c, float x, float y, float w) {
        card(c, x, y, w, 18);
        cloud(c, x + 10, y + 9);
        float tx = x + 18 + text(c, tr("fluxlite.gui.steam"), x + 18, y + 5, Theme.LABEL2) + 12;
        long in = data.getLong("sin"), out = data.getLong("sout");
        c.circle(tx + 2, y + 8.5f, 2, Theme.INPUT);
        tx += 7 + text(c, Fmt.si(in) + " L/t", tx + 7, y + 5, in > 0 ? Theme.LABEL : Theme.LABEL3) + 12;
        c.circle(tx + 2, y + 8.5f, 2, Theme.OUTPUT);
        text(c, Fmt.si(out) + " L/t", tx + 7, y + 5, out > 0 ? Theme.LABEL : Theme.LABEL3);
    }

    /** Small steam cloud, centered on (x, y). */
    static void cloud(Canvas c, float x, float y) {
        c.circle(x - 2.6f, y + 0.8f, 2.1f, Theme.STEAM);
        c.circle(x, y - 0.6f, 2.5f, Theme.STEAM);
        c.circle(x + 2.4f, y + 1, 1.9f, Theme.STEAM);
    }

    private void face(Canvas c, Row r, float x, float y, float w) {
        NBTTagCompound f = r.face;
        int side = f.getByte("side");
        int vis = f.getByte("vis");
        if (r.first) textCenter(
            c,
            tr("fluxlite.side." + side),
            x + 14,
            y + 6,
            vis == TileConnector.VIS_NONE ? Theme.LABEL3 : Theme.LABEL,
            1);
        if (vis == TileConnector.VIS_NONE) {
            PortStatus st = PortStatus.byId(f.getByte("status"));
            String t = st == PortStatus.CONNECTOR_NEIGHBOUR ? tr(st.langKey()) : tr("fluxlite.gui.not_connected");
            text(c, t, x + 28, y + 6, Theme.LABEL3);
            return;
        }
        boolean off = vis == TileConnector.VIS_OFF;
        // right side: switch (once per face), value, pill
        float right = x + w - 8;
        if (r.first) toggle(c, right - 22, y + 3, !off, () -> edit(Kinds.OP_TOGGLE, side, null));
        right -= 28;
        PortRole role = PortRole.byId(f.getByte("role"));
        String val = "";
        int valColor = Theme.LABEL2;
        if (!off && vis != TileConnector.VIS_IDLE && vis != TileConnector.VIS_ERROR) {
            long in = f.getLong("in"), out = f.getLong("out");
            String unit = r.steam ? " L" : "";
            if (role == PortRole.BOTH) {
                val = "+" + Fmt.si(in) + " −" + Fmt.si(out) + unit;
                valColor = Theme.LABEL;
            } else {
                long v = role == PortRole.INPUT ? in : out;
                val = Fmt.si(v) + unit;
                valColor = v > 0 ? Theme.LABEL : Theme.LABEL3;
            }
        }
        float vw = c.width(val, 1);
        c.text(val, right - vw, y + 6, valColor, 1);
        right -= Math.max(vw, 34) + 6;
        String pill;
        int pc;
        switch (vis) {
            case TileConnector.VIS_IN -> {
                pill = tr(r.steam ? "fluxlite.role.steam_in" : "fluxlite.role.input");
                pc = Theme.INPUT;
            }
            case TileConnector.VIS_OUT -> {
                pill = tr(r.steam ? "fluxlite.role.steam_out" : "fluxlite.role.output");
                pc = Theme.OUTPUT;
            }
            case TileConnector.VIS_BOTH -> {
                pill = tr("fluxlite.role.both");
                pc = Theme.BOTH;
            }
            case TileConnector.VIS_OFF -> {
                pill = tr("fluxlite.status.disabled");
                pc = Theme.GRAY;
            }
            case TileConnector.VIS_ERROR -> {
                pill = tr("fluxlite.status.unknown_spec");
                pc = Theme.RED;
            }
            default -> {
                pill = tr(r.steam ? "fluxlite.role.steam_idle" : "fluxlite.role.none");
                pc = Theme.GRAY;
            }
        }
        float pw = c.width(pill, 1) + 10;
        pill(c, right - pw, y + 4, pill, pc);
        right -= pw + 6;
        String target = f.getString("target");
        if (target.isEmpty()) target = tr(r.steam ? "fluxlite.gui.pipe_empty" : "fluxlite.gui.cable_empty");
        long v = f.getLong("v");
        if (v > 0) target += "  §8" + Fmt.tier(v);
        float tx = x + 28;
        if (r.steam && !r.first) tx += 2;
        text(c, fit(c, target, right - tx), tx, y + 6, off ? Theme.LABEL3 : Theme.LABEL2);
    }
}
