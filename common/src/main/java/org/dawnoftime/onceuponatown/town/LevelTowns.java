package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.dawnoftime.onceuponatown.datapack.QuestDataHandler;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LevelTowns extends SavedData {
    private static final String DATA_NAME = "ouat_towns";
    private final Map<Long, Town> towns = new HashMap<>();

    public static LevelTowns get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            LevelTowns::fromNbt,
            LevelTowns::new,
            DATA_NAME
        );
    }

    public void registerTown(BlockPos anchorPos, Town town) {
        towns.put(anchorPos.asLong(), town);
        setDirty();
    }

    public Optional<Town> getTownAt(BlockPos anchorPos) {
        return Optional.ofNullable(towns.get(anchorPos.asLong()));
    }

    public Collection<Town> getAllTowns() { return towns.values(); }

    public Set<Map.Entry<Long, Town>> getAllTownEntries() { return towns.entrySet(); }

    public Optional<Town> getNearestTown(BlockPos pos, int maxRadius) {
        double maxRadiusSq = (double) maxRadius * maxRadius;
        return towns.entrySet().stream()
            .filter(e -> BlockPos.of(e.getKey()).distSqr(pos) <= maxRadiusSq)
            .min(Comparator.comparingDouble(e -> BlockPos.of(e.getKey()).distSqr(pos)))
            .map(Map.Entry::getValue);
    }

    // Call after any Town mutation so SavedData schedules a write on next autosave
    public void markDirty() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        towns.forEach((posLong, town) -> {
            CompoundTag entry = town.toNbt();
            entry.putLong("AnchorPos", posLong);
            list.add(entry);
        });
        tag.put("Towns", list);
        return tag;
    }

    public static LevelTowns fromNbt(CompoundTag tag) {
        LevelTowns lt = new LevelTowns();
        tag.getList("Towns", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag entry = (CompoundTag) t;
            long posLong = entry.getLong("AnchorPos");
            lt.towns.put(posLong, Town.fromNbt(entry));
        });
        Set<String> validQuestIds = QuestDataHandler.getRegistryKeySet();
        boolean anyChanged = false;
        for (Town town : lt.towns.values()) {
            if (town.cleanupOrphanedQuestData(validQuestIds)) anyChanged = true;
        }
        if (anyChanged) lt.setDirty();
        return lt;
    }
}
