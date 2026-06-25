package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

public record ActiveBuildState(
    String defId,
    BlockPos placementPos,
    Rotation rotation,
    BlockPos connectionPos,
    Direction connectionDir,
    String connectionTarget,
    BlockPos entryConnectorPos,
    List<ItemCost> cost,
    String queueDefId,
    long queueEntryId
) {}
