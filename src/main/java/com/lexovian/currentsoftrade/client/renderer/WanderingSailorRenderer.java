package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

public class WanderingSailorRenderer extends WanderingTraderRenderer {

    private static final Identifier SAILOR_TEXTURE =
            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/wandering_sailor.png");

    public WanderingSailorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return SAILOR_TEXTURE;
    }
}
