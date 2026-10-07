package com.fluxlite.gui.ui;

/**
 * Apple dark-mode palette (iOS / macOS system colors) and the few sizes the screens share. Input (generation into the
 * network) is green, output (consumption) is orange, a face doing both is blue.
 */
public final class Theme {

    // backgrounds
    public static final int SCRIM = 0x99000000;
    public static final int WINDOW = 0xF51C1C1E;
    public static final int WINDOW_TOP = 0xF5242426;
    public static final int CARD = 0xFF2C2C2E;
    public static final int CARD_HOVER = 0xFF3A3A3C;
    public static final int FILL = 0xFF3A3A3C;
    public static final int FILL_SELECTED = 0xFF636366;
    public static final int STROKE = 0x1FFFFFFF;
    public static final int SEPARATOR = 0x33FFFFFF;
    public static final int MENU = 0xFF323234;

    // labels
    public static final int LABEL = 0xFFFFFFFF;
    public static final int LABEL2 = 0x99EBEBF5;
    public static final int LABEL3 = 0x5CEBEBF5;
    public static final int LABEL4 = 0x2EEBEBF5;

    // system colors
    public static final int BLUE = 0xFF0A84FF;
    public static final int GREEN = 0xFF30D158;
    public static final int ORANGE = 0xFFFF9F0A;
    public static final int RED = 0xFFFF453A;
    public static final int YELLOW = 0xFFFFD60A;
    public static final int TEAL = 0xFF64D2FF;
    public static final int PURPLE = 0xFFBF5AF2;
    public static final int GRAY = 0xFF8E8E93;

    public static final int INPUT = GREEN;
    public static final int OUTPUT = ORANGE;
    public static final int BOTH = TEAL;

    public static final float RADIUS_WINDOW = 10, RADIUS_CARD = 7, RADIUS_CONTROL = 5;

    private Theme() {}

    public static int alpha(int argb, float a) {
        int base = argb >>> 24;
        return ((int) (base * a) & 0xFF) << 24 | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
            | (int) (ag + (bg - ag) * t) << 8
            | (int) (ab + (bb - ab) * t);
    }
}
