package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.spawner.WanderingSailorSpawner;
import com.lexovian.currentsoftrade.world.village.VillagePoolInjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        com.lexovian.currentsoftrade.world.village.CoastalWaterCheckProcessor.clearCache();
        VillagePoolInjection.inject(event.getServer());
    }

    /** Equivalent of NeoForge's LevelTickEvent.Post. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            WanderingSailorSpawner.serverTick(serverLevel);
        }
    }

    /** Salted food bonus (1.21 stored it in the stack's FOOD component; 1.20.1 applies it on eat). */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!event.getEntity().level().isClientSide) {
            com.lexovian.currentsoftrade.item.SaltPouchItem.applySaltedBonus(event.getEntity(), event.getItem());
        }
    }
}
