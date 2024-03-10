package com.dotteam.onceuponatown.culture;

import net.minecraft.resources.ResourceLocation;
import org.checkerframework.checker.units.qual.C;

import java.util.List;

public class Culture {
    public static final Culture PLAINS = new Culture("plains");
    private String id;
    private List<ResourceLocation> buildingStarterPack;
    private List<ResourceLocation> buildingPrimaryPool;

    public String getId() {
        return id;
    }

    public Culture(String id) {
        this.id = id;
    }
}
