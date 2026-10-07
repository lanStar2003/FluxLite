package com.fluxlite.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.fluxlite.block.BlockConnector;
import com.fluxlite.tile.TileConnector;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Core cube plus an arm and flange towards every connected neighbour. */
@SideOnly(Side.CLIENT)
public final class ConnectorRenderer implements ISimpleBlockRenderingHandler {

    private final int id;

    public ConnectorRenderer(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        TileEntity te = world.getTileEntity(x, y, z);
        TileConnector c = te instanceof TileConnector t ? t : null;
        boolean all = renderer.renderAllFaces;
        renderer.renderAllFaces = true;

        BlockConnector.renderPart = BlockConnector.PART_CORE;
        BlockConnector.renderActive = c != null && c.activeMask != 0;
        float a = BlockConnector.CORE_MIN, b = BlockConnector.CORE_MAX;
        renderer.setRenderBounds(a, a, a, b, b, b);
        renderer.renderStandardBlock(block, x, y, z);

        if (c != null) {
            for (int s = 0; s < 6; s++) {
                int vis = c.visual[s];
                if (vis == TileConnector.VIS_NONE) continue;
                BlockConnector.renderVis = vis;
                BlockConnector.renderPart = BlockConnector.PART_ARM;
                float[] arm = BlockConnector.armBox(s);
                renderer.setRenderBounds(arm[0], arm[1], arm[2], arm[3], arm[4], arm[5]);
                renderer.renderStandardBlock(block, x, y, z);
                BlockConnector.renderPart = BlockConnector.PART_PLATE;
                float[] plate = BlockConnector.plateBox(s);
                renderer.setRenderBounds(plate[0], plate[1], plate[2], plate[3], plate[4], plate[5]);
                renderer.renderStandardBlock(block, x, y, z);
            }
        }
        BlockConnector.renderPart = BlockConnector.PART_CORE;
        renderer.renderAllFaces = all;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks renderer) {
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        BlockConnector.renderActive = true;
        BlockConnector.renderPart = BlockConnector.PART_CORE;
        float a = BlockConnector.CORE_MIN, b = BlockConnector.CORE_MAX;
        box(block, renderer, a, a, a, b, b, b);
        // show two connections so the item reads as "connector"
        BlockConnector.renderVis = TileConnector.VIS_IN;
        part(block, renderer, 4);
        BlockConnector.renderVis = TileConnector.VIS_OUT;
        part(block, renderer, 5);
        BlockConnector.renderPart = BlockConnector.PART_CORE;
        BlockConnector.renderActive = false;
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    private static void part(Block block, RenderBlocks r, int side) {
        BlockConnector.renderPart = BlockConnector.PART_ARM;
        float[] arm = BlockConnector.armBox(side);
        box(block, r, arm[0], arm[1], arm[2], arm[3], arm[4], arm[5]);
        BlockConnector.renderPart = BlockConnector.PART_PLATE;
        float[] p = BlockConnector.plateBox(side);
        box(block, r, p[0], p[1], p[2], p[3], p[4], p[5]);
    }

    private static void box(Block block, RenderBlocks r, float x0, float y0, float z0, float x1, float y1, float z1) {
        r.setRenderBounds(x0, y0, z0, x1, y1, z1);
        Tessellator t = Tessellator.instance;
        IIcon icon = block.getIcon(0, 0);
        t.startDrawingQuads();
        t.setNormal(0, -1, 0);
        r.renderFaceYNeg(block, 0, 0, 0, icon);
        t.setNormal(0, 1, 0);
        r.renderFaceYPos(block, 0, 0, 0, icon);
        t.setNormal(0, 0, -1);
        r.renderFaceZNeg(block, 0, 0, 0, icon);
        t.setNormal(0, 0, 1);
        r.renderFaceZPos(block, 0, 0, 0, icon);
        t.setNormal(-1, 0, 0);
        r.renderFaceXNeg(block, 0, 0, 0, icon);
        t.setNormal(1, 0, 0);
        r.renderFaceXPos(block, 0, 0, 0, icon);
        t.draw();
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }
}
