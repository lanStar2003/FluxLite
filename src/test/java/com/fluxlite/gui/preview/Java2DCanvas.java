package com.fluxlite.gui.preview;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import com.fluxlite.gui.ui.Canvas;

/**
 * Java2D version of the in-game canvas, for PNG previews. Text uses Minecraft's unicode font pages (the font the game
 * uses for Chinese) taken from the vanilla jar, with the same glyph widths and advance rules as FontRenderer.
 */
public final class Java2DCanvas implements Canvas {

    private static final int[] COLOR_CODES = new int[16];

    static {
        for (int i = 0; i < 16; i++) {
            int j = (i >> 3 & 1) * 85;
            int r = (i >> 2 & 1) * 170 + j, g = (i >> 1 & 1) * 170 + j, b = (i & 1) * 170 + j;
            if (i == 6) r += 85;
            COLOR_CODES[i] = r << 16 | g << 8 | b;
        }
    }

    public final BufferedImage image;
    private final Graphics2D g;
    private final int scale;
    private final ZipFile jar;
    private final byte[] glyphWidth = new byte[65536];
    private final Map<Integer, BufferedImage> pages = new HashMap<>();
    private float opacity = 1;

    public Java2DCanvas(int guiW, int guiH, int scale, ZipFile minecraftJar) throws Exception {
        this.scale = scale;
        this.jar = minecraftJar;
        image = new BufferedImage(guiW * scale, guiH * scale, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(scale, scale);
        try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/font/glyph_sizes.bin"))) {
            int off = 0, n;
            while (off < glyphWidth.length && (n = in.read(glyphWidth, off, glyphWidth.length - off)) > 0) off += n;
        }
    }

    /** Paints a backdrop that looks like a dimmed game world. */
    public void backdrop() {
        g.setPaint(
            new GradientPaint(0, 0, new Color(0x6A8CAF), 0, image.getHeight() / (float) scale, new Color(0x3B4A33)));
        g.fill(new Rectangle2D.Float(0, 0, image.getWidth(), image.getHeight()));
    }

    private Color c(int argb) {
        return new Color(fade(argb), true);
    }

    private int fade(int argb) {
        if (opacity >= 1) return argb;
        return (int) ((argb >>> 24) * opacity) << 24 | (argb & 0xFFFFFF);
    }

    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    /** Direct access for previews that compose several renders. */
    public Graphics2D graphics() {
        return g;
    }

    @Override
    public void fill(float x, float y, float w, float h, int argb) {
        g.setColor(c(argb));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        g.setPaint(new GradientPaint(0, y, c(top), 0, y + h, c(bottom)));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    private static Shape rr(float x, float y, float w, float h, float r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        return new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r);
    }

    @Override
    public void round(float x, float y, float w, float h, float r, int argb) {
        if (w <= 0 || h <= 0) return;
        g.setColor(c(argb));
        g.fill(rr(x, y, w, h, r));
    }

    @Override
    public void roundGradient(float x, float y, float w, float h, float r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.setPaint(new GradientPaint(0, y, c(top), 0, y + h, c(bottom)));
        g.fill(rr(x, y, w, h, r));
    }

    @Override
    public void roundStroke(float x, float y, float w, float h, float r, float t, int argb) {
        Area a = new Area(rr(x, y, w, h, r));
        a.subtract(new Area(rr(x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t))));
        g.setColor(c(argb));
        g.fill(a);
    }

    @Override
    public void circle(float cx, float cy, float r, int argb) {
        g.setColor(c(argb));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override
    public void line(float[] xs, float[] ys, float width, int argb) {
        if (xs.length < 2) return;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++) p.lineTo(xs[i], ys[i]);
        g.setColor(c(argb));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    @Override
    public void area(float[] xs, float[] ys, float baseY, int top, int bottom) {
        if (xs.length < 2) return;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xs[0], baseY);
        float minY = baseY;
        for (int i = 0; i < xs.length; i++) {
            p.lineTo(xs[i], ys[i]);
            minY = Math.min(minY, ys[i]);
        }
        p.lineTo(xs[xs.length - 1], baseY);
        p.closePath();
        g.setPaint(new GradientPaint(0, minY, c(top), 0, baseY, c(bottom)));
        g.fill(p);
    }

    // ------------------------------------------------------------------ Minecraft unicode font

    private BufferedImage page(int page) {
        return pages.computeIfAbsent(page, p -> {
            try {
                ZipEntry e = jar.getEntry(String.format("assets/minecraft/textures/font/unicode_page_%02x.png", p));
                if (e == null) return null;
                try (InputStream in = jar.getInputStream(e)) {
                    return ImageIO.read(in);
                }
            } catch (Exception ex) {
                return null;
            }
        });
    }

    @Override
    public float text(String s, float x, float y, int argb, float textScale) {
        if (s == null || s.isEmpty()) return 0;
        AffineTransform saved = g.getTransform();
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setComposite(AlphaComposite.SrcOver);
        argb = fade(argb);
        int alpha = argb >>> 24;
        int color = argb & 0xFFFFFF;
        boolean bold = false;
        float pos = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                int code = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(s.charAt(++i)));
                if (code >= 0 && code < 16) {
                    color = COLOR_CODES[code];
                    bold = false;
                } else if (code == 17) bold = true;
                else if (code == 21) {
                    bold = false;
                    color = argb & 0xFFFFFF;
                }
                continue;
            }
            float adv = glyph(ch, x + pos * textScale, y, color, alpha, textScale);
            if (bold) {
                glyph(ch, x + (pos + 0.5f) * textScale, y, color, alpha, textScale);
                adv += 1;
            }
            pos += adv;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        g.setTransform(saved);
        return width(s, textScale);
    }

    /** Draws one glyph at GUI position (gx, gy); returns the advance in unscaled GUI units. */
    private float glyph(char ch, float gx, float gy, int rgb, int alpha, float textScale) {
        if (ch == ' ') return 4;
        int size = glyphWidth[ch] & 0xFF;
        if (size == 0) return 0;
        BufferedImage pg = page(ch >> 8);
        if (pg == null) return 0;
        int start = size >>> 4, end = (size & 15) + 1;
        int tx = (ch % 16) * 16, ty = ((ch & 255) / 16) * 16;
        float texel = scale * textScale / 2f; // a page texel is half a GUI unit
        AffineTransform saved = g.getTransform();
        java.awt.geom.Point2D o = saved.transform(new java.awt.geom.Point2D.Float(gx, gy), null);
        float ox = (float) o.getX(), oy = (float) o.getY();
        g.setTransform(new AffineTransform());
        for (int py = 0; py < 16; py++) {
            for (int px = start; px < end; px++) {
                int a = pg.getRGB(tx + px, ty + py) >>> 24;
                if (a == 0) continue;
                g.setColor(new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, a * alpha / 255));
                float x0 = ox + (px - start) * texel, y0 = oy + py * texel;
                g.fill(new Rectangle2D.Float(x0, y0, texel, texel));
            }
        }
        g.setTransform(saved);
        return (end - start) / 2f + 1;
    }

    @Override
    public float width(String s, float textScale) {
        if (s == null) return 0;
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(++i));
                if (code == 'l') bold = true;
                else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) bold = false;
                continue;
            }
            int cw;
            if (ch == ' ') cw = 4;
            else {
                int size = glyphWidth[ch] & 0xFF;
                if (size == 0) cw = 0;
                else {
                    int j = size >>> 4, k = size & 15;
                    if (k > 7) {
                        k = 15;
                        j = 0;
                    }
                    cw = (k + 1 - j) / 2 + 1;
                }
            }
            w += cw;
            if (bold && cw > 0) w++;
        }
        return w * textScale;
    }

    @Override
    public void clip(float x, float y, float w, float h) {
        g.setClip(new Rectangle2D.Float(x, y, w, h));
    }

    @Override
    public void unclip() {
        g.setClip(null);
    }
}
