package com.lexovian.currentsoftrade.world.harbor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HarborSavedData extends SavedData {

    private static final String DATA_NAME = "currents_of_trade_harbors";
    private final Map<BlockPos, List<HarborTradeOffer>> harborTradesMap = new HashMap<>();
    private final Map<BlockPos, String> harborNamesMap = new HashMap<>();
    private final Map<BlockPos, Integer> harborLevelsMap = new HashMap<>();
    private final Map<BlockPos, HarborSpecialization> harborSpecializationsMap = new HashMap<>();

    // --- Persistence & Access ---

    public HarborSavedData() {
    }

    public static HarborSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                tag -> HarborSavedData.load(tag, overworld.registryAccess()),
                HarborSavedData::new,
                DATA_NAME
        );
    }

    // --- Harbor Data Accessors ---

    public List<HarborTradeOffer> getTrades(BlockPos pos) {
        return harborTradesMap.getOrDefault(pos, List.of());
    }

    public void setTrades(BlockPos pos, List<HarborTradeOffer> trades) {
        harborTradesMap.put(pos.immutable(), new ArrayList<>(trades));
        setDirty();
    }

    public String getHarborName(BlockPos pos) {
        return harborNamesMap.get(pos);
    }

    public void setHarborName(BlockPos pos, String name) {
        if (name != null && !name.trim().isEmpty()) {
            harborNamesMap.put(pos.immutable(), name.trim());
        } else {
            harborNamesMap.remove(pos);
        }
        setDirty();
    }

    public int getHarborLevel(BlockPos pos) {
        return harborLevelsMap.getOrDefault(pos, 1);
    }

    public void setHarborLevel(BlockPos pos, int level) {
        harborLevelsMap.put(pos.immutable(), Math.max(1, Math.min(5, level)));
        setDirty();
    }

    public HarborSpecialization getHarborSpecialization(BlockPos pos) {
        return harborSpecializationsMap.getOrDefault(pos, HarborSpecialization.NONE);
    }

    public void setHarborSpecialization(BlockPos pos, HarborSpecialization spec) {
        if (spec != null && spec != HarborSpecialization.NONE) {
            harborSpecializationsMap.put(pos.immutable(), spec);
        } else {
            harborSpecializationsMap.remove(pos);
        }
        setDirty();
    }

    public Set<BlockPos> getAllHarborPositions() {
        Set<BlockPos> all = new HashSet<>(harborTradesMap.keySet());
        all.addAll(harborNamesMap.keySet());
        all.addAll(harborLevelsMap.keySet());
        all.addAll(harborSpecializationsMap.keySet());
        return all;
    }

    public boolean hasHarbor(BlockPos pos) {
        return harborNamesMap.containsKey(pos) || harborTradesMap.containsKey(pos)
                || harborLevelsMap.containsKey(pos) || harborSpecializationsMap.containsKey(pos);
    }

    public void removeHarbor(BlockPos pos) {
        boolean changed = harborTradesMap.remove(pos) != null;
        if (harborNamesMap.remove(pos) != null) changed = true;
        if (harborLevelsMap.remove(pos) != null) changed = true;
        if (harborSpecializationsMap.remove(pos) != null) changed = true;
        if (changed) setDirty();
    }

    // --- NBT Serialization ---

    public static HarborSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HarborSavedData data = new HarborSavedData();
        ListTag harborsList = tag.getList("Harbors", Tag.TAG_COMPOUND);
        for (int i = 0; i < harborsList.size(); i++) {
            CompoundTag harborTag = harborsList.getCompound(i);
            BlockPos pos = BlockPos.of(harborTag.getLong("Pos"));

            if (harborTag.contains("Name")) {
                data.harborNamesMap.put(pos, harborTag.getString("Name"));
            }
            if (harborTag.contains("Level")) {
                data.harborLevelsMap.put(pos, harborTag.getInt("Level"));
            }
            if (harborTag.contains("Specialization")) {
                HarborSpecialization spec = HarborSpecialization.byId(harborTag.getString("Specialization"));
                if (spec != HarborSpecialization.NONE) {
                    data.harborSpecializationsMap.put(pos, spec);
                }
            }

            ListTag tradesList = harborTag.getList("Trades", Tag.TAG_COMPOUND);
            List<HarborTradeOffer> trades = new ArrayList<>();
            for (int j = 0; j < tradesList.size(); j++) {
                trades.add(HarborTradeOffer.fromTag(tradesList.getCompound(j), registries));
            }
            data.harborTradesMap.put(pos, trades);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        final HolderLookup.Provider registries = null; // unused in 1.20.1 (plain NBT item serialization)
        ListTag harborsList = new ListTag();
        Set<BlockPos> allPositions = new HashSet<>();
        allPositions.addAll(harborTradesMap.keySet());
        allPositions.addAll(harborNamesMap.keySet());
        allPositions.addAll(harborLevelsMap.keySet());
        allPositions.addAll(harborSpecializationsMap.keySet());

        for (BlockPos pos : allPositions) {
            CompoundTag harborTag = new CompoundTag();
            harborTag.putLong("Pos", pos.asLong());

            String name = harborNamesMap.get(pos);
            if (name != null) harborTag.putString("Name", name);

            Integer level = harborLevelsMap.get(pos);
            if (level != null && level > 1) harborTag.putInt("Level", level);

            HarborSpecialization spec = harborSpecializationsMap.get(pos);
            if (spec != null && spec != HarborSpecialization.NONE) {
                harborTag.putString("Specialization", spec.getId());
            }

            ListTag trades = harborTradesMap.get(pos) != null ? new ListTag() : null;
            List<HarborTradeOffer> tradeOffers = harborTradesMap.get(pos);
            if (tradeOffers != null && !tradeOffers.isEmpty() && trades != null) {
                for (HarborTradeOffer offer : tradeOffers) {
                    trades.add(offer.toTag(registries));
                }
                harborTag.put("Trades", trades);
            }

            harborsList.add(harborTag);
        }
        tag.put("Harbors", harborsList);
        return tag;
    }
}
