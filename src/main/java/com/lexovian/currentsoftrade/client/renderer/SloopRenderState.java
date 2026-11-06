package com.lexovian.currentsoftrade.client.renderer;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class SloopRenderState extends EntityRenderState {
    public Identifier texture;
    public float yRot;
    public float hurtTime;
    public float damage;
    public int hurtDir;
    public float ageInTicks;
    public float roll;
    public float pitch;
    public float windBow;
    public float tradePennantYRot;
    public float tradePennantZRot;
    public float helmWheelZRot;
    public float bannerPhase;

    public boolean isCargo;
    public boolean isTrade;
    public boolean hasCopperHull;
    public boolean hasBowLantern;
    public boolean hasBanner;

    public int sailColorId = -1;
    public int bowLantern = 0;
    public int pennantColorId = -1;
    public ItemStack bannerStack = ItemStack.EMPTY;
}
