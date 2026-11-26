package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.harbor.GuildReputationData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Lightweight bobbing ocean flotsam entity.
 * Spawns exclusively in ocean biomes at the outer edge of the player's render distance.
 * Right-click/attack to salvage, or sail directly over it with a Trawl Net to scoop it into the ship's hold.
 */
public class DriftingFlotsamEntity extends Entity {

    private static final int MAX_LIFETIME_TICKS = 6000; // 5 minutes
    private int ageTicks = 0;

    public DriftingFlotsamEntity(EntityType<? extends DriftingFlotsamEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.ageTicks = tag.getInt("AgeTicks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("AgeTicks", this.ageTicks);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        super.tick();
        this.ageTicks++;

        // Realistic water buoyancy, gravity (never hovers in mid-air), and gentle ocean drift
        BlockPos bp = this.blockPosition();
        net.minecraft.world.level.material.FluidState fluidAt = this.level().getFluidState(bp);
        boolean inWater = fluidAt.is(FluidTags.WATER);
        boolean waterAbove = this.level().getFluidState(bp.above()).is(FluidTags.WATER);
        net.minecraft.world.phys.Vec3 vel = this.getDeltaMovement();

        if (inWater) {
            double vx = vel.x * 0.88D;
            double vz = vel.z * 0.88D;
            double vy;
            if (waterAbove) {
                vy = Math.min(0.12D, vel.y + 0.04D);
            } else {
                double waterSurface = bp.getY() + fluidAt.getHeight(this.level(), bp);
                double targetY = waterSurface - 0.30D;
                vy = net.minecraft.util.Mth.clamp((targetY - this.getY()) * 0.25D, -0.08D, 0.08D);
                if (!this.level().isClientSide) {
                    double driftAngle = (this.getId() * 1.6180339887D) % (Math.PI * 2.0D);
                    vx += Math.cos(driftAngle) * 0.0028D;
                    vz += Math.sin(driftAngle) * 0.0028D;
                }
            }
            this.setDeltaMovement(vx, vy, vz);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        } else {
            double vy = this.onGround() ? 0.0D : Math.max(-0.5D, vel.y - 0.04D);
            double vx = this.onGround() ? vel.x * 0.6D : vel.x * 0.94D;
            double vz = this.onGround() ? vel.z * 0.6D : vel.z * 0.94D;
            this.setDeltaMovement(vx, vy, vz);
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        }

        if (this.level().isClientSide) {
            if (inWater && this.random.nextFloat() < 0.14F) {
                this.level().addParticle(ParticleTypes.SPLASH,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.7,
                        this.getY() + 0.28,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.7,
                        0.0, 0.03, 0.0);
            }
            return;
        }

        // Lightweight periodic despawn check (every 40 ticks)
        if (this.ageTicks >= MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }
        if (this.tickCount % 40 == 0 && this.level() instanceof ServerLevel sl) {
            Player nearest = sl.getNearestPlayer(this, 152.0D);
            if (nearest == null) {
                this.discard();
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl && !this.isRemoved()) {
            salvageByPlayer(sl, player);
            this.discard();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl && !this.isRemoved()) {
            if (source.getEntity() instanceof Player player) {
                salvageByPlayer(sl, player);
            }
            this.discard();
            return true;
        }
        return false;
    }

    private void salvageByPlayer(ServerLevel level, Player player) {
        RandomSource rng = level.getRandom();
        ItemStack doubloons = new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2 + rng.nextInt(4));
        ItemStack cargo = rollFlotsamCargo(rng);

        if (!player.getInventory().add(doubloons)) {
            this.spawnAtLocation(doubloons);
        }
        if (!cargo.isEmpty() && !player.getInventory().add(cargo)) {
            this.spawnAtLocation(cargo);
        }

        playSalvageEffects(level);
        if (player instanceof ServerPlayer sp) {
            GuildReputationData.awardReputation(level, sp, 5);
            sp.displayClientMessage(Component.translatable("message.currents_of_trade.flotsam_salvaged"), true);
        }
    }

    /**
     * Automatically scoops this flotsam into a passing ship's cargo hold when equipped with a Trawl Net.
     */
    public boolean collectIntoShip(ServerLevel level, Boat ship, Container hold) {
        if (this.isRemoved()) return false;
        RandomSource rng = level.getRandom();
        ItemStack doubloons = new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2 + rng.nextInt(4));
        ItemStack cargo = rollFlotsamCargo(rng);
        int doubloonCount = doubloons.getCount();
        Component cargoName = cargo.getHoverName();

        if (!com.lexovian.currentsoftrade.item.TrawlNetItem.insertIntoContainer(hold, doubloons) && !doubloons.isEmpty()) {
            this.spawnAtLocation(doubloons);
        }
        if (!cargo.isEmpty() && !com.lexovian.currentsoftrade.item.TrawlNetItem.insertIntoContainer(hold, cargo) && !cargo.isEmpty()) {
            this.spawnAtLocation(cargo);
        }

        playSalvageEffects(level);
        if (ship.getControllingPassenger() instanceof ServerPlayer captain) {
            GuildReputationData.awardReputation(level, captain, 5);
            captain.displayClientMessage(Component.translatable("message.currents_of_trade.flotsam_trawled",
                    doubloonCount, cargoName), true);
        }
        this.discard();
        return true;
    }

    private void playSalvageEffects(ServerLevel level) {
        RandomSource rng = level.getRandom();
        level.playSound(null, this.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.0F, 0.9F + rng.nextFloat() * 0.2F);
        level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.9F, 1.05F);
        level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.3, this.getZ(), 18, 0.45, 0.2, 0.45, 0.08);
    }

    private static ItemStack rollFlotsamCargo(RandomSource rng) {
        int roll = rng.nextInt(100);
        if (roll < 10) return new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get());
        if (roll < 18) return new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get());
        if (roll < 26) return new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get());
        if (roll < 34) return new ItemStack(CurrentsofTrade.JADE_FIGURINE.get());
        if (roll < 42) return new ItemStack(CurrentsofTrade.ERMINE_PELT.get());
        if (roll < 50) return new ItemStack(CurrentsofTrade.VELVET_BOLT.get());
        if (roll < 58) return new ItemStack(CurrentsofTrade.INDIGO_DYE_CAKE.get(), 1 + rng.nextInt(2));
        if (roll < 66) return new ItemStack(CurrentsofTrade.CINNAMON_QUILLS.get(), 1 + rng.nextInt(2));
        if (roll < 74) return new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 1 + rng.nextInt(3));
        if (roll < 82) return new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 1 + rng.nextInt(2));
        if (roll < 88) return new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 1 + rng.nextInt(2));
        if (roll < 94) return new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1 + rng.nextInt(2));
        return new ItemStack(Items.NAUTILUS_SHELL);
    }

    /**
     * Spawns lightweight drifting flotsam strictly in OCEAN biomes (never rivers)
     * at the outer horizon of a player's view distance (works both at sea and from coastal shores).
     */
    public static void tickOceanFlotsamSpawner(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 900L != 450L) return;
        var players = level.players();
        if (players.isEmpty()) return;

        RandomSource rng = level.getRandom();
        ServerPlayer target = players.get(rng.nextInt(players.size()));
        if (target.isSpectator()) return;

        AABB localBox = target.getBoundingBox().inflate(150.0D);
        if (level.getEntitiesOfClass(DriftingFlotsamEntity.class, localBox).size() >= 3) {
            return;
        }

        int viewChunks = level.getServer() != null ? level.getServer().getPlayerList().getViewDistance() : 8;
        double minDist = Math.max(40.0D, (viewChunks - 2.5D) * 16.0D);
        double maxDist = Math.min(128.0D, Math.max(minDist + 14.0D, (viewChunks - 1.1D) * 16.0D));

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + rng.nextDouble() * (maxDist - minDist);
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * dist);
            BlockPos col = new BlockPos(x, target.getBlockY(), z);
            if (!level.isLoaded(col)) continue;

            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos pos = new BlockPos(x, surfaceY, z);
            BlockPos waterPos = pos.below();
            if (!level.getBiome(pos).is(BiomeTags.IS_OCEAN)) continue;
            if (!level.getBlockState(pos).isAir() || !level.canSeeSky(pos)) continue;
            if (!level.getFluidState(waterPos).is(FluidTags.WATER)) continue;
            if (!level.getFluidState(waterPos.below()).is(FluidTags.WATER)) continue;
            if (!level.getFluidState(waterPos.north()).is(FluidTags.WATER)
                    || !level.getFluidState(waterPos.south()).is(FluidTags.WATER)
                    || !level.getFluidState(waterPos.east()).is(FluidTags.WATER)
                    || !level.getFluidState(waterPos.west()).is(FluidTags.WATER)) continue;

            DriftingFlotsamEntity flotsam = CurrentsofTrade.DRIFTING_FLOTSAM.get().create(level);
            if (flotsam != null) {
                flotsam.moveTo(x + 0.5D, waterPos.getY() + 0.58D, z + 0.5D, rng.nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(flotsam);
                return;
            }
        }
    }
}
