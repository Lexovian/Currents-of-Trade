package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.AABB;

public class SloopRenderer extends EntityRenderer<Boat> {
    private static final ResourceLocation SLOOP_TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/sloop.png");
    private static final ResourceLocation CARGO_TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/cargo_boat.png");
    private static final ResourceLocation TRADE_TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/trade_boat.png");

    private final SloopModel model;
    private final ModelPart bannerFlag;

    public SloopRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.model = new SloopModel(context.bakeLayer(SloopModel.LAYER_LOCATION));
        // Bake ONLY the pure cloth "flag" child of the Banner model (excluding "pole" and "bar" so no stick ever shows)
        this.bannerFlag = context.bakeLayer(ModelLayers.BANNER).getChild("flag");
    }

    @Override
    public boolean shouldRender(Boat entity, Frustum camera, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        double distSq = entity.distanceToSqr(camX, camY, camZ);
        if (distSq < 1024.0) {
            return true;
        }
        AABB cullingBox = new AABB(
                entity.getX() - 10.0, entity.getY() - 3.0, entity.getZ() - 10.0,
                entity.getX() + 10.0, entity.getY() + 20.0, entity.getZ() + 10.0
        );
        return camera.isVisible(cullingBox);
    }

    @Override
    public void render(Boat entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Position boat realistically in the water with a proper keel draft
        poseStack.translate(0.0, 0.22, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // Hurt / damage shake animation
        float hurtTime = (float) entity.getHurtTime() - partialTicks;
        float damage = entity.getDamage() - partialTicks;
        if (damage < 0.0F) damage = 0.0F;
        if (hurtTime > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(hurtTime) * hurtTime * damage / 25.0F * (float) entity.getHurtDir()));
        }

        // Gentle natural water ocean sway
        float ageInTicks = (float) entity.tickCount + partialTicks;
        float roll = Mth.sin(ageInTicks * 0.08F) * 1.5F;
        float pitch = Mth.cos(ageInTicks * 0.06F) * 1.0F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // Scale ship to grand sailing vessel proportions (2.4x scale)
        poseStack.scale(-2.4F, -2.4F, 2.4F);

        // Setup model animation
        this.model.setupAnim(entity, 0.0F, 0.0F, ageInTicks, 0.0F, 0.0F);

        VertexConsumer vertexConsumer = buffer.getBuffer(this.model.renderType(this.getTextureLocation(entity)));
        this.model.renderCustomShip(entity, poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        // Render pure cloth Heraldic Banner on the back of the ship (stern crest & transom) with ZERO wooden pole/stick
        ItemStack bannerStack = ItemStack.EMPTY;
        if (entity instanceof SloopEntity sloop && !sloop.getBannerStack().isEmpty()) {
            bannerStack = sloop.getBannerStack();
        } else if (entity instanceof CargoBoatEntity cargo && !cargo.getBannerStack().isEmpty()) {
            bannerStack = cargo.getBannerStack();
        }

        if (!bannerStack.isEmpty() && bannerStack.getItem() instanceof BannerItem bannerItem) {
            poseStack.pushPose();
            // Position flush against the stern crest & taffrail at the back of the ship (Z = 22.30 / 16, Y = -14.5 / 16)
            poseStack.translate(0.0F, -14.5F / 16.0F, 22.30F / 16.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(0.38F, 0.38F, 0.38F);

            this.bannerFlag.x = 0.0F;
            this.bannerFlag.y = 0.0F;
            this.bannerFlag.z = 0.0F;
            this.bannerFlag.xRot = -0.05F - Mth.sin(ageInTicks * 0.10F) * 0.04F;
            this.bannerFlag.yRot = 0.0F;
            this.bannerFlag.zRot = 0.0F;

            BannerPatternLayers patterns = bannerStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
            BannerRenderer.renderPatterns(
                    poseStack,
                    buffer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    this.bannerFlag,
                    ModelBakery.BANNER_BASE,
                    true,
                    bannerItem.getColor(),
                    patterns
            );
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected boolean shouldShowName(Boat entity) {
        // Never render floating nameplate above the ship
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(Boat entity) {
        if (entity instanceof CargoBoatEntity) {
            return CARGO_TEXTURE;
        }
        if (entity instanceof TradeBoatEntity trade) {
            if (trade.isVoyageShip()) {
                return SLOOP_TEXTURE;
            }
            return TRADE_TEXTURE;
        }
        if (entity instanceof SloopEntity sloop && sloop.isMerchantShip()) {
            return TRADE_TEXTURE;
        }
        return SLOOP_TEXTURE;
    }
}
