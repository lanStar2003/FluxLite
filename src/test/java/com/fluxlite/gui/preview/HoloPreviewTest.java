package com.fluxlite.gui.preview;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigInteger;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fluxlite.gui.holo.HoloPanel;
import com.fluxlite.gui.holo.HoloState;

/**
 * Previews of the floating display: the panel itself, its unfold animation, and the control center with the display
 * above it (isometric, the same geometry as the in-game renderer). Written to build/previews.
 */
class HoloPreviewTest {

    private static final File JAR = new File(
        System.getProperty("user.home"),
        ".gradle/caches/retro_futura_gradle/mc-vanilla/1.7.10/client.jar");
    private static final File OUT = new File("build/previews");
    private static final int PAD = 6;
    private static PreviewTest.FakeHost HOST;

    static HoloState sample(float open) {
        HoloState s = new HoloState();
        s.team = "lan";
        s.balance = new BigInteger("184467440737095516");
        s.in = 12_582_912;
        s.out = 9_437_184;
        s.shownIn = s.in;
        s.shownOut = s.out;
        s.eta = -1;
        s.online = 7;
        s.connectors = 8;
        s.alerts = 0;
        int n = 50;
        s.curveIn = new long[n];
        s.curveOut = new long[n];
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < n; i++) {
            s.curveIn[i] = (long) (12_000_000 + 1_200_000 * Math.sin(i / 6.0) + 300_000 * r.nextDouble());
            s.curveOut[i] = (long) (9_000_000 + 2_200_000 * Math.sin(i / 3.5 + 1) + 300_000 * r.nextDouble());
        }
        s.steamOn = true;
        s.steam = new BigInteger("48200000");
        s.steamIn = 3_600;
        s.steamOut = 2_880;
        s.open = open;
        return s;
    }

    /** The panel alone on a transparent image, {@code scale} pixels per panel unit. */
    private static BufferedImage panel(ZipFile jar, HoloState s, int scale, long now) throws Exception {
        return panel(jar, s, scale, now, false);
    }

    private static BufferedImage panel(ZipFile jar, HoloState s, int scale, long now, boolean opaque) throws Exception {
        Java2DCanvas c = new Java2DCanvas((int) HoloPanel.W + 2 * PAD, (int) HoloPanel.H + 2 * PAD, scale, jar);
        c.graphics()
            .translate(PAD, PAD);
        HoloPanel.draw(c, s, now, false, opaque, HOST::tr);
        return c.image;
    }

    private static void night(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(0x0B1220), 0, h, new Color(0x1B2633)));
        g.fillRect(0, 0, w, h);
    }

    @Test
    void renderHologram() throws Exception {
        Assumptions.assumeTrue(JAR.isFile(), "vanilla client jar not found");
        HOST = new PreviewTest.FakeHost();
        OUT.mkdirs();
        try (ZipFile jar = new ZipFile(JAR)) {
            // the panel, fully open
            BufferedImage p = panel(jar, sample(1), 6, 1200);
            BufferedImage img = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            night(g, img.getWidth(), img.getHeight());
            g.drawImage(p, 0, 0, null);
            ImageIO.write(img, "png", new File(OUT, "holo_panel.png"));

            // see-through glass next to the solid panel, over a busy background so the difference shows
            BufferedImage glass = panel(jar, sample(1), 4, 1200, false), solid = panel(jar, sample(1), 4, 1200, true);
            BufferedImage both = new BufferedImage(
                glass.getWidth() * 2 + 24,
                glass.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            g = both.createGraphics();
            for (int bx = 0; bx < both.getWidth(); bx += 24) for (int by = 0; by < both.getHeight(); by += 24) {
                g.setColor((bx / 24 + by / 24) % 2 == 0 ? new Color(0x5D8A3A) : new Color(0x8A6A45));
                g.fillRect(bx, by, 24, 24);
            }
            g.drawImage(glass, 0, 0, null);
            g.drawImage(solid, glass.getWidth() + 24, 0, null);
            ImageIO.write(both, "png", new File(OUT, "holo_glass_vs_solid.png"));
            HoloState dry = sample(1);
            dry.steamOn = false;
            p = panel(jar, dry, 6, 1200);
            g = img.createGraphics();
            night(g, img.getWidth(), img.getHeight());
            g.drawImage(p, 0, 0, null);
            ImageIO.write(img, "png", new File(OUT, "holo_panel_nosteam.png"));

            // unfolding: line, glass, content flickering in, open
            float[] steps = { 0.18f, 0.45f, 0.8f, 1f };
            int sc = 3;
            int pw = ((int) HoloPanel.W + 2 * PAD) * sc, ph = ((int) HoloPanel.H + 2 * PAD) * sc;
            BufferedImage strip = new BufferedImage(pw * steps.length, ph, BufferedImage.TYPE_INT_ARGB);
            g = strip.createGraphics();
            night(g, strip.getWidth(), ph);
            for (int i = 0; i < steps.length; i++) g.drawImage(panel(jar, sample(steps[i]), sc, 700), i * pw, 0, null);
            ImageIO.write(strip, "png", new File(OUT, "holo_unfold.png"));

            scene(jar);
        }
    }

    // ------------------------------------------------------------------ isometric scene

    private static final double S = 150;
    private static final int SW = 820, SH = 720;
    /** Same numbers as HologramRenderer. */
    private static final double UNIT = 1 / 72.0, LIFT = 0.45;

    private static double[] project(double x, double y, double z) {
        double sx = (x - z) * Math.cos(Math.toRadians(30)) * S + SW / 2.0;
        double sy = (x + z) * Math.sin(Math.toRadians(30)) * S - y * S + SH * 0.78;
        return new double[] { sx, sy };
    }

    private static void quad(Graphics2D g, double[][] pts, Color c) {
        Polygon poly = new Polygon();
        for (double[] q : pts) {
            double[] s = project(q[0], q[1], q[2]);
            poly.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        }
        g.setColor(c);
        g.fillPolygon(poly);
        g.drawPolygon(poly);
    }

    private static Color shade(int argb, double f) {
        return new Color((int) ((argb >> 16 & 255) * f), (int) ((argb >> 8 & 255) * f), (int) ((argb & 255) * f));
    }

    private static BufferedImage tex(String name) throws Exception {
        return ImageIO
            .read(HoloPreviewTest.class.getResourceAsStream("/assets/fluxlite/textures/blocks/" + name + ".png"));
    }

    /** A full block at the origin: top, south (+Z, the screen) and east faces. */
    private static void block(Graphics2D g, BufferedImage top, BufferedImage south, BufferedImage east) {
        for (int u = 0; u < 16; u++) for (int v = 0; v < 16; v++) {
            double a = u / 16.0, b = (u + 1) / 16.0, c = v / 16.0, d = (v + 1) / 16.0;
            quad(g, new double[][] { { a, 1, c }, { b, 1, c }, { b, 1, d }, { a, 1, d } }, shade(top.getRGB(u, v), 1));
            quad(
                g,
                new double[][] { { a, 1 - c, 1 }, { b, 1 - c, 1 }, { b, 1 - d, 1 }, { a, 1 - d, 1 } },
                shade(south.getRGB(u, v), 0.8));
            quad(
                g,
                new double[][] { { 1, 1 - c, 1 - a }, { 1, 1 - c, 1 - b }, { 1, 1 - d, 1 - b }, { 1, 1 - d, 1 - a } },
                shade(east.getRGB(u, v), 0.6));
        }
    }

    private static void scene(ZipFile jar) throws Exception {
        BufferedImage img = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        night(g, SW, SH);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // floor
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) quad(
            g,
            new double[][] { { x, 0, z }, { x + 1, 0, z }, { x + 1, 0, z + 1 }, { x, 0, z + 1 } },
            (x + z & 1) == 0 ? new Color(0x2A3138) : new Color(0x262C33));
        block(g, tex("control_center_top"), tex("control_center_front"), tex("control_center_side"));

        double pw = HoloPanel.W * UNIT, ph = HoloPanel.H * UNIT;
        double x0 = 0.5 - pw / 2, bottom = 1 + LIFT, z = 0.5;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // beam from the lens to the bottom edge of the panel
        double[] lens = project(0.5, 1.01, 0.5), l = project(x0 + 0.12, bottom, z),
            r = project(x0 + pw - 0.12, bottom, z);
        Polygon beam = new Polygon(
            new int[] { (int) lens[0], (int) l[0], (int) r[0] },
            new int[] { (int) lens[1], (int) l[1], (int) r[1] },
            3);
        g.setPaint(
            new GradientPaint(
                0,
                (float) lens[1],
                new Color(100, 210, 255, 90),
                0,
                (float) l[1],
                new Color(100, 210, 255, 12)));
        g.fill(beam);
        g.setColor(new Color(150, 230, 255, 120));
        g.fillOval((int) lens[0] - 18, (int) lens[1] - 9, 36, 18);

        // the panel: a plane at z = 0.5, which the isometric projection maps affinely
        int scale = 6;
        BufferedImage p = panel(jar, sample(1), scale, 1200);
        double cos = Math.cos(Math.toRadians(30)) * S, sin = Math.sin(Math.toRadians(30)) * S;
        double perPx = UNIT / scale; // blocks per panel image pixel
        double ox = x0 - PAD * UNIT, oy = bottom + ph + PAD * UNIT;
        double[] o = project(ox, oy, z);
        AffineTransform at = new AffineTransform(cos * perPx, sin * perPx, 0, S * perPx, o[0], o[1]);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(p, at, null);
        ImageIO.write(img, "png", new File(OUT, "holo_scene.png"));
    }
}
