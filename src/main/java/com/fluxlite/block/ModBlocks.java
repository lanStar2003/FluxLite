package com.fluxlite.block;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.fluxlite.tile.TileConnector;
import com.fluxlite.tile.TileControlCenter;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    public static BlockConnector connector;
    public static BlockControlCenter controlCenter;

    public static final CreativeTabs TAB = new CreativeTabs("fluxlite") {

        @Override
        public Item getTabIconItem() {
            return Item.getItemFromBlock(connector);
        }
    };

    private ModBlocks() {}

    public static void register() {
        connector = new BlockConnector();
        controlCenter = new BlockControlCenter();
        GameRegistry.registerBlock(connector, ItemBlockFlux.class, "connector");
        GameRegistry.registerBlock(controlCenter, ItemBlockFlux.class, "control_center");
        GameRegistry.registerTileEntity(TileConnector.class, "fluxlite.connector");
        GameRegistry.registerTileEntity(TileControlCenter.class, "fluxlite.control_center");
    }
}
