package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A single entry in the player construction queue.
 * Either a new building placement or a visual+stat upgrade of a placed building.
 */
public sealed interface QueueEntry permits QueueEntry.NewBuild, QueueEntry.Upgrade {

    long entryId();
    String defId();

    /** A new building to construct from a connection point. */
    record NewBuild(long entryId, String defId) implements QueueEntry {}

    /**
     * An upgrade task for a building already placed in the world.
     * fromLevel is the building's upgrade level when this task was enqueued.
     */
    record Upgrade(long entryId, String defId, BlockPos buildingWorldPos, int fromLevel) implements QueueEntry {}

    static CompoundTag serialize(QueueEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("EntryId", entry.entryId());
        if (entry instanceof Upgrade u) {
            tag.putString("Type", "upgrade");
            tag.putString("DefId", u.defId());
            tag.putLong("BuildingWorldPos", u.buildingWorldPos().asLong());
            tag.putInt("FromLevel", u.fromLevel());
        } else if (entry instanceof NewBuild nb) {
            tag.putString("Type", "new_build");
            tag.putString("DefId", nb.defId());
        }
        return tag;
    }

    static QueueEntry deserialize(CompoundTag tag) {
        long entryId = tag.contains("EntryId") ? tag.getLong("EntryId") : 0L;
        String defId = tag.getString("DefId");
        if ("upgrade".equals(tag.getString("Type"))) {
            return new Upgrade(entryId, defId, BlockPos.of(tag.getLong("BuildingWorldPos")), tag.getInt("FromLevel"));
        }
        return new NewBuild(entryId, defId);
    }
}
