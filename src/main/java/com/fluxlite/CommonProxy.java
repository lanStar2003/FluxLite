package com.fluxlite;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import com.fluxlite.backend.SteamNetwork;
import com.fluxlite.block.ModBlocks;
import com.fluxlite.block.WrenchActions;
import com.fluxlite.chunk.ChunkLoadManager;
import com.fluxlite.command.CommandFluxLite;
import com.fluxlite.core.ServerEvents;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.item.ModItems;
import com.fluxlite.net.Net;
import com.fluxlite.recipe.Recipes;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
        ModBlocks.register();
        ModItems.register();
        Net.init();
    }

    public void init(FMLInitializationEvent event) {
        ServerEvents events = new ServerEvents();
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(new WrenchActions());
        ForgeChunkManager.setForcedChunkLoadingCallback(FluxLite.instance, ChunkLoadManager.INSTANCE);
        if (Loader.isModLoaded("Waila")) {
            FMLInterModComms.sendMessage("Waila", "register", "com.fluxlite.compat.WailaCompat.register");
        }
    }

    public void postInit(FMLPostInitializationEvent event) {
        if (Config.enableDefaultRecipes) Recipes.register();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandFluxLite());
    }

    public void serverStarted(FMLServerStartedEvent event) {
        ChunkLoadManager.INSTANCE.onServerStarted();
    }

    /** Statistics change every tick but are only marked dirty periodically; make sure the final save has them. */
    public void serverStopping(FMLServerStoppingEvent event) {
        Registry reg = Registry.get();
        if (reg != null) reg.markDirty();
        SteamNetwork steam = SteamNetwork.get();
        if (steam != null) steam.markDirty();
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        ServerEvents.reset();
        ChunkLoadManager.INSTANCE.reset();
        Registry.reset();
        SteamNetwork.reset();
    }

    /** Opens a client GUI; no-op on a dedicated server. */
    public void openConnectorGui(EntityPlayer player, int x, int y, int z) {}

    public void openControlCenterGui(EntityPlayer player, int x, int y, int z) {}

    public void openTerminalGui(EntityPlayer player) {}
}
