package com.lexovian.currentsoftrade.world.market;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Dynamic Trade Winds — deterministic daily prevailing maritime wind direction.
 * Ships sailing with the Trade Wind at their back receive a natural speed boost.
 */
public final class TradeWinds {

    private static final String[] CARDINALS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private TradeWinds() {}

    /** Returns 0..7 index for today's prevailing Trade Wind direction (changes each Minecraft day). */
    public static int getWindDirectionIndex(Level level) {
        if (level == null) return 0;
        long day = Math.floorDiv(level.getDayTime(), 24000L);
        // Smooth deterministic progression with gentle pseudo-random variation
        long hash = (day * 1103515245L + 12345L) >>> 16;
        return (int) Math.floorMod(hash, 8L);
    }

    /** Returns the Minecraft yaw angle (degrees) in which the Trade Wind is blowing. */
    public static float getWindYaw(Level level) {
        return getWindDirectionIndex(level) * 45.0F;
    }

    /** Returns human-readable compass direction (S, SW, W, NW, N, NE, E, SE). */
    public static String getWindCardinal(Level level) {
        return CARDINALS[getWindDirectionIndex(level)];
    }

    /**
     * Returns cosine alignment [-1.0 .. +1.0] between a ship's heading and today's Trade Wind.
     * Values > 0.60 mean the ship has a strong favorable tailwind.
     */
    public static double getWindAlignment(Level level, float shipYaw) {
        float diffRad = Mth.wrapDegrees(shipYaw - getWindYaw(level)) * Mth.DEG_TO_RAD;
        return Math.cos(diffRad);
    }

    public static boolean hasFavorableTradeWind(Level level, float shipYaw) {
        return getWindAlignment(level, shipYaw) > 0.60D;
    }
}
