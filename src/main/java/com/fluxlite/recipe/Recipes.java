package com.fluxlite.recipe;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.fluxlite.FluxLite;
import com.fluxlite.block.ModBlocks;
import com.fluxlite.item.ModItems;

import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;

/**
 * Crafting table recipes from the steam age: bronze plates, a bronze hull, redstone, glass and ender pearls (the
 * "wireless" part). Lower case letters are GT tools (w = wrench, d = screwdriver). Set
 * {@code enableDefaultRecipes=false} to replace them with CraftTweaker / MineTweaker scripts.
 */
public final class Recipes {

    private static final String PLATE = "plateBronze", REDSTONE = "dustRedstone", GLASS = "paneGlass";

    private Recipes() {}

    public static void register() {
        try {
            ItemStack hull = ItemList.Hull_Bronze.hasBeenSet() ? ItemList.Hull_Bronze.get(1) : null;
            Object casing = hull != null ? hull : "blockBronze";
            ItemStack pearl = new ItemStack(Items.ender_pearl);
            long bits = GTModHandler.RecipeBits.NOT_REMOVABLE;

            GTModHandler.addCraftingRecipe(
                new ItemStack(ModBlocks.connector, 2),
                bits,
                new Object[] { "PRP", "EHE", "PwP", 'P', PLATE, 'R', REDSTONE, 'E', pearl, 'H', casing });
            GTModHandler.addCraftingRecipe(
                new ItemStack(ModBlocks.controlCenter),
                bits,
                new Object[] { "GGG", "RHR", "PdP", 'G', GLASS, 'R', REDSTONE, 'H', casing, 'P', PLATE });
            GTModHandler.addCraftingRecipe(
                new ItemStack(ModItems.terminal),
                bits,
                new Object[] { "PGP", "RER", "PdP", 'P', PLATE, 'G', GLASS, 'R', REDSTONE, 'E', pearl });
        } catch (Throwable t) {
            FluxLite.LOG.error("Failed to register FluxLite recipes", t);
        }
    }
}
