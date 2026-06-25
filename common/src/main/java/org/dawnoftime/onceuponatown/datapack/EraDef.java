package org.dawnoftime.onceuponatown.datapack;

import java.util.List;

public class EraDef {
    public final int era;
    public final String orientation;
    // Human-readable orientation name (e.g. "Agricultural", "Pastoral")
    public final String orientationLabel;
    // Human-readable structure type (e.g. "Settlement", "Village", "Castle")
    public final String structureLabel;
    public final String iconItem;
    // defId of the starter building placed at world gen that triggers this era state
    public final String starterBuildingId;
    public final List<String> boostedBuildings;
    // Starting weight cap for this orientation (increases with each era transition)
    public final int initialMaxWeight;
    // Production multiplier stamped on boosted buildings at placement time (default 1.0 = no bonus)
    public final double boostMultiplier;

    public EraDef(int era, String orientation, String orientationLabel, String structureLabel,
                  String iconItem, String starterBuildingId, List<String> boostedBuildings,
                  int initialMaxWeight, double boostMultiplier) {
        this.era = era;
        this.orientation = orientation;
        this.orientationLabel = orientationLabel;
        this.structureLabel = structureLabel;
        this.iconItem = iconItem;
        this.starterBuildingId = starterBuildingId;
        this.boostedBuildings = boostedBuildings;
        this.initialMaxWeight = initialMaxWeight;
        this.boostMultiplier = boostMultiplier;
    }
}
