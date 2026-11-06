package com.lexovian.currentsoftrade.world.village;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
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
 * Custom LegacySinglePoolElement for the coastal dock structure.
 *
 * Overrides:
 *  1. getSettings: strips the TERRAIN_MATCHING GravityProcessor so the dock
 *     and pier remain flat, rigid, and strictly horizontal across water.
 *  2. getShuffledJigsawBlocks: strips the internal "minecraft:bottom" villager jigsaw
 *     so orphan villagers are not spawned if the dock is rejected.
 *  3. place: handles harbormaster villager spawning directly once placement succeeds.
 */
public class CoastalDockPoolElement extends LegacySinglePoolElement {

    public static final MapCodec<CoastalDockPoolElement> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                    templateCodec(),
                    processorsCodec(),
                    projectionCodec(),
                    overrideLiquidSettingsCodec()
            ).apply(builder, CoastalDockPoolElement::new)
    );

    protected CoastalDockPoolElement(
            Either<Identifier, StructureTemplate> template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection,
            Optional<LiquidSettings> liquidSettings
    ) {
        super(template, processors, projection, liquidSettings);
    }

    public CoastalDockPoolElement(
            Identifier template,
            Holder<StructureProcessorList> processors,
            StructureTemplatePool.Projection projection
    ) {
        this(Either.left(template), processors, projection, Optional.empty());
    }

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox boundingBox, LiquidSettings liquidSettings, boolean keepJigsaws) {
        StructurePlaceSettings settings = super.getSettings(rotation, boundingBox, liquidSettings, keepJigsaws);

        List<StructureProcessor> filtered = new ArrayList<>();
        for (StructureProcessor proc : settings.getProcessors()) {
            if (!(proc instanceof GravityProcessor)) {
                filtered.add(proc);
            }
        }

        settings.clearProcessors();
        for (StructureProcessor proc : filtered) {
            settings.addProcessor(proc);
        }

        return settings;
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureTemplateManager structureTemplateManager,
            BlockPos pos,
            Rotation rotation,
            RandomSource random
    ) {
        List<StructureTemplate.JigsawBlockInfo> original =
                super.getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random);

        List<StructureTemplate.JigsawBlockInfo> filtered = new ArrayList<>();
        for (StructureTemplate.JigsawBlockInfo blockInfo : original) {
            String name = blockInfo.name().toString();
            String target = blockInfo.target().toString();
            if ("minecraft:bottom".equals(name) || "minecraft:bottom".equals(target)) {
                continue;
            }
            filtered.add(blockInfo);
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
                    Villager villager = EntityType.VILLAGER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                    if (villager != null) {
                        villager.snapTo(villagerPos.getX() + 0.5, villagerPos.getY(), villagerPos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
                        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villagerPos), EntitySpawnReason.STRUCTURE, null);
                        villager.setVillagerData(villager.getVillagerData().withProfession(CurrentsofTrade.HARBORMASTER));
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
}
