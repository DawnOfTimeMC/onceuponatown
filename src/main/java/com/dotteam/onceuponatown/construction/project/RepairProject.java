package com.dotteam.onceuponatown.construction.project;

import com.dotteam.onceuponatown.building.Building;
import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.server.level.ServerLevel;

public class RepairProject extends ConstructionProject{
    private Building toRepair;

    private RemovingEntitiesPhase phase1;
    private RemovingBlocksPhase phase2;
    private PlacingBlocksPhase phase3;
    private PlacingEntitiesPhase phase4;

    protected RepairProject(ServerLevel level, String name, BuildingPlacementSettings settings) {
        super(level, name, settings);
    }
}
