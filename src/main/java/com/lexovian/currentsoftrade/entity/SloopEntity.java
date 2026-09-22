package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Player-drivable sailing ship with custom 3D model and quarterdeck helm seating.
 */
public class SloopEntity extends Boat {

    // --- Multi-Part Hull Hitboxes ---
    public final ShipPart<SloopEntity> sternPart;
    public final ShipPart<SloopEntity> prowPart;
    private final ShipPart<?>[] subEntities;

    public SloopEntity(EntityType<? extends Boat> entityType, Level level) {
        super(entityType, level);
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
    }

    public SloopEntity(Level level, double x, double y, double z) {
        super(CurrentsofTrade.SLOOP.get(), level);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.sternPart = new ShipPart<>(this, 2.2F, 0.85F);
        this.prowPart = new ShipPart<>(this, 2.0F, 0.85F);
        this.subEntities = new ShipPart<?>[]{ this.sternPart, this.prowPart };
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.subEntities.length; ++i) {
            this.subEntities[i].setId(id + i + 1);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.subEntities;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        for (var part : this.subEntities) {
            part.remove(reason);
        }
    }

    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        // Compact 2.4m x 2.4m central waist bounding box (around main deck & mast)
        // Perfectly fits within the 2.7m hull beam at ANY angle (even 45-degree diagonal!)
        double half = 1.20;
        return new net.minecraft.world.phys.AABB(
                this.getX() - half, this.getY(), this.getZ() - half,
                this.getX() + half, this.getY() + 0.85, this.getZ() + half
        );
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        // Broad culling box matching the grand 2.4x scale model (16m mast height, 14m length, 10m beam)
        return new net.minecraft.world.phys.AABB(
                this.getX() - 10.0, this.getY() - 3.0, this.getZ() - 10.0,
                this.getX() + 10.0, this.getY() + 20.0, this.getZ() + 10.0
        );
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_MERCHANT_SHIP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SloopEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public boolean isMerchantShip() {
        return this.entityData.get(IS_MERCHANT_SHIP);
    }

    public void setMerchantShip(boolean merchantShip) {
        this.entityData.set(IS_MERCHANT_SHIP, merchantShip);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_MERCHANT_SHIP, false);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsMerchantShip", this.isMerchantShip());
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("IsMerchantShip")) {
            this.setMerchantShip(tag.getBoolean("IsMerchantShip"));
        }
    }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        // If this is the Wandering Trader's ship, players cannot board it.
        // Instead, right-clicking opens the trader's trading screen if sailor is present!
        if (this.isMerchantShip()) {
            for (Entity p : this.getPassengers()) {
                if (p instanceof WanderingSailorEntity sailor) {
                    return sailor.mobInteract(player, hand);
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        }
        return super.interact(player, hand);
    }

    @Override
    public float maxUpStep() {
        return 0.0F;
    }

    @Override
    public Item getDropItem() {
        // Merchant ship drops nothing when broken or destroyed!
        return this.isMerchantShip() ? Items.AIR : CurrentsofTrade.SLOOP_ITEM.get();
    }

    @Override
    public void destroy(Item dropItem) {
        if (this.isMerchantShip()) {
            // Eject passengers and silently discard without dropping any item
            this.ejectPassengers();
            this.discard();
            return;
        }
        super.destroy(dropItem);
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("entity.currents_of_trade.sloop");
    }

    @Override
    public Component getName() {
        Component customName = this.getCustomName();
        return customName != null ? customName : this.getTypeName();
    }

    @Override
    public void tick() {
        float prevYaw = this.getYRot();
        super.tick();

        // 1. Significantly reduce steering rotation speed (A, D keys):
        // Replaces quick dinghy snap-turning with a majestic, heavy sailing ship feel (~86% damping).
        float rawTurnDelta = Mth.wrapDegrees(this.getYRot() - prevYaw);
        if (Math.abs(rawTurnDelta) > 0.001F) {
            float dampedTurn = rawTurnDelta * 0.14F;
            this.setYRot(prevYaw + dampedTurn);
            this.yRotO = prevYaw;
        }

        // 2. Synchronize multi-part hitboxes along ship's spine (Stern & Prow):
        // Enables walking on the entire ship deck (helm to bow) WITHOUT sticking out into water when diagonal!
        double yawRad = Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // Stern Part (-1.90m aft under helm & quarterdeck)
        double sternX = this.getX() + 1.90 * sin;
        double sternZ = this.getZ() - 1.90 * cos;
        this.sternPart.moveTo(sternX, this.getY(), sternZ);

        // Prow Part (+2.10m forward under foredeck & bow)
        double prowX = this.getX() - 2.10 * sin;
        double prowZ = this.getZ() + 2.10 * cos;
        this.prowPart.moveTo(prowX, this.getY(), prowZ);

        // 3. Anti-sinking buoyancy protection:
        // Guarantees the ship always floats gracefully at the water surface and can never sink!
        BlockPos pos = this.blockPosition();
        FluidState fluid = this.level().getFluidState(pos);
        if (fluid.is(FluidTags.WATER) || this.level().getFluidState(pos.below()).is(FluidTags.WATER)) {
            double waterSurfaceY = pos.getY() + (fluid.is(FluidTags.WATER) ? fluid.getHeight(this.level(), pos) : 0.0);
            if (!fluid.is(FluidTags.WATER)) {
                FluidState belowFluid = this.level().getFluidState(pos.below());
                waterSurfaceY = pos.below().getY() + belowFluid.getHeight(this.level(), pos.below());
            }

            double targetY = waterSurfaceY - 0.45;
            if (this.getY() < targetY) {
                Vec3 motion = this.getDeltaMovement();
                double upwardCorrection = Math.min(0.25, (targetY - this.getY()) * 0.6);
                this.setDeltaMovement(motion.x, Math.max(motion.y, upwardCorrection), motion.z);
                this.setOnGround(false);
            }
        }

        // 4. Storm dynamic sea spray & wave effects:
        if (this.level().isClientSide && this.level().isThundering() && this.getDeltaMovement().horizontalDistanceSqr() > 0.005) {
            double sprayX = prowX + (this.random.nextDouble() - 0.5) * 1.2;
            double sprayZ = prowZ + (this.random.nextDouble() - 0.5) * 1.2;
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, sprayX, this.getY() + 0.3, sprayZ, 0.0, 0.15, 0.0);
            if (this.random.nextFloat() < 0.3F) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE, sprayX, this.getY() + 0.1, sprayZ, 0.0, 0.05, 0.0);
            }
        }

        // Keep center bounding box synchronized
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    public void push(Entity entity) {
        // Prevent swimming mobs, monsters, and animals from automatically climbing aboard on collision
        if (!this.isPassengerOfSameVehicle(entity) && !entity.noPhysics && !this.noPhysics) {
            double d0 = entity.getX() - this.getX();
            double d1 = entity.getZ() - this.getZ();
            double d2 = Mth.absMax(d0, d1);
            if (d2 >= 0.01F) {
                d2 = Math.sqrt(d2);
                d0 /= d2;
                d1 /= d2;
                double d3 = Math.min(1.0, 1.0 / d2);
                d0 *= d3 * 0.05F;
                d1 *= d3 * 0.05F;
                if (!entity.isVehicle() && entity.isPushable()) {
                    entity.push(d0, 0.0, d1);
                }
            }
        }
    }

    @Override
    protected int getMaxPassengers() {
        return 3;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        // If this is a Merchant Sloop, nobody (players, animals, mobs) can board it except the trader itself!
        if (this.isMerchantShip()) {
            return passenger instanceof WanderingSailorEntity && this.getPassengers().isEmpty();
        }

        if (this.getPassengers().size() >= this.getMaxPassengers() || this.canBoatInFluid(this.getEyeInFluidType())) {
            return false;
        }
        // Captain seat (Station 1) is strictly reserved for players (or the WanderingSailor trader).
        // Animals and regular mobs cannot board an empty ship (captain seat), and cannot exceed 2 passenger seats.
        if (!(passenger instanceof Player) && !(passenger instanceof WanderingSailorEntity)) {
            if (this.getPassengers().isEmpty()) {
                return false;
            }
            int nonPlayerCount = 0;
            for (Entity p : this.getPassengers()) {
                if (!(p instanceof Player)) {
                    nonPlayerCount++;
                }
            }
            if (nonPlayerCount >= 2) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // Only a Player can ever be the captain and steer the ship
        for (Entity p : this.getPassengers()) {
            if (p instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!this.hasPassenger(passenger)) return;

        float forwardOffset;
        float verticalOffset;

        boolean isCaptain = (passenger instanceof Player || passenger instanceof WanderingSailorEntity) && this.getPassengers().indexOf(passenger) == 0;
        if (isCaptain) {
            // Station 1: Captain at the helm on elevated quarterdeck (players or Wandering Sailor)
            forwardOffset = -2.00F;
            verticalOffset = 0.98F;
        } else {
            // Station 2 & 3: Middle (waist) and Bow (foredeck) seats for other players or animals/mobs
            int nonCaptainIndex = 0;
            for (Entity p : this.getPassengers()) {
                if (p == passenger) break;
                if (!((p instanceof Player || p instanceof WanderingSailorEntity) && this.getPassengers().indexOf(p) == 0)) {
                    nonCaptainIndex++;
                }
            }

            if (nonCaptainIndex == 0) {
                // Station 2: Middle waist bench (between sail/mast and quarterdeck)
                forwardOffset = 0.65F;
                verticalOffset = 0.70F;
            } else {
                // Station 3: Foredeck bow bench (between bow and sail)
                forwardOffset = 2.20F;
                verticalOffset = 0.72F;
            }
        }

        float yawRad = (float) Math.toRadians(this.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double worldX = -forwardOffset * sin;
        double worldZ =  forwardOffset * cos;

        callback.accept(passenger, this.getX() + worldX, this.getY() + verticalOffset, this.getZ() + worldZ);
        this.clampRotation(passenger);

        if (passenger instanceof net.minecraft.world.entity.animal.Animal) {
            int i = passenger.getId() % 2 == 0 ? 90 : 270;
            passenger.setYBodyRot(((net.minecraft.world.entity.animal.Animal) passenger).yBodyRot + (float) i);
            passenger.setYHeadRot(passenger.getYHeadRot() + (float) i);
        }
    }
}
