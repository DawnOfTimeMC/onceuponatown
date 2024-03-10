package com.dotteam.onceuponatown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;

public class TownSavedData extends SavedData {
    private final List<Town> towns = new ArrayList<>();
    private final Level level;

    public static TownSavedData get(ServerLevel level) {
        if (!level.isClientSide()) {
            DimensionDataStorage storage = level.getDataStorage();
            // First argument is load function, second is create function
            return storage.computeIfAbsent((tag) -> new TownSavedData(level, tag), () -> new TownSavedData(level), "towns");
        } else {
            return null;
        }
    }

    private TownSavedData(ServerLevel level) {
        this.level = level;
        //setDirty();
    }

    private TownSavedData(ServerLevel level, CompoundTag tag) {
        this(level);
        load(tag);
    }

    public boolean isDirty() {
        return true;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag townsTag = new ListTag();
        for (Town town : this.towns) {
            CompoundTag townTag = new CompoundTag();
            town.saveNBT(townTag);
            townsTag.add(townTag);
        }
        tag.put("Towns", townsTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        ListTag townsTag = tag.getList("Towns", 10);
        for(int i = 0; i < townsTag.size(); ++i) {
            CompoundTag townTag = townsTag.getCompound(i);
            loadtown(townTag);
        }
    }

    private void loadtown(CompoundTag tag) {
        this.towns.add(Town.loadtown(this.level, tag));
    }

    public List<Town> gettowns() {
        return this.towns;
    }

    public boolean addtown(Town town) {
        //setDirty();
        return this.towns.add(town);
    }

    public boolean removetown(Town town) {
        //setDirty();
        return this.towns.remove(town);
    }
}
