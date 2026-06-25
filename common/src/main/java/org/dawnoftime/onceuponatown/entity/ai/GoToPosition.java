package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;

public class GoToPosition {

    private final Npc npc;
    private BlockPos target;
    private final double speed;
    private final double arrivalRadius;
    public GoToPosition(Npc npc, BlockPos target, double speed, double arrivalRadius) {
        this.npc = npc;
        this.target = target;
        this.speed = speed;
        this.arrivalRadius = arrivalRadius;
        npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
    }

    // Redirect navigation to a new block target.
    public void updateTarget(BlockPos newTarget) {
        this.target = newTarget;
        npc.getNavigation().moveTo(newTarget.getX() + 0.5, newTarget.getY(), newTarget.getZ() + 0.5, speed);
    }

    // Returns true when the NPC has reached the target.
    public boolean tick() {
        double distSq = npc.distanceToSqr(Vec3.atCenterOf(target));
        if (distSq <= arrivalRadius * arrivalRadius) {
            npc.getNavigation().stop();
            return true;
        }
        // Re-issue moveTo only when the path was actually cancelled (stroll goal interference).
        // Avoids the stutter caused by interrupting an already-valid path every fixed interval.
        if (npc.getNavigation().isDone()) {
            npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        }
        return false;
    }
}
