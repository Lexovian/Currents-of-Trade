package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.entity.SloopEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SloopItem extends Item {

    public SloopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        HitResult hitresult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);

        if (hitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        }
        if (hitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        }

        Vec3 clickLoc = hitresult.getLocation();

        // Block other entities from occupying the exact click point
        List<Entity> nearby = level.getEntities(player,
                player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(5.0)).inflate(1.0),
                EntitySelector.NO_SPECTATORS.and(Entity::isPickable));
        for (Entity entity : nearby) {
            AABB aabb = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (aabb.contains(player.getEyePosition())) {
                return InteractionResultHolder.pass(itemstack);
            }
        }

        if (!level.isClientSide) {
            // Require at least one water block at or just below the click position
            BlockPos clickedBlock = ((BlockHitResult) hitresult).getBlockPos();
            boolean waterNearby = level.getFluidState(clickedBlock).is(FluidTags.WATER)
                    || level.getFluidState(clickedBlock.below()).is(FluidTags.WATER)
                    || level.getFluidState(clickedBlock.above()).is(FluidTags.WATER);

            if (!waterNearby) {
                player.displayClientMessage(
                        Component.translatable("message.currents_of_trade.sloop_needs_water"), true);
                return InteractionResultHolder.fail(itemstack);
            }

            // Spawn the sloop at the water surface (offset up slightly so it floats cleanly)
            SloopEntity sloop = new SloopEntity(level, clickLoc.x, clickLoc.y + 0.1, clickLoc.z);
            sloop.setYRot(player.getYRot());
            // Note: we intentionally skip the noCollision check here -- vanilla boats do the same.
            // The large visual hitbox (4.5 wide) would otherwise block placement near any shoreline.

            level.addFreshEntity(sloop);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, clickLoc);
            level.playSound(null, sloop.getX(), sloop.getY(), sloop.getZ(),
                    SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.0F, 1.0F);

            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.currents_of_trade.sloop.desc"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
