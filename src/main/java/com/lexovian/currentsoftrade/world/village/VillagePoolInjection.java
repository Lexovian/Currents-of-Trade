package com.lexovian.currentsoftrade.world.village;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class VillagePoolInjection {
    private static final Logger LOGGER = LoggerFactory.getLogger(VillagePoolInjection.class);

    private static final String DOCK_STRUCTURE = "currents_of_trade:village/dock";
    private static final String DOCK_STREET_STRUCTURE = "currents_of_trade:village/dock_street";
    private static final int DOCK_WEIGHT_HOUSES = 5;
    private static final int DOCK_WEIGHT_TERMINATORS = 3;

    private static final String[] TARGET_VILLAGE_HOUSES_POOLS = {
            "minecraft:village/plains/houses",
            "minecraft:village/savanna/houses",
            "minecraft:village/taiga/houses",
            "minecraft:village/snowy/houses",
            "minecraft:village/desert/houses"
    };

    private static final String[] TARGET_VILLAGE_TERMINATOR_POOLS = {
            "minecraft:village/plains/terminators",
            "minecraft:village/savanna/terminators",
            "minecraft:village/taiga/terminators",
            "minecraft:village/snowy/terminators",
            "minecraft:village/desert/terminators"
    };

    private static Field rawTemplatesField;
    private static Field templatesField;
    private static Field maxSizeField;
    private static boolean fieldsInitialized = false;

    private static void initReflection() {
        if (fieldsInitialized) return;
        try {
            rawTemplatesField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
            rawTemplatesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Obfuscated / fallback names if ever needed
            for (Field f : StructureTemplatePool.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType()) && f.getGenericType().getTypeName().contains("Pair")) {
                    rawTemplatesField = f;
                    rawTemplatesField.setAccessible(true);
                    break;
                }
            }
        }

        try {
            templatesField = StructureTemplatePool.class.getDeclaredField("templates");
            templatesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field f : StructureTemplatePool.class.getDeclaredFields()) {
                if (ObjectArrayList.class.isAssignableFrom(f.getType())) {
                    templatesField = f;
                    templatesField.setAccessible(true);
                    break;
                }
            }
        }

        try {
            maxSizeField = StructureTemplatePool.class.getDeclaredField("maxSize");
            maxSizeField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field f : StructureTemplatePool.class.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    maxSizeField = f;
                    maxSizeField.setAccessible(true);
                    break;
                }
            }
        }
        fieldsInitialized = true;
    }

    public static void inject(MinecraftServer server) {
        initReflection();
        if (rawTemplatesField == null || templatesField == null) {
            LOGGER.error("[Currents of Trade] Could not resolve StructureTemplatePool fields for village dock injection.");
            return;
        }

        Registry<StructureTemplatePool> poolRegistry = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureProcessorList> processorList = Holder.direct(new StructureProcessorList(List.of(new CoastalWaterCheckProcessor())));
        StructurePoolElement dockHousePiece = StructurePoolElement.legacy(DOCK_STRUCTURE, processorList).apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        StructurePoolElement dockStreetPiece = StructurePoolElement.legacy(DOCK_STREET_STRUCTURE, processorList).apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);

        int houseWeight = (com.lexovian.currentsoftrade.Config.VILLAGE_DOCK_SPAWN_WEIGHT != null)
                ? com.lexovian.currentsoftrade.Config.VILLAGE_DOCK_SPAWN_WEIGHT.get() : DOCK_WEIGHT_HOUSES;
        int terminatorWeight = Math.max(1, (houseWeight * 3) / 5);

        int injectedCount = 0;
        for (String poolName : TARGET_VILLAGE_HOUSES_POOLS) {
            StructureTemplatePool pool = poolRegistry.get(ResourceLocation.parse(poolName));
            if (pool != null) {
                if (injectPieceIntoPool(pool, dockHousePiece, DOCK_STRUCTURE, houseWeight)) {
                    injectedCount++;
                }
            }
        }

        for (String poolName : TARGET_VILLAGE_TERMINATOR_POOLS) {
            StructureTemplatePool pool = poolRegistry.get(ResourceLocation.parse(poolName));
            if (pool != null) {
                if (injectPieceIntoPool(pool, dockStreetPiece, DOCK_STREET_STRUCTURE, terminatorWeight)) {
                    injectedCount++;
                }
            }
        }

        LOGGER.info("[Currents of Trade] Injected village dock into {} village template pools (houses & terminators).", injectedCount);
    }

    @SuppressWarnings("unchecked")
    private static boolean injectPieceIntoPool(StructureTemplatePool pool, StructurePoolElement piece, String structureId, int weight) {
        try {
            List<Pair<StructurePoolElement, Integer>> rawTemplates = (List<Pair<StructurePoolElement, Integer>>) rawTemplatesField.get(pool);
            ObjectArrayList<StructurePoolElement> templates = (ObjectArrayList<StructurePoolElement>) templatesField.get(pool);

            if (rawTemplates == null || templates == null) {
                return false;
            }

            // Prevent duplicate injections on reload
            for (Pair<StructurePoolElement, Integer> entry : rawTemplates) {
                if (entry.getFirst() != null && entry.getFirst().toString().contains(structureId)) {
                    return false;
                }
            }

            // Ensure mutable lists
            if (!(rawTemplates instanceof ArrayList)) {
                rawTemplates = new ArrayList<>(rawTemplates);
                rawTemplatesField.set(pool, rawTemplates);
            }

            rawTemplates.add(Pair.of(piece, weight));

            // Populate generation templates according to weight
            for (int i = 0; i < weight; i++) {
                templates.add(piece);
            }

            // Reset cached maxSize so the structure template manager recalculates height bounds
            if (maxSizeField != null) {
                try {
                    maxSizeField.setInt(pool, Integer.MIN_VALUE);
                } catch (Exception ignored) {}
            }

            return true;
        } catch (Exception e) {
            LOGGER.error("[Currents of Trade] Failed to inject dock into pool: {}", e.getMessage());
            return false;
        }
    }
}
