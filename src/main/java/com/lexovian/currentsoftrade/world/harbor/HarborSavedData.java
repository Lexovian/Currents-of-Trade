package com.lexovian.currentsoftrade.world.harbor;

import com.lexovian.currentsoftrade.CurrentsofTrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class HarborSavedData extends SavedData {

    private record HarborEntry(long posLong, Optional<String> name, int level, List<HarborTradeOffer> trades) {
        static final Codec<HarborEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.fieldOf("Pos").forGetter(HarborEntry::posLong),
                Codec.STRING.optionalFieldOf("Name").forGetter(HarborEntry::name),
                Codec.INT.optionalFieldOf("Level", 1).forGetter(HarborEntry::level),
                HarborTradeOffer.CODEC.listOf().optionalFieldOf("Trades", List.of()).forGetter(HarborEntry::trades)
        ).apply(inst, HarborEntry::new));
    }

    public static final Codec<HarborSavedData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            HarborEntry.CODEC.listOf().optionalFieldOf("Harbors", List.of()).forGetter(HarborSavedData::toEntries)
    ).apply(inst, HarborSavedData::fromEntries));

    public static final SavedDataType<HarborSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CurrentsofTrade.MODID, "harbors"),
            HarborSavedData::new,
            CODEC
    );

    private final Map<BlockPos, List<HarborTradeOffer>> harborTradesMap = new HashMap<>();
    private final Map<BlockPos, String> harborNamesMap = new HashMap<>();
    private final Map<BlockPos, Integer> harborLevelsMap = new HashMap<>();

    // --- Persistence & Access ---

    public HarborSavedData() {
    }

    public static HarborSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
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

    public Set<BlockPos> getAllHarborPositions() {
        Set<BlockPos> all = new HashSet<>(harborTradesMap.keySet());
        all.addAll(harborNamesMap.keySet());
        all.addAll(harborLevelsMap.keySet());
        return all;
    }

    public boolean hasHarbor(BlockPos pos) {
        return harborNamesMap.containsKey(pos) || harborTradesMap.containsKey(pos) || harborLevelsMap.containsKey(pos);
    }

    public void removeHarbor(BlockPos pos) {
        boolean changed = harborTradesMap.remove(pos) != null;
        if (harborNamesMap.remove(pos) != null) changed = true;
        if (harborLevelsMap.remove(pos) != null) changed = true;
        if (changed) setDirty();
    }

    // --- Codec Serialization ---

    private static HarborSavedData fromEntries(List<HarborEntry> entries) {
        HarborSavedData data = new HarborSavedData();
        for (HarborEntry entry : entries) {
            BlockPos pos = BlockPos.of(entry.posLong());
            entry.name().ifPresent(name -> data.harborNamesMap.put(pos, name));
            if (entry.level() > 1) {
                data.harborLevelsMap.put(pos, entry.level());
            }
            data.harborTradesMap.put(pos, new ArrayList<>(entry.trades()));
        }
        return data;
    }

    private List<HarborEntry> toEntries() {
        List<HarborEntry> result = new ArrayList<>();
        Set<BlockPos> allPositions = getAllHarborPositions();
        for (BlockPos pos : allPositions) {
            String name = harborNamesMap.get(pos);
            int level = harborLevelsMap.getOrDefault(pos, 1);
            List<HarborTradeOffer> trades = harborTradesMap.getOrDefault(pos, List.of());
            result.add(new HarborEntry(
                    pos.asLong(),
                    name != null ? Optional.of(name) : Optional.empty(),
                    level,
                    trades
            ));
        }
        return result;
    }
}
