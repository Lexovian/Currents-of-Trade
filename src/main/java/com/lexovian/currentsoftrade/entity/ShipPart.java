package com.lexovian.currentsoftrade.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Universal multi-part sub-entity representing stern (aft) or prow (bow) sections of large ships.
 * Enables walking on deck, full collision detection, and interaction passthrough to parent ship.
 */
public class ShipPart<T extends Entity> extends PartEntity<T> {
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
        return entity != this.getParent() && !this.getParent().hasPassenger(entity);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
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
