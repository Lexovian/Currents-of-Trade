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
 *   <li><b>ARID</b> — Desert, Badlands, Savanna. Exports Silk, Spices, Saffron & Damascus Steel; high demand for Amber, Jade, Sea Ivory & Cacao.</li>
 *   <li><b>COLD</b> — Snowy, Frozen Ocean, Taiga. Exports Amber, Fossils & Sea Ivory; high demand for Tea, Vanilla, Spices, Saffron, Cacao & Damascus Steel.</li>
 *   <li><b>TROPICAL</b> — Jungle, Bamboo, Mangrove. Exports Sandalwood, Vanilla, Jade & Cacao; high demand for Fine Porcelain, Silk, Damascus Steel & Sea Ivory.</li>
 *   <li><b>TEMPERATE</b> — Plains, Forests, Coastal Waters. Exports Fine Porcelain, Tea, Pearls & Damascus Steel; high demand for Sandalwood, Spices, Saffron & Jade.</li>
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

    // --- Datapack Biome Tags (Modpack Compatibility) ---

    public static final TagKey<Biome> TAG_ARID = TagKey.create(Registries.BIOME, new ResourceLocation(CurrentsofTrade.MODID, "region/arid"));
    public static final TagKey<Biome> TAG_COLD = TagKey.create(Registries.BIOME, new ResourceLocation(CurrentsofTrade.MODID, "region/cold"));
    public static final TagKey<Biome> TAG_TROPICAL = TagKey.create(Registries.BIOME, new ResourceLocation(CurrentsofTrade.MODID, "region/tropical"));
    public static final TagKey<Biome> TAG_TEMPERATE = TagKey.create(Registries.BIOME, new ResourceLocation(CurrentsofTrade.MODID, "region/temperate"));

    // --- Biome & Entity Classification ---

    public static MaritimeTradeRegion fromBiome(Holder<Biome> biomeHolder) {
        if (biomeHolder == null) return TEMPERATE;

        // 0. Explicit datapack / modpack biome tags take highest priority
        if (biomeHolder.is(TAG_ARID)) return ARID;
        if (biomeHolder.is(TAG_COLD)) return COLD;
        if (biomeHolder.is(TAG_TROPICAL)) return TROPICAL;
        if (biomeHolder.is(TAG_TEMPERATE)) return TEMPERATE;

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
                    || item == CurrentsofTrade.SALT_POUCH.get()
                    || item == CurrentsofTrade.SALT_CRATE.get()
                    || item == CurrentsofTrade.SPICE_CRATE.get()
                    || item == CurrentsofTrade.SUGAR_CRATE.get()
                    || item == CurrentsofTrade.SAFFRON_BUNDLE.get()
                    || item == CurrentsofTrade.FRANKINCENSE_TEARS.get()
                    || item == CurrentsofTrade.DAMASCUS_STEEL.get()
                    || item == CurrentsofTrade.INDIGO_DYE_CAKE.get()
                    || item == Items.GLASS || item == Items.TERRACOTTA || item == Items.SUGAR;

            case COLD -> item == CurrentsofTrade.AMBER_VIAL.get()
                    || item == CurrentsofTrade.AMMONITE_FOSSIL.get()
                    || item == CurrentsofTrade.NARWHAL_IVORY.get()
                    || item == CurrentsofTrade.ERMINE_PELT.get()
                    || item == CurrentsofTrade.FISH_CRATE.get()
                    || item == Items.COD || item == Items.SALMON
                    || item == Items.PACKED_ICE || item == Items.SPRUCE_LOG || item == Items.IRON_INGOT;

            case TROPICAL -> item == CurrentsofTrade.SANDALWOOD.get()
                    || item == CurrentsofTrade.VANILLA_BUNDLE.get()
                    || item == CurrentsofTrade.JADE_FIGURINE.get()
                    || item == CurrentsofTrade.CACAO_ELIXIR.get()
                    || item == CurrentsofTrade.DRAGONS_BLOOD_RESIN.get()
                    || item == CurrentsofTrade.CINNAMON_QUILLS.get()
                    || item == CurrentsofTrade.COCOA_CRATE.get()
                    || item == CurrentsofTrade.SUGAR_CRATE.get()
                    || item == Items.COCOA_BEANS || item == Items.JUNGLE_LOG || item == Items.HONEY_BOTTLE;

            case TEMPERATE -> item == CurrentsofTrade.FINE_PORCELAIN.get()
                    || item == CurrentsofTrade.TEA_BRICK.get()
                    || item == CurrentsofTrade.LUMINOUS_PEARL.get()
                    || item == CurrentsofTrade.TYRIAN_PURPLE.get()
                    || item == CurrentsofTrade.DAMASCUS_STEEL.get()
                    || item == CurrentsofTrade.VELVET_BOLT.get()
                    || item == CurrentsofTrade.WOOL_BALE.get()
                    || item == Items.OAK_LOG || item == Items.WHITE_WOOL || item == Items.PAPER || item == Items.BOOK || item == Items.LEATHER || item == Items.COPPER_INGOT;
        };
    }

    /**
     * Checks if this item is in high demand, commanding a 1.5x - 2.0x Doubloon premium when delivered here.
     */
    public boolean isHighDemand(Item item) {
        return switch (this) {
            case ARID -> item == CurrentsofTrade.AMBER_VIAL.get()
                    || item == CurrentsofTrade.JADE_FIGURINE.get()
                    || item == CurrentsofTrade.NARWHAL_IVORY.get()
                    || item == CurrentsofTrade.CACAO_ELIXIR.get()
                    || item == CurrentsofTrade.TYRIAN_PURPLE.get()
                    || item == CurrentsofTrade.DRAGONS_BLOOD_RESIN.get()
                    || item == CurrentsofTrade.ERMINE_PELT.get()
                    || item == CurrentsofTrade.VELVET_BOLT.get()
                    || item == CurrentsofTrade.FISH_CRATE.get()
                    || item == CurrentsofTrade.WOOL_BALE.get()
                    || item == Items.COD || item == Items.SALMON
                    || item == Items.COOKED_COD || item == Items.COOKED_SALMON
                    || item == Items.ICE || item == Items.PACKED_ICE || item == Items.BLUE_ICE
                    || item == Items.SPRUCE_LOG || item == Items.WHITE_WOOL;

            case COLD -> item == CurrentsofTrade.TEA_BRICK.get()
                    || item == CurrentsofTrade.VANILLA_BUNDLE.get()
                    || item == CurrentsofTrade.SPICE_SACK.get()
                    || item == CurrentsofTrade.SPICE_CRATE.get()
                    || item == CurrentsofTrade.COCOA_CRATE.get()
                    || item == CurrentsofTrade.SUGAR_CRATE.get()
                    || item == CurrentsofTrade.SAFFRON_BUNDLE.get()
                    || item == CurrentsofTrade.FRANKINCENSE_TEARS.get()
                    || item == CurrentsofTrade.DRAGONS_BLOOD_RESIN.get()
                    || item == CurrentsofTrade.CACAO_ELIXIR.get()
                    || item == CurrentsofTrade.DAMASCUS_STEEL.get()
                    || item == CurrentsofTrade.INDIGO_DYE_CAKE.get()
                    || item == CurrentsofTrade.CINNAMON_QUILLS.get()
                    || item == Items.COCOA_BEANS || item == Items.HONEY_BOTTLE || item == Items.SUGAR || item == Items.TERRACOTTA;

            case TROPICAL -> item == CurrentsofTrade.FINE_PORCELAIN.get()
                    || item == CurrentsofTrade.SILK_BALE.get()
                    || item == CurrentsofTrade.TEA_BRICK.get()
                    || item == CurrentsofTrade.TYRIAN_PURPLE.get()
                    || item == CurrentsofTrade.DAMASCUS_STEEL.get()
                    || item == CurrentsofTrade.NARWHAL_IVORY.get()
                    || item == CurrentsofTrade.ERMINE_PELT.get()
                    || item == CurrentsofTrade.VELVET_BOLT.get()
                    || item == CurrentsofTrade.WOOL_BALE.get()
                    || item == CurrentsofTrade.SALT_CRATE.get()
                    || item == Items.IRON_INGOT || item == Items.GLASS || item == Items.WHITE_WOOL || item == Items.BOOK;

            case TEMPERATE -> item == CurrentsofTrade.SANDALWOOD.get()
                    || item == CurrentsofTrade.SPICE_SACK.get()
                    || item == CurrentsofTrade.SPICE_CRATE.get()
                    || item == CurrentsofTrade.COCOA_CRATE.get()
                    || item == CurrentsofTrade.SUGAR_CRATE.get()
                    || item == CurrentsofTrade.AMBER_VIAL.get()
                    || item == CurrentsofTrade.SAFFRON_BUNDLE.get()
                    || item == CurrentsofTrade.FRANKINCENSE_TEARS.get()
                    || item == CurrentsofTrade.DRAGONS_BLOOD_RESIN.get()
                    || item == CurrentsofTrade.JADE_FIGURINE.get()
                    || item == CurrentsofTrade.NARWHAL_IVORY.get()
                    || item == CurrentsofTrade.INDIGO_DYE_CAKE.get()
                    || item == CurrentsofTrade.CINNAMON_QUILLS.get()
                    || item == Items.COCOA_BEANS || item == Items.JUNGLE_LOG || item == Items.LAPIS_LAZULI || item == Items.PRISMARINE_CRYSTALS;
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
                // Lv 1: Fish intake, salt, glass, terracotta & northern timber/ice imports
                offers.add(new HarborTradeOffer(new ItemStack(Items.COD, 8), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 4)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.GLASS, 12)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.TERRACOTTA, 12)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.PACKED_ICE, 6), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.SPRUCE_LOG, 16), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));

                if (level >= 2) {
                    // Lv 2: Spice, Frankincense & Bulk Crate wholesale
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.FISH_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.WOOL_BALE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                }
                if (level >= 3) {
                    // Lv 3: Silk, Saffron & Royal Indigo export
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 2)));
                }
                if (level >= 4) {
                    // Lv 4: Amber, Cacao, Tyrian Purple, Dragon's Blood, Ermine Pelt & Velvet imports + Damascus Steel export
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 9)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.ERMINE_PELT.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.VELVET_BOLT.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 9)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1)));
                }
                if (level >= 5) {
                    // Lv 5 (Royal Dockyard): Sea Ivory & Jade Figurine imports (14 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                }
            }

            case COLD -> {
                // Lv 1: Fish, northern timber, packed ice, iron exports & warm-land imports
                offers.add(new HarborTradeOffer(new ItemStack(Items.SALMON, 12), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.COOKED_COD, 4)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.PACKED_ICE, 6)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.SPRUCE_LOG, 16)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT, 4)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.COCOA_BEANS, 10), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.HONEY_BOTTLE, 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));

                if (level >= 2) {
                    // Lv 2: Tea Brick, Vanilla & Bulk Crate wholesale imports
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.COCOA_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SUGAR_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SPICE_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                }
                if (level >= 3) {
                    // Lv 3: Amber & Royal Ermine Pelt exports + Saffron / Cacao / Frankincense / Cinnamon imports
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.ERMINE_PELT.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                }
                if (level >= 4) {
                    // Lv 4: Fossil & Spiral Sea Ivory export + Dragon's Blood & Royal Indigo imports
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1)));
                }
                if (level >= 5) {
                    // Lv 5 (Royal Dockyard): Damascus Steel import (14 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                }
            }

            case TROPICAL -> {
                // Lv 1: Tropical timber, cocoa, honey exports & iron/glass/wool imports
                offers.add(new HarborTradeOffer(new ItemStack(Items.MELON_SLICE, 16), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.BAMBOO, 16)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.COCOA_BEANS, 10)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.JUNGLE_LOG, 16)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(Items.HONEY_BOTTLE, 3)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.IRON_INGOT, 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.GLASS, 12), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2)));

                if (level >= 2) {
                    // Lv 2: Vanilla, Spiced Cacao Elixir, Ceylon Cinnamon & Bulk Crate wholesale
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.WOOL_BALE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SALT_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 6)));
                }
                if (level >= 3) {
                    // Lv 3: Sandalwood & Dragon's Blood Resin exports + Porcelain import
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12)));
                }
                if (level >= 4) {
                    // Lv 4: Silk, Tyrian Purple, Velvet Bolt & Ermine Pelt imports + Carved Jade Figurine export
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.VELVET_BOLT.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.ERMINE_PELT.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1)));
                }
                if (level >= 5) {
                    // Lv 5 (Royal Dockyard): Damascus Steel & Sea Ivory imports (14 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                }
            }

            case TEMPERATE -> {
                // Lv 1: Fish, salt, oak timber, wool, paper/books exports & exotic vanilla imports
                offers.add(new HarborTradeOffer(new ItemStack(Items.COD, 12), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.OAK_LOG, 16)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.WHITE_WOOL, 12)));
                offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), ItemStack.EMPTY, new ItemStack(Items.PAPER, 16)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.LAPIS_LAZULI, 6), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2)));
                offers.add(new HarborTradeOffer(new ItemStack(Items.PRISMARINE_CRYSTALS, 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));

                if (level >= 2) {
                    // Lv 2: Tea export & Bulk Crate wholesale imports
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.COCOA_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SUGAR_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SPICE_CRATE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                }
                if (level >= 3) {
                    // Lv 3: Fine Porcelain, Tyrian Purple & Crimson Velvet exports + Sandalwood, Saffron & Cinnamon imports
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 4), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.VELVET_BOLT.get(), 1)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                }
                if (level >= 4) {
                    // Lv 4: Amber, Dragon's Blood, Frankincense & Royal Indigo imports + Damascus Steel export
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DRAGONS_BLOOD_RESIN.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 9)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 8)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 10)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1)));
                }
                if (level >= 5) {
                    // Lv 5 (Royal Dockyard): Carved Jade Figurine & Sea Ivory imports (14 Doubloons!)
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                    offers.add(new HarborTradeOffer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 14)));
                }
            }
        }

        return offers;
    }

    /**
     * Generates a daily rotating Urgent Harbor Bounty contract (2x Doubloon reward)
     * for one of this region's high-demand commodities. Refreshes every 24,000 ticks (1 Minecraft day).
     */
    public HarborTradeOffer getUrgentBountyOffer(Level level, BlockPos pos) {
        return getUrgentBountyOffer(level, pos, 0);
    }

    public HarborTradeOffer getSecondaryUrgentBountyOffer(Level level, BlockPos pos) {
        return getUrgentBountyOffer(level, pos, 2);
    }

    public HarborTradeOffer getUrgentBountyOffer(Level level, BlockPos pos, int variantOffset) {
        long day = (level != null) ? (level.getDayTime() / 24000L) : 0L;
        long posHash = (pos != null) ? (pos.getX() * 3129871L ^ pos.getZ() * 116129781L) : 0L;
        int variant = (int) Math.floorMod(day + posHash + variantOffset, 7L);

        if (variant == 6) {
            ItemStack cagedAnimal = com.lexovian.currentsoftrade.item.AnimalCageItem.createDeterministicCage(
                    day * 31L + posHash + this.ordinal() * 13L + variantOffset);
            return new HarborTradeOffer(cagedAnimal, ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 22), true);
        }

        return switch (this) {
            case ARID -> switch (variant) {
                case 0 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
                case 1 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 2 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), true);
                case 3 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
                case 4 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), true);
                default -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.ERMINE_PELT.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
            };
            case COLD -> switch (variant) {
                case 0 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), true);
                case 1 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 12), true);
                case 2 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 3 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 4 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), true);
                default -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
            };
            case TROPICAL -> switch (variant) {
                case 0 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
                case 1 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
                case 2 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.TYRIAN_PURPLE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
                case 3 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), true);
                case 4 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), true);
                default -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.VELVET_BOLT.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 20), true);
            };
            case TEMPERATE -> switch (variant) {
                case 0 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 1 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 2 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 3 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.FRANKINCENSE_TEARS.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 16), true);
                case 4 -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 24), true);
                default -> new HarborTradeOffer(new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 2), ItemStack.EMPTY, new ItemStack(CurrentsofTrade.DOUBLOON.get(), 18), true);
            };
        };
    }
}
