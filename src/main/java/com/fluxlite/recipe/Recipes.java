package com.fluxlite.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.fluxlite.Config;
import com.fluxlite.FluxLite;
import com.fluxlite.block.ModBlocks;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/**
 * Default assembler recipes. Tiers are configurable; set {@code enableDefaultRecipes=false} to replace them with
 * CraftTweaker/MineTweaker scripts.
 */
public final class Recipes {

    private static final String[] TIER = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV",
        "UMV", "UXV" };
    private static final Materials[] CIRCUIT = { Materials.ULV, Materials.LV, Materials.MV, Materials.HV, Materials.EV,
        Materials.IV, Materials.LuV, Materials.ZPM, Materials.UV, Materials.UHV, Materials.UEV, Materials.UIV,
        Materials.UMV, Materials.UXV };

    private Recipes() {}

    private static ItemStack item(String name, int amount) {
        try {
            ItemList l = ItemList.valueOf(name);
            return l.hasBeenSet() ? l.get(amount) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Tier-indexed GT component, falling back to lower tiers if a tier has no such item. */
    private static ItemStack component(String prefix, int tier, int amount) {
        for (int t = tier; t >= 1; t--) {
            ItemStack s = item(prefix + TIER[t], amount);
            if (s != null) return s;
        }
        return null;
    }

    public static void register() {
        try {
            connector();
            controlCenter();
        } catch (Throwable t) {
            FluxLite.LOG.error("Failed to register FluxLite recipes", t);
        }
    }

    private static void connector() {
        int t = Math.max(1, Math.min(13, Config.connectorRecipeTier));
        List<ItemStack> in = new ArrayList<>();
        in.add(component("Hull_", Math.min(t, 9), 1));
        if (Config.connectorNeedsWirelessHatch) in.add(item("Wireless_Hatch_Energy_ULV", 1));
        in.add(component("Sensor_", t, 2));
        in.add(component("Emitter_", t, 2));
        in.add(GTOreDictUnificator.get(OrePrefixes.circuit, CIRCUIT[t], 2));
        in.add(GTOreDictUnificator.get(OrePrefixes.plate, Materials.Neutronium, 4));
        add(in, new ItemStack(ModBlocks.connector), 20 * SECONDS, GTValues.VP[t]);
    }

    private static void controlCenter() {
        int t = Math.max(1, Math.min(13, Config.controlCenterRecipeTier));
        List<ItemStack> in = new ArrayList<>();
        in.add(component("Hull_", Math.min(t, 9), 1));
        in.add(item("Cover_Screen", 1));
        in.add(item("Cover_EnergyDetector", 1));
        in.add(component("Sensor_", t, 1));
        in.add(GTOreDictUnificator.get(OrePrefixes.circuit, CIRCUIT[t], 2));
        add(in, new ItemStack(ModBlocks.controlCenter), 10 * SECONDS, GTValues.VP[t]);
    }

    private static void add(List<ItemStack> in, ItemStack out, int duration, long eut) {
        in.removeIf(s -> s == null);
        if (in.size() < 2) {
            FluxLite.LOG.warn("Skipping recipe for {}: ingredients missing", out.getDisplayName());
            return;
        }
        GTValues.RA.stdBuilder()
            .itemInputs(in.toArray(new ItemStack[0]))
            .itemOutputs(out)
            .fluidInputs(Materials.SolderingAlloy.getMolten(576))
            .duration(duration)
            .eut(eut)
            .addTo(assemblerRecipes);
    }
}
