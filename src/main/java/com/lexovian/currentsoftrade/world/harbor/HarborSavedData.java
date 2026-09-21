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

    public HarborSavedData() {
    }

    public static HarborSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HarborSavedData::new, HarborSavedData::load),
                DATA_NAME
        );
    }

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

    public Set<BlockPos> getAllHarborPositions() {
        Set<BlockPos> all = new HashSet<>(harborTradesMap.keySet());
        all.addAll(harborNamesMap.keySet());
        all.addAll(harborLevelsMap.keySet());
        return all;
    }

    public void removeHarbor(BlockPos pos) {
        boolean changed = harborTradesMap.remove(pos) != null;
        if (harborNamesMap.remove(pos) != null) changed = true;
        if (harborLevelsMap.remove(pos) != null) changed = true;
        if (changed) setDirty();
    }

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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag harborsList = new ListTag();
        // Collect all known harbor positions from both trades and names
        Set<BlockPos> allPositions = new HashSet<>();
        allPositions.addAll(harborTradesMap.keySet());
        allPositions.addAll(harborNamesMap.keySet());

        for (BlockPos pos : allPositions) {
            CompoundTag harborTag = new CompoundTag();
            harborTag.putLong("Pos", pos.asLong());

            String name = harborNamesMap.get(pos);
            if (name != null) harborTag.putString("Name", name);

            Integer level = harborLevelsMap.get(pos);
            if (level != null && level > 1) harborTag.putInt("Level", level);

            List<HarborTradeOffer> trades = harborTradesMap.get(pos);
            if (trades != null && !trades.isEmpty()) {
                ListTag tradesList = new ListTag();
                for (HarborTradeOffer offer : trades) {
                    tradesList.add(offer.toTag(registries));
                }
                harborTag.put("Trades", tradesList);
            }

            harborsList.add(harborTag);
        }
        tag.put("Harbors", harborsList);
        return tag;
    }
}
