package com.fluxlite.gui.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.fluxlite.block.BlockConnector;

/**
 * Isometric software render of the connector model (core, arms, flanges) with the real textures, so the block's
 * look can be checked without starting the game. Faces are shaded like Minecraft (top 1.0, sides 0.8 / 0.6).
 */
class ModelPreviewTest {

    private static final double S = 220;
    private static final int W = 560, H = 560;

    private static final class Box {

        final float[] b;
        final String tex;

        Box(float[] b, String tex) {
            this.b = b;
            this.tex = tex;
        }
    }

    private static BufferedImage tex(String name) throws Exception {
        return ImageIO
            .read(ModelPreviewTest.class.getResourceAsStream("/assets/fluxlite/textures/blocks/" + name + ".png"));
    }

    private static double[] project(double x, double y, double z) {
        double sx = (x - z) * Math.cos(Math.toRadians(30)) * S + W / 2.0;
        double sy = (x + z) * Math.sin(Math.toRadians(30)) * S - y * S + H * 0.62;
        return new double[] { sx, sy };
    }

    private static void quad(Graphics2D g, double[][] pts, Color c) {
        Polygon p = new Polygon();
        for (double[] q : pts) {
            double[] s = project(q[0], q[1], q[2]);
            p.addPoint((int) Math.round(s[0]), (int) Math.round(s[1]));
        }
        g.setColor(c);
        g.fillPolygon(p);
        g.drawPolygon(p);
    }

    private static Color shade(int argb, double f) {
        int r = (int) ((argb >> 16 & 255) * f), gg = (int) ((argb >> 8 & 255) * f), b = (int) ((argb & 255) * f);
        return new Color(r, gg, b);
    }

    /** Draws the three faces of a box visible from (+x, +y, +z). */
    private static void box(Graphics2D g, float[] b, BufferedImage t) {
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        // top (+Y): u = x, v = z
        for (int u = (int) Math.floor(x0 * 16); u < Math.ceil(x1 * 16); u++)
            for (int v = (int) Math.floor(z0 * 16); v < Math.ceil(z1 * 16); v++) {
                double ua = Math.max(x0, u / 16.0), ub = Math.min(x1, (u + 1) / 16.0);
                double va = Math.max(z0, v / 16.0), vb = Math.min(z1, (v + 1) / 16.0);
                quad(
                    g,
                    new double[][] { { ua, y1, va }, { ub, y1, va }, { ub, y1, vb }, { ua, y1, vb } },
                    shade(t.getRGB(u, v), 1.0));
            }
        // south (+Z): u = x, v = 1 - y
        for (int u = (int) Math.floor(x0 * 16); u < Math.ceil(x1 * 16); u++)
            for (int v = (int) Math.floor((1 - y1) * 16); v < Math.ceil((1 - y0) * 16); v++) {
                double ua = Math.max(x0, u / 16.0), ub = Math.min(x1, (u + 1) / 16.0);
                double ya = Math.min(y1, 1 - v / 16.0), yb = Math.max(y0, 1 - (v + 1) / 16.0);
                quad(
                    g,
                    new double[][] { { ua, ya, z1 }, { ub, ya, z1 }, { ub, yb, z1 }, { ua, yb, z1 } },
                    shade(t.getRGB(u, v), 0.8));
            }
        // east (+X): u = 1 - z, v = 1 - y
        for (int u = (int) Math.floor((1 - z1) * 16); u < Math.ceil((1 - z0) * 16); u++)
            for (int v = (int) Math.floor((1 - y1) * 16); v < Math.ceil((1 - y0) * 16); v++) {
                double za = Math.min(z1, 1 - u / 16.0), zb = Math.max(z0, 1 - (u + 1) / 16.0);
                double ya = Math.min(y1, 1 - v / 16.0), yb = Math.max(y0, 1 - (v + 1) / 16.0);
                quad(
                    g,
                    new double[][] { { x1, ya, za }, { x1, ya, zb }, { x1, yb, zb }, { x1, yb, za } },
                    shade(t.getRGB(u, v), 0.6));
            }
    }

    private static void render(String file, int[] sides, String[] states) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0x3C4A5A));
        g.fillRect(0, 0, W, H);
        List<Box> boxes = new ArrayList<>();
        float a = BlockConnector.CORE_MIN, b = BlockConnector.CORE_MAX;
        boxes.add(new Box(new float[] { a, a, a, b, b, b }, "connector_core_active"));
        for (int i = 0; i < sides.length; i++) {
            boxes.add(new Box(BlockConnector.armBox(sides[i]), "connector_arm_" + states[i]));
            boxes.add(new Box(BlockConnector.plateBox(sides[i]), "connector_plate_" + states[i]));
        }
        // painter's order: farthest (smallest x+y+z centre) first
        boxes.sort(
            (p, q) -> Float.compare(
                p.b[0] + p.b[3] + p.b[1] + p.b[4] + p.b[2] + p.b[5],
                q.b[0] + q.b[3] + q.b[1] + q.b[4] + q.b[2] + q.b[5]));
        for (Box x : boxes) box(g, x.b, tex(x.tex));
        // faint outline of the full block for scale
        g.setColor(new Color(255, 255, 255, 40));
        double[][] corners = { { 0, 0, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 }, { 0, 1, 0 }, { 0, 1, 1 },
            { 0, 0, 1 } };
        for (int i = 0; i < corners.length - 1; i++) {
            double[] p = project(corners[i][0], corners[i][1], corners[i][2]);
            double[] q = project(corners[i + 1][0], corners[i + 1][1], corners[i + 1][2]);
            g.drawLine((int) p[0], (int) p[1], (int) q[0], (int) q[1]);
        }
        new File("build/previews").mkdirs();
        ImageIO.write(img, "png", new File("build/previews/" + file + ".png"));
    }

    @Test
    void renderModels() throws Exception {
        // east = generator (input), south = machine (output), up = mixed cable (both), west = switched off
        render("model_connector", new int[] { 5, 3, 1, 4 }, new String[] { "in", "out", "both", "off" });
        render("model_connector_single", new int[] { 3 }, new String[] { "out" });
        render("model_connector_bare", new int[0], new String[0]);
    }
}
