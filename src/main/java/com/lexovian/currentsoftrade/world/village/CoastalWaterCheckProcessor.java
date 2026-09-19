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
 * - Enforces minimum distance of 70 blocks between docks so villages have at most 1 dock.
 * - Failsafe try-catch ensures world creation can NEVER be crashed by dock generation.
 */
public class CoastalWaterCheckProcessor extends StructureProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoastalWaterCheckProcessor.class);
    public static final MapCodec<CoastalWaterCheckProcessor> CODEC = MapCodec.unit(CoastalWaterCheckProcessor::new);

    private static final int MIN_WATER_BLOCKS = 6;
    private static final int MIN_DISTANCE_BETWEEN_DOCKS = 70;

    // Bounded cache tracking recently generated dock positions in memory
    private static final Map<BlockPos, Long> PLACED_DOCKS = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
                    return size() > 500;
                }
            }
    );

    public static List<BlockPos> getPlacedDocks() {
        synchronized (PLACED_DOCKS) {
            return new ArrayList<>(PLACED_DOCKS.keySet());
        }
    }

    public CoastalWaterCheckProcessor() {}

    @Override
    protected StructureProcessorType<?> getType() {
        return CurrentsofTrade.COASTAL_WATER_CHECK.get();
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
            // Locate entrance and pier tip positions
            BlockPos entranceWorldPos = StructureTemplate.calculateRelativePosition(settings, new BlockPos(5, 1, 0)).offset(offset);
            BlockPos pierWorldPos = null;
            if (originalBlockInfos != null) {
                for (int i = 0; i < originalBlockInfos.size() && i < processedBlockInfos.size(); i++) {
                    StructureTemplate.StructureBlockInfo orig = originalBlockInfos.get(i);
                    if (orig.pos().getX() == 5 && orig.pos().getY() == 1 && (orig.pos().getZ() == 13 || orig.pos().getZ() == 14)) {
                        pierWorldPos = processedBlockInfos.get(i).pos();
                        break;
                    }
                }
            }

            if (pierWorldPos == null) {
                pierWorldPos = StructureTemplate.calculateRelativePosition(settings, new BlockPos(5, 1, 14)).offset(offset);
            }

            // Enforce minimum distance between village docks
            synchronized (PLACED_DOCKS) {
                for (BlockPos placed : PLACED_DOCKS.keySet()) {
                    double dSq = placed.distSqr(pierWorldPos);
                    if (dSq < 25) {
                        return processedBlockInfos;
                    }
                    if (dSq < (MIN_DISTANCE_BETWEEN_DOCKS * MIN_DISTANCE_BETWEEN_DOCKS)) {
                        LOGGER.info("[Currents of Trade] Rejecting duplicate dock at {}: another dock already placed at {} within {} blocks.",
                                pierWorldPos, placed, MIN_DISTANCE_BETWEEN_DOCKS);
                        return List.of();
                    }
                }
            }

            // Forward vector facing away from village into the water
            int rawDx = pierWorldPos.getX() - entranceWorldPos.getX();
            int rawDz = pierWorldPos.getZ() - entranceWorldPos.getZ();
            int fX = Integer.signum(rawDx);
            int fZ = Integer.signum(rawDz);
            if (fX == 0 && fZ == 0) fX = 1;
            int rX = -fZ;
            int rZ = fX;

            // Verify natural water in front of the dock
            int waterBlocks = 0;
            for (int f = 0; f <= 5; f++) {
                for (int s = -2; s <= 2; s++) {
                    for (int dy = -2; dy <= 0; dy++) {
                        BlockPos check = pierWorldPos.offset(fX * f + rX * s, dy, fZ * f + rZ * s);
                        if (!serverLevel.hasChunk(check.getX() >> 4, check.getZ() >> 4)) {
                            continue;
                        }
                        if (serverLevel.getFluidState(check).is(FluidTags.WATER) || serverLevel.getBlockState(check).is(Blocks.WATER)) {
                            waterBlocks++;
                        }
                    }
                }
            }

            if (waterBlocks < MIN_WATER_BLOCKS) {
                LOGGER.info("[Currents of Trade] Rejecting dock placement: insufficient water in front of pier (found {} water blocks, required {}) at {}",
                        waterBlocks, MIN_WATER_BLOCKS, pierWorldPos);
                return List.of();
            }

            synchronized (PLACED_DOCKS) {
                PLACED_DOCKS.put(pierWorldPos, System.currentTimeMillis());
            }
            LOGGER.info("[Currents of Trade] Successfully placed village dock facing water at {} (water blocks: {})",
                    pierWorldPos, waterBlocks);

            // Extend pilings and perimeter foundations down to the seabed
            List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processedBlockInfos);
            if (originalBlockInfos != null && originalBlockInfos.size() == processedBlockInfos.size()) {
                for (int i = 0; i < originalBlockInfos.size(); i++) {
                    StructureTemplate.StructureBlockInfo orig = originalBlockInfos.get(i);
                    if (orig.pos().getY() == 0) {
                        BlockPos worldBottomPos = processedBlockInfos.get(i).pos();
                        boolean isPiling = orig.state().is(Blocks.STRIPPED_SPRUCE_LOG);
                        boolean isFoundation = orig.state().is(Blocks.COBBLESTONE) || orig.state().is(Blocks.STONE_BRICKS);

                        if (isPiling || (isFoundation && (orig.pos().getX() <= 2 || orig.pos().getX() >= 8 || orig.pos().getZ() == 0 || orig.pos().getZ() == 7))) {
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
                                    serverLevel.setBlock(down, extensionState, 2);
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
            LOGGER.warn("[Currents of Trade] Exception in CoastalWaterCheckProcessor at {}, placing dock safely: {}", pos, t.getMessage());
            return processedBlockInfos;
        }
    }
}
