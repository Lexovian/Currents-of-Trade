package com.lexovian.currentsoftrade.world.harbor;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maritime Guild Reputation & Rank persistence per player.
 * Ranks: Deckhand (0+), Quartermaster (50+, -5% fee), Merchant Captain (150+, -10% fee), Admiral (350+, -15% fee).
 */
public class GuildReputationData extends SavedData {

    private static final String DATA_NAME = "currents_of_trade_guild_reputation";
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

    public static GuildReputationData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GuildReputationData::new, GuildReputationData::load),
                DATA_NAME
        );
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
            player.displayClientMessage(Component.translatable("message.currents_of_trade.guild_rank_up",
                    nextRank.getDisplayName(), pct), false);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.05F);
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.5, 0.5, 0.5, 0.08);
        }
    }

    public static GuildReputationData load(CompoundTag tag, HolderLookup.Provider registries) {
        GuildReputationData data = new GuildReputationData();
        if (tag.contains("Players", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.hasUUID("UUID")) {
                    data.reputationMap.put(entry.getUUID("UUID"), entry.getInt("Rep"));
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> e : reputationMap.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", e.getKey());
            entry.putInt("Rep", e.getValue());
            list.add(entry);
        }
        tag.put("Players", list);
        return tag;
    }
}
