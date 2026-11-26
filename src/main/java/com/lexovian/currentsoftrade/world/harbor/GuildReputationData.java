package com.lexovian.currentsoftrade.world.harbor;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maritime Guild Reputation & Rank persistence per player.
 * Ranks: Deckhand (0+), Quartermaster (50+, -5% fee), Merchant Captain (150+, -10% fee), Admiral (350+, -15% fee).
 */
public class GuildReputationData extends SavedData {

    private record RepEntry(String uuid, int rep) {
        static final Codec<RepEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("UUID").forGetter(RepEntry::uuid),
                Codec.INT.optionalFieldOf("Rep", 0).forGetter(RepEntry::rep)
        ).apply(inst, RepEntry::new));
    }

    public static final Codec<GuildReputationData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            RepEntry.CODEC.listOf().optionalFieldOf("Players", List.of()).forGetter(GuildReputationData::toEntries)
    ).apply(inst, GuildReputationData::fromEntries));

    public static final SavedDataType<GuildReputationData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "guild_reputation"),
            GuildReputationData::new,
            CODEC
    );

    private final Map<UUID, Integer> reputationMap = new HashMap<>();

    public enum GuildRank {
        DECKHAND("deckhand", 0, 0.00D),
        QUARTERMASTER("quartermaster", 50, 0.05D),
        MERCHANT_CAPTAIN("merchant_captain", 150, 0.10D),
        ADMIRAL("admiral", 350, 0.15D);

        public final String id;
        public final int minPoints;
        public final double feeDiscount;

        GuildRank(String id, int minPoints, double feeDiscount) {
            this.id = id;
            this.minPoints = minPoints;
            this.feeDiscount = feeDiscount;
        }

        public Component getDisplayName() {
            return Component.translatable("rank.currents_of_trade." + this.id);
        }

        public static GuildRank fromPoints(int points) {
            GuildRank best = DECKHAND;
            for (GuildRank r : values()) {
                if (points >= r.minPoints) best = r;
            }
            return best;
        }
    }

    public GuildReputationData() {}

    private List<RepEntry> toEntries() {
        List<RepEntry> list = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : reputationMap.entrySet()) {
            list.add(new RepEntry(e.getKey().toString(), e.getValue()));
        }
        return list;
    }

    private static GuildReputationData fromEntries(List<RepEntry> entries) {
        GuildReputationData data = new GuildReputationData();
        for (RepEntry e : entries) {
            try {
                data.reputationMap.put(UUID.fromString(e.uuid()), e.rep());
            } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    public static GuildReputationData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public int getReputation(UUID playerId) {
        return reputationMap.getOrDefault(playerId, 0);
    }

    public GuildRank getRank(UUID playerId) {
        return GuildRank.fromPoints(getReputation(playerId));
    }

    public int applyRankDiscount(UUID playerId, int rawFee) {
        if (playerId == null || rawFee <= 1) return rawFee;
        double disc = getRank(playerId).feeDiscount;
        if (disc <= 0.0D) return rawFee;
        return Math.max(1, (int) Math.floor(rawFee * (1.0D - disc)));
    }

    public static void awardReputation(ServerLevel level, ServerPlayer player, int points) {
        if (level == null || player == null || points <= 0) return;
        GuildReputationData data = get(level);
        UUID id = player.getUUID();
        int prevPts = data.getReputation(id);
        GuildRank prevRank = GuildRank.fromPoints(prevPts);
        int nextPts = prevPts + points;
        data.reputationMap.put(id, nextPts);
        data.setDirty();

        GuildRank nextRank = GuildRank.fromPoints(nextPts);
        if (nextRank != prevRank) {
            int pct = (int) Math.round(nextRank.feeDiscount * 100.0D);
            player.sendSystemMessage(Component.translatable("message.currents_of_trade.guild_rank_up",
                    nextRank.getDisplayName(), pct));
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.05F);
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.5, 0.5, 0.5, 0.08);
        }
    }
}
