package com.fluxlite.gui.holo;

import com.fluxlite.gui.ui.Canvas;
import com.fluxlite.gui.ui.Theme;
import com.fluxlite.util.Fmt;

/**
 * The floating display above a control center, drawn in panel units (one unit is 1/72 block in the world). A
 * holographic take on GTNH's industrial information panel: balance, net flow, input / output and the last ten
 * seconds as a curve, on tinted glass with scanlines.
 * <p>
 * Opening unfolds it like a projection: a bright line grows sideways, the glass unfolds vertically, then the content
 * flickers in.
 */
public final class HoloPanel {

    public static final float W = 168, H = 112;

    static final int CYAN = Theme.TEAL;
    private static final int GLASS_TOP = 0xA0102434, GLASS_BOTTOM = 0x800A1622;
    private static final float R = 6;

    /** Translations; arguments are formatted like {@code String.format}. */
    public interface Tr {

        String tr(String key, Object... args);
    }

    private HoloPanel() {}

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float ease(float t) {
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /**
     * @param back true when seen from behind: only the glass, no (mirrored) content
     */
    public static void draw(Canvas c, HoloState s, long now, boolean back, Tr tr) {
        float open = s.open;
        float line = clamp(open / 0.3f), unfold = clamp((open - 0.3f) / 0.4f), content = clamp((open - 0.7f) / 0.3f);
        if (line <= 0) return;
        float w = W * ease(line), h = Math.max(1.2f, H * ease(unfold));
        float x = (W - w) / 2, y = (H - h) / 2;
        float r = Math.min(R, h / 2);

        c.setOpacity(1);
        // soft glow around the glass
        for (int i = 3; i >= 1; i--)
            c.roundStroke(x - i, y - i, w + 2 * i, h + 2 * i, r + i, 1, Theme.withAlpha(CYAN, 0x30 / (i + 1)));
        if (unfold <= 0) {
            c.round(x, y, w, h, r, Theme.withAlpha(CYAN, 0xD0));
            return;
        }
        c.roundGradient(x, y, w, h, r, GLASS_TOP, GLASS_BOTTOM);
        c.roundStroke(x, y, w, h, r, 0.6f, Theme.withAlpha(CYAN, 0x99));
        for (float yy = y + 3; yy < y + h - 2; yy += 2.5f) c.fill(x + 2, yy, w - 4, 0.3f, 0x0C64D2FF);
        brackets(c, x, y, w, h);
        sweep(c, x, y, w, h, now);
        if (back || content <= 0) return;

        float flicker = content >= 1 ? 1 : 0.55f + 0.45f * (float) Math.abs(Math.sin(now * 0.07));
        c.setOpacity(content * flicker);
        content(c, s, now, tr);
        c.setOpacity(1);
    }

    /** Sci-fi corner brackets. */
    private static void brackets(Canvas c, float x, float y, float w, float h) {
        float l = Math.min(9, h / 3), t = 0.8f;
        int col = Theme.withAlpha(CYAN, 0xE6);
        float[][] corners = { { x, y, 1, 1 }, { x + w, y, -1, 1 }, { x, y + h, 1, -1 }, { x + w, y + h, -1, -1 } };
        for (float[] k : corners) {
            float cx = k[0] + k[2] * 1.6f, cy = k[1] + k[3] * 1.6f;
            c.fill(k[2] > 0 ? cx : cx - l, k[3] > 0 ? cy : cy - t, l, t, col);
            c.fill(k[2] > 0 ? cx : cx - t, k[3] > 0 ? cy : cy - l, t, l, col);
        }
    }

    /** A faint band of light running down the glass every few seconds. */
    private static void sweep(Canvas c, float x, float y, float w, float h, long now) {
        float p = (now % 3600) / 3600f;
        float sy = y + p * (h + 10) - 5;
        float top = Math.max(y + 2, sy - 9), bottom = Math.min(y + h - 2, sy);
        if (bottom > top) c.gradient(x + 1.5f, top, w - 3, bottom - top, 0x0064D2FF, 0x1E64D2FF);
        if (sy > y + 2 && sy < y + h - 2) c.fill(x + 1.5f, sy, w - 3, 0.4f, 0x4064D2FF);
    }

    private static void content(Canvas c, HoloState s, long now, Tr tr) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 280.0);

        // header: status light, title, state
        int state = s.down ? Theme.RED : s.alerts > 0 ? Theme.ORANGE : Theme.GREEN;
        c.circle(10.5f, 10.5f, 2.2f + 1.6f * pulse, Theme.withAlpha(state, (int) (0x20 + 0x30 * pulse)));
        c.circle(10.5f, 10.5f, 1.9f, state);
        float tx = 16;
        c.text("FLUXLITE", tx, 7, Theme.LABEL, 1);
        tx += c.text("FLUXLITE", tx + 0.5f, 7, Theme.LABEL, 1) + 4.5f;
        c.text(tr.tr("fluxlite.holo.title"), tx, 7, Theme.withAlpha(CYAN, 0xE6), 1);
        String st = s.down ? tr.tr("fluxlite.holo.down")
            : s.alerts > 0 ? tr.tr("fluxlite.holo.alerts", s.alerts) : tr.tr("fluxlite.holo.ok");
        float pw = c.width(st, 1) + 10;
        c.round(W - 8 - pw, 5.5f, pw, 11, 5.5f, Theme.withAlpha(state, 0x40));
        c.text(st, W - 8 - pw + 5, 7, state, 1);

        c.fill(8, 19.5f, W - 16, 0.4f, Theme.withAlpha(CYAN, 0x45));
        c.fill(W / 2 - 22, 19.4f, 44, 0.6f, Theme.withAlpha(CYAN, 0xB0));

        // balance (left) and net flow (right)
        c.text(tr.tr("fluxlite.holo.balance"), 9, 24, Theme.LABEL2, 1);
        float bw = c.text(Fmt.si(s.balance), 8.5f, 33, Theme.LABEL, 2);
        c.text("EU", 8.5f + bw + 2, 41, Theme.LABEL2, 1);

        long net = Math.round(s.shownIn - s.shownOut);
        right(c, fit(c, s.team, 70), W - 9, 24, Theme.LABEL3);
        String netText = (net > 0 ? "+" : "") + Fmt.si(net) + " EU/t";
        int netColor = net > 0 ? Theme.GREEN : net < 0 ? Theme.RED : Theme.LABEL;
        float nw = c.width(netText, 1);
        c.text(netText, W - 9 - nw - 0.5f, 34, netColor, 1);
        c.text(netText, W - 9 - nw, 34, netColor, 1);
        String eta = net >= 0 ? tr.tr("fluxlite.holo.growing")
            : s.eta < 0 ? tr.tr("fluxlite.holo.draining") : tr.tr("fluxlite.holo.eta", Fmt.duration(s.eta));
        right(c, eta, W - 9, 44, s.eta >= 0 && s.eta < 600 && net < 0 ? Theme.RED : Theme.LABEL3);

        // input / output tiles
        float tw = (W - 18 - 4) / 2, ty = 53;
        tile(c, 9, ty, tw, tr.tr("fluxlite.gui.input"), Math.round(s.shownIn), Theme.INPUT);
        tile(c, 9 + tw + 4, ty, tw, tr.tr("fluxlite.gui.output"), Math.round(s.shownOut), Theme.OUTPUT);

        // the last ten seconds
        float gy = 81, gh = 14;
        c.fill(9, gy + gh, W - 18, 0.4f, Theme.withAlpha(CYAN, 0x40));
        long max = 1;
        for (long v : s.curveIn) max = Math.max(max, v);
        for (long v : s.curveOut) max = Math.max(max, v);
        curve(c, 9, gy, W - 18, gh, s.curveIn, max, Theme.INPUT);
        curve(c, 9, gy, W - 18, gh, s.curveOut, max, Theme.OUTPUT);

        // footer
        c.text(tr.tr("fluxlite.holo.connectors", s.online, s.connectors), 9, 100, Theme.LABEL3, 1);
        String live = tr.tr("fluxlite.holo.live");
        float lw = c.width(live, 1);
        c.text(live, W - 9 - lw, 100, Theme.LABEL3, 1);
        boolean blink = (now / 600) % 2 == 0;
        c.circle(W - 9 - lw - 4.5f, 104, 1.6f, blink ? Theme.RED : Theme.withAlpha(Theme.RED, 0x50));
    }

    /** Label on top, value below, a coloured bar on the left. */
    private static void tile(Canvas c, float x, float y, float w, String label, long value, int color) {
        c.round(x, y, w, 24, 4, Theme.withAlpha(color, 0x1C));
        c.round(x + 2, y + 4, 1.2f, 16, 0.6f, color);
        c.circle(x + 8.5f, y + 7.5f, 1.8f, color);
        c.text(label, x + 13, y + 3.5f, Theme.LABEL2, 1);
        String v = Fmt.si(value);
        c.text(v, x + 6.5f, y + 13, Theme.LABEL, 1);
        float vw = c.text(v, x + 7, y + 13, Theme.LABEL, 1);
        c.text("EU/t", x + 7 + vw + 3, y + 13, Theme.LABEL2, 1);
    }

    private static void curve(Canvas c, float x, float y, float w, float h, long[] v, long max, int color) {
        int n = v.length;
        if (n < 2) return;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = x + w * i / (n - 1);
            ys[i] = y + h - (float) ((double) Math.max(0, v[i]) / max * h);
        }
        c.area(xs, ys, y + h, Theme.withAlpha(color, 0x50), Theme.withAlpha(color, 0x00));
        c.line(xs, ys, 0.8f, color);
    }

    private static void right(Canvas c, String s, float right, float y, int color) {
        c.text(s, right - c.width(s, 1), y, color, 1);
    }

    private static String fit(Canvas c, String s, float maxW) {
        if (s == null) return "";
        if (c.width(s, 1) <= maxW) return s;
        int end = s.length();
        while (end > 0 && c.width(s.substring(0, end) + "…", 1) > maxW) end--;
        return s.substring(0, end) + "…";
    }
}
