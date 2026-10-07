package com.fluxlite.gui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import net.minecraft.nbt.NBTTagCompound;

import com.fluxlite.util.Fmt;

/**
 * Immediate-mode screen: {@link #render} draws everything each frame and registers click areas as it goes. No
 * Minecraft client classes in here, so the same code renders in the game and in the PNG preview test.
 */
public abstract class UiScreen {

    // LWJGL key codes (kept as ints so this class does not need LWJGL)
    public static final int KEY_ESCAPE = 1, KEY_BACK = 14, KEY_RETURN = 28, KEY_NUMPADENTER = 156, KEY_DELETE = 211;

    protected Host host;
    public int width, height;
    protected int mx = -1, my = -1;
    protected long ticks;
    /** Milliseconds, for animations (caret blink, pulse). */
    protected long now;

    private List<Hit> hits = new ArrayList<>();
    private List<Hit> building = new ArrayList<>();
    protected TextInput focused;

    private static final class Hit {

        final float x, y, w, h;
        final Runnable action;

        Hit(float x, float y, float w, float h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    public void attach(Host host) {
        this.host = host;
    }

    public final void frame(Canvas c, int mouseX, int mouseY, int w, int h, long nowMs) {
        width = w;
        height = h;
        mx = mouseX;
        my = mouseY;
        now = nowMs;
        building = new ArrayList<>();
        render(c);
        hits = building;
    }

    protected abstract void render(Canvas c);

    public void tick() {
        ticks++;
    }

    public void onData(NBTTagCompound data) {}

    public void scroll(int direction) {}

    /** Mouse wheel: an open menu scrolls first. */
    public final void wheel(int direction) {
        if (menu != null && menu.options.length > MENU_ROWS) {
            menu.scroll = Math.max(0, Math.min(menu.options.length - MENU_ROWS, menu.scroll - direction));
            return;
        }
        scroll(direction);
    }

    public void onClose() {
        if (focused != null) focused.commit();
    }

    public void click(int x, int y, int button) {
        if (button != 0) return;
        TextInput wasFocused = focused;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && y >= h.y && x < h.x + h.w && y < h.y + h.h) {
                h.action.run();
                if (wasFocused != null && focused == wasFocused && wasFocused != hitInput) unfocus();
                hitInput = null;
                return;
            }
        }
        unfocus();
    }

    private TextInput hitInput;

    /** @return true when the key was used (a focused text field swallows everything except escape) */
    public boolean key(char ch, int code) {
        if (menu != null && code == KEY_ESCAPE) {
            menu = null;
            return true;
        }
        if (focused == null) return false;
        if (code == KEY_ESCAPE) {
            focused.focused = false;
            focused = null;
            return true;
        }
        if (code == KEY_RETURN || code == KEY_NUMPADENTER) {
            unfocus();
            return true;
        }
        focused.key(ch, code);
        return true;
    }

    private void unfocus() {
        if (focused == null) return;
        TextInput f = focused;
        focused = null;
        f.focused = false;
        f.commit();
    }

    // ------------------------------------------------------------------ interaction helpers

    protected void onClick(float x, float y, float w, float h, Runnable r) {
        building.add(new Hit(x, y, w, h, r));
    }

    protected boolean hover(float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    protected String tr(String key, Object... args) {
        return host == null ? key : host.tr(key, args);
    }

    /** Single line editable text. */
    public static final class TextInput {

        public String text = "";
        public boolean focused;
        private final int maxLen;
        private final Consumer<String> onCommit;
        private String committed = "";

        public TextInput(int maxLen, Consumer<String> onCommit) {
            this.maxLen = maxLen;
            this.onCommit = onCommit;
        }

        /** Empties the field without firing its commit action. */
        public void clear() {
            text = "";
            committed = "";
            focused = false;
        }

        public void set(String s) {
            if (focused) return;
            text = s == null ? "" : s;
            committed = text;
        }

        void key(char ch, int code) {
            if (code == KEY_BACK) {
                if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            } else if (code == KEY_DELETE) {
                text = "";
            } else if (ch >= 32 && ch != 127 && ch != '§' && text.length() < maxLen) {
                text += ch;
            }
        }

        void commit() {
            if (!text.equals(committed)) {
                committed = text;
                onCommit.accept(text);
            }
        }
    }

    // ------------------------------------------------------------------ text

    protected float text(Canvas c, String s, float x, float y, int color) {
        return c.text(s, x, y, color, 1);
    }

    /** Bold without Minecraft's extra letter spacing: the text drawn twice, half a unit apart. */
    protected float bold(Canvas c, String s, float x, float y, int color) {
        c.text(s, x, y, color, 1);
        return c.text(s, x + 0.5f, y, color, 1) + 0.5f;
    }

    protected void textRight(Canvas c, String s, float right, float y, int color) {
        c.text(s, right - c.width(s, 1), y, color, 1);
    }

    protected void textCenter(Canvas c, String s, float cx, float y, int color, float scale) {
        c.text(s, cx - c.width(s, scale) / 2, y, color, scale);
    }

    protected String fit(Canvas c, String s, float maxW) {
        if (s == null) return "";
        if (c.width(s, 1) <= maxW) return s;
        String dots = "…";
        float dw = c.width(dots, 1);
        int end = s.length();
        while (end > 0 && c.width(s.substring(0, end), 1) + dw > maxW) end--;
        return s.substring(0, end) + dots;
    }

    /** Big number with a small unit right after it, baseline aligned. Returns total width. */
    protected float value(Canvas c, String number, String unit, float x, float y, int color, float scale) {
        float w = c.text(number, x, y, color, scale);
        if (unit != null && !unit.isEmpty()) {
            w += 2;
            w += c.text(unit, x + w, y + 8 * scale - 8, Theme.LABEL2, 1);
        }
        return w;
    }

    // ------------------------------------------------------------------ chrome

    protected void scrim(Canvas c) {
        c.fill(0, 0, width, height, Theme.SCRIM);
    }

    protected void window(Canvas c, float x, float y, float w, float h) {
        for (int i = 4; i >= 1; i--)
            c.round(x - i * 1.5f, y - i * 1.5f + 2, w + i * 3, h + i * 3, Theme.RADIUS_WINDOW + i * 1.5f, 0x14000000);
        c.roundGradient(x, y, w, h, Theme.RADIUS_WINDOW, Theme.WINDOW_TOP, Theme.WINDOW);
        c.roundStroke(x, y, w, h, Theme.RADIUS_WINDOW, 0.5f, Theme.STROKE);
    }

    protected void card(Canvas c, float x, float y, float w, float h) {
        c.round(x, y, w, h, Theme.RADIUS_CARD, Theme.CARD);
    }

    /** Close button: grey circle with a cross. */
    protected void closeButton(Canvas c, float cx, float cy) {
        boolean hov = hover(cx - 6, cy - 6, 12, 12);
        c.circle(cx, cy, 5.5f, hov ? Theme.FILL_SELECTED : Theme.FILL);
        int col = hov ? Theme.LABEL : Theme.LABEL2;
        c.line(new float[] { cx - 2, cx + 2 }, new float[] { cy - 2, cy + 2 }, 1, col);
        c.line(new float[] { cx - 2, cx + 2 }, new float[] { cy + 2, cy - 2 }, 1, col);
        onClick(cx - 6, cy - 6, 12, 12, () -> host.close());
    }

    protected void chevronLeft(Canvas c, float x, float y, int color) {
        c.line(new float[] { x + 3, x, x + 3 }, new float[] { y, y + 3.5f, y + 7 }, 1.2f, color);
    }

    /** Segmented control (iOS style). Returns its width. */
    protected float segmented(Canvas c, float x, float y, String[] labels, int selected, IntConsumer pick) {
        return segmented(c, x, y, labels, selected, pick, null);
    }

    /** Segmented control with optional red count badges. */
    protected float segmented(Canvas c, float x, float y, String[] labels, int selected, IntConsumer pick,
        int[] badges) {
        float h = 14, pad = 9;
        float[] widths = new float[labels.length];
        float total = 2;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = c.width(labels[i], 1) + pad * 2 + badgeWidth(c, badges, i);
            total += widths[i];
        }
        c.round(x, y, total, h, Theme.RADIUS_CONTROL, Theme.FILL);
        float sx = x + 1;
        for (int i = 0; i < labels.length; i++) {
            boolean sel = i == selected;
            if (sel) c.round(sx, y + 1, widths[i], h - 2, Theme.RADIUS_CONTROL - 1, Theme.FILL_SELECTED);
            else if (i > 0 && i != selected + 1) c.fill(sx - 0.25f, y + 4, 0.5f, h - 8, Theme.SEPARATOR);
            int color = sel ? Theme.LABEL : hover(sx, y, widths[i], h) ? Theme.LABEL : Theme.LABEL2;
            float bw = badgeWidth(c, badges, i);
            float tx = sx + (widths[i] - bw) / 2 - c.width(labels[i], 1) / 2;
            c.text(labels[i], tx, y + 3.5f, color, 1);
            if (bw > 0) {
                String n = Integer.toString(badges[i]);
                float bx = tx + c.width(labels[i], 1) + 3;
                c.round(bx, y + 2.5f, bw - 3, 9, 4.5f, Theme.RED);
                c.text(n, bx + (bw - 3 - c.width(n, 1)) / 2, y + 3.5f, Theme.LABEL, 1);
            }
            final int idx = i;
            onClick(sx, y, widths[i], h, () -> pick.accept(idx));
            sx += widths[i];
        }
        return total;
    }

    private static float badgeWidth(Canvas c, int[] badges, int i) {
        if (badges == null || i >= badges.length || badges[i] <= 0) return 0;
        return Math.max(9, c.width(Integer.toString(badges[i]), 1) + 5) + 3;
    }

    public static float segmentedWidth(Canvas c, String[] labels) {
        return segmentedWidth(c, labels, null);
    }

    public static float segmentedWidth(Canvas c, String[] labels, int[] badges) {
        float total = 2;
        for (int i = 0; i < labels.length; i++) total += c.width(labels[i], 1) + 18 + badgeWidth(c, badges, i);
        return total;
    }

    /** iOS switch, 22 x 13. */
    protected void toggle(Canvas c, float x, float y, boolean on, Runnable r) {
        c.round(x, y, 22, 13, 6.5f, on ? Theme.GREEN : Theme.FILL_SELECTED);
        float kx = on ? x + 15.5f : x + 6.5f;
        c.circle(kx, y + 6.5f, 5.3f, 0xFFFFFFFF);
        onClick(x - 2, y - 2, 26, 17, r);
    }

    /** Small rounded badge, tinted. Returns its width. */
    protected float pill(Canvas c, float x, float y, String label, int color) {
        float w = c.width(label, 1) + 10;
        c.round(x, y, w, 11, 5.5f, Theme.withAlpha(color, 0x38));
        c.text(label, x + 5, y + 1.5f, color, 1);
        return w;
    }

    /** Rounded button; filled with {@code color} when it is not 0, plain text otherwise. */
    protected void button(Canvas c, float x, float y, float w, float h, String label, int color, Runnable r) {
        boolean hov = hover(x, y, w, h);
        if (color != 0) c.round(x, y, w, h, h / 2, hov ? Theme.mix(color, 0xFFFFFFFF, 0.15f) : color);
        else if (hov) c.round(x, y, w, h, h / 2, Theme.FILL);
        textCenter(c, label, x + w / 2, y + (h - 8) / 2 + 0.5f, color != 0 ? Theme.LABEL : Theme.BLUE, 1);
        onClick(x, y, w, h, r);
    }

    /** Text field drawn in the theme. */
    protected void input(Canvas c, TextInput in, float x, float y, float w, float h, String placeholder) {
        c.round(x, y, w, h, Theme.RADIUS_CONTROL, in.focused ? Theme.FILL_SELECTED : Theme.FILL);
        if (in.focused) c.roundStroke(x, y, w, h, Theme.RADIUS_CONTROL, 0.75f, Theme.BLUE);
        float ty = y + (h - 8) / 2 + 0.5f;
        if (in.text.isEmpty() && !in.focused) c.text(placeholder, x + 5, ty, Theme.LABEL3, 1);
        else {
            String shown = in.text;
            while (shown.length() > 0 && c.width(shown, 1) > w - 12) shown = shown.substring(1);
            float tw = c.text(shown, x + 5, ty, Theme.LABEL, 1);
            if (in.focused && (now / 500) % 2 == 0) c.fill(x + 5 + tw + 0.5f, ty - 1, 0.75f, 10, Theme.BLUE);
        }
        onClick(x, y, w, h, () -> {
            hitInput = in;
            if (focused != in) {
                if (focused != null) unfocus();
                focused = in;
                in.focused = true;
            }
        });
    }

    // ------------------------------------------------------------------ drop-down menus

    private static final int MENU_ROWS = 9;
    private static final float MENU_ROW_H = 13;

    /** The open drop-down; drawn over everything by {@link #menus}. */
    private Menu menu;
    private String pendingMenu;

    private static final class Menu {

        final String id;
        float x, y, w, anchorY;
        String[] options;
        int selected, scroll;
        IntConsumer pick;

        Menu(String id) {
            this.id = id;
        }
    }

    /** Opens the menu of the chip with this id on the next frame (used by previews). */
    public void openMenu(String id) {
        pendingMenu = id;
    }

    protected boolean menuOpen() {
        return menu != null;
    }

    /**
     * Filter chip that opens a menu of options. The chip shows {@code text}; when {@code active} (a filter is set) it
     * is tinted. Returns its width.
     */
    protected float chip(Canvas c, float x, float y, String id, String text, boolean active, String[] options,
        int selected, IntConsumer pick) {
        float w = c.width(text, 1) + 22, h = 14;
        boolean open = menu != null && menu.id.equals(id);
        boolean hov = hover(x, y, w, h);
        int bg = active ? Theme.withAlpha(Theme.BLUE, open || hov ? 0x66 : 0x4D)
            : open || hov ? Theme.FILL_SELECTED : Theme.FILL;
        c.round(x, y, w, h, h / 2, bg);
        int fg = active ? Theme.LABEL : Theme.LABEL2;
        c.text(text, x + 8, y + 3.5f, fg, 1);
        float ax = x + w - 10, ay = y + 6;
        if (open) c.line(new float[] { ax - 2.5f, ax, ax + 2.5f }, new float[] { ay + 2, ay - 0.5f, ay + 2 }, 1, fg);
        else c.line(new float[] { ax - 2.5f, ax, ax + 2.5f }, new float[] { ay, ay + 2.5f, ay }, 1, fg);
        if (id.equals(pendingMenu)) {
            pendingMenu = null;
            open = false;
            menu = null;
            openAt(id, x, y, w, h);
            open = true;
        }
        if (open) {
            menu.options = options;
            menu.selected = selected;
            menu.pick = pick;
        }
        onClick(x, y, w, h, () -> {
            if (menu != null && menu.id.equals(id)) menu = null;
            else openAt(id, x, y, w, h);
        });
        return w;
    }

    private void openAt(String id, float x, float y, float w, float h) {
        Menu m = new Menu(id);
        m.x = x;
        m.y = y + h + 3;
        m.anchorY = y;
        m.w = w;
        m.options = new String[0];
        menu = m;
    }

    /** Draws the open menu on top; call last in {@link #render}. */
    protected void menus(Canvas c) {
        Menu m = menu;
        if (m == null || m.pick == null) return;
        // a click anywhere else closes it
        onClick(0, 0, width, height, () -> menu = null);
        float optW = 0;
        for (String o : m.options) optW = Math.max(optW, c.width(o, 1));
        float w = Math.max(m.w, optW + 30);
        int rows = Math.min(MENU_ROWS, m.options.length);
        m.scroll = Math.max(0, Math.min(m.scroll, m.options.length - rows));
        float h = rows * MENU_ROW_H + 6;
        float x = Math.min(m.x, width - w - 4), y = m.y;
        if (y + h > height - 4) y = Math.max(4, m.anchorY - 3 - h);
        for (int i = 3; i >= 1; i--) c.round(x - i, y - i + 1.5f, w + 2 * i, h + 2 * i, 7 + i, 0x1A000000);
        c.round(x, y, w, h, 7, Theme.MENU);
        c.roundStroke(x, y, w, h, 7, 0.5f, Theme.STROKE);
        for (int r = 0; r < rows; r++) {
            int i = m.scroll + r;
            float iy = y + 3 + r * MENU_ROW_H;
            boolean hov = hover(x + 3, iy, w - 6, MENU_ROW_H);
            if (hov) c.round(x + 3, iy, w - 6, MENU_ROW_H, 4, Theme.BLUE);
            if (i == m.selected) {
                float cx = x + 8, cy = iy + 6.5f;
                c.line(
                    new float[] { cx - 2.5f, cx - 0.5f, cx + 3 },
                    new float[] { cy, cy + 2, cy - 2.5f },
                    1.1f,
                    Theme.LABEL);
            }
            c.text(m.options[i], x + 16, iy + 2.5f, Theme.LABEL, 1);
            final int idx = i;
            onClick(x + 3, iy, w - 6, MENU_ROW_H, () -> {
                IntConsumer pick = m.pick;
                menu = null;
                pick.accept(idx);
            });
        }
        if (m.options.length > rows) {
            float trackH = h - 8, barH = Math.max(8, trackH * rows / m.options.length);
            float barY = y + 4 + (trackH - barH) * m.scroll / (m.options.length - rows);
            c.round(x + w - 4, barY, 2, barH, 1, Theme.LABEL3);
        }
    }

    // ------------------------------------------------------------------ charts

    /** Area chart of one or two series, auto scaled, with hairline grid and the max value labelled. */
    protected void chart(Canvas c, float x, float y, float w, float h, long[] a, int ca, long[] b, int cb) {
        long max = 1;
        for (long v : a) max = Math.max(max, v);
        if (b != null) for (long v : b) max = Math.max(max, v);
        max = niceCeil(max);
        float gutter = Math.max(c.width(Fmt.si(max), 1), c.width(Fmt.si(max / 2), 1)) + 6;
        float pw = w - gutter;
        for (int i = 0; i <= 2; i++) {
            float gy = y + h * i / 2;
            c.fill(x, gy, pw, 0.5f, Theme.LABEL4);
        }
        textRight(c, Fmt.si(max), x + w, y - 3.5f, Theme.LABEL3);
        textRight(c, Fmt.si(max / 2), x + w, y + h / 2 - 3.5f, Theme.LABEL3);
        textRight(c, "0", x + w, y + h - 3.5f, Theme.LABEL3);
        series(c, x, y, pw, h, a, max, ca);
        if (b != null) series(c, x, y, pw, h, b, max, cb);
    }

    private void series(Canvas c, float x, float y, float w, float h, long[] v, long max, int color) {
        int n = v.length;
        if (n == 0) return;
        float[] xs = new float[Math.max(2, n)], ys = new float[Math.max(2, n)];
        for (int i = 0; i < n; i++) {
            xs[i] = n == 1 ? x : x + w * i / (n - 1);
            ys[i] = y + h - (float) ((double) Math.max(0, v[i]) / max * h);
        }
        if (n == 1) {
            xs[1] = x + w;
            ys[1] = ys[0];
        }
        c.area(xs, ys, y + h, Theme.withAlpha(color, 0x60), Theme.withAlpha(color, 0x00));
        c.line(xs, ys, 1.2f, color);
    }

    /** Vertical bars, the highlighted one in full color. */
    protected void bars(Canvas c, float x, float y, float w, float h, long[] v, int color, int highlight) {
        long max = 1;
        for (long e : v) max = Math.max(max, e);
        float bw = w / v.length;
        for (int i = 0; i < v.length; i++) {
            float bh = (float) ((double) v[i] / max * h);
            int col = i == highlight ? color : Theme.withAlpha(color, 0x70);
            c.round(
                x + i * bw + bw * 0.15f,
                y + h - Math.max(1, bh),
                bw * 0.7f,
                Math.max(1, bh),
                Math.min(1.5f, bw * 0.35f),
                col);
        }
    }

    static long niceCeil(long v) {
        if (v <= 10) return 10;
        long p = 1;
        while (p <= v / 10) p *= 10;
        for (long m : new long[] { 1, 2, 5, 10 }) if (p * m >= v) return p * m;
        return v;
    }
}
