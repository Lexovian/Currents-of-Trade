package com.lexovian.currentsoftrade;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for Currents of Trade.
 * Defines harbor requirements, ship speeds, worldgen chances, and sea provisions.
 */
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- Harbor & Voyage ---
    public static final ModConfigSpec.IntValue MAX_VOYAGE_DISTANCE;
    public static final ModConfigSpec.IntValue VOYAGE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue MIN_HARBOR_WATER_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_HARBOR_TRADES;

    // --- Ships & Navigation ---
    public static final ModConfigSpec.DoubleValue FAST_TRAVEL_BOAT_SPEED;
    public static final ModConfigSpec.DoubleValue CARGO_BOAT_SPEED;

    // --- World Spawning & Generation ---
    public static final ModConfigSpec.IntValue WANDERING_SAILOR_CHECK_INTERVAL;
    public static final ModConfigSpec.DoubleValue WANDERING_SAILOR_SPAWN_CHANCE;
    public static final ModConfigSpec.IntValue VILLAGE_DOCK_SPAWN_WEIGHT;

    // --- Provisions & Consumables ---
    public static final ModConfigSpec.IntValue SPICE_SACK_DURATION_TICKS;
    public static final ModConfigSpec.IntValue AMBER_VIAL_REGEN_DURATION_TICKS;
    public static final ModConfigSpec.IntValue AMBER_VIAL_NAUSEA_DURATION_TICKS;

    static {
        BUILDER.comment("Harbor and voyage mechanics configuration").push("harbor");

        MAX_VOYAGE_DISTANCE = BUILDER
                .comment("Maximum distance in blocks allowed for single-stage sea voyages (default: 6400 blocks)")
                .defineInRange("maxVoyageDistance", 6400, 100, 100000);

        VOYAGE_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown period in seconds between harbor departures (default: 60 seconds)")
                .defineInRange("voyageCooldownSeconds", 60, 0, 3600);

        MIN_HARBOR_WATER_BLOCKS = BUILDER
                .comment("Minimum contiguous open-sky water blocks required for a valid harbor dock (default: 40 blocks)")
                .defineInRange("minHarborWaterBlocks", 40, 5, 500);

        MAX_HARBOR_TRADES = BUILDER
                .comment("Maximum number of trade offers an Anchor Point harbor can store (default: 12)")
                .defineInRange("maxHarborTrades", 12, 1, 64);

        BUILDER.pop();

        BUILDER.comment("Vessel cruising speed settings").push("vessels");

        FAST_TRAVEL_BOAT_SPEED = BUILDER
                .comment("Cruising speed of player fast-travel sloops in blocks per tick (default: 1.8, approx 36 blocks/sec)")
                .defineInRange("fastTravelBoatSpeed", 1.8, 0.1, 5.0);

        CARGO_BOAT_SPEED = BUILDER
                .comment("Cruising speed of autonomous cargo and trade ships in blocks per tick (default: 0.9, approx 18 blocks/sec)")
                .defineInRange("cargoBoatSpeed", 0.9, 0.1, 5.0);

        BUILDER.pop();

        BUILDER.comment("Spawning and village world generation settings").push("worldgen");

        WANDERING_SAILOR_CHECK_INTERVAL = BUILDER
                .comment("Interval in game ticks between harbor arrival checks for the Wandering Sailor (default: 12000 ticks / 10 minutes)")
                .defineInRange("wanderingSailorCheckInterval", 12000, 600, 72000);

        WANDERING_SAILOR_SPAWN_CHANCE = BUILDER
                .comment("Probability (0.0 to 1.0) of a Wandering Sailor docking at a valid harbor per check (default: 0.35)")
                .defineInRange("wanderingSailorSpawnChance", 0.35, 0.0, 1.0);

        VILLAGE_DOCK_SPAWN_WEIGHT = BUILDER
                .comment("Structure pool weight for coastal village harbor dock generation (default: 5, yielding ~70-80% spawn chance in coastal villages)")
                .defineInRange("villageDockSpawnWeight", 5, 0, 1000);

        BUILDER.pop();

        BUILDER.comment("Maritime consumable effects and durations").push("consumables");

        SPICE_SACK_DURATION_TICKS = BUILDER
                .comment("Duration in game ticks for Speed II and Haste I granted by the Spice Sack (default: 240 ticks / 12 seconds)")
                .defineInRange("spiceSackDurationTicks", 240, 20, 6000);

        AMBER_VIAL_REGEN_DURATION_TICKS = BUILDER
                .comment("Duration in game ticks for Regeneration II granted by the Amber Vial (default: 300 ticks / 15 seconds)")
                .defineInRange("amberVialRegenDurationTicks", 300, 20, 6000);

        AMBER_VIAL_NAUSEA_DURATION_TICKS = BUILDER
                .comment("Duration in game ticks for seasickness Nausea caused by the Amber Vial (default: 160 ticks / 8 seconds)")
                .defineInRange("amberVialNauseaDurationTicks", 160, 0, 6000);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
