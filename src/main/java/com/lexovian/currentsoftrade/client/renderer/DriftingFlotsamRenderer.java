package com.lexovian.currentsoftrade.client.renderer;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.entity.DriftingFlotsamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class DriftingFlotsamRenderer extends EntityRenderer<DriftingFlotsamEntity, DriftingFlotsamRenderer.FlotsamRenderState> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "textures/entity/drifting_flotsam.png");
    private final ModelPart root;

    public static class FlotsamRenderState extends EntityRenderState {
        public float yRot;
        public float time;
        public int seed;
    }

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
    public FlotsamRenderState createRenderState() {
        return new FlotsamRenderState();
    }

    @Override
    public void extractRenderState(DriftingFlotsamEntity entity, FlotsamRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yRot = entity.getYRot();
        state.time = entity.tickCount + partialTick;
        state.seed = entity.getId();
    }

    @Override
    public void submit(FlotsamRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        float bob = Mth.sin(state.time * 0.09F + state.seed) * 0.05F;
        float rockX = Mth.sin(state.time * 0.07F + state.seed * 1.3F) * 4.5F;
        float rockZ = Mth.cos(state.time * 0.06F + state.seed * 0.7F) * 4.5F;

        poseStack.translate(0.0D, 0.04D + bob, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot + state.time * 0.25F));
        poseStack.mulPose(Axis.XP.rotationDegrees(rockX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rockZ));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
            PoseStack renderPose = new PoseStack();
            renderPose.last().set(pose);
            this.root.render(renderPose, buffer, state.lightCoords, OverlayTexture.NO_OVERLAY);
        });
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
