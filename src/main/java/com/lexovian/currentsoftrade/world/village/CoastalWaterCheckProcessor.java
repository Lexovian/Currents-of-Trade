package com.lexovian.currentsoftrade.world.village;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Processor ensuring village docks only generate when facing natural water bodies (oceans, rivers).
 * - Safe chunk access: checks hasChunk() before reading any block/fluid, preventing WorldGenRegion crashes.
 * - Multi-chunk consistent: caches both accepted and rejected dock positions so docks spanning chunk
 *   boundaries are always placed or rejected as a complete unit (with full pilings and loot in all chunks).
 * - Enforces minimum distance between docks so normal villages get 1 dock and huge coastal villages get at most 2.
 */
public class CoastalWaterCheckProcessor extends StructureProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoastalWaterCheckProcessor.class);
    public static final MapCodec<CoastalWaterCheckProcessor> CODEC = MapCodec.unit(CoastalWaterCheckProcessor::new);

    private static final int MIN_WATER_BLOCKS = 6;
    private static final int MIN_DISTANCE_BETWEEN_DOCKS = 85;
    private static final int VILLAGE_CLUSTER_RADIUS = 180;
    private static final int MAX_DOCKS_PER_CLUSTER = 2;

    // Bounded cache tracking recently accepted dock positions in memory
    private static final Map<BlockPos, Long> PLACED_DOCKS = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
                    return size() > 500;
                }
            }
    );

    // Bounded cache tracking recently rejected dock positions so multi-chunk docks never place partial slices
    private static final Map<BlockPos, Long> REJECTED_DOCKS = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
                    return size() > 1000;
                }
            }
    );

    public static List<BlockPos> getPlacedDocks() {
        synchronized (PLACED_DOCKS) {
            return new ArrayList<>(PLACED_DOCKS.keySet());
        }
    }

    public static void clearCache() {
        synchronized (PLACED_DOCKS) {
            PLACED_DOCKS.clear();
        }
        synchronized (REJECTED_DOCKS) {
            REJECTED_DOCKS.clear();
        }
    }

    public CoastalWaterCheckProcessor() {}

    @Override
    protected StructureProcessorType<?> getType() {
        return CurrentsofTrade.COASTAL_WATER_CHECK.get();
    }

    private static boolean isColdEnoughToSnowSafe(ServerLevelAccessor level, BlockPos pos) {
        try {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!level.hasChunk(cx + dx, cz + dz)) {
                        return false;
                    }
                }
            }
            return level.getBiome(pos).value().coldEnoughToSnow(pos);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void markRejected(BlockPos pierWorldPos) {
        synchronized (REJECTED_DOCKS) {
            REJECTED_DOCKS.put(pierWorldPos, System.currentTimeMillis());
        }
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor serverLevel,
            BlockPos offset,
            BlockPos pos,
            List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
            List<StructureTemplate.StructureBlockInfo> processedBlockInfos,
            StructurePlaceSettings settings
    ) {
        if (processedBlockInfos == null || processedBlockInfos.isEmpty()) {
            return processedBlockInfos;
        }

        try {
            // Deterministic entrance and pier tip world positions across all chunks of the same dock
            BlockPos entranceWorldPos = StructureTemplate.calculateRelativePosition(settings, new BlockPos(5, 1, 0)).offset(offset);
            BlockPos pierWorldPos = StructureTemplate.calculateRelativePosition(settings, new BlockPos(5, 1, 14)).offset(offset);

            // If an earlier chunk of this exact dock already rejected it, reject this chunk too
            synchronized (REJECTED_DOCKS) {
                for (BlockPos rejected : REJECTED_DOCKS.keySet()) {
                    if (rejected.distSqr(pierWorldPos) < 9) {
                        return List.of();
                    }
                }
            }

            // Check if an earlier chunk of this exact dock already accepted it, or if another nearby dock conflicts
            boolean alreadyAcceptedSameDock = false;
            synchronized (PLACED_DOCKS) {
                int nearbyClusterDocks = 0;
                for (BlockPos placed : PLACED_DOCKS.keySet()) {
                    double dSq = placed.distSqr(pierWorldPos);
                    if (dSq < 25) {
                        alreadyAcceptedSameDock = true;
                        break;
                    }
                    if (dSq < (MIN_DISTANCE_BETWEEN_DOCKS * MIN_DISTANCE_BETWEEN_DOCKS)) {
                        LOGGER.info("[Currents of Trade] Rejecting duplicate dock at {}: another dock already placed at {} within {} blocks.",
                                pierWorldPos, placed, MIN_DISTANCE_BETWEEN_DOCKS);
                        markRejected(pierWorldPos);
                        return List.of();
                    }
                    if (dSq < (VILLAGE_CLUSTER_RADIUS * VILLAGE_CLUSTER_RADIUS)) {
                        nearbyClusterDocks++;
                        if (nearbyClusterDocks >= MAX_DOCKS_PER_CLUSTER) {
                            LOGGER.info("[Currents of Trade] Rejecting extra dock at {}: large village cluster already has {} docks.",
                                    pierWorldPos, nearbyClusterDocks);
                            markRejected(pierWorldPos);
                            return List.of();
                        }
                    }
                }
            }

            if (!alreadyAcceptedSameDock) {
                // Forward vector facing away from village into the water
                int rawDx = pierWorldPos.getX() - entranceWorldPos.getX();
                int rawDz = pierWorldPos.getZ() - entranceWorldPos.getZ();
                int fX = Integer.signum(rawDx);
                int fZ = Integer.signum(rawDz);
                if (fX == 0 && fZ == 0) fX = 1;
                int rX = -fZ;
                int rZ = fX;

                // Reject if the pier deck itself is submerged underwater or encased in ice
                if (serverLevel.hasChunk(pierWorldPos.getX() >> 4, pierWorldPos.getZ() >> 4)) {
                    if (serverLevel.getFluidState(pierWorldPos.above()).is(FluidTags.WATER)
                            || serverLevel.getBlockState(pierWorldPos.above()).is(Blocks.WATER)
                            || serverLevel.getBlockState(pierWorldPos).is(net.minecraft.tags.BlockTags.ICE)
                            || isColdEnoughToSnowSafe(serverLevel, pierWorldPos)) {
                        markRejected(pierWorldPos);
                        return List.of();
                    }
                }

                BlockPos waterAheadPos = pierWorldPos.offset(fX * 4, 0, fZ * 4);
                if (isColdEnoughToSnowSafe(serverLevel, waterAheadPos)) {
                    markRejected(pierWorldPos);
                    return List.of();
                }

                // Reject if any ice is present around or in front of the dock
                for (int f = -2; f <= 6; f++) {
                    for (int s = -3; s <= 3; s++) {
                        for (int dy = -4; dy <= 1; dy++) {
                            BlockPos check = pierWorldPos.offset(fX * f + rX * s, dy, fZ * f + rZ * s);
                            if (!serverLevel.hasChunk(check.getX() >> 4, check.getZ() >> 4)) {
                                continue;
                            }
                            if (serverLevel.getBlockState(check).is(net.minecraft.tags.BlockTags.ICE)) {
                                LOGGER.info("[Currents of Trade] Rejecting dock placement on frozen water/ice at {}", pierWorldPos);
                                markRejected(pierWorldPos);
                                return List.of();
                            }
                        }
                    }
                }

                // Verify natural unfrozen water at sea level in front of the dock (dy = -3..0).
                // Rejects hilltop/cliff placements (dy <= -4) and requires water right at the pier tip (f = 0..2).
                int waterBlocks = 0;
                int pierTipWaterBlocks = 0;
                for (int f = 0; f <= 5; f++) {
                    for (int s = -2; s <= 2; s++) {
                        for (int dy = -3; dy <= 0; dy++) {
                            BlockPos check = pierWorldPos.offset(fX * f + rX * s, dy, fZ * f + rZ * s);
                            if (!serverLevel.hasChunk(check.getX() >> 4, check.getZ() >> 4)) {
                                continue;
                            }
                            if (serverLevel.getFluidState(check).is(FluidTags.WATER) || serverLevel.getBlockState(check).is(Blocks.WATER)) {
                                BlockPos above = check.above();
                                net.minecraft.world.level.block.state.BlockState aboveState = serverLevel.getBlockState(above);
                                if (aboveState.isAir()
                                        || aboveState.is(Blocks.WATER)
                                        || serverLevel.getFluidState(above).is(FluidTags.WATER)
                                        || aboveState.is(Blocks.SEAGRASS)
                                        || aboveState.is(Blocks.TALL_SEAGRASS)
                                        || aboveState.is(Blocks.KELP)
                                        || aboveState.is(Blocks.LILY_PAD)) {
                                    waterBlocks++;
                                    if (f <= 2 && Math.abs(s) <= 1) {
                                        pierTipWaterBlocks++;
                                    }
                                }
                            }
                        }
                    }
                }

                if (waterBlocks < MIN_WATER_BLOCKS || pierTipWaterBlocks < 2) {
                    LOGGER.info("[Currents of Trade] Rejecting dock placement: insufficient water at pier tip (found {} water blocks, tipWater={}) at {}",
                            waterBlocks, pierTipWaterBlocks, pierWorldPos);
                    markRejected(pierWorldPos);
                    return List.of();
                }

                synchronized (PLACED_DOCKS) {
                    PLACED_DOCKS.put(pierWorldPos, System.currentTimeMillis());
                }
                LOGGER.info("[Currents of Trade] Successfully placed village dock facing water at {} (water blocks: {}, tipWater: {})",
                        pierWorldPos, waterBlocks, pierTipWaterBlocks);
            }

            // Populate chests/barrels with maritime loot tables and extend pilings/foundations down to the seabed
            // (runs for ALL chunks of an accepted dock so multi-chunk docks never miss pilings or loot!)
            List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processedBlockInfos.size() + 64);
            for (StructureTemplate.StructureBlockInfo info : processedBlockInfos) {
                if (info.state().is(Blocks.CHEST) || info.state().is(Blocks.BARREL)) {
                    net.minecraft.nbt.CompoundTag tag = info.nbt() != null ? info.nbt().copy() : new net.minecraft.nbt.CompoundTag();
                    if (!tag.contains("LootTable", net.minecraft.nbt.Tag.TAG_STRING)) {
                        String lootTable = info.state().is(Blocks.CHEST)
                                ? "currents_of_trade:chests/village_dock"
                                : "minecraft:chests/village/village_fisher";
                        tag.putString("LootTable", lootTable);
                        tag.putLong("LootTableSeed", serverLevel.getRandom().nextLong());
                        result.add(new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), tag));
                        continue;
                    }
                }
                result.add(info);
            }

            if (originalBlockInfos != null && originalBlockInfos.size() == processedBlockInfos.size()) {
                for (int i = 0; i < originalBlockInfos.size(); i++) {
                    StructureTemplate.StructureBlockInfo orig = originalBlockInfos.get(i);
                    if (orig.pos().getY() == 0) {
                        BlockPos worldBottomPos = processedBlockInfos.get(i).pos();
                        boolean isPiling = orig.state().is(Blocks.STRIPPED_SPRUCE_LOG);
                        boolean isFoundation = orig.state().is(Blocks.COBBLESTONE) || orig.state().is(Blocks.STONE_BRICKS);

                        if (isPiling || isFoundation) {
                            net.minecraft.world.level.block.state.BlockState extensionState = isPiling
                                    ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Y)
                                    : Blocks.COBBLESTONE.defaultBlockState();

                            BlockPos.MutableBlockPos down = worldBottomPos.mutable().move(net.minecraft.core.Direction.DOWN);
                            for (int depth = 0; depth < 30 && down.getY() > serverLevel.getMinBuildHeight(); depth++) {
                                if (!serverLevel.hasChunk(down.getX() >> 4, down.getZ() >> 4)) {
                                    break;
                                }
                                net.minecraft.world.level.block.state.BlockState current = serverLevel.getBlockState(down);
                                if (current.is(Blocks.WATER) || serverLevel.getFluidState(down).is(FluidTags.WATER) || current.isAir() || current.is(Blocks.SEAGRASS) || current.is(Blocks.TALL_SEAGRASS) || current.is(Blocks.KELP) || current.is(Blocks.KELP_PLANT)) {
                                    StructureTemplate.StructureBlockInfo added = new StructureTemplate.StructureBlockInfo(down.immutable(), extensionState, null);
                                    result.add(added);
                                    down.move(net.minecraft.core.Direction.DOWN);
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            return result;

        } catch (Throwable t) {
            LOGGER.warn("[Currents of Trade] Exception in CoastalWaterCheckProcessor at {}, rejecting dock safely: {}", pos, t.getMessage());
            return List.of();
        }
    }
}
