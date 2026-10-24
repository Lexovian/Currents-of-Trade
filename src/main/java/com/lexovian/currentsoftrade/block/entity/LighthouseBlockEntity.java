package com.lexovian.currentsoftrade.block.entity;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lighthouse Lantern Block Entity.
 *
 * <p>Radiates a coastal navigational aura in a 128-block radius.
 * Vessels operating within its beacon beam gain the Tailwind bonus (+25% cruising speed)
 * and safe harbor guidance through ocean tempests.
 */
public class LighthouseBlockEntity extends BlockEntity {

    public static final double BEACON_RADIUS = 128.0;
    private static final Map<UUID, Long> LAST_NOTIFICATION_TIME = new HashMap<>();

    public static void clearCache() {
        LAST_NOTIFICATION_TIME.clear();
    }

    public LighthouseBlockEntity(BlockPos pos, BlockState blockState) {
        super(CurrentsofTrade.LIGHTHOUSE_BE.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LighthouseBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        // Run aura pulse every 10 ticks (0.5s)
        if (level.getGameTime() % 10 != 0) {
            return;
        }

        AABB area = new AABB(pos).inflate(BEACON_RADIUS, 64.0, BEACON_RADIUS);
        List<Entity> vessels = level.getEntities((Entity) null, area, e ->
                e instanceof SloopEntity || e instanceof TradeBoatEntity
        );

        long now = level.getGameTime();

        for (Entity vessel : vessels) {
            if (vessel instanceof SloopEntity sloop) {
                sloop.applyTailwind(30);

                LivingEntity captain = sloop.getControllingPassenger();
                if (captain instanceof ServerPlayer player) {
                    UUID uuid = player.getUUID();
                    long last = LAST_NOTIFICATION_TIME.getOrDefault(uuid, 0L);
                    // Send notification at most once every 20 seconds (400 ticks)
                    if (now - last > 400) {
                        LAST_NOTIFICATION_TIME.put(uuid, now);
                        player.displayClientMessage(
                                Component.translatable("message.currents_of_trade.lighthouse_tailwind"),
                                true
                        );
                        if (level instanceof ServerLevel sl) {
                            sl.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.8F);
                        }
                    }
                }
            } else if (vessel instanceof TradeBoatEntity tradeBoat) {
                tradeBoat.applyTailwind(30);
            }
        }
    }
}
