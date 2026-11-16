package com.lexovian.currentsoftrade.world.harbor;

import net.minecraft.network.chat.Component;

/**
 * Harbor Specializations unlocked at Harbor Level 3+.
 *
 * <ul>
 *   <li><b>FREE_PORT</b> — +25% Doubloon rewards on Urgent Needs, Guild Charters &amp; Trade Missions, and -25% shipping fees.</li>
 *   <li><b>ROYAL_DRYDOCK</b> — +20% ship speed &amp; reinforced hulls on outbound ships, 50% faster harbor cooldowns, and passive hull repair for nearby ships.</li>
 *   <li><b>GUILD_DEPOT</b> — 2 Daily Urgent Needs instead of 1, expanded 8x4x8 harbor warehouse range, and +9 bonus cargo slots.</li>
 * </ul>
 */
public enum HarborSpecialization {
    NONE(0, "none", "⚓", 0xAAAAAA),
    FREE_PORT(1, "free_port", "⚖", 0xFFD700),
    ROYAL_DRYDOCK(2, "royal_drydock", "⚒", 0x55FFFF),
    GUILD_DEPOT(3, "guild_depot", "📦", 0x55FF55);

    public static final int UNLOCK_LEVEL = 3;
    public static final int SWITCH_COST_DOUBLOONS = 8;

    private final int index;
    private final String id;
    private final String icon;
    private final int color;

    HarborSpecialization(int index, String id, String icon, int color) {
        this.index = index;
        this.id = id;
        this.icon = icon;
        this.color = color;
    }

    public int getIndex() {
        return this.index;
    }

    public String getId() {
        return this.id;
    }

    public String getIcon() {
        return this.icon;
    }

    public int getColor() {
        return this.color;
    }

    public boolean isActive() {
        return this != NONE;
    }

    public Component getDisplayName() {
        return Component.translatable("specialization.currents_of_trade." + this.id);
    }

    public Component getShortBadge() {
        if (this == NONE) {
            return Component.translatable("specialization.currents_of_trade.none_badge");
        }
        return Component.literal(this.icon + " ").append(getDisplayName());
    }

    public Component getDescription() {
        return Component.translatable("specialization.currents_of_trade." + this.id + ".desc");
    }

    public HarborSpecialization nextActive() {
        return switch (this) {
            case NONE -> FREE_PORT;
            case FREE_PORT -> ROYAL_DRYDOCK;
            case ROYAL_DRYDOCK -> GUILD_DEPOT;
            case GUILD_DEPOT -> FREE_PORT;
        };
    }

    public static HarborSpecialization byIndex(int idx) {
        for (HarborSpecialization spec : values()) {
            if (spec.index == idx) {
                return spec;
            }
        }
        return NONE;
    }

    public static HarborSpecialization byId(String id) {
        if (id == null || id.isEmpty()) return NONE;
        for (HarborSpecialization spec : values()) {
            if (spec.id.equalsIgnoreCase(id)) {
                return spec;
            }
        }
        return NONE;
    }
}
