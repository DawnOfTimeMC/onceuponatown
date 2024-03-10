package com.dotteam.onceuponatown.building;

import net.minecraft.world.item.Item;

import java.util.HashMap;

public class BuildingType {
    private HashMap<Item, Integer> production = new HashMap<>();

    public BuildingType(HashMap<Item, Integer> production) {
        this.production = production;
    }

    public HashMap<Item, Integer> getProduction() {
        return this.production;
    }
}
