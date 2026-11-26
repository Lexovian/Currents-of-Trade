package com.lexovian.currentsoftrade;

import com.google.common.collect.ImmutableSet;
import com.lexovian.currentsoftrade.block.AnchorPointBlock;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.item.DoubloonItem;
import com.lexovian.currentsoftrade.item.NauticalChartItem;
import com.lexovian.currentsoftrade.item.RouteJournalItem;
import com.lexovian.currentsoftrade.item.SloopItem;
import com.lexovian.currentsoftrade.item.TradeLedgerItem;
import com.lexovian.currentsoftrade.item.SpiceSackItem;
import com.lexovian.currentsoftrade.item.AmberVialItem;
import com.lexovian.currentsoftrade.item.SaltPouchItem;
import com.lexovian.currentsoftrade.item.SilkBaleItem;
import com.lexovian.currentsoftrade.item.FinePorcelainItem;
import com.lexovian.currentsoftrade.item.LuminousPearlItem;
import com.lexovian.currentsoftrade.item.BrassAstrolabeItem;
import com.lexovian.currentsoftrade.item.TeaBrickItem;
import com.lexovian.currentsoftrade.item.SandalwoodItem;
import com.lexovian.currentsoftrade.item.VanillaBundleItem;
import com.lexovian.currentsoftrade.item.AmmoniteFossilItem;
import com.lexovian.currentsoftrade.item.SaffronBundleItem;
import com.lexovian.currentsoftrade.item.JadeFigurineItem;
import com.lexovian.currentsoftrade.item.CacaoElixirItem;
import com.lexovian.currentsoftrade.item.NarwhalIvoryItem;
import com.lexovian.currentsoftrade.item.DamascusSteelItem;
import com.lexovian.currentsoftrade.item.FrankincenseTearsItem;
import com.lexovian.currentsoftrade.item.TyrianPurpleItem;
import com.lexovian.currentsoftrade.item.DragonsBloodResinItem;
import com.lexovian.currentsoftrade.item.MessageInABottleItem;
import com.lexovian.currentsoftrade.item.StormGlassItem;
import com.lexovian.currentsoftrade.item.CaptainsPipeItem;
import com.lexovian.currentsoftrade.item.TradeCharterItem;
import com.lexovian.currentsoftrade.item.GuildStrongboxItem;
import com.lexovian.currentsoftrade.item.CaptainsSpyglassItem;
import com.lexovian.currentsoftrade.item.BosunsWhistleItem;
import com.lexovian.currentsoftrade.item.SwivelHarpoonItem;
import com.lexovian.currentsoftrade.loot.AddTableLootModifier;
import com.lexovian.currentsoftrade.network.ModPayloads;
import com.lexovian.currentsoftrade.world.inventory.AnchorPointMenu;
import com.lexovian.currentsoftrade.world.inventory.RequestTradeMenu;
import com.lexovian.currentsoftrade.world.inventory.SendItemsMenu;
import com.lexovian.currentsoftrade.world.inventory.TravelMenu;
import com.lexovian.currentsoftrade.item.CargoBoatItem;
import com.lexovian.currentsoftrade.item.TradeBoatItem;
import com.lexovian.currentsoftrade.entity.WanderingSailorEntity;
import com.lexovian.currentsoftrade.recipe.TreasureMapCraftingRecipe;
import com.lexovian.currentsoftrade.world.village.CoastalDockPoolElement;
import com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

@Mod(CurrentsofTrade.MODID)
public class CurrentsofTrade {

    public static final String MODID = "currents_of_trade";
    public static final Logger LOGGER = LogUtils.getLogger();

    // --- Deferred Registries ---
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(ForgeRegistries.POI_TYPES, MODID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, MODID);
    public static final DeferredRegister<StructurePoolElementType<?>> STRUCTURE_POOL_ELEMENTS = DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, MODID);
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<net.minecraft.sounds.SoundEvent> BOSUNS_WHISTLE_SOUND =
            SOUND_EVENTS.register("bosuns_whistle", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    new net.minecraft.resources.ResourceLocation(MODID, "bosuns_whistle")));
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> HARPOON_FIRE_SOUND =
            SOUND_EVENTS.register("harpoon_fire", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    new net.minecraft.resources.ResourceLocation(MODID, "harpoon_fire")));
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> SHIP_HORN_SOUND =
            SOUND_EVENTS.register("ship_horn", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    new net.minecraft.resources.ResourceLocation(MODID, "ship_horn")));

    public static final RegistryObject<StructureProcessorType<CoastalWaterCheckProcessor>> COASTAL_WATER_CHECK =
            STRUCTURE_PROCESSORS.register("coastal_water_check", () -> () -> CoastalWaterCheckProcessor.CODEC);

    public static final RegistryObject<StructurePoolElementType<CoastalDockPoolElement>> COASTAL_DOCK_ELEMENT =
            STRUCTURE_POOL_ELEMENTS.register("coastal_dock_element", () -> () -> CoastalDockPoolElement.CODEC);

    public static final RegistryObject<SimpleCraftingRecipeSerializer<TreasureMapCraftingRecipe>> TREASURE_MAP_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("crafting_special_treasure_map", () -> new SimpleCraftingRecipeSerializer<>(TreasureMapCraftingRecipe::new));

    /** Forge 1.20.1 has no built-in "add_table" GLM (NeoForge 1.21 does), so the mod ships its own. */
    public static final RegistryObject<Codec<AddTableLootModifier>> ADD_TABLE_LOOT_MODIFIER =
            LOOT_MODIFIERS.register("add_table", () -> AddTableLootModifier.CODEC);

    // --- Blocks & Block Entities ---
    public static final RegistryObject<AnchorPointBlock> ANCHOR_POINT = BLOCKS.register(
            "anchor_point",
            () -> new AnchorPointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD))
    );
    public static final RegistryObject<BlockItem> ANCHOR_POINT_ITEM =
            ITEMS.register("anchor_point", () -> new BlockItem(ANCHOR_POINT.get(), new Item.Properties()) {
                @Override
                public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
                    com.lexovian.currentsoftrade.compat.CustomData cd = com.lexovian.currentsoftrade.compat.CustomData.getOrNull(stack);
                    if (cd != null) {
                        net.minecraft.nbt.CompoundTag root = cd.copyTag();
                        if (root.contains("SavedHarborData", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            net.minecraft.nbt.CompoundTag h = root.getCompound("SavedHarborData");
                            if (h.getInt("TradeLevel") > 1 || h.getBoolean("SpecializationChosen")) {
                                return true;
                            }
                        }
                    }
                    return super.isFoil(stack);
                }

                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tooltipComponents, net.minecraft.world.item.TooltipFlag isAdvanced) {
                    com.lexovian.currentsoftrade.compat.CustomData cd = com.lexovian.currentsoftrade.compat.CustomData.getOrNull(stack);
                    if (cd != null) {
                        net.minecraft.nbt.CompoundTag root = cd.copyTag();
                        if (root.contains("SavedHarborData", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            net.minecraft.nbt.CompoundTag h = root.getCompound("SavedHarborData");
                            String name = h.getString("HarborName");
                            int lvl = Math.max(1, h.getInt("TradeLevel"));
                            com.lexovian.currentsoftrade.world.harbor.HarborSpecialization spec =
                                    com.lexovian.currentsoftrade.world.harbor.HarborSpecialization.byId(h.getString("Specialization"));
                            if (!name.isEmpty() && (h.getBoolean("CustomNamed") || lvl > 1 || spec != com.lexovian.currentsoftrade.world.harbor.HarborSpecialization.NONE)) {
                                tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.anchor_saved_harbor", name));
                            }
                            if (lvl > 1) {
                                tooltipComponents.add(net.minecraft.network.chat.Component.translatable(
                                        "tooltip.currents_of_trade.anchor_saved_level",
                                        lvl,
                                        net.minecraft.network.chat.Component.translatable("harbor.currents_of_trade.level." + lvl)));
                            }
                            if (spec != com.lexovian.currentsoftrade.world.harbor.HarborSpecialization.NONE) {
                                tooltipComponents.add(net.minecraft.network.chat.Component.translatable("tooltip.currents_of_trade.spec_active", spec.getShortBadge()));
                            }
                        }
                    }
                    super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
                }
            });

    public static final RegistryObject<BlockEntityType<AnchorPointBlockEntity>> ANCHOR_POINT_BE =
            BLOCK_ENTITIES.register("anchor_point", () ->
                    BlockEntityType.Builder.of(AnchorPointBlockEntity::new, ANCHOR_POINT.get()).build(null)
            );

    public static final RegistryObject<com.lexovian.currentsoftrade.block.LighthouseBlock> LIGHTHOUSE = BLOCKS.register(
            "lighthouse",
            () -> new com.lexovian.currentsoftrade.block.LighthouseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 15)
                    .noOcclusion())
    );
    public static final RegistryObject<BlockItem> LIGHTHOUSE_ITEM =
            ITEMS.register("lighthouse", () -> new BlockItem(LIGHTHOUSE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity>> LIGHTHOUSE_BE =
            BLOCK_ENTITIES.register("lighthouse", () ->
                    BlockEntityType.Builder.of(com.lexovian.currentsoftrade.block.entity.LighthouseBlockEntity::new, LIGHTHOUSE.get()).build(null)
            );

    // --- Core Navigation & Vessels ---
    public static final RegistryObject<NauticalChartItem> NAUTICAL_CHART =
            ITEMS.register("nautical_chart", () -> new NauticalChartItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<RouteJournalItem> ROUTE_JOURNAL =
            ITEMS.register("route_journal", () -> new RouteJournalItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<DoubloonItem> DOUBLOON =
            ITEMS.register("doubloon", () -> new DoubloonItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<TradeLedgerItem> TRADE_LEDGER =
            ITEMS.register("trade_ledger", () -> new TradeLedgerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<SloopItem> SLOOP_ITEM =
            ITEMS.register("sloop", () -> new SloopItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CargoBoatItem> CARGO_BOAT_ITEM =
            ITEMS.register("cargo_boat", () -> new CargoBoatItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<TradeBoatItem> TRADE_BOAT_ITEM =
            ITEMS.register("trade_boat", () -> new TradeBoatItem(new Item.Properties().stacksTo(1)));

    // --- Trade Commodities & Goods ---
    /** Exotic spice blend -- eating grants Speed II + Haste I for 30 seconds. */
    public static final RegistryObject<SpiceSackItem> SPICE_SACK =
            ITEMS.register("spice_sack", () -> new SpiceSackItem(new Item.Properties()));

    /** Rare amber extract -- drinking grants Regeneration II (15s) with a Nausea side-effect (3s). */
    public static final RegistryObject<AmberVialItem> AMBER_VIAL =
            ITEMS.register("amber_vial", () -> new AmberVialItem(new Item.Properties()));

    /** Sea salt pouch -- right-click on edible in offhand to preserve it (adds Salted NBT tag). */
    public static final RegistryObject<SaltPouchItem> SALT_POUCH =
            ITEMS.register("salt_pouch", () -> new SaltPouchItem(new Item.Properties()));

    /** Luxury silk bale -- high-value trade currency, rarer than Doubloon, used in harbor trade offers. */
    public static final RegistryObject<SilkBaleItem> SILK_BALE =
            ITEMS.register("silk_bale", () -> new SilkBaleItem(new Item.Properties().stacksTo(16)));

    /** Hand-painted glazed ceramic from eastern ports -- high-demand luxury trade commodity. */
    public static final RegistryObject<FinePorcelainItem> FINE_PORCELAIN =
            ITEMS.register("fine_porcelain", () -> new FinePorcelainItem(new Item.Properties().stacksTo(16)));

    /** Rare shimmering gemstone dredged from deep ocean trenches -- prized ocean treasure. */
    public static final RegistryObject<LuminousPearlItem> LUMINOUS_PEARL =
            ITEMS.register("luminous_pearl", () -> new LuminousPearlItem(new Item.Properties().stacksTo(16)));

    /** Ornate brass navigational instrument mapping the stars and seas. */
    public static final RegistryObject<BrassAstrolabeItem> BRASS_ASTROLABE =
            ITEMS.register("brass_astrolabe", () -> new BrassAstrolabeItem(new Item.Properties().stacksTo(1)));

    /** Densely pressed block of aged tea leaves -- ancient currency across shipping routes. */
    public static final RegistryObject<TeaBrickItem> TEA_BRICK =
            ITEMS.register("tea_brick", () -> new TeaBrickItem(new Item.Properties().stacksTo(16)));

    /** Fragrant piece of rare timber from tropical archipelagos. */
    public static final RegistryObject<SandalwoodItem> SANDALWOOD =
            ITEMS.register("sandalwood", () -> new SandalwoodItem(new Item.Properties().stacksTo(16)));

    /** Dried pods of tropical orchids -- precious aromatic culinary spice. */
    public static final RegistryObject<VanillaBundleItem> VANILLA_BUNDLE =
            ITEMS.register("vanilla_bundle", () -> new VanillaBundleItem(new Item.Properties().stacksTo(16)));

    /** Spiraled petrified shell recovered from ocean floor strata. */
    public static final RegistryObject<AmmoniteFossilItem> AMMONITE_FOSSIL =
            ITEMS.register("ammonite_fossil", () -> new AmmoniteFossilItem(new Item.Properties().stacksTo(16)));

    /** Weathered nautical bottle sealed with wax, containing lost voyage secrets. */
    public static final RegistryObject<MessageInABottleItem> MESSAGE_IN_A_BOTTLE =
            ITEMS.register("message_in_a_bottle", () -> new MessageInABottleItem(new Item.Properties().stacksTo(16)));

    /** Weather forecasting sealed barometer vial filled with sensitive crystal solutions. */
    public static final RegistryObject<StormGlassItem> STORM_GLASS =
            ITEMS.register("storm_glass", () -> new StormGlassItem(new Item.Properties().stacksTo(1)));

    /** Polished seaworn wooden pipe favored by seasoned captains and sea dogs. */
    public static final RegistryObject<CaptainsPipeItem> CAPTAINS_PIPE =
            ITEMS.register("captains_pipe", () -> new CaptainsPipeItem(new Item.Properties().stacksTo(1)));

    /** Prestigious naval tricorne hat granting +20% sailing speed and 10% harbor fee discounts. */
    public static final RegistryObject<com.lexovian.currentsoftrade.item.CaptainsTricorneItem> CAPTAINS_TRICORNE =
            ITEMS.register("captains_tricorne", () -> new com.lexovian.currentsoftrade.item.CaptainsTricorneItem(new Item.Properties()));

    /** Weighted iron dredge used to scrape ocean depths and haul up lost cargo and relics. */
    public static final RegistryObject<com.lexovian.currentsoftrade.item.SalvageDredgeItem> SALVAGE_DREDGE =
            ITEMS.register("salvage_dredge", () -> new com.lexovian.currentsoftrade.item.SalvageDredgeItem(new Item.Properties()));

    /** Precious desert crocus threads — eating grants Fire Resistance I (45s) and Absorption I (30s). */
    public static final RegistryObject<SaffronBundleItem> SAFFRON_BUNDLE =
            ITEMS.register("saffron_bundle", () -> new SaffronBundleItem(new Item.Properties().stacksTo(16)));

    /** Carved jade sea-serpent statuette — right-click channels Luck I & Dolphin's Grace I (30s). */
    public static final RegistryObject<JadeFigurineItem> JADE_FIGURINE =
            ITEMS.register("jade_figurine", () -> new JadeFigurineItem(new Item.Properties().stacksTo(16)));

    /** Spiced jungle cacao brew — drinking grants Dolphin's Grace I & Strength I and clears Weakness/Mining Fatigue. */
    public static final RegistryObject<CacaoElixirItem> CACAO_ELIXIR =
            ITEMS.register("cacao_elixir", () -> new CacaoElixirItem(new Item.Properties().stacksTo(16)));

    /** Spiraled arctic sea tusk prized across tropical and desert ports. */
    public static final RegistryObject<NarwhalIvoryItem> NARWHAL_IVORY =
            ITEMS.register("narwhal_ivory", () -> new NarwhalIvoryItem(new Item.Properties().stacksTo(16)));

    /** Watered crucible steel ingot — right-click with damaged item in offhand to restore 25% durability. */
    public static final RegistryObject<DamascusSteelItem> DAMASCUS_STEEL =
            ITEMS.register("damascus_steel", () -> new DamascusSteelItem(new Item.Properties().stacksTo(16)));

    /** Golden resin tears from desert cliffs — clears debuffs and grants Resistance I (30s) + Regeneration I (20s). */
    public static final RegistryObject<FrankincenseTearsItem> FRANKINCENSE_TEARS =
            ITEMS.register("frankincense_tears", () -> new FrankincenseTearsItem(new Item.Properties().stacksTo(16)));

    /** Royal marine snail dye — dyes sails royal purple or grants Hero of the Village I (60s) + Glowing (30s). */
    public static final RegistryObject<TyrianPurpleItem> TYRIAN_PURPLE =
            ITEMS.register("tyrian_purple", () -> new TyrianPurpleItem(new Item.Properties().stacksTo(16)));

    /** Exotic dragon's blood resin — dyes sails blood red or grants Fire Resistance I (60s) + Strength I (30s). */
    public static final RegistryObject<DragonsBloodResinItem> DRAGONS_BLOOD_RESIN =
            ITEMS.register("dragons_blood_resin", () -> new DragonsBloodResinItem(new Item.Properties().stacksTo(16)));

    /** Sealed Guild Trade Charter — merchant contract fulfilled at regional Anchor Points or via Cargo/Trade Ships. */
    public static final RegistryObject<TradeCharterItem> TRADE_CHARTER =
            ITEMS.register("trade_charter", () -> new TradeCharterItem(new Item.Properties().stacksTo(1)));

    /** Guild Strongbox — ornate lockbox rewarded for completing Sealed Trade Charters. */
    public static final RegistryObject<GuildStrongboxItem> GUILD_STRONGBOX =
            ITEMS.register("guild_strongbox", () -> new GuildStrongboxItem(new Item.Properties().stacksTo(16)));

    /** Captain's Brass Spyglass — naval telescope with live ship, harbor & maritime region Recon HUD. */
    public static final RegistryObject<CaptainsSpyglassItem> CAPTAINS_SPYGLASS =
            ITEMS.register("captains_spyglass", () -> new CaptainsSpyglassItem(new Item.Properties().stacksTo(1)));

    /** Bosun's Whistle — summons nearby Sloops/Cargo Boats or recalls outbound autonomous ships. */
    public static final RegistryObject<BosunsWhistleItem> BOSUNS_WHISTLE =
            ITEMS.register("bosuns_whistle", () -> new BosunsWhistleItem(new Item.Properties().stacksTo(1)));

    /** Brass Swivel Harpoon — bow-mounted shipwright defense turret repelling aquatic threats. */
    public static final RegistryObject<SwivelHarpoonItem> SWIVEL_HARPOON =
            ITEMS.register("swivel_harpoon", () -> new SwivelHarpoonItem(new Item.Properties().stacksTo(16)));

    /** Navigation Buoy — floating illuminated sea marker placed directly on water; integrates with Nautical Charts. */
    public static final RegistryObject<com.lexovian.currentsoftrade.block.NavigationBuoyBlock> NAVIGATION_BUOY = BLOCKS.register(
            "navigation_buoy",
            () -> new com.lexovian.currentsoftrade.block.NavigationBuoyBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(1.0F, 2.0F)
                            .sound(SoundType.WOOD)
                            .lightLevel(state -> 14)
                            .noOcclusion()
            )
    );
    public static final RegistryObject<com.lexovian.currentsoftrade.item.NavigationBuoyItem> NAVIGATION_BUOY_ITEM =
            ITEMS.register("navigation_buoy", () -> new com.lexovian.currentsoftrade.item.NavigationBuoyItem(
                    NAVIGATION_BUOY.get(), new Item.Properties().stacksTo(16)));

    /** Harbor Brass Bell — rings with a Redstone pulse when trade ships arrive; right-click for fleet status. */
    public static final RegistryObject<com.lexovian.currentsoftrade.block.HarborBellBlock> HARBOR_BELL = BLOCKS.register(
            "harbor_bell",
            () -> new com.lexovian.currentsoftrade.block.HarborBellBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            )
    );
    public static final RegistryObject<BlockItem> HARBOR_BELL_ITEM =
            ITEMS.register("harbor_bell", () -> new BlockItem(HARBOR_BELL.get(), new Item.Properties()));

    /** Shipboard Trawl Net — mount on a Sloop or Cargo Ship to passively haul fish & sunken relics while sailing. */
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TrawlNetItem> TRAWL_NET =
            ITEMS.register("trawl_net", () -> new com.lexovian.currentsoftrade.item.TrawlNetItem(new Item.Properties().stacksTo(16)));

    // --- Exotic Regional Trade Commodities (v1.0.8) ---
    public static final RegistryObject<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> INDIGO_DYE_CAKE =
            ITEMS.register("indigo_dye_cake", () -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("indigo_dye_cake", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> ERMINE_PELT =
            ITEMS.register("ermine_pelt", () -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("ermine_pelt", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> CINNAMON_QUILLS =
            ITEMS.register("cinnamon_quills", () -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("cinnamon_quills", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.ExoticTradeGoodItem> VELVET_BOLT =
            ITEMS.register("velvet_bolt", () -> new com.lexovian.currentsoftrade.item.ExoticTradeGoodItem("velvet_bolt", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.AnimalCageItem> ANIMAL_CAGE =
            ITEMS.register("animal_cage", () -> new com.lexovian.currentsoftrade.item.AnimalCageItem(new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TradeCrateItem> FISH_CRATE =
            ITEMS.register("fish_crate", () -> new com.lexovian.currentsoftrade.item.TradeCrateItem("fish_crate", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TradeCrateItem> SALT_CRATE =
            ITEMS.register("salt_crate", () -> new com.lexovian.currentsoftrade.item.TradeCrateItem("salt_crate", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TradeCrateItem> SPICE_CRATE =
            ITEMS.register("spice_crate", () -> new com.lexovian.currentsoftrade.item.TradeCrateItem("spice_crate", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TradeCrateItem> COCOA_CRATE =
            ITEMS.register("cocoa_crate", () -> new com.lexovian.currentsoftrade.item.TradeCrateItem("cocoa_crate", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TradeCrateItem> SUGAR_CRATE =
            ITEMS.register("sugar_crate", () -> new com.lexovian.currentsoftrade.item.TradeCrateItem("sugar_crate", new Item.Properties()));
    public static final RegistryObject<com.lexovian.currentsoftrade.item.TradeCrateItem> WOOL_BALE =
            ITEMS.register("wool_bale", () -> new com.lexovian.currentsoftrade.item.TradeCrateItem("wool_bale", new Item.Properties()));

    // --- Menu Types ---
    public static final RegistryObject<MenuType<AnchorPointMenu>> ANCHOR_POINT_MENU =
            MENUS.register("anchor_point", () -> IForgeMenuType.create(AnchorPointMenu::new));

    public static final RegistryObject<MenuType<TravelMenu>> TRAVEL_MENU =
            MENUS.register("travel", () -> new MenuType<>(TravelMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<SendItemsMenu>> SEND_ITEMS_MENU =
            MENUS.register("send_items", () -> new MenuType<>(SendItemsMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<RequestTradeMenu>> REQUEST_TRADE_MENU =
            MENUS.register("request_trade", () -> new MenuType<>(RequestTradeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // --- Entity Types ---
    public static final RegistryObject<EntityType<TradeBoatEntity>> TRADE_BOAT =
            ENTITY_TYPES.register("trade_boat", () ->
                    EntityType.Builder.<TradeBoatEntity>of(TradeBoatEntity::new, MobCategory.MISC)
                            .sized(2.45F, 0.82F)
                            .clientTrackingRange(12)
                            .build("trade_boat")
            );

    public static final RegistryObject<EntityType<CargoBoatEntity>> CARGO_BOAT =
            ENTITY_TYPES.register("cargo_boat", () ->
                    EntityType.Builder.<CargoBoatEntity>of(CargoBoatEntity::new, MobCategory.MISC)
                            .sized(2.45F, 0.82F)
                            .clientTrackingRange(12)
                            .build("cargo_boat")
            );

    public static final RegistryObject<EntityType<SloopEntity>> SLOOP =
            ENTITY_TYPES.register("sloop", () ->
                    EntityType.Builder.<SloopEntity>of(SloopEntity::new, MobCategory.MISC)
                            .sized(2.45F, 0.82F)
                            .clientTrackingRange(12)
                            .build("sloop")
            );

    public static final RegistryObject<EntityType<WanderingSailorEntity>> WANDERING_SAILOR =
            ENTITY_TYPES.register("wandering_sailor", () ->
                    EntityType.Builder.<WanderingSailorEntity>of(
                            WanderingSailorEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("wandering_sailor")
            );

    public static final RegistryObject<EntityType<com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity>> DRIFTING_FLOTSAM =
            ENTITY_TYPES.register("drifting_flotsam", () ->
                    EntityType.Builder.<com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity>of(
                            com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity::new, MobCategory.MISC)
                            .sized(0.95F, 0.75F)
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .build("drifting_flotsam")
            );

    // --- Village & POI Integration ---
    public static final RegistryObject<PoiType> ANCHOR_POINT_POI =
            POI_TYPES.register("anchor_point_poi", () ->
                    new PoiType(ImmutableSet.copyOf(ANCHOR_POINT.get().getStateDefinition().getPossibleStates()), 1, 1)
            );

    public static final RegistryObject<VillagerProfession> HARBORMASTER =
            VILLAGER_PROFESSIONS.register("harbormaster", () ->
                    new VillagerProfession(
                            "harbormaster",
                            holder -> holder.is(ANCHOR_POINT_POI.getKey()),
                            holder -> holder.is(ANCHOR_POINT_POI.getKey()),
                            ImmutableSet.of(),
                            ImmutableSet.of(),
                            SoundEvents.VILLAGER_WORK_CARTOGRAPHER
                    )
            );

    // --- Creative Tab ---
    public static final RegistryObject<CreativeModeTab> CURRENTS_TAB =
            CREATIVE_MODE_TABS.register("currents_of_trade_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.currents_of_trade"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> ANCHOR_POINT_ITEM.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ANCHOR_POINT_ITEM.get());
                                output.accept(LIGHTHOUSE_ITEM.get());
                                output.accept(NAVIGATION_BUOY_ITEM.get());
                                output.accept(HARBOR_BELL_ITEM.get());
                                output.accept(TRAWL_NET.get());
                                output.accept(CAPTAINS_TRICORNE.get());
                                output.accept(SALVAGE_DREDGE.get());
                                output.accept(NAUTICAL_CHART.get());
                                output.accept(ROUTE_JOURNAL.get());
                                output.accept(DOUBLOON.get());
                                output.accept(SILK_BALE.get());
                                output.accept(TEA_BRICK.get());
                                output.accept(FINE_PORCELAIN.get());
                                output.accept(SANDALWOOD.get());
                                output.accept(VANILLA_BUNDLE.get());
                                output.accept(LUMINOUS_PEARL.get());
                                output.accept(AMMONITE_FOSSIL.get());
                                output.accept(SAFFRON_BUNDLE.get());
                                output.accept(JADE_FIGURINE.get());
                                output.accept(CACAO_ELIXIR.get());
                                output.accept(NARWHAL_IVORY.get());
                                output.accept(DAMASCUS_STEEL.get());
                                output.accept(FRANKINCENSE_TEARS.get());
                                output.accept(TYRIAN_PURPLE.get());
                                output.accept(DRAGONS_BLOOD_RESIN.get());
                                output.accept(INDIGO_DYE_CAKE.get());
                                output.accept(ERMINE_PELT.get());
                                output.accept(CINNAMON_QUILLS.get());
                                output.accept(VELVET_BOLT.get());
                                output.accept(ANIMAL_CAGE.get());
                                output.accept(FISH_CRATE.get());
                                output.accept(SALT_CRATE.get());
                                output.accept(SPICE_CRATE.get());
                                output.accept(COCOA_CRATE.get());
                                output.accept(SUGAR_CRATE.get());
                                output.accept(WOOL_BALE.get());
                                output.accept(MESSAGE_IN_A_BOTTLE.get());
                                output.accept(SPICE_SACK.get());
                                output.accept(AMBER_VIAL.get());
                                output.accept(SALT_POUCH.get());
                                output.accept(BRASS_ASTROLABE.get());
                                output.accept(STORM_GLASS.get());
                                output.accept(CAPTAINS_PIPE.get());
                                output.accept(CAPTAINS_SPYGLASS.get());
                                output.accept(BOSUNS_WHISTLE.get());
                                output.accept(SWIVEL_HARPOON.get());
                                output.accept(TRADE_CHARTER.get());
                                output.accept(GUILD_STRONGBOX.get());
                                output.accept(TRADE_LEDGER.get());
                                output.accept(SLOOP_ITEM.get());
                                output.accept(CARGO_BOAT_ITEM.get());
                                output.accept(TRADE_BOAT_ITEM.get());
                            })
                            .build()
            );

    // --- Mod Lifecycle ---
    public CurrentsofTrade() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        POI_TYPES.register(modEventBus);
        VILLAGER_PROFESSIONS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        STRUCTURE_PROCESSORS.register(modEventBus);
        STRUCTURE_POOL_ELEMENTS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(WANDERING_SAILOR.get(), WanderingSailorEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModPayloads::register);
        LOGGER.info("Currents of Trade common setup completed.");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // All mod items live exclusively in CURRENTS_TAB.
    }
}
