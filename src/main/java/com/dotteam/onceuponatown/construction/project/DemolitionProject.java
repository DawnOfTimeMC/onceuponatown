package com.dotteam.onceuponatown.construction.project;

import com.dotteam.onceuponatown.building.Building;
import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.server.level.ServerLevel;

public class DemolitionProject extends ConstructionProject {
    private Building toDemolish;
    private RemovingEntitiesPhase phase1;
    private RemovingBlocksPhase phase2;

    protected DemolitionProject(ServerLevel level, String name, BuildingPlacementSettings settings) {
        super(level, name, settings);
    }
}
