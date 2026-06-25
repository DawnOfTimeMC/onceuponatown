package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.town.ActiveBuildState;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.schematic.SchematicBlock;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// Unified NPC construction executor. Pluggable behavior is provided by a BuildAction:
//   - NewBuildAction: places a full NBT template block-by-block
//   - UpgradeAction:  applies a visual diff between two NBT levels
// Future modes (e.g. demolition) follow the same interface.
//
// Phase flow:
//   MOVING   -- NPC walks to action.getTargetPos() (connection point or building origin)
//   BUILDING -- waits for any reading animation, then places blocks one-by-one with burst rhythm
//   DONE     -- task complete (success or failure)
//
// For instant (terrain-matched) builds the BUILDING phase is skipped:
//   MOVING -> executeInstant() -> DONE
public class BuildGoal implements BuildTask {
    private enum Phase { MOVING, BUILDING, DONE }

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildGoal.class);

    private static int blockDelay()        { return BuilderConfigDataHandler.get().blockDelayTicks; }
    private static int burstPauseMin()     { return BuilderConfigDataHandler.get().burstPauseMinTicks; }
    private static int burstPauseMax()     { return BuilderConfigDataHandler.get().burstPauseMaxTicks; }
    private static int maxBurstExtra()     { return BuilderConfigDataHandler.get().maxBurstExtraBlocks; }
    private static double reachDist()      { return BuilderConfigDataHandler.get().blockReachDistance; }
    private static int stuckFallback()     { return BuilderConfigDataHandler.get().stuckFallbackTicks; }
    private static int movingTimeout()     { return BuilderConfigDataHandler.get().movingTimeoutTicks; }
    private static float planReadChance()  { return BuilderConfigDataHandler.get().planReadChance; }
    private static int planReadMin()       { return BuilderConfigDataHandler.get().planReadMinTicks; }
    private static int planReadMax()       { return BuilderConfigDataHandler.get().planReadMaxTicks; }

    private final Npc npc;
    private final BuildAction action;
    private Phase phase = Phase.MOVING;
    private final GoToPosition goTo;
    private boolean failed = false;
    private int movingTicks = 0;

    // BUILDING state
    private List<SchematicBlock> blocks = null;
    private int buildProgress = 0;
    private int buildSpeedCooldown = 0;
    private int burstBlocksLeft = 0;
    private GoToPosition buildGoTo = null;
    private BlockPos currentBuildTarget = null;
    private int stuckTicks = 0;

    public BuildGoal(Npc npc, BuildAction action) {
        this.npc = npc;
        this.action = action;
        this.goTo = new GoToPosition(npc, action.getTargetPos(), BuilderConfigDataHandler.get().walkSpeed, 5.5);
    }

    @Override
    public boolean isFailed() { return failed || action.isFailed(); }

    @Override
    public BlockPos getFinalPlacementPos() { return action.getOrigin(); }

    @Override
    public boolean tick() {
        return switch (phase) {
            case MOVING -> tickMoving();
            case BUILDING -> tickBuilding();
            case DONE -> true;
        };
    }

    private boolean tickMoving() {
        if (++movingTicks > movingTimeout()) {
            LOGGER.warn("[OUAT-BUILD] MOVING timeout -- target={}", action.getTargetPos());
            failed = true;
            return true;
        }
        if (!goTo.tick()) return false;

        action.onArrived(npc);

        if (action.isInstant()) {
            if (!(npc.level() instanceof ServerLevel sl)) return false;
            if (action.executeInstant(sl, npc)) {
                action.onComplete(sl, npc);
                phase = Phase.DONE;
                return true;
            }
            // Placement failed; retry next tick.
            LOGGER.error("[OUAT-BUILD] Instant placement failed -- retrying next tick, origin={}",
                action.getOrigin());
            return false;
        }

        phase = Phase.BUILDING;
        return false;
    }

    private boolean tickBuilding() {
        if (!(npc.level() instanceof ServerLevel sl)) return false;

        // Wait for any pre-build animation (e.g. reading the plan) to finish.
        if (npc.isReading()) return false;

        // Load block list on first BUILDING tick.
        if (blocks == null) {
            blocks = action.prepareBlocks(sl, npc);
        }

        // Empty list or action marked itself failed (e.g. fallback instant placement already ran).
        if (blocks.isEmpty() || action.isFailed()) {
            if (!action.isFailed()) action.onComplete(sl, npc);
            phase = Phase.DONE;
            return true;
        }

        if (buildProgress >= blocks.size()) {
            action.onComplete(sl, npc);
            phase = Phase.DONE;
            return true;
        }

        // Keep the NPC facing the current block every tick for smooth head tracking.
        BlockPos nextWorldPos = action.getOrigin().offset(blocks.get(buildProgress).localPos());
        npc.getLookControl().setLookAt(nextWorldPos.getX() + 0.5, nextWorldPos.getY() + 0.5, nextWorldPos.getZ() + 0.5);

        // Navigate toward the next block before placing it.
        double distSq = npc.distanceToSqr(Vec3.atCenterOf(nextWorldPos));
        boolean inReach = distSq <= reachDist() * reachDist();

        if (!inReach && stuckTicks < stuckFallback()) {
            stuckTicks++;
            if (!nextWorldPos.equals(currentBuildTarget)) {
                currentBuildTarget = nextWorldPos;
                if (buildGoTo == null) buildGoTo = new GoToPosition(npc, nextWorldPos, BuilderConfigDataHandler.get().walkSpeed, reachDist());
                else buildGoTo.updateTarget(nextWorldPos);
            }
            buildGoTo.tick();
            return false;
        }

        npc.getNavigation().stop();
        stuckTicks = 0;

        if (buildSpeedCooldown > 0) { buildSpeedCooldown--; return false; }

        // Place exactly one block.
        SchematicBlock b = blocks.get(buildProgress);
        BlockPos worldPos = action.getOrigin().offset(b.localPos());

        ItemStack handItem = new ItemStack(b.state().getBlock().asItem());
        if (!handItem.isEmpty()) npc.holdInMainHand(handItem);

        npc.getLookControl().setLookAt(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
        sl.setBlock(worldPos, b.state(), Block.UPDATE_ALL);

        SoundType sound = b.state().getSoundType();
        sl.playSound(null, worldPos, sound.getPlaceSound(), SoundSource.BLOCKS,
            sound.getVolume(), sound.getPitch());

        if (b.nbt() != null) {
            BlockEntity be = sl.getBlockEntity(worldPos);
            if (be != null) be.load(b.nbt().copy());
        }

        buildProgress++;
        npc.notifyBlockPlaced();
        npc.swing(InteractionHand.MAIN_HAND);

        // Burst rhythm: quick follow-up within a burst, longer pause between bursts.
        if (burstBlocksLeft > 0) {
            burstBlocksLeft--;
            buildSpeedCooldown = blockDelay();
        } else {
            burstBlocksLeft = npc.getRandom().nextInt(maxBurstExtra() + 1);
            buildSpeedCooldown = burstPauseMin() + npc.getRandom().nextInt(burstPauseMax() - burstPauseMin() + 1);
            if (npc.getRandom().nextFloat() < planReadChance()) {
                npc.startReading(planReadMin() + npc.getRandom().nextInt(planReadMax() - planReadMin() + 1));
            }
        }

        if (buildProgress >= blocks.size()) {
            action.onComplete(sl, npc);
            phase = Phase.DONE;
            return true;
        }
        return false;
    }

    @Override
    public void saveTo(CompoundTag tag) {
        // State is persisted in Town.activeBuilds; no NPC NBT serialization needed.
    }

    // Reconstructs a BuildGoal from a Town.ActiveBuildState after a server restart.
    // The NPC re-walks to the build site (MOVING phase) but skips terrain prep and the reading animation.
    // Also registers the build BB into underConstruction -- the only place ServerLevel is available post-reload.
    public static BuildGoal fromActiveBuildState(ActiveBuildState state, Npc npc, Town town, ServerLevel level) {
        BuildingDef def = BuildingDataHandler.get(state.defId()).orElse(null);
        if (def == null) return null;

        ConnectionPoint conn = new ConnectionPoint(
            state.connectionPos(), state.connectionDir(), state.connectionTarget(), 0L);

        NewBuildAction action = new NewBuildAction(
            def, conn, state.placementPos(), state.rotation(),
            state.entryConnectorPos(), state.cost(), town);
        action.skipTerrainPrep = true;
        action.skipInitialReading = true;

        // addUnderConstruction is called here -- Town.fromNbt() is static with no ServerLevel.
        org.dawnoftime.onceuponatown.building.schematic.BuildSchematic
            .computeBoundingBox(level, state.placementPos(), def.nbt, state.rotation())
            .ifPresent(bb -> town.addUnderConstruction(def.id, state.placementPos(), bb, state.rotation()));

        return new BuildGoal(npc, action);
    }
}
