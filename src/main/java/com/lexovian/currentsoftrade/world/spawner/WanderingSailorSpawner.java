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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class WanderingSailorSpawner {

    public static final int CHECK_INTERVAL_TICKS = 12000;
    public static final double SPAWN_CHANCE = 0.35D;
    private static final int MIN_MOORING_SURFACE_WATER = 85;

    private static int tickCounter = 0;

    public static void serverTick(ServerLevel level) {
        if (level.dimension() != ServerLevel.OVERWORLD || level.players().isEmpty()) {
            return;
        }

        int interval = Config.WANDERING_SAILOR_CHECK_INTERVAL.get();
        double chance  = Config.WANDERING_SAILOR_SPAWN_CHANCE.get();

        if (++tickCounter >= interval) {
            tickCounter = 0;
            if (level.getRandom().nextDouble() < chance) {
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
                if (level.getBlockEntity(anchorPos) instanceof AnchorPointBlockEntity anchor
                        && anchor.getHarborStatus() != AnchorPointBlockEntity.HarborStatus.VALID) {
                    continue;
                }
                String harborName = resolveHarborName(level, anchorPos);
                return spawnSailorAtLocation(level, anchorPos, anchorPos, harborName);
            }
        }

        PoiManager poiManager = level.getPoiManager();
        if (CurrentsofTrade.ANCHOR_POINT_POI.getKey() != null) {
            Optional<BlockPos> anchorPoi = poiManager.find(
                    holder -> holder.is(CurrentsofTrade.ANCHOR_POINT_POI.getKey()),
                    p -> level.isLoaded(p)
                            && (!(level.getBlockEntity(p) instanceof AnchorPointBlockEntity anchor)
                                || anchor.getHarborStatus() == AnchorPointBlockEntity.HarborStatus.VALID),
                    playerPos,
                    128,
                    PoiManager.Occupancy.ANY
            );
            if (anchorPoi.isPresent()) {
                BlockPos dockAnchor = anchorPoi.get();
                String harborName = resolveHarborName(level, dockAnchor);
                return spawnSailorAtLocation(level, dockAnchor, dockAnchor, harborName);
            }
        }

        // 3. Candidate B: Village meeting point (bell) near player (within 96 blocks)
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

    private static boolean isPassableAir(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (!state.blocksMotion() && state.getCollisionShape(level, pos).isEmpty());
    }

    private static boolean isTrueSurfaceWater(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || fluid.getHeight(level, pos) < 0.75F) {
            return false;
        }
        return level.getFluidState(pos.above()).isEmpty();
    }

    /**
     * Verifies via cached 2D surface-water flood-fill that a candidate mooring water block belongs to
     * a genuinely open sea, lake, or river (>= MIN_MOORING_SURFACE_WATER connected surface tiles),
     * preventing 14m Sloops from ever spawning inside tiny enclosed village ponds, wells, or ditches.
     */
    private static boolean hasOpenWaterBasin(ServerLevel level, BlockPos startWaterPos, Map<Long, Boolean> basinCache) {
        long startKey = startWaterPos.asLong();
        Boolean cached = basinCache.get(startKey);
        if (cached != null) {
            return cached;
        }
        if (!isTrueSurfaceWater(level, startWaterPos)) {
            basinCache.put(startKey, false);
            return false;
        }

        Set<Long> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        visited.add(startKey);
        queue.add(startWaterPos.immutable());
        int count = 0;

        while (!queue.isEmpty() && count < MIN_MOORING_SURFACE_WATER) {
            BlockPos cur = queue.poll();
            count++;

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = 0; dy >= -1; dy--) {
                    BlockPos adj = cur.relative(dir).offset(0, dy, 0);
                    if (Math.abs(adj.getX() - startWaterPos.getX()) > 36 || Math.abs(adj.getZ() - startWaterPos.getZ()) > 36) {
                        continue;
                    }
                    long key = adj.asLong();
                    if (!visited.contains(key) && isTrueSurfaceWater(level, adj)) {
                        visited.add(key);
                        queue.add(adj.immutable());
                        break;
                    }
                }
            }
        }

        boolean valid = count >= MIN_MOORING_SURFACE_WATER;
        for (Long k : visited) {
            basinCache.put(k, valid);
        }
        return valid;
    }

    /**
     * Checks whether the full 3D footprint of a 14m Sloop fits comfortably at the given position and yaw.
     * Guarantees that every hull sample sits on TRUE surface water (never submerged underwater), under open sky,
     * with full 16m mast clearance and zero clipping into piers, roofs, overhangs, or existing boats.
     */
    private static boolean isHullClear(ServerLevel level, double shipX, int waterY, double shipZ, float yaw) {
        AABB existingBoatCheck = new AABB(
                shipX - 4.0, waterY - 1.5, shipZ - 4.0,
                shipX + 4.0, waterY + 4.0, shipZ + 4.0
        );
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class, existingBoatCheck, net.minecraft.world.entity.Entity::isAlive).isEmpty()) {
            return false;
        }

        float rad = yaw * ((float) Math.PI / 180.0F);
        // Forward vector in Minecraft boat coordinates: -sin(yaw), cos(yaw)
        double fwdX = -Mth.sin(rad);
        double fwdZ = Mth.cos(rad);
        // Starboard / Right vector (perpendicular): -cos(yaw), -sin(yaw)
        double rightX = -Mth.cos(rad);
        double rightZ = -Mth.sin(rad);

        double[] fwdOffsets = {-4.5, -3.4, -2.2, -1.0, 0.0, 1.2, 2.5, 3.8, 4.9};
        double[] sideOffsets = {-1.8, -0.9, 0.0, 0.9, 1.8};

        for (double fwd : fwdOffsets) {
            for (double side : sideOffsets) {
                // Bowsprit is narrower
                if (fwd > 3.0 && Math.abs(side) > 0.9) continue;

                double px = shipX + fwdX * fwd + rightX * side;
                double pz = shipZ + fwdZ * fwd + rightZ * side;
                BlockPos pos = BlockPos.containing(px, waterY, pz);

                // 1. Strict surface + 2-block deep water check (rejects underwater dy=-2/-3 blocks!)
                if (!isTrueSurfaceWater(level, pos) || !level.getFluidState(pos.below()).is(FluidTags.WATER)) {
                    return false;
                }

                // 2. Open sky check over hull & mast — never spawn under roofs, boathouses, bridges, or caves
                if (Math.abs(side) <= 0.9 && !level.canSeeSky(pos.above())) {
                    return false;
                }

                // 3. Full mast (16m) & deck/rigging (6m) clearance free of fluids and collidable/solid blocks
                int requiredAir = (Math.abs(fwd) <= 2.5 && Math.abs(side) <= 1.8) ? 16 : 6;
                for (int y = 1; y <= requiredAir; y++) {
                    BlockPos airPos = pos.above(y);
                    if (!level.getFluidState(airPos).isEmpty()) {
                        return false;
                    }
                    BlockState state = level.getBlockState(airPos);
                    if (!state.isAir() && (!state.getCollisionShape(level, airPos).isEmpty() || state.blocksMotion())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static double scoreDockCandidate(ServerLevel level, BlockPos origin, BlockPos dockPos) {
        double score = level.canSeeSky(dockPos) ? 500.0 : 0.0;
        int dockY = dockPos.getY() - 1;
        for (int i = 0; i < 8; i++) {
            double ang = i * (Math.PI / 4.0);
            int sx = Mth.floor(dockPos.getX() + 0.5 + Math.cos(ang) * 5.0);
            int sz = Mth.floor(dockPos.getZ() + 0.5 + Math.sin(ang) * 5.0);
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos wp = new BlockPos(sx, dockY + dy, sz);
                if (isTrueSurfaceWater(level, wp) && level.canSeeSky(wp.above())) {
                    score += 25.0;
                    break;
                }
            }
        }
        score -= Math.sqrt(dockPos.distSqr(origin)) * 1.5;
        return score;
    }

    private static double scoreMooringSpot(ServerLevel level, BlockPos origin, BlockPos dockPos,
                                           double shipX, int waterY, double shipZ, float yaw) {
        double score = level.canSeeSky(dockPos) ? 1000.0 : 0.0;
        double[] radii = {6.5, 11.0};
        for (double r : radii) {
            for (int i = 0; i < 8; i++) {
                double ang = i * (Math.PI / 4.0);
                BlockPos wp = BlockPos.containing(shipX + Math.cos(ang) * r, waterY, shipZ + Math.sin(ang) * r);
                if (isTrueSurfaceWater(level, wp) && level.canSeeSky(wp.above())) {
                    score += 18.0;
                }
            }
        }
        float rad = yaw * ((float) Math.PI / 180.0F);
        double fwdX = -Mth.sin(rad);
        double fwdZ = Mth.cos(rad);
        BlockPos bowNear = BlockPos.containing(shipX + fwdX * 7.5, waterY, shipZ + fwdZ * 7.5);
        BlockPos bowFar = BlockPos.containing(shipX + fwdX * 12.0, waterY, shipZ + fwdZ * 12.0);
        if (isTrueSurfaceWater(level, bowNear) && level.canSeeSky(bowNear.above())) score += 35.0;
        if (isTrueSurfaceWater(level, bowFar) && level.canSeeSky(bowFar.above())) score += 35.0;

        score -= Math.sqrt(dockPos.distSqr(origin)) * 1.2;
        return score;
    }

    /**
     * Finds an open, seaward deep-water basin suitable for mooring the 14m Sloop vessel on the water surface
     * without intersecting any pier planks, buildings or eaves, and selects an open-sky pier/dock spot for the sailor.
     */
    private static MooringSpot findMooringLocation(ServerLevel level, BlockPos origin, int radius) {
        // 1. Collect candidate solid dock/pier blocks bordering surface water around origin
        List<BlockPos> candidateDocks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 3; dy >= -4; dy--) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) continue;

                    BlockState state = level.getBlockState(check);
                    if (state.isFaceSturdy(level, check, Direction.UP)
                            && !level.getFluidState(check).is(FluidTags.WATER)
                            && isPassableAir(level, check.above())
                            && isPassableAir(level, check.above(2))) {

                        boolean nearSurfaceWater = false;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos adj = check.relative(dir);
                            for (int wy = 0; wy >= -2; wy--) {
                                if (isTrueSurfaceWater(level, adj.offset(0, wy, 0))) {
                                    nearSurfaceWater = true;
                                    break;
                                }
                            }
                            if (nearSurfaceWater) break;
                        }
                        if (nearSurfaceWater) {
                            candidateDocks.add(check.above());
                        }
                    }
                }
            }
        }

        if (candidateDocks.isEmpty()) return null;

        // Rank open-sky pier tips surrounded by open water ahead of indoor/roofed boathouse blocks
        Map<BlockPos, Double> dockScores = new HashMap<>(candidateDocks.size());
        for (BlockPos dp : candidateDocks) {
            dockScores.put(dp, scoreDockCandidate(level, origin, dp));
        }
        candidateDocks.sort((a, b) -> Double.compare(dockScores.getOrDefault(b, 0.0), dockScores.getOrDefault(a, 0.0)));

        Map<Long, Boolean> basinCache = new HashMap<>();
        MooringSpot bestSpot = null;
        double bestScore = -1e9;
        int checkedDocks = 0;

        // 2. Evaluate top pier/shoreline candidates and select the most open seaward mooring placement
        for (BlockPos dockPos : candidateDocks) {
            if (checkedDocks >= 24 && bestSpot != null) {
                break;
            }
            checkedDocks++;
            int dockY = dockPos.getY() - 1;

            // Option A: Perpendicular mooring pointing outward into open water
            double[] testDists = {5.5, 6.0, 6.6};
            for (double dist : testDists) {
                for (int deg = 0; deg < 360; deg += 22) {
                    float yaw = (float) deg;
                    float rad = yaw * ((float) Math.PI / 180.0F);
                    double fwdX = -Mth.sin(rad);
                    double fwdZ = Mth.cos(rad);

                    double shipX = dockPos.getX() + 0.5 + fwdX * dist;
                    double shipZ = dockPos.getZ() + 0.5 + fwdZ * dist;

                    for (int dy = 0; dy >= -2; dy--) {
                        int waterY = dockY + dy;
                        BlockPos wPos = BlockPos.containing(shipX, waterY, shipZ);
                        if (!isTrueSurfaceWater(level, wPos)) continue;
                        if (!hasOpenWaterBasin(level, wPos, basinCache)) continue;

                        if (isHullClear(level, shipX, waterY, shipZ, yaw)) {
                            double sc = scoreMooringSpot(level, origin, dockPos, shipX, waterY, shipZ, yaw);
                            if (sc > bestScore) {
                                bestScore = sc;
                                bestSpot = new MooringSpot(new Vec3(shipX, waterY, shipZ), yaw, dockPos, waterY);
                            }
                        }
                    }
                }
            }

            // Option B: Parallel (broadside) docking alongside the outer pier
            double[] sideDists = {2.9, 3.4};
            for (double dist : sideDists) {
                for (int deg = 0; deg < 360; deg += 45) {
                    float radOut = (float) Math.toRadians(deg);
                    double outX = -Mth.sin(radOut);
                    double outZ = Mth.cos(radOut);

                    double shipX = dockPos.getX() + 0.5 + outX * dist;
                    double shipZ = dockPos.getZ() + 0.5 + outZ * dist;

                    float[] parallelYaws = {(float) (deg + 90), (float) (deg - 90)};
                    for (float yaw : parallelYaws) {
                        for (int dy = 0; dy >= -2; dy--) {
                            int waterY = dockY + dy;
                            BlockPos wPos = BlockPos.containing(shipX, waterY, shipZ);
                            if (!isTrueSurfaceWater(level, wPos)) continue;
                            if (!hasOpenWaterBasin(level, wPos, basinCache)) continue;

                            if (isHullClear(level, shipX, waterY, shipZ, yaw)) {
                                double sc = scoreMooringSpot(level, origin, dockPos, shipX, waterY, shipZ, yaw);
                                if (sc > bestScore) {
                                    bestScore = sc;
                                    bestSpot = new MooringSpot(new Vec3(shipX, waterY, shipZ), yaw, dockPos, waterY);
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestSpot;
    }

    /**
     * Spawns a Wandering Sailor on foot on safe ground near origin, with an optional
     * moored Merchant Ship (Sloop) floating cleanly on adjacent open water, and sets their wander destination.
     */
    public static SpawnResult spawnSailorAtLocation(ServerLevel level, BlockPos origin,
                                                    @Nullable BlockPos wanderDest,
                                                    @Nullable String harborName) {
        int searchRadius = (harborName != null) ? 18 : 24;
        MooringSpot spot = findMooringLocation(level, origin, searchRadius);
        int leagueId = level.getRandom().nextInt(WanderingSailorEntity.LEAGUE_COUNT);

        BlockPos sailorPos;
        SloopEntity ship = null;

        if (spot != null) {
            sailorPos = spot.dockPos();

            double boatX = spot.shipPos().x;
            double boatZ = spot.shipPos().z;
            int waterY = spot.waterY();
            double waterSurfaceY = waterY + 0.89;
            BlockPos bPos = BlockPos.containing(boatX, waterY, boatZ);
            FluidState fs = level.getFluidState(bPos);
            if (fs.is(FluidTags.WATER)) {
                waterSurfaceY = waterY + fs.getHeight(level, bPos);
            }
            // Spawn right at the water surface so vanilla Boat physics registers Status.IN_WATER cleanly
            double boatY = waterSurfaceY - 0.10;

            ship = new SloopEntity(level, boatX, boatY, boatZ);
            ship.setMerchantShip(true);
            applyTradeLeagueIdentity(level, ship, leagueId);
            ship.setYRot(spot.yaw());
            ship.yRotO = spot.yaw();
            ship.setDeltaMovement(0, 0, 0);

            level.addFreshEntity(ship);
        } else {
            // Fallback if no open water basin found: place sailor on open-sky ground without ship
            sailorPos = findSafeWalkableGround(level, origin, 10);
            if (sailorPos == null) {
                sailorPos = origin.above();
            }
        }

        WanderingSailorEntity sailor = CurrentsofTrade.WANDERING_SAILOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
        if (sailor == null) {
            if (ship != null) ship.discard();
            return new SpawnResult(false, sailorPos, "Failed to create Wandering Sailor entity!");
        }

        sailor.setTradeLeague(leagueId);

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

        if (spot != null && wanderDest != null && wanderDest.equals(origin)) {
            sailor.setWanderTarget(sailorPos);
        } else if (wanderDest != null) {
            sailor.setWanderTarget(wanderDest);
        }

        level.addFreshEntity(sailor);

        // Sound harbor/village arrival bell
        level.playSound(null, sailorX, sailorY, sailorZ, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);

        // Announcement to nearby players with the visiting Maritime Trade League's name
        Component leagueName = Component.translatable("league.currents_of_trade." + leagueId);
        Component announcement;
        if (harborName != null && !harborName.isBlank()) {
            announcement = Component.translatable("message.currents_of_trade.league_sailor_arrived", leagueName, harborName);
        } else {
            announcement = Component.translatable("message.currents_of_trade.league_sailor_village_arrived", leagueName);
        }

        for (var player : level.players()) {
            if (player.distanceToSqr(sailorX, sailorY, sailorZ) < 128.0 * 128.0) {
                player.sendSystemMessage(announcement);
            }
        }

        String targetDesc = (harborName != null) ? harborName : "village";
        return new SpawnResult(true, sailorPos, "Wandering Sailor (" + leagueName.getString() + ") arrived at " + targetDesc + "!");
    }

    /**
     * Outfits a naturally spawned Merchant Ship with its Maritime Trade League's flagship name,
     * dyed sail colors, heraldic stern banner, bow lantern, and copper hull trim.
     */
    private static void applyTradeLeagueIdentity(ServerLevel level, SloopEntity ship, int leagueId) {
        ship.setCustomName(Component.translatable("ship.currents_of_trade.league_" + leagueId));
        ship.setCustomNameVisible(true);

        var patternGetter = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN);

        switch (leagueId) {
            case 0 -> { // Golden Levant Company — Orange sails, Gold/Orange Sun & Border Banner
                ship.setSailColor(net.minecraft.world.item.DyeColor.ORANGE.getId());
                ship.setBowLanternType(1);
                ship.setCopperHull(true);
                net.minecraft.world.item.ItemStack banner = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ORANGE_BANNER);
                banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.RHOMBUS_MIDDLE, net.minecraft.world.item.DyeColor.YELLOW)
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.CURLY_BORDER, net.minecraft.world.item.DyeColor.YELLOW)
                                .build());
                ship.setBannerStack(banner);
            }
            case 1 -> { // Boreal Amber Hanse — Light Blue sails, Hanseatic Cross Banner
                ship.setSailColor(net.minecraft.world.item.DyeColor.LIGHT_BLUE.getId());
                ship.setBowLanternType(2);
                ship.setCopperHull(true);
                net.minecraft.world.item.ItemStack banner = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LIGHT_BLUE_BANNER);
                banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.STRAIGHT_CROSS, net.minecraft.world.item.DyeColor.WHITE)
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.BORDER, net.minecraft.world.item.DyeColor.BLUE)
                                .build());
                ship.setBannerStack(banner);
            }
            case 2 -> { // Emerald Archipelago Guild — Lime sails, Green/Gold Diamond Banner
                ship.setSailColor(net.minecraft.world.item.DyeColor.LIME.getId());
                ship.setBowLanternType(1);
                ship.setCopperHull(false);
                net.minecraft.world.item.ItemStack banner = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GREEN_BANNER);
                banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.RHOMBUS_MIDDLE, net.minecraft.world.item.DyeColor.LIME)
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.FLOWER, net.minecraft.world.item.DyeColor.YELLOW)
                                .build());
                ship.setBannerStack(banner);
            }
            case 3 -> { // Imperial Jade Flotilla — Red sails, Imperial Gold Circle & Border Banner
                ship.setSailColor(net.minecraft.world.item.DyeColor.RED.getId());
                ship.setBowLanternType(1);
                ship.setCopperHull(true);
                net.minecraft.world.item.ItemStack banner = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_BANNER);
                banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.CIRCLE_MIDDLE, net.minecraft.world.item.DyeColor.YELLOW)
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.BORDER, net.minecraft.world.item.DyeColor.YELLOW)
                                .build());
                ship.setBannerStack(banner);
            }
            case 4 -> { // Crimson Privateer Syndicate — Black sails, Black/Red Saltire & Skull Banner
                ship.setSailColor(net.minecraft.world.item.DyeColor.BLACK.getId());
                ship.setBowLanternType(2);
                ship.setCopperHull(true);
                net.minecraft.world.item.ItemStack banner = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLACK_BANNER);
                banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.CROSS, net.minecraft.world.item.DyeColor.RED)
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.SKULL, net.minecraft.world.item.DyeColor.WHITE)
                                .build());
                ship.setBannerStack(banner);
            }
            default -> { // Azure Thalassocracy — Blue sails, Cyan Saltire & Gold Compass Banner
                ship.setSailColor(net.minecraft.world.item.DyeColor.BLUE.getId());
                ship.setBowLanternType(2);
                ship.setCopperHull(true);
                net.minecraft.world.item.ItemStack banner = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLUE_BANNER);
                banner.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder()
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.CROSS, net.minecraft.world.item.DyeColor.LIGHT_BLUE)
                                .addIfRegistered(patternGetter, net.minecraft.world.level.block.entity.BannerPatterns.RHOMBUS_MIDDLE, net.minecraft.world.item.DyeColor.YELLOW)
                                .build());
                ship.setBannerStack(banner);
            }
        }
    }

    private static String resolveHarborName(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AnchorPointBlockEntity anchor) {
            return anchor.getHarborName();
        }
        return HarborSavedData.get(level).getHarborName(pos);
    }

    private static BlockPos findSafeWalkableGround(ServerLevel level, BlockPos center, int radius) {
        BlockPos indoorFallback = null;
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
                                && isPassableAir(level, above)
                                && isPassableAir(level, above2)) {
                            if (level.canSeeSky(above)) {
                                return above;
                            }
                            if (indoorFallback == null) {
                                indoorFallback = above;
                            }
                        }
                    }
                }
            }
        }
        return indoorFallback;
    }

    private static BlockPos findShorelineNear(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = 4; dy >= -4; dy--) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (!isTrueSurfaceWater(level, check)) continue;

                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos neighbor = check.relative(dir);
                        BlockState nState = level.getBlockState(neighbor);
                        if (nState.isFaceSturdy(level, neighbor, Direction.UP)
                                && !level.getFluidState(neighbor).is(FluidTags.WATER)
                                && isPassableAir(level, neighbor.above())
                                && isPassableAir(level, neighbor.above(2))
                                && level.canSeeSky(neighbor.above())) {
                            return neighbor.above();
                        }
                    }
                }
            }
        }
        return null;
    }

    public record SpawnResult(boolean success, BlockPos pos, String message) {}
}
