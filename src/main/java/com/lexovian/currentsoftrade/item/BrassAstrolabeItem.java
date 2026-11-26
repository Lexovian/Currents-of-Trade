package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.harbor.HarborSavedData;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Brass Astrolabe — active celestial navigation instrument.
 * Right-click to read coordinates, compass heading, celestial time, current Maritime Trade Region,
 * and bearing/distance to the nearest registered harbor.
 */
public class BrassAstrolabeItem extends Item {

    private static final String[] CARDINALS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public BrassAstrolabeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockPos pos = player.blockPosition();
            MaritimeTradeRegion region = MaritimeTradeRegion.fromLevelAndPos(serverLevel, pos);
            String heading = yawToCardinal(player.getYRot());

            long dayTime = (serverLevel.getGameTime() + 6000L) % 24000L;
            int hours = (int) (dayTime / 1000L);
            int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
            String timeStr = String.format("%02d:%02d", hours, minutes);

            player.sendSystemMessage(Component.translatable("message.currents_of_trade.astrolabe_reading",
                    pos.getX(), pos.getZ(), heading, timeStr, region.getDisplayName()));
            String windDir = com.lexovian.currentsoftrade.world.market.TradeWinds.getWindCardinal(serverLevel);
            var repData = com.lexovian.currentsoftrade.world.harbor.GuildReputationData.get(serverLevel);
            int repPts = repData.getReputation(player.getUUID());
            var rank = repData.getRank(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.currents_of_trade.astrolabe_wind_rank",
                    windDir, rank.getDisplayName(), repPts));

            HarborSavedData data = HarborSavedData.get(serverLevel);
            BlockPos nearestPos = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (BlockPos hp : data.getAllHarborPositions()) {
                double d = hp.distSqr(pos);
                if (d < nearestDistSq) {
                    nearestDistSq = d;
                    nearestPos = hp;
                }
            }

            if (nearestPos != null) {
                int dist = (int) Math.round(Math.sqrt(nearestDistSq));
                String bearing = deltaToCardinal(nearestPos.getX() - pos.getX(), nearestPos.getZ() - pos.getZ());
                String hName = data.getHarborName(nearestPos);
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.astrolabe_nearest_harbor",
                        hName, dist, bearing));
            } else {
                player.sendOverlayMessage(Component.translatable("message.currents_of_trade.astrolabe_no_harbors"));
            }

            serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.25F);
            serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getEyeY() + 0.2, player.getZ(),
                    10, 0.35, 0.25, 0.35, 0.02);
            player.getCooldowns().addCooldown(stack, 80);

            if (player instanceof ServerPlayer sp && serverLevel.getServer() != null) {
                var adv = serverLevel.getServer().getAdvancements().get(
                        Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "star_navigator"));
                if (adv != null) {
                    for (String c : adv.value().criteria().keySet()) {
                        sp.getAdvancements().award(adv, c);
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static String yawToCardinal(float yaw) {
        int idx = Math.floorMod(Math.round(Mth.wrapDegrees(yaw) / 45.0F), 8);
        return CARDINALS[idx];
    }

    private static String deltaToCardinal(double dx, double dz) {
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        return yawToCardinal(yaw);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag tooltipFlag) {
        adder.accept(Component.translatable("tooltip.currents_of_trade.brass_astrolabe_desc"));
        adder.accept(Component.translatable("tooltip.currents_of_trade.brass_astrolabe_hint"));
        super.appendHoverText(stack, context, display, adder, tooltipFlag);
    }
}
