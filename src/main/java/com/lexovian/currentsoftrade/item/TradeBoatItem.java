package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

public class TradeBoatItem extends Item {

    public TradeBoatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        HitResult hitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);

        if (hitresult.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }
        if (hitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        Vec3 clickLoc = hitresult.getLocation();
        Vec3 playerPos = player.position();
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 horizView = new Vec3(viewVec.x, 0.0, viewVec.z);
        if (horizView.lengthSqr() < 1e-4) {
            horizView = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        horizView = horizView.normalize();

        double spawnX = clickLoc.x;
        double spawnZ = clickLoc.z;
        double distToPlayer = Math.sqrt((spawnX - playerPos.x) * (spawnX - playerPos.x) + (spawnZ - playerPos.z) * (spawnZ - playerPos.z));
        if (distToPlayer < 3.2) {
            double pushDist = 3.2 - distToPlayer;
            spawnX += horizView.x * pushDist;
            spawnZ += horizView.z * pushDist;
        }

        List<Entity> nearby = level.getEntities(player,
                player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(5.0)).inflate(1.0),
                EntitySelector.CAN_BE_PICKED);
        for (Entity entity : nearby) {
            AABB aabb = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (aabb.contains(player.getEyePosition())) {
                return InteractionResult.PASS;
            }
        }

        if (!level.isClientSide()) {
            BlockPos spawnBlock = BlockPos.containing(spawnX, clickLoc.y, spawnZ);
            boolean waterNearby = level.getFluidState(spawnBlock).is(FluidTags.WATER)
                    || level.getFluidState(spawnBlock.below()).is(FluidTags.WATER)
                    || level.getFluidState(spawnBlock.above()).is(FluidTags.WATER);

            if (!waterNearby) {
                BlockPos clickedBlock = ((BlockHitResult) hitresult).getBlockPos();
                waterNearby = level.getFluidState(clickedBlock).is(FluidTags.WATER)
                        || level.getFluidState(clickedBlock.below()).is(FluidTags.WATER)
                        || level.getFluidState(clickedBlock.above()).is(FluidTags.WATER);
                if (!waterNearby) {
                    player.sendOverlayMessage(Component.translatable("message.currents_of_trade.sloop_needs_water"));
                    return InteractionResult.FAIL;
                }
                spawnX = clickLoc.x;
                spawnZ = clickLoc.z;
            }

            TradeBoatEntity tradeBoat = new TradeBoatEntity(level, spawnX, clickLoc.y + 0.1, spawnZ);
            tradeBoat.setYRot(player.getYRot());
            tradeBoat.setCustomName(Component.literal("Trade Boat"));
            tradeBoat.setCustomNameVisible(true);

            level.addFreshEntity(tradeBoat);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, new Vec3(spawnX, clickLoc.y, spawnZ));
            level.playSound(null, tradeBoat.getX(), tradeBoat.getY(), tradeBoat.getZ(),
                    SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.0F, 1.0F);

            itemstack.consume(1, player);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("item.currents_of_trade.trade_boat.desc"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
