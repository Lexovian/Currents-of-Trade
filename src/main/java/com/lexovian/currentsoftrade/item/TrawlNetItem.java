package com.lexovian.currentsoftrade.item;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Shipboard Trawl Net — mount on a Sloop or Cargo Ship to passively haul fish, kelp,
 * and sunken sea relics while sailing, AND automatically scoop up drifting ocean flotsam.
 */
public class TrawlNetItem extends Item {

    public static final int HAUL_INTERVAL_TICKS = 240; // ~12 seconds of active sailing
    private static final WeakHashMap<AbstractBoat, double[]> SAIL_TRACKER = new WeakHashMap<>();

    public TrawlNetItem(Properties properties) {
        super(properties);
    }

    public static void tickShipTrawlNet(AbstractBoat ship, Container hold) {
        tickShipTrawlNet(ship, hold, 0);
    }

    public static void tickShipTrawlNet(AbstractBoat ship, Container hold, int figureheadType) {
        if (ship == null || hold == null || ship.level().isClientSide() || !ship.isAlive()) return;
        if (!(ship.level() instanceof ServerLevel serverLevel)) return;

        // 1. Auto-scoop nearby Drifting Ocean Flotsam & floating ItemEntities (STRICTLY in water, never on dry land!)
        boolean shipInWater = serverLevel.getFluidState(ship.blockPosition()).is(FluidTags.WATER)
                || serverLevel.getFluidState(ship.blockPosition().below()).is(FluidTags.WATER);
        if (shipInWater && ship.tickCount % 5 == 0) {
            AABB scoopBox = ship.getBoundingBox().inflate(3.5D, 2.0D, 3.5D);
            List<DriftingFlotsamEntity> flotsams = serverLevel.getEntitiesOfClass(DriftingFlotsamEntity.class, scoopBox);
            for (DriftingFlotsamEntity f : flotsams) {
                f.collectIntoShip(serverLevel, ship, hold);
            }
            List<net.minecraft.world.entity.item.ItemEntity> items = serverLevel.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, scoopBox,
                    ie -> ie.isAlive() && !ie.hasPickUpDelay() && !ie.getItem().isEmpty()
                            && (ie.isInWater()
                            || serverLevel.getFluidState(ie.blockPosition()).is(FluidTags.WATER)
                            || serverLevel.getFluidState(ie.blockPosition().below()).is(FluidTags.WATER))
            );
            for (net.minecraft.world.entity.item.ItemEntity ie : items) {
                ItemStack stack = ie.getItem().copy();
                int before = stack.getCount();
                insertIntoContainer(hold, stack);
                if (stack.getCount() < before) {
                    if (stack.isEmpty()) {
                        ie.discard();
                    } else {
                        ie.setItem(stack);
                    }
                    serverLevel.playSound(null, ie.getX(), ie.getY(), ie.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.45F, 1.1F);
                    serverLevel.sendParticles(ParticleTypes.SPLASH, ie.getX(), ie.getY() + 0.15, ie.getZ(), 4, 0.2, 0.1, 0.2, 0.03);
                }
            }
        }

        // 2. Track real server-side horizontal movement (player-steered boats have deltaMovement=0 on server!)
        double[] state = SAIL_TRACKER.computeIfAbsent(ship, k -> new double[]{ship.getX(), ship.getZ(), 0.0D});
        double dx = ship.getX() - state[0];
        double dz = ship.getZ() - state[1];
        state[0] = ship.getX();
        state[1] = ship.getZ();

        double movedSq = dx * dx + dz * dz;
        double deltaSq = ship.getDeltaMovement().horizontalDistanceSqr();
        if (Math.max(movedSq, deltaSq) < 0.0004D) {
            return;
        }
        if (!serverLevel.getFluidState(ship.blockPosition()).is(FluidTags.WATER)
                && !serverLevel.getFluidState(ship.blockPosition().below()).is(FluidTags.WATER)) {
            return;
        }

        state[2] += 1.0D;
        int interval = (figureheadType == 3) ? 160 : HAUL_INTERVAL_TICKS; // Ammonite Figurehead: ~35% faster
        if (state[2] < interval) return;
        state[2] = 0.0D;

        boolean boostedLuck = serverLevel.isRaining() || figureheadType == 1;
        ItemStack catchStack = rollMaritimeCatch(serverLevel.getRandom(), boostedLuck);
        if (catchStack.isEmpty()) return;

        if (!insertIntoContainer(hold, catchStack.copy())) {
            return;
        }

        float yawRad = ship.getYRot() * Mth.DEG_TO_RAD;
        double sternX = ship.getX() + Mth.sin(yawRad) * 3.4D;
        double sternY = ship.getY() + 0.35D;
        double sternZ = ship.getZ() - Mth.cos(yawRad) * 3.4D;

        serverLevel.playSound(null, sternX, sternY, sternZ, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.75F, 1.05F);
        serverLevel.sendParticles(ParticleTypes.SPLASH, sternX, sternY, sternZ, 12, 0.5, 0.15, 0.5, 0.05);
        serverLevel.sendParticles(ParticleTypes.BUBBLE, sternX, sternY, sternZ, 8, 0.45, 0.1, 0.45, 0.03);

        if (ship.getControllingPassenger() instanceof Player captain) {
            if (captain instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.currents_of_trade.trawl_net_catch",
                        catchStack.getCount(), catchStack.getHoverName()));
                if (sp.level().getServer() != null) {
                    var adv = sp.level().getServer().getAdvancements().get(
                            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "trawlers_fortune"));
                    if (adv != null) {
                        for (String c : adv.value().criteria().keySet()) {
                            sp.getAdvancements().award(adv, c);
                        }
                    }
                }
            }
        }
    }

    private static ItemStack rollMaritimeCatch(RandomSource rng, boolean boostedLuck) {
        int roll = rng.nextInt(100);
        if (boostedLuck && roll >= 78 && roll < 92) {
            roll = Math.min(99, roll + 10);
        }
        if (roll < 26) return new ItemStack(Items.COD, 1 + rng.nextInt(2));
        if (roll < 48) return new ItemStack(Items.SALMON, 1 + rng.nextInt(2));
        if (roll < 62) return new ItemStack(Items.KELP, 2 + rng.nextInt(3));
        if (roll < 72) return new ItemStack(Items.TROPICAL_FISH, 1);
        if (roll < 80) return new ItemStack(Items.INK_SAC, 1 + rng.nextInt(2));
        if (roll < 85) return new ItemStack(Items.PUFFERFISH, 1);
        if (roll < 89) return new ItemStack(Items.PRISMARINE_SHARD, 1 + rng.nextInt(2));
        if (roll < 92) return new ItemStack(CurrentsofTrade.SALT_POUCH.get(), 1);
        if (roll < 95) return new ItemStack(Items.NAUTILUS_SHELL, 1);
        if (roll < 97) return new ItemStack(CurrentsofTrade.MESSAGE_IN_A_BOTTLE.get(), 1);
        if (roll < 99) return new ItemStack(CurrentsofTrade.AMMONITE_FOSSIL.get(), 1);
        return new ItemStack(CurrentsofTrade.LUMINOUS_PEARL.get(), 1);
    }

    public static boolean insertIntoContainer(Container hold, ItemStack stack) {
        int size = hold.getContainerSize();
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            ItemStack existing = hold.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int move = Math.min(space, stack.getCount());
                existing.grow(move);
                stack.shrink(move);
                hold.setChanged();
            }
        }
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (hold.getItem(i).isEmpty()) {
                hold.setItem(i, stack.copy());
                stack.setCount(0);
                hold.setChanged();
                return true;
            }
        }
        return stack.isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("tooltip.currents_of_trade.trawl_net.desc"));
        tooltipAdder.accept(Component.translatable("tooltip.currents_of_trade.trawl_net.mount"));
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
    }
}
