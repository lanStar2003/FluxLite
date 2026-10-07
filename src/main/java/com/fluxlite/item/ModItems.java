package com.fluxlite.item;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModItems {

    public static ItemFluxTerminal terminal;

    private ModItems() {}

    public static void register() {
        terminal = new ItemFluxTerminal();
        GameRegistry.registerItem(terminal, "terminal");
    }
}
