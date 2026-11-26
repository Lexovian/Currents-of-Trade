package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Brass Swivel Harpoon — bow-mounted shipwright defensive turret upgrade.
 *
 * <p>Right-click a Sloop, Cargo Boat, or Trade Ship to mount on the foredeck (remove with Shears).
 * Automatically fires brass harpoon bolts at nearby Drowned, Guardians, and aquatic monsters,
 * while gently clearing obstructing squids away from the ship's bow.
 */
public class SwivelHarpoonItem extends Item {

    public static final double HARPOON_RANGE = 14.0D;
    public static final float HARPOON_DAMAGE = 10.0F;
    public static final int FIRE_INTERVAL_TICKS = 45;

    public static final TagKey<EntityType<?>> HARPOON_TARGETS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(CurrentsofTrade.MODID, "harpoon_targets"));
    public static final TagKey<EntityType<?>> HARPOON_IGNORED =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(CurrentsofTrade.MODID, "harpoon_ignored"));

    public SwivelHarpoonItem(Properties properties) {
        super(properties);
    }

    /**
     * Runs server-side bow defense for any ship equipped with a Brass Swivel Harpoon.
     */
    public static void tickShipHarpoonDefense(Boat ship) {
        if (ship == null || ship.level().isClientSide || !ship.isAlive()) return;
        if (!(ship.level() instanceof ServerLevel serverLevel)) return;

        // 1. Every 10 ticks: gently clear peaceful squids/water animals directly blocking the bow path (non-lethal)
        if (ship.tickCount % 10 == 0) {
            float yawRad = ship.getYRot() * Mth.DEG_TO_RAD;
            double bowX = ship.getX() - Mth.sin(yawRad) * 2.8D;
            double bowZ = ship.getZ() + Mth.cos(yawRad) * 2.8D;
            AABB bowClearBox = new AABB(bowX - 2.6, ship.getY() - 1.5, bowZ - 2.6, bowX + 2.6, ship.getY() + 1.5, bowZ + 2.6);
            List<WaterAnimal> squids = serverLevel.getEntitiesOfClass(WaterAnimal.class, bowClearBox,
                    a -> a.isAlive() && !a.getType().is(HARPOON_TARGETS));
            for (WaterAnimal animal : squids) {
                Vec3 away = animal.position().subtract(ship.position());
                Vec3 horiz = new Vec3(away.x, 0.0, away.z);
                if (horiz.lengthSqr() < 1.0E-4) {
                    horiz = new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
                }
                horiz = horiz.normalize().scale(0.65D);
                animal.setDeltaMovement(animal.getDeltaMovement().add(horiz.x, -0.08D, horiz.z));
                animal.hasImpulse = true;
            }
        }

        // 2. Every FIRE_INTERVAL_TICKS: target nearest tagged sea monster or aquatic hostile within HARPOON_RANGE
        if (ship.tickCount % FIRE_INTERVAL_TICKS != 0) return;

        AABB searchBox = ship.getBoundingBox().inflate(HARPOON_RANGE, 7.0D, HARPOON_RANGE);
        List<LivingEntity> threats = serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox, e ->
                e.isAlive()
                        && !e.isSpectator()
                        && !(e instanceof Player)
                        && !ship.hasPassenger(e)
                        && !e.getType().is(HARPOON_IGNORED)
                        && (e.getType().is(HARPOON_TARGETS) || (e instanceof Monster && e.isInWaterOrBubble()))
        );
        if (threats.isEmpty()) return;

        // Compute bow turret muzzle position in world space
        float yawRad = ship.getYRot() * Mth.DEG_TO_RAD;
        double muzzleX = ship.getX() - Mth.sin(yawRad) * 3.1D;
        double muzzleY = ship.getY() + 1.35D;
        double muzzleZ = ship.getZ() + Mth.cos(yawRad) * 3.1D;
        Vec3 muzzlePos = new Vec3(muzzleX, muzzleY, muzzleZ);

        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (LivingEntity m : threats) {
            double d = m.distanceToSqr(ship);
            if (d < nearestDistSq && serverLevel.clip(new ClipContext(
                    muzzlePos, m.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ship
            )).getType() == HitResult.Type.MISS) {
                nearestDistSq = d;
                nearest = m;
            }
        }
        if (nearest == null) return;

        double targetX = nearest.getX();
        double targetY = nearest.getY(0.6D);
        double targetZ = nearest.getZ();

        // Damage & strong knockback away from hull
        Player captain = (ship.getControllingPassenger() instanceof Player p) ? p : null;
        var dmgSource = captain != null
                ? serverLevel.damageSources().playerAttack(captain)
                : serverLevel.damageSources().trident(ship, ship);

        nearest.hurt(dmgSource, HARPOON_DAMAGE);
        if (captain instanceof ServerPlayer sp && sp.getServer() != null) {
            var adv = sp.getServer().getAdvancements().getAdvancement(
                    new ResourceLocation(CurrentsofTrade.MODID, "monster_hunter_of_the_deep"));
            if (adv != null) {
                for (String c : adv.getCriteria().keySet()) {
                    sp.getAdvancements().award(adv, c);
                }
            }
        }
        Vec3 knockDir = new Vec3(targetX - ship.getX(), 0.2D, targetZ - ship.getZ()).normalize().scale(1.15D);
        nearest.setDeltaMovement(nearest.getDeltaMovement().add(knockDir));
        nearest.hasImpulse = true;

        // Crisp harpoon bolt particle trail & audio feedback
        serverLevel.playSound(null, muzzleX, muzzleY, muzzleZ, CurrentsofTrade.HARPOON_FIRE_SOUND.get(), SoundSource.PLAYERS, 0.95F, 0.80F);
        serverLevel.playSound(null, targetX, targetY, targetZ, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.85F, 1.1F);

        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double px = Mth.lerp(t, muzzleX, targetX);
            double py = Mth.lerp(t, muzzleY, targetY);
            double pz = Mth.lerp(t, muzzleZ, targetZ);
            serverLevel.sendParticles(ParticleTypes.CRIT, px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE, px, py, pz, 1, 0.04, 0.04, 0.04, 0.02);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.swivel_harpoon.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.swivel_harpoon.mount"));
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
    }
}
