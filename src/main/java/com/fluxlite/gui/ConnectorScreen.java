package com.fluxlite.gui;

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
 * switch per face.
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

    @Override
    protected void render(Canvas c) {
        scrim(c);
        int rows = 6;
        float h = 12 + 30 + 10 + 40 + 10 + rows * ROW + 8 + 16;
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

        // faces
        float ly = cy + 50;
        float lw = W - 24;
        card(c, x + 12, ly, lw, rows * ROW);
        NBTTagList faces = data.getTagList("faces", 10);
        for (int i = 0; i < faces.tagCount(); i++) {
            face(c, faces.getCompoundTagAt(i), x + 12, ly + i * ROW, lw, i < faces.tagCount() - 1);
        }
        textCenter(c, tr("fluxlite.gui.connector_hint"), x + W / 2f, ly + rows * ROW + 7, Theme.LABEL3, 1);
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

    private void face(Canvas c, NBTTagCompound f, float x, float y, float w, boolean separator) {
        int side = f.getByte("side");
        int vis = f.getByte("vis");
        if (separator) c.fill(x + 28, y + ROW - 0.5f, w - 28, 0.5f, Theme.SEPARATOR);
        textCenter(
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
        // right side: switch, value, pill
        float right = x + w - 8;
        toggle(c, right - 22, y + 3, !off, () -> edit(Kinds.OP_TOGGLE, side, null));
        right -= 28;
        PortRole role = PortRole.byId(f.getByte("role"));
        String val = "";
        int valColor = Theme.LABEL2;
        if (!off && vis != TileConnector.VIS_IDLE && vis != TileConnector.VIS_ERROR) {
            long in = f.getLong("in"), out = f.getLong("out");
            if (role == PortRole.BOTH) {
                val = "+" + Fmt.si(in) + " −" + Fmt.si(out);
                valColor = Theme.LABEL;
            } else {
                long v = role == PortRole.INPUT ? in : out;
                val = Fmt.si(v);
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
                pill = tr("fluxlite.role.input");
                pc = Theme.INPUT;
            }
            case TileConnector.VIS_OUT -> {
                pill = tr("fluxlite.role.output");
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
                pill = tr("fluxlite.role.none");
                pc = Theme.GRAY;
            }
        }
        float pw = c.width(pill, 1) + 10;
        pill(c, right - pw, y + 4, pill, pc);
        right -= pw + 6;
        String target = f.getString("target");
        long v = f.getLong("v");
        if (v > 0) target += "  §8" + Fmt.tier(v);
        text(c, fit(c, target, right - (x + 28)), x + 28, y + 6, off ? Theme.LABEL3 : Theme.LABEL2);
    }
}
