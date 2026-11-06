package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.entity.SloopEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
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

public class SloopItem extends Item {

    public SloopItem(Properties properties) {
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

        // The sloop is ~4.5m long with stern extending ~2.2m behind origin.
        // Ensure spawn center is at least 3.2m away from the player along the view vector
        // so the vessel NEVER overlaps the player's bounding box upon deployment!
        double spawnX = clickLoc.x;
        double spawnZ = clickLoc.z;
        double distToPlayer = Math.sqrt((spawnX - playerPos.x) * (spawnX - playerPos.x) + (spawnZ - playerPos.z) * (spawnZ - playerPos.z));
        if (distToPlayer < 3.2) {
            double pushDist = 3.2 - distToPlayer;
            spawnX += horizView.x * pushDist;
            spawnZ += horizView.z * pushDist;
        }

        // Block other entities from occupying the exact click point
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
                    player.sendOverlayMessage(
                            Component.translatable("message.currents_of_trade.sloop_needs_water"));
                    return InteractionResult.FAIL;
                }
                spawnX = clickLoc.x;
                spawnZ = clickLoc.z;
            }

            // Spawn the sloop at the water surface (offset up slightly so it floats cleanly)
            SloopEntity sloop = new SloopEntity(level, spawnX, clickLoc.y + 0.1, spawnZ);
            sloop.setYRot(player.getYRot());
            sloop.loadUpgradesFromStack(itemstack);

            level.addFreshEntity(sloop);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, new Vec3(spawnX, clickLoc.y, spawnZ));
            level.playSound(null, sloop.getX(), sloop.getY(), sloop.getZ(),
                    SoundEvents.BOAT_PADDLE_WATER, SoundSource.BLOCKS, 1.0F, 1.0F);

            itemstack.consume(1, player);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("item.currents_of_trade.sloop.desc"));
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            int sailColor = tag.getIntOr("SailColor", -1);
            if (sailColor >= 0) {
                DyeColor color = DyeColor.byId(sailColor);
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sloop_sail_color",
                        Component.translatable("color.minecraft." + color.getName())));
            }
            if (tag.contains("BannerItem")) {
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sloop_banner"));
            }
            int lType = tag.getIntOr("BowLanternType", 0);
            if (lType > 0) {
                Component lName = Component.translatable(lType == 2 ? "block.minecraft.soul_lantern" : "block.minecraft.lantern");
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sloop_lantern", lName));
            }
            if (tag.getBooleanOr("CopperHull", false)) {
                tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sloop_copper_hull"));
            }
        }
        tooltipComponents.accept(Component.translatable("tooltip.currents_of_trade.sloop_customize_hint"));
        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }
}
