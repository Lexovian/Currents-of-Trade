package com.lexovian.currentsoftrade.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class WanderingSailorEntity extends WanderingTrader {

    // Stay time: min 7 minutes (8,400 ticks), max 15 minutes (18,000 ticks)
    public static final int MIN_DESPAWN_DELAY = 8400;
    public static final int MAX_DESPAWN_DELAY = 18000;
    public static final int DEFAULT_DESPAWN_DELAY = 12000; // ~10 minutes
    private int sailorDespawnDelay = DEFAULT_DESPAWN_DELAY;

    @Nullable
    private java.util.UUID mooredBoatUUID = null;

    public @Nullable java.util.UUID getMooredBoatUUID() {
        return this.mooredBoatUUID;
    }

    public void setMooredBoatUUID(@Nullable java.util.UUID uuid) {
        this.mooredBoatUUID = uuid;
    }

    public WanderingSailorEntity(EntityType<? extends WanderingTrader> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.translatable("entity.currents_of_trade.wandering_sailor"));
        this.setCustomNameVisible(true);
        if (!level.isClientSide) {
            this.sailorDespawnDelay = level.random.nextIntBetweenInclusive(MIN_DESPAWN_DELAY, MAX_DESPAWN_DELAY);
            this.setDespawnDelay(this.sailorDespawnDelay);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D);
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = this.getOffers();
        offers.clear();

        // ── Fixed trades: always shown, every sailor, every visit ────────────────
        // Currency exchange — xp=0 prevents XP-exploit loop, trades still fully functional
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2),                      new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1), 10, 0, 0.0F));
        offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),     new ItemStack(Items.EMERALD, 2),                  10, 0, 0.0F));
        // Small vanilla food provision (secondary role — keeps sailor flavour)
        offers.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),     new ItemStack(Items.COOKED_SALMON, 2),             8,  1, 0.05F));
        // Reverse: sailor buys cooked fish from the player
        offers.add(new MerchantOffer(new ItemCost(Items.COOKED_COD, 8),                   new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1),  8,  3, 0.05F));

        // ── Pool A: Market Cargo — common mod goods + 1 vanilla food option ─────
        // Sailor shows 5 randomly chosen offers from this pool of 10.
        List<MerchantOffer> poolA = new ArrayList<>();
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 1),  new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 2),         8, 2,  0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 2),         6, 5,  0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 2),          6, 5,  0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.VANILLA_BUNDLE.get(), 2),     6, 5,  0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.SANDALWOOD.get(), 1),         5, 8,  0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 3),  new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 1),5, 8,  0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4),  new ItemStack(CurrentsofTrade.FINE_PORCELAIN.get(), 1),     4, 10, 0.05F));
        poolA.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 2),  new ItemStack(CurrentsofTrade.NAUTICAL_CHART.get(), 1),     4, 5,  0.05F));
        // Reverse: sailor buys nautilus shells
        poolA.add(new MerchantOffer(new ItemCost(Items.NAUTILUS_SHELL, 1),            new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3),           4, 10, 0.05F));
        // Small vanilla food option — intentionally placed last so it has lower draw chance
        poolA.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1),                   new ItemStack(Items.COOKED_SALMON, 3),                      6, 1,  0.05F));

        // ── Pool B: Sailor's Rarities — rare & premium mod items ─────────────────
        // Sailor shows 4 randomly chosen offers from this pool of 10.
        List<MerchantOffer> poolB = new ArrayList<>();
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 4),  new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1),         4, 12, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1),    3, 15, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 5),  new ItemStack(CurrentsofTrade.STORM_GLASS.get(), 1),        3, 15, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.BRASS_ASTROLABE.get(), 1),    3, 18, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 6),  new ItemStack(CurrentsofTrade.CAPTAINS_PIPE.get(), 1),      2, 20, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 7),  new ItemStack(CurrentsofTrade.SILK_BALE.get(), 1),          3, 15, 0.05F));
        // Reverse: sailor buys ammonite fossils
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1), new ItemStack(CurrentsofTrade.DOUBLOON.get(), 5),    3, 15, 0.05F));
        // Premium/rare — low maxUses, high value
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 10), Optional.of(new ItemCost(Items.EMERALD, 4)),  new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1),  2, 25, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 26), Optional.of(new ItemCost(Items.EMERALD, 6)),  new ItemStack(CurrentsofTrade.SLOOP_ITEM.get(), 1),       1, 30, 0.05F));
        poolB.add(new MerchantOffer(new ItemCost(CurrentsofTrade.DOUBLOON.get(), 18), Optional.of(new ItemCost(Items.EMERALD, 8)),  new ItemStack(Items.HEART_OF_THE_SEA, 1),                 1, 30, 0.05F));

        // ── Shuffle both pools with a seed derived from this entity's ID ──────────
        // Same sailor → same combo each session; different sailors → different combos.
        java.util.Random rng = new java.util.Random((long) this.getId() * 2654435761L);
        Collections.shuffle(poolA, rng);
        rng.setSeed((long) this.getId() * 2246822519L);
        Collections.shuffle(poolB, rng);

        // Add top 5 from Pool A and top 4 from Pool B
        for (int i = 0; i < Math.min(5, poolA.size()); i++) offers.add(poolA.get(i));
        for (int i = 0; i < Math.min(4, poolB.size()); i++) offers.add(poolB.get(i));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (itemstack.is(Items.NAME_TAG)) {
            itemstack.interactLivingEntity(player, this, hand);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (hand == InteractionHand.MAIN_HAND) {
                this.setTradingPlayer(player);
                this.openTradingScreen(player, this.getDisplayName(), 1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            if (--this.sailorDespawnDelay <= 0) {
                // Despawn gracefully with smoke particles
                if (this.getVehicle() != null) {
                    var vehicle = this.getVehicle();
                    this.stopRiding();
                    vehicle.discard();
                }
                if (this.mooredBoatUUID != null && this.level() instanceof ServerLevel serverLevel) {
                    net.minecraft.world.entity.Entity boat = serverLevel.getEntity(this.mooredBoatUUID);
                    if (boat != null && boat.isAlive()) {
                        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                boat.getX(), boat.getY() + 0.5, boat.getZ(),
                                15, 0.4, 0.4, 0.4, 0.02);
                        boat.discard();
                    }
                }
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            this.getX(), this.getY() + 1.0, this.getZ(),
                            20, 0.5, 0.5, 0.5, 0.02);
                }
                this.discard();
            }
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!this.level().isClientSide && this.mooredBoatUUID != null && this.level() instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity boat = serverLevel.getEntity(this.mooredBoatUUID);
            if (boat != null && boat.isAlive()) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        boat.getX(), boat.getY() + 0.5, boat.getZ(),
                        15, 0.4, 0.4, 0.4, 0.02);
                boat.discard();
            }
        }
    }

    public int getSailorDespawnDelay() {
        return this.sailorDespawnDelay;
    }

    public void setSailorDespawnDelay(int delay) {
        this.sailorDespawnDelay = delay;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SailorDespawnDelay", this.sailorDespawnDelay);
        if (this.mooredBoatUUID != null) {
            tag.putUUID("MooredBoatUUID", this.mooredBoatUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SailorDespawnDelay")) {
            this.sailorDespawnDelay = tag.getInt("SailorDespawnDelay");
        }
        if (tag.hasUUID("MooredBoatUUID")) {
            this.mooredBoatUUID = tag.getUUID("MooredBoatUUID");
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }
}
