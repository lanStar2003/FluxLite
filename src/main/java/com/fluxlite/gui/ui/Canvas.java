package com.fluxlite.gui.ui;

/**
 * Drawing surface in GUI units. The game draws through OpenGL ({@code McCanvas}); tests render the same screens with
 * Java2D to PNG previews.
 */
public interface Canvas {

    void fill(float x, float y, float w, float h, int argb);

    void gradient(float x, float y, float w, float h, int top, int bottom);

    void round(float x, float y, float w, float h, float r, int argb);

    void roundGradient(float x, float y, float w, float h, float r, int top, int bottom);

    void roundStroke(float x, float y, float w, float h, float r, float thickness, int argb);

    void circle(float cx, float cy, float r, int argb);

    /** Polyline through the points. */
    void line(float[] xs, float[] ys, float width, int argb);

    /** Filled area between the polyline and {@code baseY}, fading from {@code top} to {@code bottom}. */
    void area(float[] xs, float[] ys, float baseY, int top, int bottom);

    /** Draws text with Minecraft's font, scaled; returns the width in GUI units. */
    float text(String s, float x, float y, int argb, float scale);

    float width(String s, float scale);

    void clip(float x, float y, float w, float h);

    void unclip();

    /** Multiplies the alpha of everything drawn afterwards (fades). 1 = as given. */
    void setOpacity(float opacity);
}
