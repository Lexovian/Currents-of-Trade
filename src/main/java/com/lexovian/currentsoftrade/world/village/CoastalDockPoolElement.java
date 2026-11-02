package com.lexovian.currentsoftrade.world.village;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Custom StructurePoolElement for village docks that prevents Beardifier from raising
 * an oversized rectangular terrain platform around/under the dock or leaving empty grass
 * islands when a dock candidate is rejected by {@link CoastalWaterCheckProcessor}.
 *
 * <p>How it works:
 * <ul>
 *   <li>Reports {@link StructureTemplatePool.Projection#TERRAIN_MATCHING} to {@code Beardifier},
 *       so {@code Beardifier.forStructuresInChunk} skips creating a {@code Beardifier.Rigid} platform.</li>
 *   <li>Strips {@code GravityProcessor} in {@link #getSettings} so the dock building and wooden pier
 *       still place 100% rigidly and flat over the water instead of sinking to the seabed.</li>
 *   <li>Filters out the internal {@code minecraft:bottom} villager jigsaw during {@code JigsawPlacement}
 *       so rejected dock candidates never leave behind orphan child pieces, and spawns the Harbormaster
 *       villager directly inside {@link #place} only when the dock is actually accepted and placed.</li>
 * </ul>
 */
public class CoastalDockPoolElement extends LegacySinglePoolElement {

    public static final MapCodec<CoastalDockPoolElement> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec(),
                    overrideLiquidSettingsCodec()
            ).apply(instance, CoastalDockPoolElement::new)
    );

    public CoastalDockPoolElement(
            Either<ResourceLocation, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            Optional<LiquidSettings> overrideLiquidSettings
    ) {
        // Always pass TERRAIN_MATCHING to super so Beardifier never creates a Rigid terrain pad
        super(template, processors, StructureTemplatePool.Projection.TERRAIN_MATCHING, overrideLiquidSettings);
    }

    public static CoastalDockPoolElement create(String structureId, Holder<StructureProcessorList> processors) {
        return new CoastalDockPoolElement(
                Either.left(ResourceLocation.parse(structureId)),
                processors,
                StructureTemplatePool.Projection.TERRAIN_MATCHING,
                Optional.empty()
        );
    }

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox boundingBox, LiquidSettings liquidSettings, boolean offset) {
        StructurePlaceSettings settings = super.getSettings(rotation, boundingBox, liquidSettings, offset);
        // Remove GravityProcessor added by TERRAIN_MATCHING so the dock & pier remain flat and rigid
        for (StructureProcessor processor : StructureTemplatePool.Projection.TERRAIN_MATCHING.getProcessors()) {
            settings.popProcessor(processor);
        }
        return settings;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager structureTemplateManager,
            BlockPos pos,
            Rotation rotation,
            RandomSource random
    ) {
        List<StructureTemplate.StructureBlockInfo> original = super.getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random);
        List<StructureTemplate.StructureBlockInfo> filtered = new ArrayList<>(original.size());
        for (StructureTemplate.StructureBlockInfo info : original) {
            CompoundTag nbt = info.nbt();
            if (nbt != null && "minecraft:bottom".equals(nbt.getString("name"))) {
                // Skip internal villager jigsaw so rejected docks never spawn orphan child pieces
                continue;
            }
            filtered.add(info);
        }
        return filtered;
    }

    @Override
    public boolean place(
            StructureTemplateManager structureTemplateManager,
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            BlockPos offset,
            BlockPos pos,
            Rotation rotation,
            BoundingBox box,
            RandomSource random,
            LiquidSettings liquidSettings,
            boolean keepJigsaws
    ) {
        boolean placed = super.place(structureTemplateManager, level, structureManager, generator, offset, pos, rotation, box, random, liquidSettings, keepJigsaws);
        if (placed) {
            try {
                StructurePlaceSettings settings = this.getSettings(rotation, box, liquidSettings, keepJigsaws);
                BlockPos anchorWorldPos = StructureTemplate.calculateRelativePosition(settings, new BlockPos(3, 2, 2)).offset(offset);
                if (level.hasChunk(anchorWorldPos.getX() >> 4, anchorWorldPos.getZ() >> 4)
                        && level.getBlockState(anchorWorldPos).is(CurrentsofTrade.ANCHOR_POINT.get())) {
                    BlockPos villagerPos = StructureTemplate.calculateRelativePosition(settings, new BlockPos(4, 2, 5)).offset(offset);
                    Villager villager = EntityType.VILLAGER.create(level.getLevel());
                    if (villager != null) {
                        villager.moveTo(villagerPos.getX() + 0.5, villagerPos.getY(), villagerPos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villagerPos), MobSpawnType.STRUCTURE, null);
                        villager.setVillagerData(villager.getVillagerData().setProfession(CurrentsofTrade.HARBORMASTER.get()));
                        villager.setPersistenceRequired();
                        level.addFreshEntityWithPassengers(villager);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return placed;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return CurrentsofTrade.COASTAL_DOCK_ELEMENT.get();
    }

    @Override
    public String toString() {
        return "CoastalDock[" + this.template + "]";
    }
}

