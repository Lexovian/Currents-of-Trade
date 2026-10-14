package com.lexovian.currentsoftrade.world.market;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Maritime Trade Regions (Deniz Ticaret Havzaları).
 *
 * <p>Classifies game biomes into 4 major commercial climates for the maritime silk road:
 * <ul>
 *   <li><b>ARID</b> — Desert, Badlands, Savanna. Exports Silk & Spices; high demand for Amber & Cold Seafood.</li>
 *   <li><b>COLD</b> — Snowy, Frozen Ocean, Taiga. Exports Amber & Fossils; high demand for Tea, Vanilla & Spices.</li>
 *   <li><b>TROPICAL</b> — Jungle, Bamboo, Mangrove. Exports Sandalwood & Vanilla; high demand for Fine Porcelain & Silk.</li>
 *   <li><b>TEMPERATE</b> — Plains, Forests, Coastal Waters. Exports Fine Porcelain & Tea; high demand for Sandalwood & Spices.</li>
 * </ul>
 */
public enum MaritimeTradeRegion {

    ARID("arid", 0xE69A28, "Arid & Silk Dunes"),
    COLD("cold", 0x5BC0EB, "Boreal & Frozen Reach"),
    TROPICAL("tropical", 0x48A14D, "Tropical Isles & Rainforest"),
    TEMPERATE("temperate", 0x2A9D8F, "Temperate & Coastal Hubs");

    private final String id;
    private final int color;
    private final String defaultTitle;

    MaritimeTradeRegion(String id, int color, String defaultTitle) {
        this.id = id;
        this.color = color;
        this.defaultTitle = defaultTitle;
    }

    public String getId() {
        return this.id;
    }

    public int getColor() {
        return this.color;
    }

    public String getDefaultTitle() {
        return this.defaultTitle;
    }

    public Component getDisplayName() {
        return Component.translatable("region.currents_of_trade." + this.id);
    }

    public Component getExportDescription() {
        return Component.translatable("region.currents_of_trade." + this.id + ".exports");
    }

    public Component getImportDescription() {
        return Component.translatable("region.currents_of_trade." + this.id + ".imports");
    }

    public static MaritimeTradeRegion byId(String id) {
        if (id == null) return TEMPERATE;
        for (MaritimeTradeRegion r : values()) {
            if (r.id.equalsIgnoreCase(id)) return r;
        }
        return TEMPERATE;
    }

    // --- Biome & Entity Classification ---

    public static MaritimeTradeRegion fromBiome(Holder<Biome> biomeHolder) {
        if (biomeHolder == null) return TEMPERATE;

        String path = biomeHolder.unwrapKey()
                .map(ResourceKey::location)
                .map(ResourceLocation::getPath)
                .orElse("ocean")
                .toLowerCase();

        // 1. Arid / Warm Dry
        if (path.contains("desert") || path.contains("badlands") || path.contains("savanna")
                || path.contains("warm_ocean") || path.contains("eroded")) {
            return ARID;
        }

        // 2. Cold / Boreal / Frozen
        if (path.contains("frozen") || path.contains("ice") || path.contains("snow")
                || path.contains("cold") || path.contains("taiga") || path.contains("grove")
                || path.contains("peaks") || path.contains("slopes")) {
            return COLD;
        }

        // 3. Tropical / Lush Rainforest
        if (path.contains("jungle") || path.contains("swamp") || path.contains("mangrove")
                || path.contains("bamboo") || path.contains("lush")) {
            return TROPICAL;
        }

        // 4. Tag fallbacks
        if (biomeHolder.is(BiomeTags.IS_BADLANDS) || biomeHolder.is(BiomeTags.IS_SAVANNA)) {
            return ARID;
        }
        if (biomeHolder.is(BiomeTags.IS_TAIGA) || biomeHolder.is(BiomeTags.HAS_VILLAGE_SNOWY) || biomeHolder.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return COLD;
        }
        if (biomeHolder.is(BiomeTags.IS_JUNGLE)) {
            return TROPICAL;
        }

        return TEMPERATE;
    }

    public static MaritimeTradeRegion fromLevelAndPos(Level level, BlockPos pos) {
        if (level == null || pos == null) return TEMPERATE;
        try {
            return fromBiome(level.getBiome(pos));
        } catch (Exception e) {
            return TEMPERATE;
        }
    }

    public static MaritimeTradeRegion fromEntity(Entity entity) {
        if (entity == null) return TEMPERATE;

        // Check villager outfit type as a helpful secondary signal
        if (entity instanceof Villager villager) {
            VillagerType vType = villager.getVillagerData().getType();
            if (vType == VillagerType.DESERT || vType == VillagerType.SAVANNA) {
                return ARID;
            }
            if (vType == VillagerType.SNOW || vType == VillagerType.TAIGA) {
                return COLD;
            }
            if (vType == VillagerType.JUNGLE || vType == VillagerType.SWAMP) {
                return TROPICAL;
            }
        }

        return fromLevelAndPos(entity.level(), entity.blockPosition());
    }

    // --- Commodities & Market Definitions ---

    /**
     * Checks if this item is a local surplus export produced abundantly in this region.
     */
    public boolean isExport(Item item) {
        return switch (this) {
            case ARID -> item == CurrentsofTrade.SILK_BALE.get()
                    || item == CurrentsofTrade.SPICE_SACK.get()
                    || item == CurrentsofTrade.SALT_POUCH.get();

            case COLD -> item == CurrentsofTrade.AMBER_VIAL.get()
                    || item == CurrentsofTrade.AMMONITE_FOSSIL.get()
                    || item == Items.COD || item == Items.SALMON;

            case TROPICAL -> item == CurrentsofTrade.SANDALWOOD.get()
                    || item == CurrentsofTrade.VANILLA_BUNDLE.get();

            case TEMPERATE -> item == CurrentsofTrade.FINE_PORCELAIN.get()
                    || item == CurrentsofTrade.TEA_BRICK.get()
                    || item == CurrentsofTrade.LUMINOUS_PEARL.get();
        };
    }

    /**
     * Checks if this item is in high demand, commanding a 1.5x - 2.0x Doubloon premium when delivered here.
     */
    public boolean isHighDemand(Item item) {
        return switch (this) {
            case ARID -> item == CurrentsofTrade.AMBER_VIAL.get()
                    || item == Items.COD || item == Items.SALMON
                    || item == Items.COOKED_COD || item == Items.COOKED_SALMON
                    || item == Items.ICE || item == Items.PACKED_ICE || item == Items.BLUE_ICE;

            case COLD -> item == CurrentsofTrade.TEA_BRICK.get()
                    || item == CurrentsofTrade.VANILLA_BUNDLE.get()
                    || item == CurrentsofTrade.SPICE_SACK.get();

            case TROPICAL -> item == CurrentsofTrade.FINE_PORCELAIN.get()
                    || item == CurrentsofTrade.SILK_BALE.get()
                    || item == CurrentsofTrade.TEA_BRICK.get();

            case TEMPERATE -> item == CurrentsofTrade.SANDALWOOD.get()
                    || item == CurrentsofTrade.SPICE_SACK.get()
                    || item == CurrentsofTrade.AMBER_VIAL.get();
        };
    }

    /**
     * Determines whether an exchange between an origin region and destination region constitutes
     * an active maritime arbitrage route for the specified reward item.
     */
    public static boolean isArbitrageRoute(MaritimeTradeRegion origin, MaritimeTradeRegion destination, Item item) {
        if (origin == destination || item == null) return false;
        // If the item is exported by destination and in high demand at origin, this is a prime arbitrage trade
        return destination.isExport(item) && origin.isHighDemand(item);
    }

    // --- Harbor Trade Seeding (Automated Regional Contracts) ---

    /**
     * Generates themed regional trade contracts for an Anchor Point based on its region and level.
     */
    public List<HarborTradeOffer> generateDefaultHarborTrades(int level) {
        List<HarborTradeOffer> offers = new ArrayList<>();

        switch (this) {
            case ARID -> {
                // Lv 1: Fish intake & salt export
                offers.add(new HarborTradeOffer(new ItemStack(Items.COD, 8), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 4)));

                if (level >= 2) {
                    // Lv 2: Spice export (cheap)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2)));
                }
                if (level >= 3) {
                    // Lv 3: Silk export (cheap)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1)));
                }
                if (level >= 4) {
                    // Lv 4: Amber import (high reward: 10 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                }
            }

            case COLD -> {
                // Lv 1: Fish export & coin exchange
                offers.add(new HarborTradeOffer(new ItemStack(Items.SALMON, 12), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.COOKED_COD, 4)));

                if (level >= 2) {
                    // Lv 2: Tea Brick import (2x payout: 6 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6)));
                }
                if (level >= 3) {
                    // Lv 3: Amber export (cheap: 3 Doubloons)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1)));
                }
                if (level >= 4) {
                    // Lv 4: Fossil export (cheap: 3 Doubloons)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1)));
                }
            }

            case TROPICAL -> {
                // Lv 1: Provisions
                offers.add(new HarborTradeOffer(new ItemStack(Items.MELON_SLICE, 16), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.BAMBOO, 16)));

                if (level >= 2) {
                    // Lv 2: Vanilla export (cheap: 1 Doubloon for 2)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2)));
                }
                if (level >= 3) {
                    // Lv 3: Sandalwood export (cheap: 2 Doubloons) & Porcelain import (high reward: 12 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12)));
                }
                if (level >= 4) {
                    // Lv 4: Silk import (high reward: 12 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12)));
                }
            }

            case TEMPERATE -> {
                // Lv 1: Fish & salt
                offers.add(new HarborTradeOffer(new ItemStack(Items.COD, 12), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2)));

                if (level >= 2) {
                    // Lv 2: Tea export (cheap: 2 Doubloons)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2)));
                }
                if (level >= 3) {
                    // Lv 3: Fine Porcelain export (cheap: 4 Doubloons) & Sandalwood import (high reward: 8 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                }
                if (level >= 4) {
                    // Lv 4: Amber import (high reward: 8 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                }
            }
        }

        return offers;
    }
}
