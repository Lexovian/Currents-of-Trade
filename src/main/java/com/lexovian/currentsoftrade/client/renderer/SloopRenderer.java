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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.AABB;

public class SloopRenderer extends EntityRenderer<Boat> {
    private static final ResourceLocation SLOOP_TEXTURE = new ResourceLocation(CurrentsofTrade.MODID, "textures/entity/ship/sloop.png");
    private static final ResourceLocation CARGO_TEXTURE = new ResourceLocation(CurrentsofTrade.MODID, "textures/entity/ship/cargo_boat.png");
    private static final ResourceLocation TRADE_TEXTURE = new ResourceLocation(CurrentsofTrade.MODID, "textures/entity/ship/trade_boat.png");
    private static final ResourceLocation TRAWL_NET_TEXTURE = new ResourceLocation(CurrentsofTrade.MODID, "textures/entity/ship/trawl_net.png");

    private final SloopModel model;
    private final ModelPart bannerFlag;
    private final ModelPart trawlNetRoot;
    private final ModelPart trawlNetBag;

    public SloopRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.model = new SloopModel(context.bakeLayer(SloopModel.LAYER_LOCATION));
        // Bake ONLY the pure cloth "flag" child of the Banner model (excluding "pole" and "bar" so no stick ever shows)
        this.bannerFlag = context.bakeLayer(ModelLayers.BANNER).getChild("flag");
        this.trawlNetRoot = createTrawlNetLayer().bakeRoot();
        this.trawlNetBag = this.trawlNetRoot.getChild("net_bag");
    }

    private static net.minecraft.client.model.geom.builders.LayerDefinition createTrawlNetLayer() {
        var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
        var root = mesh.getRoot();
        // Sleek dark-oak stern outrigger davits & horizontal trawl boom
        root.addOrReplaceChild("davits",
                net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.6F, -0.5F, -0.4F, 0.55F, 0.55F, 5.2F)
                        .texOffs(0, 0).addBox( 4.05F, -0.5F, -0.4F, 0.55F, 0.55F, 5.2F)
                        .texOffs(0, 0).addBox(-5.1F, -0.6F, 4.3F, 10.2F, 0.65F, 0.65F),
                net.minecraft.client.model.geom.PartPose.offset(0.0F, -3.8F, 21.6F));
        // Trailing open-weave diamond hemp fishnet funnel & cork headrope
        root.addOrReplaceChild("net_bag",
                net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                        // Row of 5 cork buoys along the headrope
                        .texOffs(0, 16).addBox(-4.2F, -0.2F, -0.35F, 0.9F, 0.6F, 0.65F)
                        .texOffs(0, 16).addBox(-2.2F, -0.2F, -0.35F, 0.9F, 0.6F, 0.65F)
                        .texOffs(0, 16).addBox(-0.45F, -0.2F, -0.35F, 0.9F, 0.6F, 0.65F)
                        .texOffs(0, 16).addBox( 1.3F, -0.2F, -0.35F, 0.9F, 0.6F, 0.65F)
                        .texOffs(0, 16).addBox( 3.3F, -0.2F, -0.35F, 0.9F, 0.6F, 0.65F)
                        // Slender braided hemp side towing lines
                        .texOffs(32, 16).addBox(-4.35F, 0.0F, -0.15F, 0.35F, 9.6F, 0.35F)
                        .texOffs(32, 16).addBox( 4.00F, 0.0F, -0.15F, 0.35F, 9.6F, 0.35F)
                        // Transparent diamond-mesh fishing net funnel (top, belly, left/right walls & cod-end)
                        .texOffs(0, 24).addBox(-4.2F, 0.2F,  0.1F, 8.4F, 9.4F, 0.15F)
                        .texOffs(0, 24).addBox(-3.6F, 0.8F, -1.8F, 7.2F, 8.8F, 0.15F)
                        .texOffs(0, 24).addBox(-4.2F, 0.4F, -1.7F, 0.15F, 9.0F, 1.8F)
                        .texOffs(0, 24).addBox( 4.05F, 0.4F, -1.7F, 0.15F, 9.0F, 1.8F)
                        .texOffs(0, 24).addBox(-2.5F, 9.4F, -1.5F, 5.0F, 3.0F, 1.5F),
                net.minecraft.client.model.geom.PartPose.offsetAndRotation(0.0F, -3.7F, 26.2F, 1.12F, 0.0F, 0.0F));
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64);
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

        // Render dedicated transparent open-weave Shipboard Trawl Net trailing off the stern
        boolean hasTrawlNet = (entity instanceof SloopEntity s && s.hasTrawlNet())
                || (entity instanceof CargoBoatEntity c && c.hasTrawlNet());
        if (hasTrawlNet) {
            this.trawlNetBag.xRot = 1.12F + Mth.sin(ageInTicks * 0.12F) * 0.06F;
            VertexConsumer netBuf = buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TRAWL_NET_TEXTURE));
            this.trawlNetRoot.render(poseStack, netBuf, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }

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

            var patterns = BannerBlockEntity.createPatterns(bannerItem.getColor(), BannerBlockEntity.getItemPatterns(bannerStack));
            BannerRenderer.renderPatterns(
                    poseStack,
                    buffer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    this.bannerFlag,
                    ModelBakery.BANNER_BASE,
                    true,
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
