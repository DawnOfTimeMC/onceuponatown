package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;

public class ActivityInstance {

    public enum Phase { TRAVELING, APPROACHING, PERFORMING }

    public final ActivityDef def;
    public final PlacedBuilding targetBuilding;
    public Phase phase;
    public final GoToPosition goToPosition;

    // Populated when entering APPROACHING (null for activities without targetBlock).
    public BlockPos approachTargetPos = null;
    public GoToPosition approachGoTo = null;

    public ActivityInstance(ActivityDef def, PlacedBuilding targetBuilding, Phase phase, GoToPosition goToPosition) {
        this.def = def;
        this.targetBuilding = targetBuilding;
        this.phase = phase;
        this.goToPosition = goToPosition;
    }
}
