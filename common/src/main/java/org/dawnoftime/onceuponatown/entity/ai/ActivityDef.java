package org.dawnoftime.onceuponatown.entity.ai;

public record ActivityDef(
    String requiredBuilding,
    String heldItem,
    AnimationType animationType,
    String targetBlock  // null = no APPROACHING phase (e.g. MINE); non-null = scan BB for this block
) {}
