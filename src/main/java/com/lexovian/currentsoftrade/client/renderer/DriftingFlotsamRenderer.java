package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class DriftingFlotsamRenderer extends EntityRenderer<DriftingFlotsamEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/drifting_flotsam.png");
    private final ModelPart root;

    public DriftingFlotsamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.55F;
        this.root = createModel().bakeRoot();
    }

    private static LayerDefinition createModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("crate",
                CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -10.0F, -6.0F, 12.0F, 10.0F, 12.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("driftwood_a",
                CubeListBuilder.create().texOffs(0, 22).addBox(-9.5F, -4.8F, -2.0F, 19.0F, 2.0F, 4.0F),
                PartPose.rotation(0.05F, 0.35F, -0.04F));
        root.addOrReplaceChild("driftwood_b",
                CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, -4.5F, -9.0F, 4.0F, 2.0F, 18.0F),
                PartPose.rotation(-0.04F, -0.25F, 0.05F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(DriftingFlotsamEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float time = entity.tickCount + partialTicks;
        float bob = Mth.sin(time * 0.09F + entity.getId()) * 0.05F;
        float rockX = Mth.sin(time * 0.07F + entity.getId() * 1.3F) * 4.5F;
        float rockZ = Mth.cos(time * 0.06F + entity.getId() * 0.7F) * 4.5F;

        poseStack.translate(0.0D, 0.04D + bob, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw + time * 0.25F));
        poseStack.mulPose(Axis.XP.rotationDegrees(rockX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rockZ));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DriftingFlotsamEntity entity) {
        return TEXTURE;
    }
}
