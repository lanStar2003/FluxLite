package com.fluxlite;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

import com.fluxlite.block.BlockConnector;
import com.fluxlite.client.ConnectorRenderer;
import com.fluxlite.client.HologramRenderer;
import com.fluxlite.gui.ConnectorScreen;
import com.fluxlite.gui.ControlCenterScreen;
import com.fluxlite.gui.GuiHost;
import com.fluxlite.gui.Highlighter;
import com.fluxlite.tile.TileControlCenter;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        BlockConnector.renderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new ConnectorRenderer(BlockConnector.renderId));
        ClientRegistry.bindTileEntitySpecialRenderer(TileControlCenter.class, new HologramRenderer());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(Highlighter.INSTANCE);
    }

    @Override
    public void openConnectorGui(EntityPlayer player, int x, int y, int z) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHost(new ConnectorScreen(player.worldObj.provider.dimensionId, x, y, z)));
    }

    @Override
    public void openControlCenterGui(EntityPlayer player, int x, int y, int z) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHost(new ControlCenterScreen(player.worldObj.provider.dimensionId, x, y, z)));
    }

    @Override
    public void openTerminalGui(EntityPlayer player) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHost(ControlCenterScreen.handheld()));
    }
}
