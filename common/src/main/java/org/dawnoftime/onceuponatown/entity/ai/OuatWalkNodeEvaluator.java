package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class OuatWalkNodeEvaluator extends WalkNodeEvaluator {

    // Fence gates are treated as wooden doors by the pathfinder so the NPC routes through
    // them instead of treating them as solid walls. The reactive OpenFenceGateGoal still
    // handles the physical open/close; this evaluator only teaches the route planner.
    //
    // Wooden fences are marked BLOCKED because their actual collision height (1.5) exceeds
    // the NPC jump height, but vanilla returns FENCE which the pathfinder may still attempt.
    //
    // The node directly above a fence is also marked BLOCKED to prevent the pathfinder from
    // generating a jump-over path: vanilla scores the air node at Y+1 as reachable because
    // it uses grid height (1 block) not collision height (1.5) for the jump check.
    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock) {
            return BlockPathTypes.WALKABLE;
        }
        if (state.getBlock() instanceof FenceBlock) {
            return BlockPathTypes.BLOCKED;
        }
        BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
        if (below.getBlock() instanceof FenceBlock) {
            return BlockPathTypes.BLOCKED;
        }
        return super.getBlockPathType(level, x, y, z);
    }
}
