package com.lexovian.currentsoftrade.client;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.lexovian.currentsoftrade.block.entity.AnchorPointBlockEntity;
import com.lexovian.currentsoftrade.entity.CargoBoatEntity;
import com.lexovian.currentsoftrade.entity.SloopEntity;
import com.lexovian.currentsoftrade.entity.TradeBoatEntity;
import com.lexovian.currentsoftrade.entity.WanderingSailorEntity;
import com.lexovian.currentsoftrade.item.CaptainsSpyglassItem;
import com.lexovian.currentsoftrade.item.SaltPouchItem;
import com.lexovian.currentsoftrade.world.harbor.HarborSpecialization;
import com.lexovian.currentsoftrade.world.harbor.HarborTradeOffer;
import com.lexovian.currentsoftrade.world.market.MaritimeTradeRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-side event listeners: salted food tooltips, Captain's Brass Spyglass zoom,
 * and the live Maritime Recon HUD.
 */
@EventBusSubscriber(modid = CurrentsofTrade.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.player == null || mc.level == null) return;
        if (mc.level.getGameTime() % 5L != 0L) return;

        ItemStack heldChart = ItemStack.EMPTY;
        if (mc.player.getMainHandItem().getItem() instanceof com.lexovian.currentsoftrade.item.NauticalChartItem) {
            heldChart = mc.player.getMainHandItem();
        } else if (mc.player.getOffhandItem().getItem() instanceof com.lexovian.currentsoftrade.item.NauticalChartItem) {
            heldChart = mc.player.getOffhandItem();
        }
        if (heldChart.isEmpty()) return;

        List<BlockPos> waypoints = com.lexovian.currentsoftrade.item.NauticalChartItem.getWaypoints(heldChart);
        if (waypoints.isEmpty()) return;

        double prevX = 0.0;
        double prevY = 0.0;
        double prevZ = 0.0;
        for (int idx = 0; idx < waypoints.size(); idx++) {
            BlockPos wp = waypoints.get(idx);
            double x = wp.getX() + 1.0;
            double y = wp.getY() + 0.3;
            double z = wp.getZ() + 1.0;
            if (mc.player.distanceToSqr(x, y, z) < 160.0 * 160.0) {
                for (int k = 0; k < 4; k++) {
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            x + (mc.level.getRandom().nextDouble() - 0.5) * 0.3,
                            y + k * 1.1 + mc.level.getRandom().nextDouble() * 0.4,
                            z + (mc.level.getRandom().nextDouble() - 0.5) * 0.3,
                            0.0, 0.015, 0.0);
                }
            }
            if (idx > 0) {
                double dx = x - prevX;
                double dz = z - prevZ;
                double len = Math.hypot(dx, dz);
                int dots = (int) Math.min(80, len / 3.0);
                for (int k = 1; k < dots; k++) {
                    double t = (double) k / dots;
                    double px = prevX + dx * t;
                    double py = prevY + (y - prevY) * t;
                    double pz = prevZ + dz * t;
                    if (mc.player.distanceToSqr(px, py, pz) < 64.0 * 64.0) {
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                px, py, pz, 0.0, 0.0, 0.0);
                    }
                }
            }
            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (SaltPouchItem.isSalted(stack)) {
            List<Component> tooltip = event.getToolTip();
            int insertIndex = Math.min(1, tooltip.size());
            tooltip.add(insertIndex, Component.translatable("tooltip.currents_of_trade.salted_food"));
            tooltip.add(insertIndex + 1, Component.translatable("tooltip.currents_of_trade.salted_food_bonus"));
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson() && CaptainsSpyglassItem.isScoping(event.getPlayer())) {
            event.setNewFovModifier(0.12F);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!mc.options.getCameraType().isFirstPerson() || !CaptainsSpyglassItem.isScoping(mc.player)) return;

        GuiGraphicsExtractor g = event.getGuiGraphics();
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();

        renderBrassScopeReticle(g, screenW, screenH);

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        renderNavalReconHud(mc, g, screenW, screenH, partialTick);
    }

    private static void renderBrassScopeReticle(GuiGraphicsExtractor g, int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        int halfLensW = Math.min(screenW * 38 / 100, 175);
        int halfLensH = Math.min(screenH * 38 / 100, 125);

        int left = cx - halfLensW;
        int right = cx + halfLensW;
        int top = cy - halfLensH;
        int bottom = cy + halfLensH;

        int vignette = 0xCC060E18;
        g.fill(0, 0, screenW, top, vignette);
        g.fill(0, bottom, screenW, screenH, vignette);
        g.fill(0, top, left, bottom, vignette);
        g.fill(right, top, screenW, bottom, vignette);

        // Outer & inner brass spyglass bezel
        g.outline(left - 2, top - 2, halfLensW * 2 + 4, halfLensH * 2 + 4, 0xFF8D6E2A);
        g.outline(left - 1, top - 1, halfLensW * 2 + 2, halfLensH * 2 + 2, 0xFFD4AF37);
        g.outline(left, top, halfLensW * 2, halfLensH * 2, 0x66FFE082);

        // Nautical rangefinder ticks around center crosshair
        g.fill(cx - 14, cy, cx - 4, cy + 1, 0xAAFFE082);
        g.fill(cx + 5, cy, cx + 15, cy + 1, 0xAAFFE082);
        g.fill(cx, cy - 10, cx + 1, cy - 3, 0xAAFFE082);
        g.fill(cx, cy + 4, cx + 1, cy + 11, 0xAAFFE082);
    }

    private static void renderNavalReconHud(Minecraft mc, GuiGraphicsExtractor g, int screenW, int screenH, float partialTick) {
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 look = mc.player.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(CaptainsSpyglassItem.RECON_RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player));
        double blockDist = blockHit.getType() != HitResult.Type.MISS
                ? eye.distanceTo(blockHit.getLocation())
                : CaptainsSpyglassItem.RECON_RANGE;

        // Check ships & sailors along the telescope beam
        Entity bestTarget = null;
        double bestEntityDist = blockDist + 3.0D;
        AABB searchBox = new AABB(eye, end).inflate(5.0D);
        for (Entity candidate : level.getEntities(mc.player, searchBox,
                e -> e.isAlive() && (e instanceof SloopEntity || e instanceof TradeBoatEntity || e instanceof WanderingSailorEntity))) {
            boolean standingOnCandidateDeck = (candidate instanceof SloopEntity || candidate instanceof TradeBoatEntity)
                    && candidate.getBoundingBox().inflate(3.8D, 2.4D, 3.8D).contains(mc.player.position());
            if (standingOnCandidateDeck && look.y > -0.45D) {
                continue;
            }
            AABB hitBox = (candidate instanceof SloopEntity || candidate instanceof TradeBoatEntity)
                    ? candidate.getBoundingBox().inflate(2.2D, 4.5D, 2.2D)
                    : candidate.getBoundingBox().inflate(0.6D);
            Optional<Vec3> clip = hitBox.clip(eye, end);
            if (clip.isPresent()) {
                double d = eye.distanceTo(clip.get());
                if (d < bestEntityDist) {
                    bestEntityDist = d;
                    bestTarget = candidate;
                }
            } else if (hitBox.contains(eye)) {
                bestTarget = candidate;
                bestEntityDist = 0.0D;
                break;
            }
        }

        List<Component> lines = new ArrayList<>();
        int accent = 0x5BC0EB;

        if (bestTarget != null && bestTarget != mc.player.getVehicle()) {
            int meters = Math.max(1, (int) Math.round(eye.distanceTo(bestTarget.position())));
            if (bestTarget instanceof SloopEntity sloop) {
                accent = 0xFFD54F;
                lines.add(Component.literal("⛵ ").append(sloop.getDisplayName()).append(Component.literal(" §7(" + meters + "m)")));
                int maxHp = 40;
                int curHp = Math.max(1, Math.round(maxHp - sloop.getDamage()));
                lines.add(Component.translatable("hud.currents_of_trade.recon.ship_status",
                        curHp, maxHp, sloop.getSyncedCargoStacks(), sloop.getContainerSize()));
                String upgrades = buildSloopUpgrades(sloop);
                if (!upgrades.isEmpty()) {
                    lines.add(Component.literal("§6⚙ " + upgrades));
                }
            } else if (bestTarget instanceof CargoBoatEntity cargo) {
                accent = 0xFFB74D;
                lines.add(Component.literal("⛵ ").append(cargo.getDisplayName()).append(Component.literal(" §7(" + meters + "m)")));
                int hp = Math.max(1, Math.round(cargo.getSyncedShipHealth()));
                lines.add(Component.translatable("hud.currents_of_trade.recon.ship_status",
                        hp, (int) TradeBoatEntity.MAX_SHIP_HEALTH, cargo.getSyncedCargoStacks(), cargo.getContainerSize()));
                String dest = cargo.getSyncedDestHarbor();
                if (!dest.isEmpty()) {
                    lines.add(Component.translatable("hud.currents_of_trade.recon.route", dest));
                }
                String upgrades = buildTradeShipUpgrades(cargo);
                if (!upgrades.isEmpty()) {
                    lines.add(Component.literal("§6⚙ " + upgrades));
                }
            } else if (bestTarget instanceof TradeBoatEntity trade) {
                accent = 0xFFD54F;
                lines.add(Component.literal("⛵ ").append(trade.getDisplayName()).append(Component.literal(" §7(" + meters + "m)")));
                int hp = Math.max(1, Math.round(trade.getSyncedShipHealth()));
                lines.add(Component.translatable("hud.currents_of_trade.recon.ship_status",
                        hp, (int) TradeBoatEntity.MAX_SHIP_HEALTH, trade.getSyncedCargoStacks(), trade.getContainerSize()));
                String dest = trade.getSyncedDestHarbor();
                if (!dest.isEmpty()) {
                    lines.add(Component.translatable("hud.currents_of_trade.recon.route", dest));
                }
                String upgrades = buildTradeShipUpgrades(trade);
                if (!upgrades.isEmpty()) {
                    lines.add(Component.literal("§6⚙ " + upgrades));
                }
            } else if (bestTarget instanceof WanderingSailorEntity sailor) {
                accent = 0x4FC3F7;
                lines.add(Component.literal("⚓ ").append(sailor.getDisplayName()).append(Component.literal(" §7(" + meters + "m)")));
                lines.add(Component.translatable("hud.currents_of_trade.recon.sailor"));
            }
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            BlockPos hitPos = blockHit.getBlockPos();
            int meters = Math.max(1, (int) Math.round(blockDist));
            AnchorPointBlockEntity anchor = findNearbyAnchor(level, hitPos);
            if (anchor != null) {
                MaritimeTradeRegion region = anchor.getTradeRegion();
                HarborSpecialization spec = anchor.getSpecialization();
                accent = spec.isActive() ? spec.getColor() : region.getColor();

                lines.add(Component.literal("⚓ §e" + anchor.getHarborName() + " §6[Lv." + anchor.getTradeLevel() + "] §7(" + meters + "m)"));
                lines.add(Component.literal("§b").append(region.getDisplayName()).append(Component.literal(" §7| ")).append(spec.getShortBadge()));

                for (HarborTradeOffer bounty : anchor.getActiveUrgentBounties()) {
                    if (bounty != null && !bounty.getCostA().isEmpty()) {
                        int rewCount = bounty.getResult().getCount();
                        if (spec == HarborSpecialization.FREE_PORT && bounty.getResult().is(CurrentsofTrade.DOUBLOON.get())) {
                            rewCount = Math.max(rewCount + 1, Math.round(rewCount * 1.25F));
                        }
                        lines.add(Component.literal("§6★ ")
                                .append(Component.literal(bounty.getCostA().getCount() + "x "))
                                .append(bounty.getCostA().getHoverName())
                                .append(Component.literal(" §7-> §e" + rewCount + " "))
                                .append(bounty.getResult().getHoverName()));
                    }
                }
            } else {
                MaritimeTradeRegion region = MaritimeTradeRegion.fromBiome(level.getBiome(hitPos));
                accent = region.getColor();
                lines.add(Component.literal("🧭 ").append(region.getDisplayName()).append(Component.literal(" §7(" + meters + "m)")));
                lines.add(region.getExportDescription());
                lines.add(region.getImportDescription());
            }
        }

        if (lines.isEmpty()) return;

        Font font = mc.font;
        int maxW = 120;
        for (Component line : lines) {
            maxW = Math.max(maxW, font.width(line));
        }
        int padX = 8;
        int padY = 5;
        int boxW = maxW + padX * 2 + 3;
        int boxH = padY * 2 + lines.size() * 11 - 1;
        int boxX = (screenW - boxW) / 2;
        int boxY = Math.max(8, screenH - 54 - boxH);

        int border = (0xFF << 24) | (accent & 0xFFFFFF);
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD0B1928);
        g.outline(boxX, boxY, boxW, boxH, border);
        g.fill(boxX, boxY, boxX + 3, boxY + boxH, border);

        for (int i = 0; i < lines.size(); i++) {
            int color = (i == 0) ? ((0xFF << 24) | (accent & 0xFFFFFF)) : 0xFFE0E8F0;
            g.text(font, lines.get(i), boxX + padX + 2, boxY + padY + i * 11, color, true);
        }
    }

    private static AnchorPointBlockEntity findNearbyAnchor(Level level, BlockPos center) {
        if (level.getBlockEntity(center) instanceof AnchorPointBlockEntity direct) {
            return direct;
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (level.getBlockEntity(pos) instanceof AnchorPointBlockEntity nearby) {
                return nearby;
            }
        }
        return null;
    }

    private static String buildSloopUpgrades(SloopEntity sloop) {
        List<String> tags = new ArrayList<>();
        if (sloop.hasCopperHull()) tags.add(Component.translatable("hud.currents_of_trade.upgrade.copper").getString());
        if (sloop.hasSwivelHarpoon()) tags.add(Component.translatable("hud.currents_of_trade.upgrade.harpoon").getString());
        if (sloop.getBowLanternType() == 2) tags.add(Component.translatable("hud.currents_of_trade.upgrade.soul_lantern").getString());
        else if (sloop.getBowLanternType() == 1) tags.add(Component.translatable("hud.currents_of_trade.upgrade.lantern").getString());
        return String.join(" · ", tags);
    }

    private static String buildTradeShipUpgrades(TradeBoatEntity boat) {
        List<String> tags = new ArrayList<>();
        if (boat.isSyncedDrydockReinforced()) tags.add(Component.translatable("hud.currents_of_trade.upgrade.drydock").getString());
        if (boat.hasSwivelHarpoon()) tags.add(Component.translatable("hud.currents_of_trade.upgrade.harpoon").getString());
        return String.join(" · ", tags);
    }
}
