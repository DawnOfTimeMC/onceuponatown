package com.dotteam.onceuponatown.building;

import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Building {
    private BuildingType buildingType;
    private ResourceLocation structurePath;
    private BuildingPlacementSettings placementSettings;
    private BlockPos position;
    private List<BlockPos> sleepPositions = new ArrayList<>();
    private List<BlockPos> workPositions = new ArrayList<>();

    private Building(BuildingType buildingType) {
        this.buildingType = buildingType;
    }

    public static Building create(BuildingType buildingType) {
        return new Building(buildingType);
    }

    public static Building loadBuilding() {
        return null;
    }

    public void saveNBT(CompoundTag tag) {

    }

    public BlockPos getPosition() {
        return position;
    }

    public ResourceLocation getStructurePath() {
        return structurePath;
    }

    public HashMap<Item, Integer> getProduction() {
        return this.buildingType.getProduction();
    }
}
