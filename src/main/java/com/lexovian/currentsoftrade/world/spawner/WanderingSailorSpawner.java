package com.lexovian.currentsoftrade.world.spawner;

import com.lexovian.currentsoftrade.Config;
import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.WanderingSailorEntity;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WanderingSailorSpawner {

    public static final int CHECK_INTERVAL_TICKS = 12000;
    public static final double SPAWN_CHANCE = 0.35D;

    private static int tickCounter = 0;

    public static void serverTick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD || level.players().isEmpty()) {
            return;
        }

        int interval = (Config.WANDERING_SAILOR_CHECK_INTERVAL != null)
                ? Config.WANDERING_SAILOR_CHECK_INTERVAL.get() : CHECK_INTERVAL_TICKS;
        double chance = (Config.WANDERING_SAILOR_SPAWN_CHANCE != null)
                ? Config.WANDERING_SAILOR_SPAWN_CHANCE.get() : SPAWN_CHANCE;

        if (++tickCounter >= interval) {
            tickCounter = 0;
            RandomSource random = level.getRandom();
            if (random.nextDouble() < chance) {
                spawnSailorNaturally(level);
            }
        }
    }

    /**
     * Attempts to naturally spawn a Wandering Sailor near an active player,
     * prioritizing village docks, village meeting bells, and coastal waters.
     */
    public static SpawnResult spawnSailorNaturally(ServerLevel level) {
        List<ServerPlayer> validPlayers = level.players().stream()
                .filter(p -> !p.isSpectator())
                .toList();
        if (validPlayers.isEmpty()) {
            return new SpawnResult(false, null, "No active players in Overworld.");
        }

        ServerPlayer player = validPlayers.get(level.getRandom().nextInt(validPlayers.size()));
        BlockPos playerPos = player.blockPosition();

        // 1. Prevent duplicate sailors within 192 blocks of the player
        AABB checkArea = new AABB(playerPos).inflate(192.0);
        List<WanderingSailorEntity> nearbySailors = level.getEntitiesOfClass(
                WanderingSailorEntity.class, checkArea, LivingEntity::isAlive);
        if (!nearbySailors.isEmpty()) {
            return new SpawnResult(false, null, "A Wandering Sailor is already active nearby.");
        }

        // 2. Candidate A: Placed village dock or anchor point near player (within 128 blocks)
        for (BlockPos dockPos : CoastalWaterCheckProcessor.getPlacedDocks()) {
            if (dockPos.distSqr(playerPos) <= 128.0 * 128.0 && level.isLoaded(dockPos)) {
                String harborName = resolveHarborName(level, dockPos);
                return spawnSailorAtLocation(level, dockPos, dockPos, harborName);
            }
        }

        HarborSavedData harborData = HarborSavedData.get(level);
        for (BlockPos anchorPos : harborData.getAllHarborPositions()) {
            if (anchorPos.distSqr(playerPos) <= 128.0 * 128.0 && level.isLoaded(anchorPos)) {
                String harborName = resolveHarborName(level, anchorPos);
                return spawnSailorAtLocation(level, anchorPos, anchorPos, harborName);
            }
        }

        // 3. Candidate B: Village meeting point (bell) near player (within 96 blocks)
        PoiManager poiManager = level.getPoiManager();
        Optional<BlockPos> meetingPoi = poiManager.find(
                holder -> holder.is(PoiTypes.MEETING),
                p -> true,
                playerPos,
                96,
                PoiManager.Occupancy.ANY
        );
        if (meetingPoi.isPresent()) {
            BlockPos villageCenter = meetingPoi.get();
            return spawnSailorAtLocation(level, villageCenter, villageCenter, null);
        }

        // 4. Candidate C: Natural shoreline/water near player (within 48 blocks)
        BlockPos shorePos = findShorelineNear(level, playerPos, 32);
        if (shorePos != null) {
            return spawnSailorAtLocation(level, shorePos, playerPos, null);
        }

        // 5. Candidate D: Fallback to any valid harbor across the world
        return spawnSailorAtRandomHarbor(level, false);
    }

    /**
     * Attempts to find a valid anchor harbor and spawn a Wandering Sailor at the dock.
     */
    public static SpawnResult spawnSailorAtRandomHarbor(ServerLevel level, boolean forced) {
        HarborSavedData harborData = HarborSavedData.get(level);
        List<BlockPos> validHarbors = new ArrayList<>();

        for (BlockPos pos : harborData.getAllHarborPositions()) {
            if (level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof AnchorPointBlockEntity anchor) {
                    if (anchor.getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID) {
                        validHarbors.add(pos);
                    }
                }
            } else if (forced) {
                validHarbors.add(pos);
            }
        }

        if (validHarbors.isEmpty()) {
            return new SpawnResult(false, null, "No active/valid harbor (Anchor Point) found!");
        }

        RandomSource random = level.getRandom();
        BlockPos chosenAnchor = validHarbors.get(random.nextInt(validHarbors.size()));
        return spawnSailorAtAnchor(level, chosenAnchor);
    }

    /**
     * Spawns a Wandering Sailor at the specified anchor point dock on foot,
     * with their Merchant Ship moored in the adjacent water basin.
     */
    public static SpawnResult spawnSailorAtAnchor(ServerLevel level, BlockPos anchorPos) {
        String harborName = resolveHarborName(level, anchorPos);
        return spawnSailorAtLocation(level, anchorPos, anchorPos, harborName);
    }

    private record MooringSpot(Vec3 shipPos, float yaw, BlockPos dockPos, int waterY) {}

    /**
     * Checks whether the full 3D footprint of a 14m Sloop fits comfortably at the given position and yaw.
     * Guarantees that the hull sits in deep water, with no clipping into solid dock blocks, logs, walls, or roofs.
     */
    private static boolean isHullClear(ServerLevel level, double shipX, int waterY, double shipZ, float yaw) {
        float rad = yaw * ((float) Math.PI / 180.0F);
        // Forward vector in Minecraft boat coordinates: -sin(yaw), cos(yaw)
        double fwdX = -Mth.sin(rad);
        double fwdZ = Mth.cos(rad);
        // Starboard / Right vector (perpendicular): -cos(yaw), -sin(yaw)
        double rightX = -Mth.cos(rad);
        double rightZ = -Mth.sin(rad);

        // Hull sample offsets (meters) relative to ship entity center
        // Sloop length: stern lantern & transom reach -4.0m, bowsprit reaches +4.8m. Width with fenders is ~1.5m.
        double[] fwdOffsets = {-4.4, -3.4, -2.2, -1.0, 0.0, 1.2, 2.5, 3.8, 4.8};
        double[] sideOffsets = {-1.6, 0.0, 1.6};

        for (double fwd : fwdOffsets) {
            for (double side : sideOffsets) {
                // Bowsprit is narrower
                if (fwd > 3.0 && Math.abs(side) > 0.6) continue;

                double px = shipX + fwdX * fwd + rightX * side;
                double pz = shipZ + fwdZ * fwd + rightZ * side;
                BlockPos pos = BlockPos.containing(px, waterY, pz);

                if (!level.isLoaded(pos)) return false;

                // 1. Water check: must be at least 2 blocks of water beneath the hull
                if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos.below()).is(FluidTags.WATER)) {
                    return false;
                }

                // 2. Air clearance above water: ensure no solid blocks (piers, roofs, lamps) intersect the vessel
                int requiredAir = (Math.abs(fwd) <= 1.0 && Math.abs(side) <= 0.6) ? 7 : 4;
                for (int y = 1; y <= requiredAir; y++) {
                    BlockPos airPos = pos.above(y);
                    BlockState state = level.getBlockState(airPos);
                    if (!state.isAir() && (state.blocksMotion() || state.isSolid())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Finds an open deep water basin suitable for mooring the 14m Sloop vessel without intersecting
     * any pier planks, buildings or eaves, and selects a safe adjacent solid dock/pier block for the sailor.
     */
    private static MooringSpot findMooringLocation(ServerLevel level, BlockPos origin, int radius) {
        // 1. Collect candidate solid dock/pier blocks near water around origin
        List<BlockPos> candidateDocks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) continue;

                    BlockState state = level.getBlockState(check);
                    if (state.isFaceSturdy(level, check, Direction.UP)
                            && !level.getFluidState(check).is(FluidTags.WATER)
                            && level.getBlockState(check.above()).isAir()
                            && level.getBlockState(check.above(2)).isAir()) {

                        // Check if this dock block borders water
                        boolean nearWater = false;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos adj = check.relative(dir);
                            if (level.getFluidState(adj).is(FluidTags.WATER) || level.getFluidState(adj.below()).is(FluidTags.WATER)) {
                                nearWater = true;
                                break;
                            }
                        }
                        if (nearWater) {
                            candidateDocks.add(check.above());
                        }
                    }
                }
            }
        }

        if (candidateDocks.isEmpty()) return null;

        // Sort candidates by proximity to origin (Anchor Point)
        candidateDocks.sort((a, b) -> Double.compare(a.distSqr(origin), b.distSqr(origin)));

        // 2. For each candidate dock, search for a valid open-water mooring placement
        // Test 16 directions outward (every ~22.5 degrees)
        for (BlockPos dockPos : candidateDocks) {
            int dockY = dockPos.getY() - 1; // solid surface Y

            // Try perpendicular mooring pointing away from the dock (dist = 5.4m gives comfortable ~1.4m stern clearance)
            double[] testDists = {5.4, 5.8, 5.0, 6.2};
            for (double dist : testDists) {
                for (int deg = 0; deg < 360; deg += 22) {
                    float yaw = (float) deg;
                    float rad = yaw * ((float) Math.PI / 180.0F);
                    double fwdX = -Mth.sin(rad);
                    double fwdZ = Mth.cos(rad);

                    double shipX = dockPos.getX() + 0.5 + fwdX * dist;
                    double shipZ = dockPos.getZ() + 0.5 + fwdZ * dist;

                    for (int dy = 0; dy >= -3; dy--) {
                        int waterY = dockY + dy;
                        BlockPos wPos = BlockPos.containing(shipX, waterY, shipZ);
                        if (!level.isLoaded(wPos)) continue;

                        if (level.getFluidState(wPos).is(FluidTags.WATER)) {
                            if (isHullClear(level, shipX, waterY, shipZ, yaw)) {
                                return new MooringSpot(new Vec3(shipX, waterY, shipZ), yaw, dockPos, waterY);
                            }
                        }
                    }
                }
            }

            // Fallback: try parallel (broadside) docking alongside the pier
            double[] sideDists = {2.6, 3.0};
            for (double dist : sideDists) {
                for (int deg = 0; deg < 360; deg += 45) {
                    // Outward direction from dock
                    float radOut = (float) Math.toRadians(deg);
                    double outX = -Mth.sin(radOut);
                    double outZ = Mth.cos(radOut);

                    double shipX = dockPos.getX() + 0.5 + outX * dist;
                    double shipZ = dockPos.getZ() + 0.5 + outZ * dist;

                    // Test both parallel orientations (90 deg to outward vector)
                    float[] parallelYaws = {(float) (deg + 90), (float) (deg - 90)};
                    for (float yaw : parallelYaws) {
                        for (int dy = 0; dy >= -3; dy--) {
                            int waterY = dockY + dy;
                            BlockPos wPos = BlockPos.containing(shipX, waterY, shipZ);
                            if (!level.isLoaded(wPos)) continue;

                            if (level.getFluidState(wPos).is(FluidTags.WATER)) {
                                if (isHullClear(level, shipX, waterY, shipZ, yaw)) {
                                    return new MooringSpot(new Vec3(shipX, waterY, shipZ), yaw, dockPos, waterY);
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Spawns a Wandering Sailor on foot on safe ground near origin, with an optional
     * moored Merchant Ship (Sloop) in adjacent water, and sets their wander destination (e.g. village center).
     */
    public static SpawnResult spawnSailorAtLocation(ServerLevel level, BlockPos origin,
                                                    @Nullable BlockPos wanderDest,
                                                    @Nullable String harborName) {
        MooringSpot spot = findMooringLocation(level, origin, 14);

        BlockPos sailorPos;
        SloopEntity ship = null;

        if (spot != null) {
            sailorPos = spot.dockPos();

            double boatX = spot.shipPos().x;
            double boatZ = spot.shipPos().z;
            int waterY = spot.waterY();
            double waterSurfaceY = waterY + 1.0;
            BlockPos bPos = BlockPos.containing(boatX, waterY, boatZ);
            FluidState fs = level.getFluidState(bPos);
            if (fs.is(FluidTags.WATER)) {
                waterSurfaceY = waterY + fs.getHeight(level, bPos);
            }
            double boatY = waterSurfaceY - 0.45;

            ship = new SloopEntity(CurrentsofTrade.SLOOP.get(), level);
            ship.setMerchantShip(true);
            ship.setCustomName(Component.literal("Merchant Ship"));
            ship.setCustomNameVisible(true);
            ship.setPos(boatX, boatY, boatZ);
            ship.setYRot(spot.yaw());
            ship.setDeltaMovement(0, 0, 0);

            level.addFreshEntity(ship);
        } else {
            // Fallback if no open water basin found: place sailor on ground without ship
            sailorPos = findSafeWalkableGround(level, origin, 8);
            if (sailorPos == null) {
                sailorPos = origin.above();
            }
        }

        WanderingSailorEntity sailor = CurrentsofTrade.WANDERING_SAILOR.get().create(level);
        if (sailor == null) {
            if (ship != null) ship.discard();
            return new SpawnResult(false, sailorPos, "Failed to create Wandering Sailor entity!");
        }

        double sailorX = sailorPos.getX() + 0.5;
        double sailorY = sailorPos.getY();
        double sailorZ = sailorPos.getZ() + 0.5;

        sailor.setPos(sailorX, sailorY, sailorZ);
        int stayTicks = level.getRandom().nextIntBetweenInclusive(
                WanderingSailorEntity.MIN_DESPAWN_DELAY, WanderingSailorEntity.MAX_DESPAWN_DELAY);
        sailor.setSailorDespawnDelay(stayTicks);
        sailor.setDespawnDelay(stayTicks);

        if (ship != null) {
            sailor.setMooredBoatUUID(ship.getUUID());
            // Orient sailor facing towards the moored merchant ship
            double faceX = ship.getX() - sailorX;
            double faceZ = ship.getZ() - sailorZ;
            float sailorYaw = (float) (Mth.atan2(faceZ, faceX) * (180.0 / Math.PI)) - 90.0F;
            sailor.setYRot(sailorYaw);
        }

        if (wanderDest != null) {
            sailor.setWanderTarget(wanderDest);
        }

        level.addFreshEntity(sailor);

        // Sound harbor/village arrival bell
        level.playSound(null, sailorX, sailorY, sailorZ, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);

        // Announcement to nearby players
        Component announcement;
        if (harborName != null && !harborName.isBlank()) {
            announcement = Component.translatable("message.currents_of_trade.wandering_sailor_arrived", harborName);
        } else {
            announcement = Component.translatable("message.currents_of_trade.wandering_sailor_village_arrived");
        }

        for (var player : level.players()) {
            if (player.distanceToSqr(sailorX, sailorY, sailorZ) < 128.0 * 128.0) {
                player.displayClientMessage(announcement, false);
            }
        }

        String targetDesc = (harborName != null) ? harborName : "village";
        return new SpawnResult(true, sailorPos, "Wandering Sailor arrived at " + targetDesc + "!");
    }

    private static String resolveHarborName(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AnchorPointBlockEntity anchor) {
            return anchor.getHarborName();
        }
        return HarborSavedData.get(level).getHarborName(pos);
    }

    private static BlockPos findSafeWalkableGround(ServerLevel level, BlockPos center, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 3; dy >= -4; dy--) {
                        BlockPos check = center.offset(dx, dy, dz);
                        if (!level.isLoaded(check)) continue;

                        BlockState state = level.getBlockState(check);
                        BlockPos above = check.above();
                        BlockPos above2 = check.above(2);

                        if (state.isFaceSturdy(level, check, Direction.UP)
                                && !level.getFluidState(check).is(FluidTags.WATER)
                                && level.getBlockState(above).isAir()
                                && level.getBlockState(above2).isAir()) {
                            return above;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos findShorelineNear(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = 4; dy >= -4; dy--) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) continue;

                    if (level.getFluidState(check).is(FluidTags.WATER)) {
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos neighbor = check.relative(dir);
                            BlockState nState = level.getBlockState(neighbor);
                            if (nState.isFaceSturdy(level, neighbor, Direction.UP)
                                    && level.getBlockState(neighbor.above()).isAir()
                                    && level.getBlockState(neighbor.above(2)).isAir()) {
                                return neighbor.above();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public record SpawnResult(boolean success, BlockPos pos, String message) {}
}
