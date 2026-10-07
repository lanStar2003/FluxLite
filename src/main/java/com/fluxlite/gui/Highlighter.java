package com.fluxlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Draws a see-through box around a device picked in the control center ("locate"). */
@SideOnly(Side.CLIENT)
public final class Highlighter {

    public static final Highlighter INSTANCE = new Highlighter();

    private int dim, x, y, z;
    private long until;

    private Highlighter() {}

    public void show(int dim, int x, int y, int z, int seconds) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.until = System.currentTimeMillis() + seconds * 1000L;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (System.currentTimeMillis() > until) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.theWorld.provider.dimensionId != dim) return;
        EntityLivingBase view = mc.renderViewEntity;
        double px = view.lastTickPosX + (view.posX - view.lastTickPosX) * e.partialTicks;
        double py = view.lastTickPosY + (view.posY - view.lastTickPosY) * e.partialTicks;
        double pz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * e.partialTicks;
        float pulse = 0.6F + 0.4F * (float) Math.sin(System.currentTimeMillis() / 150.0);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(3.0F);
        GL11.glColor4f(1.0F, 0.85F, 0.1F, pulse);
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - 0.02, y - 0.02, z - 0.02, x + 1.02, y + 1.02, z + 1.02)
            .getOffsetBoundingBox(-px, -py, -pz);
        RenderGlobal.drawOutlinedBoundingBox(box, -1);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
