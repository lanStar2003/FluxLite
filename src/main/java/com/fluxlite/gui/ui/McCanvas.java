package com.fluxlite.gui.ui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * {@link Canvas} on top of the Tessellator and Minecraft's font renderer. Works in GUI space and, for the floating
 * display, in the world (see {@link #world}): it only draws at z = 0 of the current matrix.
 * <p>
 * In the world every vertex gets a normal pointing up (the panel is drawn upside down, so that is -y here): shader
 * packs light geometry by its normal, and up is the side they light fully, whatever way the panel faces.
 */
@SideOnly(Side.CLIENT)
public final class McCanvas implements Canvas {

    private final FontRenderer font;
    private final int scaleFactor;
    private final int displayHeight;
    private final boolean world;
    private float opacity = 1;
    private final float[] px = new float[256], py = new float[256], qx = new float[256], qy = new float[256];

    public McCanvas(FontRenderer font, int scaleFactor, int displayHeight) {
        this(font, scaleFactor, displayHeight, false);
    }

    private McCanvas(FontRenderer font, int scaleFactor, int displayHeight, boolean world) {
        this.font = font;
        this.scaleFactor = Math.max(1, scaleFactor);
        this.displayHeight = displayHeight;
        this.world = world;
    }

    /** Canvas for drawing in the world; the caller sets up the GL state. Clipping does nothing there. */
    public static McCanvas world(FontRenderer font) {
        return new McCanvas(font, 3, 0, true);
    }

    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    private int fade(int argb) {
        if (opacity >= 1) return argb;
        return (int) ((argb >>> 24) * opacity) << 24 | (argb & 0xFFFFFF);
    }

    public void begin() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }

    public void end() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glPopAttrib();
        restoreTexture();
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * Switches texturing back on after a {@code glPopAttrib}. The pop restores OpenGL, but Angelica only tells the
     * shader pipeline when texturing is switched through glEnable / glDisable; without this, whatever is drawn next
     * (chests, signs...) samples no texture and comes out white.
     */
    public static void restoreTexture() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    @Override
    public void surface(boolean solid) {
        if (!world) return;
        GL11.glDepthMask(solid);
        if (solid) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        else {
            // drawn on the surface's plane: pulled towards the eye so it wins the depth test
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -4);
        }
    }

    private void start(Tessellator t, int mode) {
        t.startDrawing(mode);
        if (world) t.setNormal(0, -1, 0);
    }

    private void color(Tessellator t, int argb) {
        argb = fade(argb);
        t.setColorRGBA(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
    }

    private static void shapes() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        gradient(x, y, w, h, argb, argb);
    }

    @Override
    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        shapes();
        Tessellator t = Tessellator.instance;
        start(t, GL11.GL_QUADS);
        color(t, bottom);
        t.addVertex(x, y + h, 0);
        t.addVertex(x + w, y + h, 0);
        color(t, top);
        t.addVertex(x + w, y, 0);
        t.addVertex(x, y, 0);
        t.draw();
    }

    private int segments(float r) {
        return Math.max(2, Math.min(14, (int) (r * scaleFactor / 1.5f)));
    }

    /** Fills px/py with the outline of a rounded rectangle, clockwise from the top edge. */
    private int perimeter(float x, float y, float w, float h, float r, float[] ox, float[] oy, int seg) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        float[][] centers = { { x + w - r, y + r }, { x + w - r, y + h - r }, { x + r, y + h - r }, { x + r, y + r } };
        int n = 0;
        for (int c = 0; c < 4; c++) {
            double start = Math.toRadians(-90 + c * 90);
            for (int i = 0; i <= seg; i++) {
                double a = start + Math.toRadians(90.0 * i / seg);
                ox[n] = centers[c][0] + (float) Math.cos(a) * r;
                oy[n] = centers[c][1] + (float) Math.sin(a) * r;
                n++;
            }
        }
        return n;
    }

    @Override
    public void round(float x, float y, float w, float h, float r, int argb) {
        roundGradient(x, y, w, h, r, argb, argb);
    }

    @Override
    public void roundGradient(float x, float y, float w, float h, float r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        shapes();
        int n = perimeter(x, y, w, h, r, px, py, segments(r));
        Tessellator t = Tessellator.instance;
        start(t, GL11.GL_TRIANGLE_FAN);
        color(t, Theme.mix(top, bottom, 0.5f));
        t.addVertex(x + w / 2, y + h / 2, 0);
        for (int i = 0; i <= n; i++) {
            int k = i % n;
            color(t, Theme.mix(top, bottom, (py[k] - y) / h));
            t.addVertex(px[k], py[k], 0);
        }
        t.draw();
    }

    @Override
    public void roundStroke(float x, float y, float w, float h, float r, float th, int argb) {
        if (w <= 0 || h <= 0) return;
        shapes();
        int seg = segments(r);
        int n = perimeter(x, y, w, h, r, px, py, seg);
        perimeter(x + th, y + th, w - 2 * th, h - 2 * th, Math.max(0, r - th), qx, qy, seg);
        Tessellator t = Tessellator.instance;
        start(t, GL11.GL_TRIANGLE_STRIP);
        color(t, argb);
        for (int i = 0; i <= n; i++) {
            int k = i % n;
            t.addVertex(px[k], py[k], 0);
            t.addVertex(qx[k], qy[k], 0);
        }
        t.draw();
    }

    @Override
    public void circle(float cx, float cy, float r, int argb) {
        round(cx - r, cy - r, 2 * r, 2 * r, r, argb);
    }

    @Override
    public void line(float[] xs, float[] ys, float width, int argb) {
        int n = Math.min(xs.length, ys.length);
        if (n < 2) return;
        shapes();
        float hw = width / 2;
        Tessellator t = Tessellator.instance;
        start(t, GL11.GL_QUADS);
        color(t, argb);
        for (int i = 0; i < n - 1; i++) {
            float dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4f) continue;
            float nx = -dy / len * hw, ny = dx / len * hw;
            t.addVertex(xs[i] + nx, ys[i] + ny, 0);
            t.addVertex(xs[i + 1] + nx, ys[i + 1] + ny, 0);
            t.addVertex(xs[i + 1] - nx, ys[i + 1] - ny, 0);
            t.addVertex(xs[i] - nx, ys[i] - ny, 0);
        }
        t.draw();
        // round joints
        if (n <= 64) for (int i = 1; i < n - 1; i++) circle(xs[i], ys[i], hw, argb);
    }

    @Override
    public void area(float[] xs, float[] ys, float baseY, int top, int bottom) {
        int n = Math.min(xs.length, ys.length);
        if (n < 2) return;
        shapes();
        Tessellator t = Tessellator.instance;
        start(t, GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i < n; i++) {
            color(t, top);
            t.addVertex(xs[i], ys[i], 0);
            color(t, bottom);
            t.addVertex(xs[i], baseY, 0);
        }
        t.draw();
    }

    @Override
    public float text(String s, float x, float y, int argb, float scale) {
        if (s == null || s.isEmpty()) return 0;
        argb = fade(argb);
        // the font renderer treats an alpha below 4 as fully opaque
        if ((argb >>> 24) < 4) return width(s, scale);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (world) GL11.glNormal3f(0, -1, 0);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        if (scale != 1) GL11.glScalef(scale, scale, 1);
        font.drawString(s, 0, 0, argb, false);
        GL11.glPopMatrix();
        return font.getStringWidth(s) * scale;
    }

    @Override
    public float width(String s, float scale) {
        return s == null ? 0 : font.getStringWidth(s) * scale;
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        if (world) return;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            (int) (x * scaleFactor),
            displayHeight - (int) ((y + h) * scaleFactor),
            Math.max(0, (int) (w * scaleFactor)),
            Math.max(0, (int) (h * scaleFactor)));
    }

    @Override
    public void unclip() {
        if (world) return;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
