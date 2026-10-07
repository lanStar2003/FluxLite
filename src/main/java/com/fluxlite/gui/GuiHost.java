package com.fluxlite.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.fluxlite.gui.ui.Host;
import com.fluxlite.gui.ui.McCanvas;
import com.fluxlite.gui.ui.UiScreen;
import com.fluxlite.net.Net;
import com.fluxlite.util.Fmt;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Plain GuiScreen that hosts a {@link UiScreen}: forwards input, provides networking and translations. */
@SideOnly(Side.CLIENT)
public class GuiHost extends GuiScreen implements Host {

    private final UiScreen screen;

    public GuiHost(UiScreen screen) {
        this.screen = screen;
        screen.attach(this);
    }

    public UiScreen screen() {
        return screen;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        screen.onClose();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        screen.tick();
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        McCanvas c = new McCanvas(fontRendererObj, sr.getScaleFactor(), mc.displayHeight);
        c.begin();
        try {
            screen.frame(c, mx, my, width, height, System.currentTimeMillis());
        } finally {
            c.end();
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        screen.click(x, y, button);
    }

    @Override
    protected void keyTyped(char ch, int code) {
        if (screen.key(ch, code)) return;
        if (code == Keyboard.KEY_ESCAPE || code == mc.gameSettings.keyBindInventory.getKeyCode()) close();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) screen.wheel(wheel > 0 ? 1 : -1);
    }

    @Override
    public void send(int kind, NBTTagCompound data) {
        Net.toServer(kind, data);
    }

    @Override
    public void close() {
        mc.displayGuiScreen(null);
        mc.setIngameFocus();
    }

    @Override
    public String tr(String key, Object... args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String s && s.startsWith(Fmt.LANG))
                args[i] = StatCollector.translateToLocal(s.substring(Fmt.LANG.length()));
        }
        return args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }

    @Override
    public void highlight(int dim, int x, int y, int z) {
        Highlighter.INSTANCE.show(dim, x, y, z, 30);
    }
}
