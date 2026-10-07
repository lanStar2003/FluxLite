package com.fluxlite.gui.ui;

import net.minecraft.nbt.NBTTagCompound;

/** What a screen needs from the game; the preview test provides a fake one. */
public interface Host {

    void send(int kind, NBTTagCompound data);

    void close();

    String tr(String key, Object... args);

    void highlight(int dim, int x, int y, int z);
}
