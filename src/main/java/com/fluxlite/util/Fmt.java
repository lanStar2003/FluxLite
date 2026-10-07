package com.fluxlite.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Number formatting shared by server messages and the GUIs. */
public final class Fmt {

    /** Prefix marking an argument that the client should translate. */
    public static final String LANG = "@L:";

    private static final String[] SUFFIX = { "", "K", "M", "G", "T", "P", "E", "Z", "Y" };
    private static final String[] TIERS = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV",
        "UIV", "UMV", "UXV", "MAX" };

    private Fmt() {}

    public static String si(long v) {
        return si(BigInteger.valueOf(v));
    }

    public static String si(BigInteger v) {
        boolean neg = v.signum() < 0;
        BigInteger a = v.abs();
        if (a.compareTo(BigInteger.valueOf(10_000)) < 0) return (neg ? "-" : "") + a;
        BigDecimal d = new BigDecimal(a);
        int i = 0;
        BigDecimal thousand = BigDecimal.valueOf(1000);
        while (d.compareTo(thousand) >= 0 && i < SUFFIX.length - 1) {
            d = d.divide(thousand, 3, RoundingMode.DOWN);
            i++;
        }
        int scale = d.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0 : d.compareTo(BigDecimal.TEN) >= 0 ? 1 : 2;
        return (neg ? "-" : "") + d.setScale(scale, RoundingMode.DOWN)
            .toPlainString() + SUFFIX[i];
    }

    public static String eu(long v) {
        return si(v) + " EU";
    }

    public static String eu(BigInteger v) {
        return si(v) + " EU";
    }

    public static String eut(long v) {
        return si(v) + " EU/t";
    }

    public static String duration(long seconds) {
        if (seconds < 0) return "∞";
        long d = seconds / 86400, h = seconds / 3600 % 24, m = seconds / 60 % 60, s = seconds % 60;
        if (d > 999) return ">999d";
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String side(int side) {
        return LANG + "fluxlite.side." + side;
    }

    public static String tier(long voltage) {
        if (voltage <= 0) return "-";
        int t = 0;
        long v = 8;
        while (v < voltage && t < TIERS.length - 1) {
            v <<= 2;
            t++;
        }
        return TIERS[t];
    }

    public static String tierName(int tier) {
        return tier >= 0 && tier < TIERS.length ? TIERS[tier] : "?";
    }

    public static int tierIndex(long voltage) {
        if (voltage <= 0) return -1;
        int t = 0;
        long v = 8;
        while (v < voltage && t < TIERS.length - 1) {
            v <<= 2;
            t++;
        }
        return t;
    }

    public static String percent(long permille) {
        return permille / 10 + "." + Math.abs(permille % 10) + "%";
    }
}
