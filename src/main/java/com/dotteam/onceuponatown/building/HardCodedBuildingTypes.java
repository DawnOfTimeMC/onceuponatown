package com.dotteam.onceuponatown.building;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;

public class HardCodedBuildingTypes {
    public static BuildingType STANDARD_TYPE = new BuildingType(null);
    public static BuildingType LUMBERJACK;
    public static BuildingType SHEEP_FARM;

    static {
        HashMap<Item, Integer> lumberjackProduction = new HashMap<>();
        lumberjackProduction.put(Items.OAK_LOG, 10);
        lumberjackProduction.put(Items.SPRUCE_LOG, 10);
        LUMBERJACK = new BuildingType(lumberjackProduction);

        HashMap<Item, Integer> sheepFarmProduction = new HashMap<>();
        sheepFarmProduction.put(Items.MUTTON, 3);
        sheepFarmProduction.put(Items.WHITE_WOOL, 4);
        SHEEP_FARM = new BuildingType(sheepFarmProduction);
    }
}
