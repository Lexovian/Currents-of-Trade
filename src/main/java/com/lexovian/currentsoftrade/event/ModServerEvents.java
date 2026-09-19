package com.lexovian.currentsoftrade.event;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.world.spawner.WanderingSailorSpawner;
import com.lexovian.currentsoftrade.world.village.VillagePoolInjection;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = CurrentsofTrade.MODID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        VillagePoolInjection.inject(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            WanderingSailorSpawner.serverTick(serverLevel);
        }
    }
}

