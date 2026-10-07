package com.fluxlite;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * All tunables live here. Values are read once in preInit; the file is regenerated with defaults when missing.
 */
public final class Config {

    private static final String CAT_GENERAL = "general";
    private static final String CAT_CHUNK = "chunkloading";
    private static final String CAT_SCAN = "cable_scan";
    private static final String CAT_STATS = "statistics";
    private static final String CAT_ALERTS = "alert_defaults";
    private static final String CAT_RECIPES = "recipes";
    private static final String CAT_COMPAT = "compat";
    private static final String CAT_DISPLAY = "display";

    // general
    public static int settlementPeriod = 1;
    public static double wirelessLossPercent = 0.0;
    public static int maxConnectorsPerTeam = 64;
    public static int bufferPeriods = 2;

    // chunk loading
    public static boolean chunkLoadingEnabled = true;
    public static int loadRadius = 1;
    public static boolean keepLoadedWhenOffline = true;

    // cable scan
    public static int cableScanMaxNodes = 2048;
    public static int cableRescanInterval = 100;
    public static int maxSampledMachinesPerConnector = 64;

    // statistics
    public static int guiSyncInterval = 10;
    public static int registrySaveIntervalMinutes = 5;

    // alert defaults (per team, changeable in the control center)
    public static long alertLowBalance = 0L;
    public static int alertEtaMinutes = 10;
    public static int alertUnderSupplyPercent = 90;
    public static int alertIdleMinutes = 0;
    public static int alertLoadPercent = 90;
    public static int alertChatCooldownSeconds = 300;

    // recipes
    public static boolean enableDefaultRecipes = true;
    public static int connectorRecipeTier = 8;
    public static int controlCenterRecipeTier = 6;
    public static boolean connectorNeedsWirelessHatch = true;

    // display
    public static int hologramRange = 12;

    // compat
    public static long rfNominalVoltage = 8192L;
    public static int ic2MaxPacketsPerTick = 16;

    private Config() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);
        c.load();

        settlementPeriod = c.getInt(
            "settlementPeriod",
            CAT_GENERAL,
            settlementPeriod,
            1,
            1200,
            "Ticks between two settlements with the GT wireless network. Each team touches its wireless balance once per period.");
        wirelessLossPercent = c.get(
            CAT_GENERAL,
            "wirelessLossPercent",
            wirelessLossPercent,
            "Percent of the energy uploaded by output ports that is lost on the way into the wireless network (0-100).")
            .getDouble();
        wirelessLossPercent = Math.max(0, Math.min(100, wirelessLossPercent));
        maxConnectorsPerTeam = c.getInt(
            "maxConnectorsPerTeam",
            CAT_GENERAL,
            maxConnectorsPerTeam,
            0,
            100000,
            "Maximum number of flux connectors per GT team. 0 = unlimited.");
        bufferPeriods = c.getInt(
            "bufferPeriods",
            CAT_GENERAL,
            bufferPeriods,
            1,
            20,
            "Port buffer size, measured in settlement periods of full-speed transfer.");

        chunkLoadingEnabled = c.getBoolean(
            "enabled",
            CAT_CHUNK,
            chunkLoadingEnabled,
            "Connectors with an active port keep chunks loaded.");
        loadRadius = c.getInt(
            "radius",
            CAT_CHUNK,
            loadRadius,
            0,
            8,
            "Chunk loading radius around an active connector (0 = only its own chunk, 1 = 3x3, 2 = 5x5).");
        keepLoadedWhenOffline = c.getBoolean(
            "keepLoadedWhenOwnerOffline",
            CAT_CHUNK,
            keepLoadedWhenOffline,
            "If false, chunks are only kept loaded while at least one member of the owning team is online.");

        cableScanMaxNodes = c.getInt(
            "maxNodes",
            CAT_SCAN,
            cableScanMaxNodes,
            16,
            65536,
            "Maximum number of cable blocks visited when scanning a GT cable network.");
        cableRescanInterval = c.getInt(
            "rescanInterval",
            CAT_SCAN,
            cableRescanInterval,
            20,
            12000,
            "Ticks between two cable network rescans.");
        maxSampledMachinesPerConnector = c.getInt(
            "maxSampledMachines",
            CAT_SCAN,
            maxSampledMachinesPerConnector,
            0,
            4096,
            "Maximum number of cable-attached machines tracked (sampled statistics) per connector.");

        guiSyncInterval = c
            .getInt("guiSyncInterval", CAT_STATS, guiSyncInterval, 5, 100, "Ticks between two GUI refreshes.");
        registrySaveIntervalMinutes = c.getInt(
            "saveIntervalMinutes",
            CAT_STATS,
            registrySaveIntervalMinutes,
            1,
            120,
            "Minutes between two forced saves of the statistics registry.");

        alertLowBalance = Long.parseLong(
            c.getString(
                "lowBalance",
                CAT_ALERTS,
                Long.toString(alertLowBalance),
                "Alert when the wireless balance is below this many EU. 0 = disabled."));
        alertEtaMinutes = c.getInt(
            "etaMinutes",
            CAT_ALERTS,
            alertEtaMinutes,
            0,
            100000,
            "Alert when the balance runs out sooner. 0 = off.");
        alertUnderSupplyPercent = c.getInt(
            "underSupplyPercent",
            CAT_ALERTS,
            alertUnderSupplyPercent,
            0,
            100,
            "Alert when a device receives less than this share of its demand over one minute. 0 = off.");
        alertIdleMinutes = c.getInt(
            "idleMinutes",
            CAT_ALERTS,
            alertIdleMinutes,
            0,
            100000,
            "Alert when an active port moved no energy for this long. 0 = off.");
        alertLoadPercent = c.getInt(
            "loadPercent",
            CAT_ALERTS,
            alertLoadPercent,
            0,
            1000,
            "Alert when a port runs above this load. 0 = off.");
        alertChatCooldownSeconds = c.getInt(
            "chatCooldownSeconds",
            CAT_ALERTS,
            alertChatCooldownSeconds,
            10,
            86400,
            "Minimum seconds between two chat notifications of the same alert.");

        enableDefaultRecipes = c
            .getBoolean("enableDefaultRecipes", CAT_RECIPES, enableDefaultRecipes, "Register the built-in GT recipes.");
        connectorRecipeTier = c.getInt(
            "connectorTier",
            CAT_RECIPES,
            connectorRecipeTier,
            1,
            13,
            "Voltage tier of the connector recipe (1=LV ... 8=UV, 9=UHV).");
        controlCenterRecipeTier = c.getInt(
            "controlCenterTier",
            CAT_RECIPES,
            controlCenterRecipeTier,
            1,
            13,
            "Voltage tier of the control center recipe.");
        connectorNeedsWirelessHatch = c.getBoolean(
            "connectorNeedsWirelessHatch",
            CAT_RECIPES,
            connectorNeedsWirelessHatch,
            "The connector recipe consumes a ULV wireless energy hatch, tying it to the wireless stage of the pack.");

        rfNominalVoltage = c.getInt(
            "rfNominalVoltage",
            CAT_COMPAT,
            (int) rfNominalVoltage,
            1,
            Integer.MAX_VALUE,
            "EU/t one RF port moves at most per tick (before conversion).");
        ic2MaxPacketsPerTick = c.getInt(
            "ic2MaxPacketsPerTick",
            CAT_COMPAT,
            ic2MaxPacketsPerTick,
            1,
            256,
            "Max IC2 packets injected per tick.");

        hologramRange = c.getInt(
            "hologramRange",
            CAT_DISPLAY,
            hologramRange,
            0,
            48,
            "The floating display above a control center opens for players closer than this many blocks. 0 = no floating displays.");

        if (c.hasChanged()) c.save();
    }
}
