package com.lexovian.currentsoftrade.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Universal multi-part sub-entity representing the walkable wooden deck & hull sections of large ships.
 * Covers the elevated aft Quarterdeck (+1.18m) and Main/Foredeck (+0.82m) seamlessly from stern to bow,
 * carries entities smoothly across waves/turns, and forwards interactions to the parent vessel.
 */
public class ShipPart<T extends Entity> extends PartEntity<T> {

    /** Spine offsets (blocks forward of ship origin; negative = aft toward stern, positive = forward toward bow). */
    public static final double[] DECK_PART_OFFSETS = { -2.05D, -0.95D, 0.55D, 2.10D, 3.35D };

    private final EntityDimensions dimensions;

    public ShipPart(T parent, float width, float height) {
        super(parent);
        this.dimensions = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
        this.setPos(parent.getX(), parent.getY(), parent.getZ());
        this.xo = parent.getX();
        this.yo = parent.getY();
        this.zo = parent.getZ();
        this.xOld = parent.getX();
        this.yOld = parent.getY();
        this.zOld = parent.getZ();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> ShipPart<T>[] createShipDeckParts(T parent) {
        return new ShipPart[]{
                new ShipPart<>(parent, 2.30F, 1.18F), // 0: Aft Quarterdeck & Stern Helm
                new ShipPart<>(parent, 2.35F, 1.18F), // 1: Forward Quarterdeck & Stairs
                new ShipPart<>(parent, 2.45F, 0.82F), // 2: Main Midship Waist Deck
                new ShipPart<>(parent, 2.20F, 0.82F), // 3: Foredeck
                new ShipPart<>(parent, 1.45F, 0.82F)  // 4: Tapered Bow Stem
        };
    }

    public static void updateShipDeckParts(Entity parent, ShipPart<?>[] parts) {
        double yawRad = Math.toRadians(parent.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        int count = Math.min(parts.length, DECK_PART_OFFSETS.length);
        for (int i = 0; i < count; i++) {
            double forward = DECK_PART_OFFSETS[i];
            double px = parent.getX() - forward * sin;
            double pz = parent.getZ() + forward * cos;
            parts[i].moveTo(px, parent.getY(), pz);
        }
    }

    /**
     * Keeps players, crew, pets, and items standing cleanly on the wooden deck during wave bobbing,
     * sailing movement, and helm turns without ever clipping through the hull.
     */
    public static void supportDeckEntities(Entity ship, ShipPart<?>[] parts,
                                           double prevX, double prevY, double prevZ, float prevYaw) {
        if (ship.isRemoved()) return;

        AABB searchBox = new AABB(
                ship.getX() - 4.6D, ship.getY() + 0.30D, ship.getZ() - 4.6D,
                ship.getX() + 4.6D, ship.getY() + 3.40D, ship.getZ() + 4.6D
        );
        List<Entity> candidates = ship.level().getEntities(ship, searchBox,
                e -> !(e instanceof ShipPart<?>)
                        && !(e instanceof Boat)
                        && !e.noPhysics
                        && !ship.isPassengerOfSameVehicle(e));
        if (candidates.isEmpty()) return;

        double shipDx = ship.getX() - prevX;
        double shipDy = ship.getY() - prevY;
        double shipDz = ship.getZ() - prevZ;
        float yawDeltaDeg = Mth.wrapDegrees(ship.getYRot() - prevYaw);
        double yawDeltaRad = Math.toRadians(yawDeltaDeg);
        boolean movedHoriz = (shipDx * shipDx + shipDz * shipDz) > 1.0E-7D || Math.abs(yawDeltaDeg) > 0.001F;
        double cosD = Math.cos(yawDeltaRad);
        double sinD = Math.sin(yawDeltaRad);

        for (Entity entity : candidates) {
            AABB eBox = entity.getBoundingBox();
            double curDeckY = getHighestDeckY(ship, parts, eBox, 0.0D, 0.0D);
            double preDeckY = movedHoriz ? getHighestDeckY(ship, parts, eBox, shipDx, shipDz) : curDeckY;
            double refDeckY = Math.max(curDeckY, preDeckY);
            if (refDeckY < -1.0E7D) continue;

            double minY = eBox.minY;
            double deckFloorTolerance = refDeckY - Math.max(0.0D, shipDy) - 0.46D;
            if (minY < deckFloorTolerance || minY > refDeckY + 0.22D) continue;

            // 1. Carry standing entities horizontally & rotationally with the moving deck
            if (movedHoriz) {
                boolean shouldCarryHoriz = ship.level().isClientSide
                        ? (entity instanceof Player player && player.isLocalPlayer())
                        : !(entity instanceof Player);
                if (shouldCarryHoriz) {
                    double rotX = 0.0D;
                    double rotZ = 0.0D;
                    if (Math.abs(yawDeltaRad) > 1.0E-5D) {
                        double relX = entity.getX() - prevX;
                        double relZ = entity.getZ() - prevZ;
                        rotX = (relX * cosD - relZ * sinD) - relX;
                        rotZ = (relX * sinD + relZ * cosD) - relZ;
                    }
                    entity.move(MoverType.SELF, new Vec3(shipDx + rotX, 0.0D, shipDz + rotZ));
                }
            }

            // 2. Shulker-style vertical deck anti-clip & wave-trough glue
            AABB postBox = entity.getBoundingBox();
            double finalDeckY = getHighestDeckY(ship, parts, postBox, 0.0D, 0.0D);
            if (finalDeckY < -1.0E7D) continue;

            double newMinY = postBox.minY;
            if (newMinY >= finalDeckY - 0.48D && newMinY < finalDeckY) {
                double lift = finalDeckY - newMinY;
                AABB liftedBox = postBox.move(0.0D, lift, 0.0D);
                if (!ship.level().getBlockCollisions(entity, liftedBox).iterator().hasNext()) {
                    entity.setPos(entity.getX(), entity.getY() + lift, entity.getZ());
                    Vec3 vel = entity.getDeltaMovement();
                    if (vel.y < 0.0D) {
                        entity.setDeltaMovement(vel.x, 0.0D, vel.z);
                    }
                    entity.setOnGround(true);
                    entity.fallDistance = 0.0F;
                }
            } else if (shipDy < -0.002D && shipDy > -0.35D
                    && newMinY > finalDeckY && newMinY <= finalDeckY - shipDy + 0.06D
                    && entity.getDeltaMovement().y <= 0.02D) {
                entity.setPos(entity.getX(), finalDeckY, entity.getZ());
                entity.setOnGround(true);
                entity.fallDistance = 0.0F;
            }
        }
    }

    private static double getHighestDeckY(Entity ship, ShipPart<?>[] parts, AABB entityBox, double shiftX, double shiftZ) {
        double minX = entityBox.minX + 0.04D + shiftX;
        double maxX = entityBox.maxX - 0.04D + shiftX;
        double minZ = entityBox.minZ + 0.04D + shiftZ;
        double maxZ = entityBox.maxZ - 0.04D + shiftZ;
        if (minX >= maxX) {
            double cx = (entityBox.minX + entityBox.maxX) * 0.5D + shiftX;
            minX = cx - 0.05D;
            maxX = cx + 0.05D;
        }
        if (minZ >= maxZ) {
            double cz = (entityBox.minZ + entityBox.maxZ) * 0.5D + shiftZ;
            minZ = cz - 0.05D;
            maxZ = cz + 0.05D;
        }

        double highest = Double.NEGATIVE_INFINITY;
        AABB center = ship.getBoundingBox();
        if (maxX > center.minX && minX < center.maxX && maxZ > center.minZ && minZ < center.maxZ) {
            highest = Math.max(highest, center.maxY);
        }
        for (ShipPart<?> part : parts) {
            AABB pb = part.getBoundingBox();
            if (maxX > pb.minX && minX < pb.maxX && maxZ > pb.minZ && minZ < pb.maxZ) {
                highest = Math.max(highest, pb.maxY);
            }
        }
        return highest;
    }

    @Nullable
    public static Vec3 findDeckDismountLocation(Entity ship, LivingEntity passenger) {
        float yawRad = (float) Math.toRadians(ship.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double preferredForward = -(passenger.getX() - ship.getX()) * sin + (passenger.getZ() - ship.getZ()) * cos;

        double[][] deckCandidates;
        if (preferredForward < -0.5D) {
            deckCandidates = new double[][]{
                    { -1.85D, 0.0D, 1.19D },
                    { -1.15D, 0.0D, 1.19D },
                    {  0.55D, 0.0D, 0.83D },
                    {  1.95D, 0.0D, 0.83D }
            };
        } else if (preferredForward < 1.4D) {
            deckCandidates = new double[][]{
                    {  0.60D, 0.0D, 0.83D },
                    {  1.95D, 0.0D, 0.83D },
                    { -1.85D, 0.0D, 1.19D }
            };
        } else {
            deckCandidates = new double[][]{
                    {  2.05D, 0.0D, 0.83D },
                    {  0.60D, 0.0D, 0.83D },
                    { -1.85D, 0.0D, 1.19D }
            };
        }

        EntityDimensions standingDims = passenger.getDimensions(Pose.STANDING);
        for (double[] spot : deckCandidates) {
            double wx = ship.getX() - spot[0] * sin + spot[1] * cos;
            double wz = ship.getZ() + spot[0] * cos + spot[1] * sin;
            double wy = ship.getY() + spot[2];
            AABB box = standingDims.makeBoundingBox(wx, wy, wz);
            if (!ship.level().getBlockCollisions(passenger, box).iterator().hasNext()
                    && !ship.level().containsAnyLiquid(box)) {
                return new Vec3(wx, wy, wz);
            }
        }

        double[][] sideOffsets = new double[][]{
                { cos * 2.3D, sin * 2.3D },
                { -cos * 2.3D, -sin * 2.3D },
                { 3.5D * sin, -3.5D * cos },
                { -4.2D * sin, 4.2D * cos }
        };
        for (double[] offset : sideOffsets) {
            double testX = ship.getX() + offset[0];
            double testZ = ship.getZ() + offset[1];
            double testY = ship.getY() + 0.3D;
            BlockPos testPos = BlockPos.containing(testX, testY, testZ);
            if (ship.level().getFluidState(testPos).is(FluidTags.WATER)
                    || ship.level().getBlockState(testPos).isAir()) {
                return new Vec3(testX, testY, testZ);
            }
        }
        return null;
    }

    public void moveTo(double x, double y, double z) {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();
        this.setPos(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.dimensions;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return entity != this.getParent() && !this.getParent().isPassengerOfSameVehicle(entity);
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (com.lexovian.currentsoftrade.item.NauticalChartItem.tryUseAboard(player, hand, this.getParent())) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return this.getParent().interact(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.getParent().hurt(source, amount);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
