package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;

public class WanderingSailorRenderer extends WanderingTraderRenderer {

    private static final ResourceLocation SAILOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/wandering_sailor.png");

    public WanderingSailorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WanderingTrader entity) {
        return SAILOR_TEXTURE;
    }
}
