package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * Salvage Dredge (Deniz Kancası & Tarama Ağı).
 *
 * <p>A weighted iron-and-copper dredge lowered into ocean depths from a boat or pier.
 * Scrapes the seabed to haul up lost ship cargo, sunken relics, coins, and oceanic curiosities.
 */
public class SalvageDredgeItem extends Item {

    public SalvageDredgeItem(Properties properties) {
        super(properties.stacksTo(1).durability(128));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Raytrace for water surface up to 5 blocks away
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        boolean lookingAtWater = hit.getType() == HitResult.Type.BLOCK
                && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
        boolean inWaterOrBoat = player.isInWater() || (player.getVehicle() != null && player.getVehicle().isInWater());

        if (!lookingAtWater && !inWaterOrBoat) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.currents_of_trade.dredge_requires_water"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        BlockPos targetPos = lookingAtWater ? hit.getBlockPos() : player.blockPosition();

        if (!level.isClientSide) {
            RandomSource rng = level.getRandom();
            boolean hasLuck = player.hasEffect(net.minecraft.world.effect.MobEffects.LUCK);
            ItemStack loot = rollSalvageLoot(level, targetPos, rng, hasLuck);

            // Audio & particles
            level.playSound(null, targetPos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.9F, 0.8F);
            level.playSound(null, targetPos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.8F, 1.1F);

            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SPLASH, targetPos.getX() + 0.5, targetPos.getY() + 0.8, targetPos.getZ() + 0.5, 20, 0.3, 0.2, 0.3, 0.1);
                sl.sendParticles(ParticleTypes.BUBBLE_POP, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 12, 0.2, 0.3, 0.2, 0.05);
            }

            int lootCount = loot.getCount();
            String lootBaseName = loot.getHoverName().getString();
            String lootDisplay = lootCount > 1 ? (lootCount + "x " + lootBaseName) : lootBaseName;

            // Award item to player
            if (!player.getInventory().add(loot)) {
                player.drop(loot, false);
            }

            player.displayClientMessage(
                    Component.translatable("message.currents_of_trade.dredge_success", lootDisplay),
                    true
            );

            // Cooldown & durability damage
            player.getCooldowns().addCooldown(this, 50); // 2.5s
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private ItemStack rollSalvageLoot(Level level, BlockPos pos, RandomSource rng, boolean hasLuck) {
        float roll = rng.nextFloat();
        if (hasLuck) {
            // Captain's Pipe / Luck bonus shifts roll toward rare deep-sea treasures and coins
            roll = Math.max(0.0F, roll - 0.08F);
        }

        // 15% Deep Ocean Relics & Rare Treasures (23% with Luck)
        if (roll < 0.15F) {
            int sub = rng.nextInt(8);
            return switch (sub) {
                case 0 -> new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1);
                case 1 -> new ItemStack(Items.NAUTILUS_SHELL, 1);
                case 2 -> new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1);
                case 3 -> new ItemStack(Items.PRISMARINE_CRYSTALS, 2 + rng.nextInt(3));
                case 4 -> createSunkenMap(level, pos);
                case 5 -> new ItemStack(CurrentsofTrade.JADE_FIGURINE.get(), 1);
                case 6 -> new ItemStack(CurrentsofTrade.NARWHAL_IVORY.get(), 1);
                default -> rng.nextFloat() < 0.2F ? new ItemStack(Items.HEART_OF_THE_SEA, 1) : new ItemStack(CurrentsofTrade.DOUBLOON.get(), 3);
            };
        }

        // 25% Coins, Currencies & Relics
        if (roll < 0.40F) {
            int sub = rng.nextInt(5);
            return switch (sub) {
                case 0 -> new ItemStack(CurrentsofTrade.DOUBLOON.get(), 1 + rng.nextInt(3));
                case 1 -> new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 1);
                case 2 -> new ItemStack(Items.GOLD_NUGGET, 3 + rng.nextInt(5));
                case 3 -> new ItemStack(CurrentsofTrade.DAMASCUS_STEEL.get(), 1);
                default -> new ItemStack(Items.IRON_NUGGET, 4 + rng.nextInt(6));
            };
        }

        // 30% Trade Commodities
        if (roll < 0.70F) {
            int sub = rng.nextInt(6);
            return switch (sub) {
                case 0 -> new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 1 + rng.nextInt(2));
                case 1 -> new ItemStack(CurrentsofTrade.SPICE_SACK.get(), 1);
                case 2 -> new ItemStack(CurrentsofTrade.AMBER_VIAL.get(), 1);
                case 3 -> new ItemStack(CurrentsofTrade.SAFFRON_BUNDLE.get(), 1);
                case 4 -> new ItemStack(CurrentsofTrade.CACAO_ELIXIR.get(), 1);
                default -> new ItemStack(CurrentsofTrade.TEA_BRICK.get(), 1);
            };
        }

        // 30% Common Ocean Flotsam
        int sub = rng.nextInt(5);
        return switch (sub) {
            case 0 -> new ItemStack(Items.OAK_PLANKS, 1 + rng.nextInt(3));
            case 1 -> new ItemStack(Items.SEA_PICKLE, 1 + rng.nextInt(2));
            case 2 -> new ItemStack(Items.KELP, 2 + rng.nextInt(4));
            case 3 -> new ItemStack(Items.PRISMARINE_SHARD, 1 + rng.nextInt(2));
            default -> new ItemStack(Items.COD, 1 + rng.nextInt(2));
        };
    }

    private ItemStack createSunkenMap(Level level, BlockPos origin) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos treasurePos = serverLevel.findNearestMapStructure(
                    net.minecraft.tags.StructureTags.ON_TREASURE_MAPS,
                    origin,
                    200,
                    false
            );
            BlockPos target = treasurePos != null ? treasurePos : origin;
            ItemStack mapStack = MapItem.create(serverLevel, target.getX(), target.getZ(), (byte) 1, true, true);
            MapItem.renderBiomePreviewMap(serverLevel, mapStack);
            MapItemSavedData.addTargetDecoration(mapStack, target, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
            mapStack.setHoverName(Component.translatable("item.currents_of_trade.sunken_treasure_map"));
            return mapStack;
        }
        return new ItemStack(CurrentsofTrade.DOUBLOON.get(), 2);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.salvage_dredge.desc"));
        tooltipComponents.add(Component.translatable("tooltip.currents_of_trade.salvage_dredge.lore"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
