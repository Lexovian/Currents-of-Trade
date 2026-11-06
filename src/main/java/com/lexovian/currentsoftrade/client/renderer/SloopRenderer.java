package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.client.model.SloopModel;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.AABB;

public class SloopRenderer<T extends AbstractBoat> extends EntityRenderer<T, SloopRenderState> {
    private static final Identifier SLOOP_TEXTURE = Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/sloop.png");
    private static final Identifier CARGO_TEXTURE = Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/cargo_boat.png");
    private static final Identifier TRADE_TEXTURE = Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/ship/trade_boat.png");

    private final SloopModel model;
    private final BannerFlagModel bannerFlagModel;
    private final SpriteGetter sprites;

    public SloopRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.model = new SloopModel(context.bakeLayer(SloopModel.LAYER_LOCATION));
        // Bake ONLY the pure cloth "flag" child of the Banner model (excluding "pole" and "bar" so no stick ever shows)
        this.bannerFlagModel = new BannerFlagModel(context.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
        this.sprites = context.getSprites();
    }

    @Override
    public SloopRenderState createRenderState() {
        return new SloopRenderState();
    }

    @Override
    public void extractRenderState(T entity, SloopRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.texture = this.getTextureLocation(entity);
        state.yRot = entity.getYRot(partialTicks);
        float hurtTime = (float) entity.getHurtTime() - partialTicks;
        float damage = entity.getDamage() - partialTicks;
        state.hurtTime = hurtTime;
        state.damage = Math.max(0.0F, damage);
        state.hurtDir = entity.getHurtDir();

        float ageInTicks = (float) entity.tickCount + partialTicks;
        state.ageInTicks = ageInTicks;
        state.bannerPhase = ((float) Math.floorMod((long) entity.getX() * 7L + (long) entity.getY() * 9L + (long) entity.getZ() * 13L + entity.level().getGameTime(), 100L) + partialTicks) / 100.0F;
        state.roll = Mth.sin(ageInTicks * 0.08F) * 1.5F;
        state.pitch = Mth.cos(ageInTicks * 0.06F) * 1.0F;

        boolean isThundering = entity.level().isThundering();
        boolean isRaining = entity.level().isRaining();
        float windSpeed = isThundering ? 0.22F : (isRaining ? 0.14F : 0.08F);
        float windAmplitude = isThundering ? 0.09F : (isRaining ? 0.06F : 0.04F);
        float windBase = isThundering ? -0.12F : -0.06F;
        state.windBow = windBase - Mth.sin(ageInTicks * windSpeed) * windAmplitude;
        state.tradePennantYRot = -0.35F + Mth.sin(ageInTicks * (windSpeed * 2.4F)) * 0.24F;
        state.tradePennantZRot = Mth.cos(ageInTicks * (windSpeed * 1.8F)) * 0.06F;

        float turnRate = entity.getYRot() - entity.yRotO;
        if (Math.abs(turnRate) > 0.05F) {
            state.helmWheelZRot = -Mth.clamp(turnRate * 0.3F, -1.5F, 1.5F);
        } else if (entity.getDeltaMovement().lengthSqr() > 0.001) {
            state.helmWheelZRot = Mth.sin(ageInTicks * 0.12F) * 0.15F;
        } else {
            state.helmWheelZRot = 0.0F;
        }

        boolean isCargo = entity instanceof CargoBoatEntity;
        boolean isTrade = !isCargo && ((entity instanceof TradeBoatEntity trade && !trade.isVoyageShip())
                || (entity instanceof SloopEntity sloop && sloop.isMerchantShip()));
        boolean hasCopperHull = (entity instanceof SloopEntity s && s.hasCopperHull());
        boolean hasBowLantern = (entity instanceof SloopEntity s && s.getBowLanternType() > 0);
        boolean hasBanner = (entity instanceof SloopEntity s && !s.getBannerStack().isEmpty())
                || (entity instanceof CargoBoatEntity c && !c.getBannerStack().isEmpty());

        state.isCargo = isCargo;
        state.isTrade = isTrade;
        state.hasCopperHull = hasCopperHull;
        state.hasBowLantern = hasBowLantern;
        state.hasBanner = hasBanner;

        int sailColorId = -1;
        int bowLanternType = 0;
        int pennantColorId = -1;
        ItemStack banner = ItemStack.EMPTY;

        if (entity instanceof SloopEntity sloop) {
            sailColorId = sloop.getSailColor();
            bowLanternType = sloop.getBowLanternType();
            banner = sloop.getBannerStack();
            if (banner.getItem() instanceof BannerItem bannerItem) {
                pennantColorId = bannerItem.getColor().getId();
            } else if (sailColorId >= 0) {
                pennantColorId = sailColorId;
            }
        } else if (entity instanceof CargoBoatEntity cargo && cargo.isPlayerControlled()) {
            sailColorId = cargo.getSailColor();
            banner = cargo.getBannerStack();
            if (banner.getItem() instanceof BannerItem bannerItem) {
                pennantColorId = bannerItem.getColor().getId();
            } else if (sailColorId >= 0) {
                pennantColorId = sailColorId;
            }
        }

        state.sailColorId = sailColorId;
        state.bowLantern = bowLanternType;
        state.pennantColorId = pennantColorId;
        state.bannerStack = banner;
    }

    @Override
    protected AABB getBoundingBoxForCulling(AbstractBoat entity) {
        return new AABB(
                entity.getX() - 10.0, entity.getY() - 3.0, entity.getZ() - 10.0,
                entity.getX() + 10.0, entity.getY() + 20.0, entity.getZ() + 10.0
        );
    }

    @Override
    public void submit(SloopRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        // Position boat realistically in the water with a proper keel draft
        poseStack.translate(0.0, 0.22, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));

        // Hurt / damage shake animation
        if (state.hurtTime > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * state.damage / 25.0F * (float) state.hurtDir));
        }

        // Gentle natural water ocean sway
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));

        // Scale ship to grand sailing vessel proportions (2.4x scale)
        poseStack.scale(-2.4F, -2.4F, 2.4F);

        submitNodeCollector.submitCustomGeometry(poseStack, this.model.renderType(state.texture), (pose, buffer) -> {
            PoseStack renderPose = new PoseStack();
            renderPose.last().set(pose);
            this.model.setupAnim(state);
            this.model.renderCustomShip(state, renderPose, buffer, state.lightCoords, OverlayTexture.NO_OVERLAY);
        });

        // Render pure cloth Heraldic Banner on the back of the ship (stern crest & transom) with ZERO wooden pole/stick
        if (!state.bannerStack.isEmpty() && state.bannerStack.getItem() instanceof BannerItem bannerItem) {
            poseStack.pushPose();
            // Position flush against the stern crest & taffrail at the back of the ship (Z = 22.30 / 16, Y = -14.5 / 16)
            poseStack.translate(0.0F, -14.5F / 16.0F, 22.30F / 16.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(0.38F, 0.38F, 0.38F);

            this.bannerFlagModel.setupAnim(state.bannerPhase);
            BannerPatternLayers patterns = state.bannerStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
            BannerRenderer.submitPatterns(
                    this.sprites,
                    poseStack,
                    submitNodeCollector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    this.bannerFlagModel,
                    state.bannerPhase,
                    true,
                    bannerItem.getColor(),
                    patterns,
                    null
            );
            poseStack.popPose();
        }

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    protected boolean shouldShowName(T entity, double distanceToCameraSq) {
        // Never render floating nameplate above the ship
        return false;
    }

    public Identifier getTextureLocation(T entity) {
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
