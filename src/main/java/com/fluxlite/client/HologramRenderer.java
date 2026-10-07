package com.fluxlite.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.fluxlite.gui.holo.HoloPanel;
import com.fluxlite.gui.holo.HoloState;
import com.fluxlite.gui.ui.McCanvas;
import com.fluxlite.tile.TileControlCenter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Projects the floating display above a control center: a beam of light from the lens on its top and the panel,
 * facing the same way as the block's screen. It unfolds when a player comes close and folds away again.
 */
@SideOnly(Side.CLIENT)
public final class HologramRenderer extends TileEntitySpecialRenderer {

    private static final HoloPanel.Tr TR = (key, args) -> args.length == 0 ? StatCollector.translateToLocal(key)
        : StatCollector.translateToLocalFormatted(key, args);

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileControlCenter cc) || cc.holo == null) return;
        HoloState s = cc.holo;
        long now = Minecraft.getSystemTime();
        double cx = x + 0.5, cy = y + 1.5, cz = z + 0.5;
        boolean visible = cc.hologram && !s.stale(now) && cx * cx + cy * cy + cz * cz <= s.range * s.range;
        s.update(now, visible);
        if (s.open <= 0.001f) return;

        float yaw = switch (te.getBlockMetadata()) {
            case 2 -> 180;
            case 4 -> -90;
            case 5 -> 90;
            default -> 0;
        };
        // the camera is at the origin; seen from behind the panel shows only its glass
        double rad = Math.toRadians(yaw);
        boolean back = Math.sin(rad) * -cx + Math.cos(rad) * -cz < 0;
        // size and lift grow with the size the owner picked
        float unit = cc.holoUnit(), lift = cc.holoLift();
        float bob = (float) Math.sin(now / 900.0) * 0.03f * unit * 72;
        float pw = HoloPanel.W * unit, ph = HoloPanel.H * unit;

        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glTranslated(cx, y + 1, cz);
        GL11.glRotatef(yaw, 0, 1, 0);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        beam(s.open, lift + bob, pw * 0.5f - 0.12f);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTranslatef(-pw / 2, lift + bob + ph, 0);
        GL11.glScalef(unit, -unit, unit);
        HoloPanel.draw(McCanvas.world(func_147498_b()), s, now, back, cc.holoOpaque, TR);

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Fan of light from the lens up to the bottom edge of the panel, plus a narrow one across it for depth. */
    private static void beam(float open, float top, float halfWidth) {
        float a = Math.min(1, open * 1.5f);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        // panel plane
        t.setColorRGBA_F(0.39f, 0.82f, 1f, 0.30f * a);
        t.addVertex(0, 0.02, 0);
        t.setColorRGBA_F(0.39f, 0.82f, 1f, 0.05f * a);
        t.addVertex(-halfWidth * open, top, 0);
        t.addVertex(halfWidth * open, top, 0);
        // across it
        t.setColorRGBA_F(0.39f, 0.82f, 1f, 0.22f * a);
        t.addVertex(0, 0.02, 0);
        t.setColorRGBA_F(0.39f, 0.82f, 1f, 0.03f * a);
        t.addVertex(0, top, -0.18);
        t.addVertex(0, top, 0.18);
        t.draw();
        // glow on the lens
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA_F(0.6f, 0.9f, 1f, 0.55f * a);
        t.addVertex(0, 0.015, 0);
        t.setColorRGBA_F(0.39f, 0.82f, 1f, 0);
        for (int i = 0; i <= 16; i++) {
            double ang = Math.PI * 2 * i / 16;
            t.addVertex(Math.cos(ang) * 0.3, 0.015, Math.sin(ang) * 0.3);
        }
        t.draw();
    }
}
