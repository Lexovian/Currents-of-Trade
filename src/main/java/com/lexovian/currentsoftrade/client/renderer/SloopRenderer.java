package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import net.minecraft.world.phys.AABB;

public class SloopRenderer extends EntityRenderer<Boat> {
    private static final ResourceLocation SLOOP_TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/sloop.png");
    private static final ResourceLocation CARGO_TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/cargo_boat.png");
    private static final ResourceLocation TRADE_TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/trade_boat.png");

    private final SloopModel model;

    public SloopRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.model = new SloopModel(context.bakeLayer(SloopModel.LAYER_LOCATION));
    }

    @Override
    public boolean shouldRender(Boat entity, Frustum camera, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        // If close to camera (within 32 blocks - on board, at the helm, on the dock or nearby), ALWAYS render!
        // This completely eliminates the issue where the ship vanishes when looking up at the sails or sky.
        double distSq = entity.distanceToSqr(camX, camY, camZ);
        if (distSq < 1024.0) { // within 32 blocks
            return true;
        }
        // For further distances, check a generous 3D bounding box accounting for the full 2.4x scale ship
        // (16m high mast, 14m length, 10m beam)
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

        // Render 3D Banner Heraldry emblem on the front of the main sail
        if (entity instanceof SloopEntity sloop && !sloop.getBannerStack().isEmpty()) {
            poseStack.pushPose();
            // Pivot at main sail yardarm (0, -48/16, -17/16) and billow with wind
            poseStack.translate(0.0F, -3.0F, -1.0625F);
            poseStack.mulPose(Axis.XP.rotation(this.model.getSailXRot()));
            poseStack.translate(0.0F, 0.62F, -0.095F);
            poseStack.scale(-0.85F, -0.85F, 0.14F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                    sloop.getBannerStack(),
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffer,
                    entity.level(),
                    entity.getId()
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
