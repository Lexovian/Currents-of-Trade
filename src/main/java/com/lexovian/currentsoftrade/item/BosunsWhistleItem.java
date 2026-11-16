package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Bosun's Brass Whistle (Boatswain's Call).
 *
 * <ul>
 *   <li><b>Right-Click:</b> Summons the nearest unpiloted Sloop or player-controlled Cargo Boat within 48m toward your shoreline water spot.</li>
 *   <li><b>Sneak + Right-Click:</b> Recalls your active outbound autonomous Cargo/Trade vessels safely back to their home harbor.</li>
 * </ul>
 */
public class BosunsWhistleItem extends Item {

    public static final double SUMMON_RANGE = 48.0D;

    public BosunsWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            if (player.isSecondaryUseActive()) {
                int recalled = recallPlayerAutonomousShips(serverLevel, player);
                if (recalled > 0) {
                    playBosunCall(level, player, 1.35F, 1.05F);
                    player.getCooldowns().addCooldown(stack, 80);
                    player.sendOverlayMessage(
                            Component.translatable("message.currents_of_trade.whistle_recalled", recalled)
                    );
                } else {
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.7F, 0.85F);
                    player.sendOverlayMessage(
                            Component.translatable("message.currents_of_trade.whistle_no_outbound")
                    );
                }
            } else {
                AbstractBoat targetShip = findNearestSummonableShip(serverLevel, player);
                if (targetShip == null) {
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.7F, 0.85F);
                    player.sendOverlayMessage(
                            Component.translatable("message.currents_of_trade.whistle_no_ship")
                    );
                } else {
                    Vec3 waterSpot = findShorelineWaterSpot(serverLevel, player, targetShip.position());
                    if (waterSpot == null) {
                        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.7F, 0.85F);
                        player.sendOverlayMessage(
                                Component.translatable("message.currents_of_trade.anchor_not_near_water")
                        );
                    } else {
                        if (targetShip instanceof SloopEntity sloop) {
                            sloop.summonToward(waterSpot);
                        } else if (targetShip instanceof CargoBoatEntity cargo) {
                            cargo.summonToward(waterSpot);
                        }
                        playBosunCall(level, player, 1.65F, 1.95F);
                        serverLevel.sendParticles(ParticleTypes.SPLASH,
                                targetShip.getX(), targetShip.getY() + 0.5, targetShip.getZ(),
                                18, 0.8, 0.2, 0.8, 0.12);
                        serverLevel.sendParticles(ParticleTypes.END_ROD,
                                targetShip.getX(), targetShip.getY() + 1.4, targetShip.getZ(),
                                6, 0.3, 0.3, 0.3, 0.02);
                        player.getCooldowns().addCooldown(stack, 40);
                        player.sendOverlayMessage(
                                Component.translatable("message.currents_of_trade.whistle_summoned", targetShip.getDisplayName())
                        );
                    }
                }
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    private static void playBosunCall(Level level, Player player, float pitchA, float pitchB) {
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0F, pitchA);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.65F, pitchB);
    }

    @Nullable
    private static AbstractBoat findNearestSummonableShip(ServerLevel level, Player player) {
        AABB box = player.getBoundingBox().inflate(SUMMON_RANGE, 20.0D, SUMMON_RANGE);
        AbstractBoat best = null;
        double bestDistSq = Double.MAX_VALUE;

        List<SloopEntity> sloops = level.getEntitiesOfClass(SloopEntity.class, box,
                s -> s.isAlive() && !s.isMerchantShip() && !s.isVehicle());
        for (SloopEntity s : sloops) {
            double d = s.distanceToSqr(player);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = s;
            }
        }

        List<CargoBoatEntity> cargos = level.getEntitiesOfClass(CargoBoatEntity.class, box,
                c -> c.isAlive() && c.isPlayerControlled() && !c.isVehicle());
        for (CargoBoatEntity c : cargos) {
            double d = c.distanceToSqr(player);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = c;
            }
        }

        return best;
    }

    @Nullable
    private static Vec3 findShorelineWaterSpot(ServerLevel level, Player player, Vec3 shipPos) {
        BlockPos pPos = player.blockPosition();
        BlockPos bestPos = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos check : BlockPos.betweenClosed(pPos.offset(-8, -4, -8), pPos.offset(8, 2, 8))) {
            if (!level.getFluidState(check).is(FluidTags.WATER)) continue;
            var above1 = level.getBlockState(check.above());
            var above2 = level.getBlockState(check.above(2));
            if ((!above1.isAir() && above1.blocksMotion())
                    || !level.getFluidState(check.above()).isEmpty()
                    || (!above2.isAir() && above2.blocksMotion())) {
                continue;
            }

            // Prefer open water with water neighbors so the ship doesn't beach on 1-block puddles
            int waterNeighbors = 0;
            if (level.getFluidState(check.north()).is(FluidTags.WATER)) waterNeighbors++;
            if (level.getFluidState(check.south()).is(FluidTags.WATER)) waterNeighbors++;
            if (level.getFluidState(check.east()).is(FluidTags.WATER)) waterNeighbors++;
            if (level.getFluidState(check.west()).is(FluidTags.WATER)) waterNeighbors++;
            if (waterNeighbors < 2) continue;

            double distPlayer = check.distToCenterSqr(player.position());
            double distShip = check.distToCenterSqr(shipPos);
            double score = distPlayer * 2.5 + distShip * 0.15 - waterNeighbors * 2.0;
            if (distPlayer >= 4.0 && score < bestScore) {
                bestScore = score;
                bestPos = check.immutable();
            }
        }

        if (bestPos != null) {
            return new Vec3(bestPos.getX() + 0.5, bestPos.getY() + 0.85, bestPos.getZ() + 0.5);
        }
        return null;
    }

    private static int recallPlayerAutonomousShips(ServerLevel level, Player player) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof TradeBoatEntity tradeBoat
                    && tradeBoat.isAlive()
                    && !tradeBoat.isVoyageShip()
                    && !tradeBoat.isWanderingTraderShip()
                    && !tradeBoat.isCargoDelivered()
                    && player.getUUID().equals(tradeBoat.getSenderUUID())) {
                if (tradeBoat.recallToHomeHarbor(level)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.bosuns_whistle.summon"));
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.bosuns_whistle.recall"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
