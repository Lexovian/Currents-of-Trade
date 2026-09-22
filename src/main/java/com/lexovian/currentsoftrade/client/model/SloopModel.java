package com.lexovian.currentsoftrade.client.model;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Authentic 3D Sailing Sloop Model
 *
 * Sequence from front to back:
 *   Bow -> Main Deck & Rigged Mast/Sails ->
 *   Elevated Quarterdeck -> Helm Column & Steering Wheel ->
 *   Captain Seat -> Stern Transom & Stern Lantern
 *
 * Features:
 *   - Symmetrical, perfectly mapped Minecraft Vanilla Lanterns (cap, 3x3x3 glass with fire, base, ring)
 *   - Expansive naval sails widened to 42 & 30 width spanning the full ship beam
 *   - Enlarged, prominent 9-spoke captain's steering wheel
 *   - Tiered, raked baroque stern with 3-pane Captain's Cabin windows & arched crest
 *   - Hydrodynamic stepped V-hull bottom with central timber keel
 *   - Upward-angled bowsprit pointing proudly into the sky (-0.22 rad pitch)
 */
public class SloopModel extends EntityModel<Boat> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(CurrentsofTrade.MODID, "sloop"), "main");

    // Animated & Ship-Specific Feature Parts
    private final ModelPart sail;
    private final ModelPart sailTop;
    private final ModelPart helmWheel;
    private final ModelPart cargoCrate;
    private final ModelPart merchantAwning;
    private final ModelPart cargoCrane;
    private final ModelPart cargoAnchors;
    private final ModelPart cargoFenders;
    private final ModelPart merchantDesk;
    private final ModelPart tradeFigurehead;
    private final ModelPart crowsNest;
    private final ModelPart tradePennant;
    private final ModelPart waistSeat;
    private final ModelPart bowSeat;
    private final ModelPart root;

    public SloopModel(ModelPart root) {
        this.root            = root;
        this.sail            = root.getChild("sail");
        this.sailTop         = root.getChild("sail_top");
        this.helmWheel       = root.getChild("helm_wheel");
        this.cargoCrate      = root.getChild("cargo_crate");
        this.merchantAwning  = root.getChild("merchant_awning");
        this.cargoCrane      = root.getChild("cargo_crane");
        this.cargoAnchors    = root.getChild("cargo_anchors");
        this.cargoFenders    = root.getChild("cargo_fenders");
        this.merchantDesk    = root.getChild("merchant_desk");
        this.tradeFigurehead = root.getChild("trade_figurehead");
        this.crowsNest       = root.getChild("crows_nest");
        this.tradePennant    = root.getChild("trade_pennant");
        this.waistSeat       = root.getChild("waist_seat");
        this.bowSeat         = root.getChild("bow_seat");
    }

    // --- Model Definition ---

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Bow & Stempost ---
        root.addOrReplaceChild("bowsprit",
                CubeListBuilder.create().texOffs(130, 0)
                        .addBox(-1.0F, -1.0F, -11.0F, 2.0F, 2.0F, 12.0F),
                PartPose.offsetAndRotation(0.0F, -10.0F, -27.0F, -0.22F, 0.0F, 0.0F));

        root.addOrReplaceChild("stempost",
                CubeListBuilder.create().texOffs(224, 218)
                        .addBox(-1.0F, -11.0F, -28.5F, 2.0F, 12.0F, 1.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("nose_closure",
                CubeListBuilder.create().texOffs(172, 192)
                        .addBox(-2.5F, -10.0F, -27.2F, 5.0F, 11.0F, 0.8F),
                PartPose.ZERO);

        // --- Bow Lantern ---
        root.addOrReplaceChild("lantern",
                CubeListBuilder.create()
                        .texOffs(124, 121).addBox(-0.5F, -14.5F, -29.0F, 1.0F, 1.0F, 1.0F)
                        .texOffs(124, 104).addBox(-1.5F, -13.5F, -30.0F, 3.0F, 1.0F, 3.0F)
                        .texOffs(124, 109).addBox(-1.5F, -12.5F, -30.0F, 3.0F, 3.0F, 3.0F)
                        .texOffs(124, 116).addBox(-1.5F, -9.5F, -30.0F, 3.0F, 1.0F, 3.0F),
                PartPose.ZERO);

        // --- Prow Bulwarks & Foredeck ---
        root.addOrReplaceChild("prow_port",
                CubeListBuilder.create()
                        .texOffs(0, 218).addBox(-9.0F, -8.0F, -20.8F, 1.5F, 9.0F, 0.8F)
                        .texOffs(0, 217).addBox(-7.5F, -8.0F, -23.0F, 2.0F, 9.0F, 3.0F)
                        .texOffs(0, 218).addBox(-7.5F, -8.0F, -23.8F, 1.5F, 9.0F, 0.8F)
                        .texOffs(0, 218).addBox(-6.0F, -8.0F, -25.0F, 2.0F, 9.0F, 2.0F)
                        .texOffs(0, 218).addBox(-6.0F, -8.0F, -25.8F, 1.5F, 9.0F, 0.8F)
                        .texOffs(0, 218).addBox(-4.5F, -8.0F, -27.0F, 2.0F, 9.0F, 2.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("prow_starboard",
                CubeListBuilder.create()
                        .texOffs(46, 218).addBox(7.5F, -8.0F, -20.8F, 1.5F, 9.0F, 0.8F)
                        .texOffs(46, 217).addBox(5.5F, -8.0F, -23.0F, 2.0F, 9.0F, 3.0F)
                        .texOffs(46, 218).addBox(6.0F, -8.0F, -23.8F, 1.5F, 9.0F, 0.8F)
                        .texOffs(46, 218).addBox(4.0F, -8.0F, -25.0F, 2.0F, 9.0F, 2.0F)
                        .texOffs(46, 218).addBox(4.5F, -8.0F, -25.8F, 1.5F, 9.0F, 0.8F)
                        .texOffs(46, 218).addBox(2.5F, -8.0F, -27.0F, 2.0F, 9.0F, 2.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("deck_prow",
                CubeListBuilder.create().texOffs(142, 192)
                        .addBox(-5.8F, -4.0F, -23.0F, 11.6F, 2.0F, 3.0F)
                        .addBox(-4.3F, -4.0F, -25.0F, 8.6F, 2.0F, 2.0F)
                        .addBox(-2.7F, -4.0F, -27.2F, 5.4F, 2.0F, 2.2F),
                PartPose.ZERO);

        // --- Main Deck, Waist Walls & Keel ---
        root.addOrReplaceChild("deck_main",
                CubeListBuilder.create().texOffs(0, 192)
                        .addBox(-7.0F, -4.0F, -20.0F, 14.0F, 2.0F, 20.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("side_port_mid",
                CubeListBuilder.create().texOffs(0, 220)
                        .addBox(-9.0F, -8.0F, -20.0F, 2.0F, 9.0F, 20.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("side_starboard_mid",
                CubeListBuilder.create().texOffs(46, 220)
                        .addBox(7.0F, -8.0F, -20.0F, 2.0F, 9.0F, 20.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("keel_bottom",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-7.0F, 0.0F, -20.0F, 14.0F, 1.5F, 40.8F)
                        .addBox(-6.0F, 0.0F, -22.7F, 12.0F, 1.5F, 2.7F)
                        .addBox(-4.5F, 0.0F, -25.2F, 9.0F, 1.5F, 2.5F)
                        .addBox(-3.0F, 0.0F, -27.3F, 6.0F, 1.5F, 2.1F),
                PartPose.ZERO);

        root.addOrReplaceChild("keel_mid",
                CubeListBuilder.create().texOffs(0, 50)
                        .addBox(-4.5F, 1.5F, -20.0F, 9.0F, 1.5F, 40.0F)
                        .addBox(-3.5F, 1.5F, -24.0F, 7.0F, 1.5F, 4.0F)
                        .addBox(-2.0F, 1.5F, -27.3F, 4.0F, 1.5F, 3.3F),
                PartPose.ZERO);

        root.addOrReplaceChild("keel_spine",
                CubeListBuilder.create().texOffs(110, 50)
                        .addBox(-1.0F, 3.0F, -28.5F, 2.0F, 2.0F, 49.5F),
                PartPose.ZERO);

        // --- Mast, Spars & Crow's Nest ---
        root.addOrReplaceChild("mast",
                CubeListBuilder.create().texOffs(220, 0)
                        .addBox(-1.5F, -52.0F, -15.5F, 3.0F, 50.0F, 3.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("mast_step",
                CubeListBuilder.create().texOffs(220, 58)
                        .addBox(-3.5F, -5.0F, -17.5F, 7.0F, 2.0F, 7.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("topmast",
                CubeListBuilder.create().texOffs(220, 0)
                        .addBox(-1.0F, -68.0F, -15.0F, 2.0F, 17.0F, 2.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("crows_nest",
                CubeListBuilder.create().texOffs(220, 58)
                        .addBox(-4.0F, -52.5F, -19.0F, 8.0F, 1.0F, 8.0F)
                        .addBox(-4.0F, -55.0F, -19.0F, 8.0F, 2.5F, 0.8F)
                        .addBox(-4.0F, -55.0F, -19.0F, 0.8F, 2.5F, 8.0F)
                        .addBox(3.2F,  -55.0F, -19.0F, 0.8F, 2.5F, 8.0F)
                        .addBox(-4.0F, -55.0F, -11.8F, 3.0F, 2.5F, 0.8F)
                        .addBox(1.0F,  -55.0F, -11.8F, 3.0F, 2.5F, 0.8F)
                        .addBox(-1.5F, -52.5F, -16.0F, 1.0F, 3.5F, 1.0F)
                        .addBox(0.5F,  -52.5F, -16.0F, 1.0F, 3.5F, 1.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("trade_pennant",
                CubeListBuilder.create()
                        .texOffs(220, 58).addBox(-0.5F, -72.0F, -15.5F, 1.0F, 4.0F, 1.0F)
                        .texOffs(0, 150).addBox(0.5F, -72.0F, -15.5F, 6.0F, 1.0F, 2.0F)
                        .texOffs(0, 150).addBox(0.5F, -71.0F, -15.5F, 4.0F, 1.0F, 1.5F)
                        .texOffs(0, 150).addBox(0.5F, -70.0F, -15.5F, 2.0F, 1.0F, 1.0F),
                PartPose.ZERO);

        // --- Channels & Rigging Shrouds ---
        root.addOrReplaceChild("channels",
                CubeListBuilder.create().texOffs(220, 58)
                        .addBox(-10.8F, -7.5F, -16.5F, 1.8F, 1.2F, 5.0F)
                        .addBox(9.0F, -7.5F, -16.5F, 1.8F, 1.2F, 5.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("shrouds_port",
                CubeListBuilder.create()
                        .texOffs(170, 130).addBox(-0.5F, 0.0F, -15.5F, 1.0F, 38.8F, 1.0F)
                        .texOffs(170, 130).addBox(-0.5F, 0.0F, -13.0F, 1.0F, 38.8F, 1.0F)
                        .texOffs(170, 130).addBox(-0.3F, 33.0F, -15.0F, 0.6F, 0.6F, 2.2F)
                        .texOffs(220, 58).addBox(-0.7F, 36.8F, -15.8F, 1.4F, 2.0F, 1.6F)
                        .texOffs(220, 58).addBox(-0.7F, 36.8F, -13.3F, 1.4F, 2.0F, 1.6F),
                PartPose.offsetAndRotation(-1.5F, -46.0F, 0.0F, 0.0F, 0.0F, 0.215F));

        root.addOrReplaceChild("shrouds_stbd",
                CubeListBuilder.create()
                        .texOffs(170, 130).addBox(-0.5F, 0.0F, -15.5F, 1.0F, 38.8F, 1.0F)
                        .texOffs(170, 130).addBox(-0.5F, 0.0F, -13.0F, 1.0F, 38.8F, 1.0F)
                        .texOffs(170, 130).addBox(-0.3F, 33.0F, -15.0F, 0.6F, 0.6F, 2.2F)
                        .texOffs(220, 58).addBox(-0.7F, 36.8F, -15.8F, 1.4F, 2.0F, 1.6F)
                        .texOffs(220, 58).addBox(-0.7F, 36.8F, -13.3F, 1.4F, 2.0F, 1.6F),
                PartPose.offsetAndRotation(1.5F, -46.0F, 0.0F, 0.0F, 0.0F, -0.215F));

        // --- Sails & Yardarms ---
        root.addOrReplaceChild("yardarm",
                CubeListBuilder.create().texOffs(0, 130)
                        .addBox(-22.0F, -50.0F, -16.5F, 44.0F, 2.0F, 2.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("sail",
                CubeListBuilder.create()
                        .texOffs(0, 150).addBox(-21.0F, 0.0F, -0.5F, 42.0F, 10.0F, 1.0F)
                        .texOffs(0, 150).addBox(-21.0F, 9.5F, -1.5F, 42.0F, 10.5F, 1.0F)
                        .texOffs(0, 178).addBox(-21.5F, 20.0F, -1.0F, 43.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, -48.0F, -17.0F));

        root.addOrReplaceChild("yardarm_top",
                CubeListBuilder.create().texOffs(0, 136)
                        .addBox(-16.0F, -64.0F, -15.5F, 32.0F, 1.5F, 1.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("sail_top",
                CubeListBuilder.create()
                        .texOffs(0, 118).addBox(-15.0F, 0.0F, -0.5F, 30.0F, 11.0F, 1.0F),
                PartPose.offset(0.0F, -63.0F, -16.0F));

        // --- Deck Benches & Cargo Holds ---
        root.addOrReplaceChild("waist_seat",
                CubeListBuilder.create().texOffs(0, 192)
                        .addBox(-4.5F, -5.5F, -7.5F, 9.0F, 1.5F, 3.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("bow_seat",
                CubeListBuilder.create().texOffs(142, 192)
                        .addBox(-3.5F, -5.5F, -23.0F, 7.0F, 1.5F, 2.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("cargo_crate",
                CubeListBuilder.create()
                        .texOffs(96, 104).addBox(-4.5F, -9.5F, -7.0F, 9.0F, 5.5F, 6.0F)
                        .texOffs(96, 104).addBox(0.5F, -14.0F, -6.5F, 4.0F, 4.5F, 5.0F)
                        .texOffs(220, 58).addBox(-4.5F, -13.5F, -6.5F, 4.0F, 4.0F, 5.0F)
                        .texOffs(96, 104).addBox(-3.0F, -8.5F, -21.0F, 6.0F, 4.5F, 4.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("merchant_awning",
                CubeListBuilder.create()
                        .texOffs(110, 16).addBox(-7.5F, -19.5F, 1.8F, 15.0F, 1.0F, 19.0F)
                        .texOffs(180, 110).addBox(-7.6F, -18.5F, 1.8F, 0.8F, 3.5F, 19.0F)
                        .texOffs(180, 110).addBox(6.8F,  -18.5F, 1.8F, 0.8F, 3.5F, 19.0F)
                        .texOffs(180, 134).addBox(-7.5F, -18.5F, 20.8F, 15.0F, 4.0F, 1.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("cargo_crane",
                CubeListBuilder.create().texOffs(220, 58)
                        .addBox(-1.0F, -24.0F, -11.0F, 2.0F, 18.0F, 2.0F)
                        .addBox(-0.8F, -23.0F, -10.0F, 1.6F, 1.6F, 10.0F)
                        .texOffs(140, 104).addBox(-0.6F, -21.4F, -1.0F, 1.2F, 8.0F, 1.2F),
                PartPose.ZERO);

        root.addOrReplaceChild("cargo_anchors",
                CubeListBuilder.create().texOffs(124, 104)
                        .addBox(-9.2F, -7.2F, -22.0F, 1.2F, 6.7F, 1.2F)
                        .addBox(-9.2F, -1.5F, -24.0F, 1.2F, 1.5F, 5.0F)
                        .addBox(-9.5F, -6.8F, -23.0F, 1.6F, 1.2F, 3.2F)
                        .addBox(8.0F, -7.2F, -22.0F, 1.2F, 6.7F, 1.2F)
                        .addBox(8.0F, -1.5F, -24.0F, 1.2F, 1.5F, 5.0F)
                        .addBox(7.9F, -6.8F, -23.0F, 1.6F, 1.2F, 3.2F),
                PartPose.ZERO);

        root.addOrReplaceChild("cargo_fenders",
                CubeListBuilder.create().texOffs(92, 220)
                        .addBox(-9.7F, -4.5F, -15.0F, 1.0F, 2.0F, 30.0F)
                        .addBox(-9.9F, -3.0F, -7.0F, 1.4F, 4.5F, 2.2F)
                        .addBox(-9.9F, -3.0F, 7.0F, 1.4F, 4.5F, 2.2F)
                        .addBox(8.7F, -4.5F, -15.0F, 1.0F, 2.0F, 30.0F)
                        .addBox(8.5F, -3.0F, -7.0F, 1.4F, 4.5F, 2.2F)
                        .addBox(8.5F, -3.0F, 7.0F, 1.4F, 4.5F, 2.2F),
                PartPose.ZERO);

        root.addOrReplaceChild("merchant_desk",
                CubeListBuilder.create()
                        .texOffs(220, 58).addBox(3.0F, -10.5F, 4.0F, 3.5F, 4.0F, 3.5F)
                        .texOffs(140, 104).addBox(3.5F, -11.5F, 4.5F, 2.5F, 1.0F, 2.5F)
                        .texOffs(96, 104).addBox(3.2F, -9.5F, 9.0F, 3.2F, 3.2F, 3.2F),
                PartPose.ZERO);

        root.addOrReplaceChild("trade_figurehead",
                CubeListBuilder.create().texOffs(140, 104)
                        .addBox(-1.2F, -12.5F, -30.5F, 2.4F, 4.0F, 2.2F)
                        .addBox(-0.8F, -14.0F, -31.5F, 1.6F, 2.5F, 1.8F),
                PartPose.ZERO);

        // --- Quarterdeck & Helm Platform ---
        root.addOrReplaceChild("cabin_bulkhead",
                CubeListBuilder.create().texOffs(190, 192)
                        .addBox(-7.0F, -6.5F, -0.4F, 14.0F, 2.5F, 0.4F),
                PartPose.ZERO);

        root.addOrReplaceChild("deck_quarter",
                CubeListBuilder.create().texOffs(70, 192)
                        .addBox(-7.0F, -6.5F, 0.0F, 14.0F, 2.0F, 20.8F),
                PartPose.ZERO);

        root.addOrReplaceChild("side_port_aft",
                CubeListBuilder.create().texOffs(92, 220)
                        .addBox(-9.0F, -10.0F, 0.0F, 2.0F, 11.0F, 18.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("side_starboard_aft",
                CubeListBuilder.create().texOffs(140, 220)
                        .addBox(7.0F, -10.0F, 0.0F, 2.0F, 11.0F, 18.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("quarterdeck_stairs",
                CubeListBuilder.create().texOffs(0, 192)
                        .addBox(-6.5F, -5.2F, -2.0F, 2.8F, 1.2F, 1.6F)
                        .addBox(-6.5F, -6.5F, -0.4F, 2.8F, 2.5F, 0.4F)
                        .texOffs(220, 58).addBox(-3.7F, -8.0F, -2.0F, 0.6F, 3.0F, 3.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("quarterdeck_canopy",
                CubeListBuilder.create().texOffs(220, 58)
                        .addBox(-8.5F, -16.0F, 2.5F, 1.2F, 6.0F, 1.2F)
                        .addBox( 7.3F, -16.0F, 2.5F, 1.2F, 6.0F, 1.2F)
                        .addBox(-7.5F, -17.5F, 2.5F, 3.5F, 1.5F, 1.2F)
                        .addBox( 4.0F, -17.5F, 2.5F, 3.5F, 1.5F, 1.2F)
                        .addBox(-4.5F, -18.8F, 2.5F, 9.0F, 1.3F, 1.2F)
                        .addBox(-8.5F, -16.0F, 11.5F, 1.2F, 6.0F, 1.2F)
                        .addBox( 7.3F, -16.0F, 11.5F, 1.2F, 6.0F, 1.2F)
                        .addBox(-7.5F, -17.5F, 11.5F, 3.5F, 1.5F, 1.2F)
                        .addBox( 4.0F, -17.5F, 11.5F, 3.5F, 1.5F, 1.2F)
                        .addBox(-4.5F, -18.8F, 11.5F, 9.0F, 1.3F, 1.2F)
                        .addBox(-8.5F, -16.0F, 19.5F, 1.2F, 6.0F, 1.2F)
                        .addBox( 7.3F, -16.0F, 19.5F, 1.2F, 6.0F, 1.2F)
                        .addBox(-7.5F, -17.5F, 19.5F, 3.5F, 1.5F, 1.2F)
                        .addBox( 4.0F, -17.5F, 19.5F, 3.5F, 1.5F, 1.2F)
                        .addBox(-4.5F, -18.8F, 19.5F, 9.0F, 1.3F, 1.2F)
                        .addBox(-0.6F, -19.6F, 2.0F, 1.2F, 0.8F, 18.6F)
                        .addBox(-6.5F, -18.2F, 2.0F, 0.8F, 0.8F, 18.5F)
                        .addBox( 5.7F, -18.2F, 2.0F, 0.8F, 0.8F, 18.5F),
                PartPose.ZERO);

        // --- Steering Wheel & Pedestal ---
        root.addOrReplaceChild("helm_stand",
                CubeListBuilder.create().texOffs(86, 104)
                        .addBox(-1.0F, -13.0F, 8.0F, 2.0F, 6.5F, 1.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("helm_wheel",
                CubeListBuilder.create()
                        .texOffs(140, 104).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F)
                        .texOffs(140, 104).addBox(-0.6F, -4.5F, -0.42F, 1.2F, 9.0F, 0.84F)
                        .texOffs(140, 104).addBox(-4.5F, -0.6F, -0.41F, 9.0F, 1.2F, 0.82F)
                        .texOffs(150, 104).addBox(-2.5F, -3.5F, -0.38F, 5.0F, 1.0F, 0.76F)
                        .texOffs(150, 104).addBox(-2.5F,  2.5F, -0.38F, 5.0F, 1.0F, 0.76F)
                        .texOffs(150, 104).addBox(-3.5F, -2.5F, -0.38F, 1.0F, 5.0F, 0.76F)
                        .texOffs(150, 104).addBox( 2.5F, -2.5F, -0.38F, 1.0F, 5.0F, 0.76F)
                        .texOffs(150, 104).addBox(-3.0F, -3.0F, -0.37F, 1.2F, 1.2F, 0.74F)
                        .texOffs(150, 104).addBox( 1.8F, -3.0F, -0.37F, 1.2F, 1.2F, 0.74F)
                        .texOffs(150, 104).addBox(-3.0F,  1.8F, -0.37F, 1.2F, 1.2F, 0.74F)
                        .texOffs(150, 104).addBox( 1.8F,  1.8F, -0.37F, 1.2F, 1.2F, 0.74F),
                PartPose.offset(0.0F, -11.5F, 10.0F));

        // --- Transom, Rudder & Stern Lantern ---
        root.addOrReplaceChild("transom_lower",
                CubeListBuilder.create().texOffs(0, 104)
                        .addBox(-5.0F, -4.5F, 19.5F, 10.0F, 5.5F, 1.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("transom_upper",
                CubeListBuilder.create().texOffs(0, 104)
                        .addBox(-5.0F, -11.0F, 21.0F, 10.0F, 5.0F, 1.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("stern_windows",
                CubeListBuilder.create().texOffs(110, 130)
                        .addBox(-4.5F, -10.5F, 22.02F, 9.0F, 4.0F, 0.5F),
                PartPose.ZERO);

        root.addOrReplaceChild("transom_quarter_port",
                CubeListBuilder.create().texOffs(26, 104)
                        .addBox(-8.5F, -10.0F, 18.0F, 3.5F, 11.0F, 3.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("transom_quarter_stbd",
                CubeListBuilder.create().texOffs(42, 104)
                        .addBox(5.0F, -10.0F, 18.0F, 3.5F, 11.0F, 3.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("stern_crest",
                CubeListBuilder.create().texOffs(58, 104)
                        .addBox(-3.5F, -14.42F, 21.8F, 7.0F, 2.2F, 0.8F),
                PartPose.ZERO);

        root.addOrReplaceChild("stern_taffrail",
                CubeListBuilder.create().texOffs(58, 104)
                        .addBox(-5.5F, -12.21F, 21.2F, 11.0F, 1.2F, 1.4F),
                PartPose.ZERO);

        root.addOrReplaceChild("rudder_post",
                CubeListBuilder.create().texOffs(224, 218)
                        .addBox(-0.8F, -4.0F, 21.0F, 1.6F, 9.0F, 2.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("lantern_bracket",
                CubeListBuilder.create().texOffs(140, 104)
                        .addBox(-0.5F, -13.8F, 22.62F, 1.0F, 2.6F, 2.3F),
                PartPose.ZERO);

        root.addOrReplaceChild("stern_lantern",
                CubeListBuilder.create()
                        .texOffs(150, 147).addBox(-0.5F, -15.5F, 24.5F, 1.0F, 1.0F, 1.0F)
                        .texOffs(150, 130).addBox(-1.5F, -14.5F, 23.5F, 3.0F, 1.0F, 3.0F)
                        .texOffs(150, 135).addBox(-1.5F, -13.5F, 23.5F, 3.0F, 3.0F, 3.0F)
                        .texOffs(150, 142).addBox(-1.5F, -10.5F, 23.5F, 3.0F, 1.0F, 3.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 256, 256);
    }

    // --- Dynamic Ship Animations ---

    @Override
    public void setupAnim(Boat entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {

        // Main sail & Topsail: wind strength dynamically scales with weather (gale/storm billowing)
        boolean isThundering = entity.level().isThundering();
        boolean isRaining = entity.level().isRaining();
        float windSpeed = isThundering ? 0.22F : (isRaining ? 0.14F : 0.08F);
        float windAmplitude = isThundering ? 0.09F : (isRaining ? 0.06F : 0.04F);
        float windBase = isThundering ? -0.12F : -0.06F;

        float windBow = windBase - Mth.sin(ageInTicks * windSpeed) * windAmplitude;
        this.sail.xRot = windBow;
        this.sailTop.xRot = windBow * 1.15F;

        // Steering wheel: rotates smoothly around its central axis (Z axis)
        float turnRate = entity.getYRot() - entity.yRotO;
        if (Math.abs(turnRate) > 0.05F) {
            // Turning: wheel smoothly spins in steering direction
            this.helmWheel.zRot = -Mth.clamp(turnRate * 0.3F, -1.5F, 1.5F);
        } else if (entity.getDeltaMovement().lengthSqr() > 0.001) {
            // Sailing forward: subtle ocean wave sway
            this.helmWheel.zRot = Mth.sin(ageInTicks * 0.12F) * 0.15F;
        } else {
            this.helmWheel.zRot = 0.0F;
        }

        boolean isCargo = entity instanceof com.lexovian.currentsoftrade.entity.CargoBoatEntity;
        boolean isTrade = !isCargo && ((entity instanceof com.lexovian.currentsoftrade.entity.TradeBoatEntity trade && !trade.isVoyageShip())
                || (entity instanceof com.lexovian.currentsoftrade.entity.SloopEntity sloop && sloop.isMerchantShip()));

        // Cargo Boat equipment:
        this.cargoCrate.visible   = isCargo;
        this.cargoCrane.visible   = isCargo;
        this.cargoAnchors.visible = isCargo;
        this.cargoFenders.visible = isCargo;
        this.waistSeat.visible    = !isCargo;
        this.bowSeat.visible      = !isCargo;

        // Trade Ship equipment:
        this.merchantAwning.visible  = isTrade;
        this.merchantDesk.visible    = isTrade;
        this.tradeFigurehead.visible = isTrade;
        this.crowsNest.visible       = isTrade;
        this.tradePennant.visible    = isTrade;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                                int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
