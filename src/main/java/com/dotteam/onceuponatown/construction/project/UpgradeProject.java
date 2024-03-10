package com.dotteam.onceuponatown.construction.project;

import com.dotteam.onceuponatown.building.Building;
import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.server.level.ServerLevel;

public class UpgradeProject extends ConstructionProject{
    private Building toUpgrade;

    private RemovingEntitiesPhase phase1;
    private RemovingBlocksPhase phase2;
    private PlacingBlocksPhase phase3;
    private PlacingEntitiesPhase phase4;

    protected UpgradeProject(ServerLevel level, String name, BuildingPlacementSettings settings) {
        super(level, name, settings);
    }
}
