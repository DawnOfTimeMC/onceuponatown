package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.schematic.JigsawConnector;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.town.ActiveBuildState;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.QueueEntry;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownLogEntry;
import org.dawnoftime.onceuponatown.town.TownLogEntry.TownLogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SimpleStateMachine {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleStateMachine.class);

    public enum State { IDLE, BUILD, ACTIVITY }

    private final Npc npc;
    private State current = State.IDLE;
    private ActivityInstance currentActivity = null;
    private int activityTravelTicks = 0;
    private int activityPerformTicks = 0;
    private int queueCursor = 0;
    private BuildTask activeBuild = null;
    // The player-queued entry currently being built, or null if not building from queue.
    private QueueEntry activeQueueEntry = null;
    // DefIds that have already received a suppressed skip message this session.
    // Cleared when the defId is placed or falls out of the queue.
    private final Set<String> warnedDefIds = new HashSet<>();

    private enum QueueScanResult { STARTED_BUILD, BLOCKED, ALL_CLAIMED, EMPTY }

    // -------------------------------------------------------------------------
    // Placement outcome: carries either a valid result or the reason for failure.
    // Used throughout tickOpenPhase to aggregate diagnostic info before logging.
    // -------------------------------------------------------------------------
    private enum FailReason {
        NO_COMPATIBLE_CONNECTOR, // candidate has no jigsaw connector matching the connection point pool
        BOUNDING_BOX_OVERLAP     // computed BB intersects an already-occupied zone
    }

    private record PlacementSuccess(BlockPos pos, Rotation rotation, BlockPos entryConnectorWorldPos, BoundingBox bb) {}

    private record PlacementOutcome(PlacementSuccess success, FailReason failure) {
        static PlacementOutcome ok(BlockPos pos, Rotation rotation, BlockPos entryPos, BoundingBox bb) {
            return new PlacementOutcome(new PlacementSuccess(pos, rotation, entryPos, bb), null);
        }
        static PlacementOutcome fail(FailReason reason) {
            return new PlacementOutcome(null, reason);
        }
        boolean succeeded() { return success != null; }
    }

    public SimpleStateMachine(Npc npc) {
        this.npc = npc;
    }

    public State getState() { return current; }

    public void tick() {
        switch (current) {
            case IDLE -> tickIdle();
            case BUILD -> tickBuild();
            case ACTIVITY -> tickActivity();
        }
    }

    private void tickIdle() {
        // On each idle tick, check if Town has a saved build state for this builder's slot.
        // Runs first so the NPC resumes immediately on the first tick after a world reload.
        if (npc.level() instanceof ServerLevel resumeLevel) {
            Town resumeTown = findTown(resumeLevel);
            if (resumeTown != null) {
                int mySlot = resumeTown.getBuilderSlot(npc.getUUID());
                ActiveBuildState saved = resumeTown.getActiveBuild(mySlot);
                if (saved != null) {
                    BuildGoal resumed = BuildGoal.fromActiveBuildState(saved, npc, resumeTown, resumeLevel);
                    if (resumed != null) {
                        QueueEntry tentativeEntry = saved.queueDefId() != null
                            ? new QueueEntry.NewBuild(saved.queueEntryId(), saved.queueDefId()) : null;
                        // If this entry is already claimed by another builder (e.g. after a reload
                        // where claims were lost and another builder scanned first), discard the
                        // stale save so we don't double-build the same queue entry.
                        if (tentativeEntry != null) {
                            int idx = resumeTown.findQueueIndex(tentativeEntry.entryId());
                            if (idx >= 0 && !resumeTown.claimQueueEntry(idx, npc.getUUID())) {
                                resumeTown.clearActiveBuild(mySlot);
                                LevelTowns.get(resumeLevel).markDirty();
                                // Fall through to normal queue scan below.
                            } else {
                                activeBuild = resumed;
                                activeQueueEntry = tentativeEntry;
                                current = State.BUILD;
                                return;
                            }
                        } else {
                            activeBuild = resumed;
                            activeQueueEntry = null;
                            current = State.BUILD;
                            return;
                        }
                    } else {
                        // Invalid saved state (e.g. def removed); discard to avoid looping.
                        resumeTown.clearActiveBuild(mySlot);
                        LevelTowns.get(resumeLevel).markDirty();
                    }
                }
            }
        }

        if (!(npc.level() instanceof ServerLevel serverLevel)) return;

        Town town = findTown(serverLevel);
        if (town == null) return;

        if (town.getConstructionQueue().isEmpty()) {
            tryStartActivity(town);
            return;
        }

        List<ConnectionPoint> freePoints = town.getAvailableConnectionPoints();
        if (freePoints.isEmpty()) return;

        List<BoundingBox> occupied = town.getOccupiedBoxes();

        QueueScanResult result = tickPlayerQueue(serverLevel, town, freePoints, occupied);
        if (result == QueueScanResult.BLOCKED) {
            tickStreetsOnly(serverLevel, town, freePoints, occupied);
        } else if (result == QueueScanResult.ALL_CLAIMED) {
            // Every queued entry is taken by another builder; do a secondary activity
            // rather than spinning idle until one entry is released.
            tryStartActivity(town);
        }
    }

    // Player-directed queue: advances a per-builder cursor rather than re-scanning from 0 each tick.
    // Cursor advances on every skip; resets to 0 when the full queue has been walked.
    // CPs are never removed on failure -- only a successful placement consumes a CP.
    // Returns BLOCKED only when at least one unclaimed entry had no valid spatial placement,
    // so the caller can trigger road expansion exactly when the village needs it.
    private QueueScanResult tickPlayerQueue(ServerLevel serverLevel, Town town,
                                             List<ConnectionPoint> freePoints, List<BoundingBox> occupied) {
        List<QueueEntry> queue = town.getConstructionQueue();
        if (queue.isEmpty()) return QueueScanResult.EMPTY;

        UUID myId = npc.getUUID();
        boolean anyUnclaimed = false;
        boolean anyBlocked = false;

        // Clear warnedDefIds for any defId no longer present in the queue.
        Set<String> activeDefIds = new HashSet<>();
        for (QueueEntry e : queue) {
            if (e instanceof QueueEntry.NewBuild nb) activeDefIds.add(nb.defId());
        }
        warnedDefIds.retainAll(activeDefIds);

        for (int i = queueCursor; i < queue.size(); i++) {
            if (town.isQueueEntryClaimedByOther(i, myId)) {
                queueCursor = i + 1;
                continue;
            }

            anyUnclaimed = true;
            QueueEntry entry = queue.get(i);

            // Upgrade entries: walk to the building and run the visual diff.
            if (entry instanceof QueueEntry.Upgrade upgradeEntry) {
                PlacedBuilding building = town.getBuildings().stream()
                    .filter(b -> b.worldPos.equals(upgradeEntry.buildingWorldPos()))
                    .findFirst().orElse(null);
                BuildingDef def = BuildingDataHandler.get(upgradeEntry.defId()).orElse(null);

                if (building == null || def == null) {
                    town.releaseQueueClaim(i, myId);
                    town.consumeQueueEntry(entry);
                    LevelTowns.get(serverLevel).markDirty();
                    return QueueScanResult.STARTED_BUILD;
                }

                // Skip if another builder is already upgrading this building.
                if (town.isUnderUpgrade(upgradeEntry.buildingWorldPos())) {
                    queueCursor = i + 1;
                    continue;
                }

                town.claimQueueEntry(i, myId);
                activeBuild = new BuildGoal(npc, new UpgradeAction(building, def, upgradeEntry.fromLevel(), town));
                activeQueueEntry = entry;
                current = State.BUILD;
                TownLogEntry upgradeStartLog = new TownLogEntry(TownLogType.UPGRADE_START, upgradeEntry.defId(), serverLevel.getGameTime());
                town.addLogEntry(upgradeStartLog);
                LevelTowns.get(serverLevel).markDirty();
                NetworkHelper.pushLogEntryToWatchers(serverLevel, town, npc.getTownAnchorPos(), upgradeStartLog);
                NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                return QueueScanResult.STARTED_BUILD;
            }

            String defId = ((QueueEntry.NewBuild) entry).defId();
            BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
            if (def == null) {
                town.releaseQueueClaim(i, myId);
                town.consumeQueueEntry(entry);
                LevelTowns.get(serverLevel).markDirty();
                queueCursor = i + 1;
                continue;
            }

            if (!town.meetsPrerequisites(def)) {
                warnedDefIds.add(defId);
                queueCursor = i + 1;
                continue;
            }

            // Collect all CPs matching this building's pool.
            List<ConnectionPoint> matchingCps = new ArrayList<>();
            for (ConnectionPoint cp : freePoints) {
                if (!cp.targetName().isEmpty() && def.entryPool.equals(cp.targetName())) {
                    matchingCps.add(cp);
                }
            }

            if (matchingCps.isEmpty()) {
                anyBlocked = true;
                warnedDefIds.add(defId);
                queueCursor = i + 1;
                continue;
            }

            matchingCps.sort(java.util.Comparator.comparingLong(ConnectionPoint::insertionOrder));

            int bbOverlaps = 0;
            int noConnector = 0;

            for (ConnectionPoint point : matchingCps) {
                PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, def);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    town.claimQueueEntry(i, myId);
                    town.useConnection(point);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, new NewBuildAction(
                        def, point, s.pos(), s.rotation(), s.entryConnectorWorldPos(), List.of(), town));
                    if (s.bb() != null) town.addUnderConstruction(def.id, s.pos(), s.bb(), s.rotation());
                    activeQueueEntry = entry;
                    int mySlotQ = town.getBuilderSlot(myId);
                    if (mySlotQ >= 0) {
                        town.setActiveBuild(mySlotQ, new ActiveBuildState(
                            def.id, s.pos(), s.rotation(), point.pos(), point.direction(),
                            point.targetName(), s.entryConnectorWorldPos(), List.of(), defId, entry.entryId()));
                        LevelTowns.get(serverLevel).markDirty();
                    }
                    current = State.BUILD;
                    warnedDefIds.remove(defId);
                    TownLogEntry buildStartLog = new TownLogEntry(TownLogType.BUILD_START, defId, serverLevel.getGameTime());
                    town.addLogEntry(buildStartLog);
                    LevelTowns.get(serverLevel).markDirty();
                    NetworkHelper.pushLogEntryToWatchers(serverLevel, town, npc.getTownAnchorPos(), buildStartLog);
                    NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                    return QueueScanResult.STARTED_BUILD;
                }
                if (outcome.failure() == FailReason.BOUNDING_BOX_OVERLAP)        bbOverlaps++;
                else if (outcome.failure() == FailReason.NO_COMPATIBLE_CONNECTOR) noConnector++;
            }

            if (noConnector > 0 && bbOverlaps == 0) {
                warnedDefIds.add(defId);
            }
            anyBlocked = true;
            queueCursor = i + 1;
        }

        // Reached end of queue -- reset cursor so the next tick rescans from index 0.
        queueCursor = 0;

        if (anyBlocked) return QueueScanResult.BLOCKED;
        if (!anyUnclaimed) return QueueScanResult.ALL_CLAIMED;
        return QueueScanResult.ALL_CLAIMED;
    }

    // Extends the road network by one segment. Called only when the player queue has entries
    // that cannot be placed yet (no matching CPs). Skips non-street CPs so they stay free
    // for the queue. Uses round-robin CP selection to avoid always picking the same direction.
    private void tickStreetsOnly(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> freePoints, List<BoundingBox> occupied) {
        List<BuildingDef> streetCandidates = new ArrayList<>(town.getBuildableBuildings().stream()
            .filter(d -> Constants.STREETS_POOL.equals(d.entryPool))
            .toList());

        List<ConnectionPoint> streetCps = new ArrayList<>();
        for (ConnectionPoint cp : freePoints) {
            if (Constants.STREETS_POOL.equals(cp.targetName())) streetCps.add(cp);
        }
        streetCps.sort(java.util.Comparator.comparingLong(ConnectionPoint::insertionOrder));

        if (streetCps.isEmpty() || streetCandidates.isEmpty()) {
            if (town.checkVillageFullTransition()) {
                TownLogEntry fullLog = new TownLogEntry(TownLogType.VILLAGE_FULL, "", serverLevel.getGameTime());
                town.addLogEntry(fullLog);
                LevelTowns.get(serverLevel).markDirty();
                NetworkHelper.pushLogEntryToWatchers(serverLevel, town, npc.getTownAnchorPos(), fullLog);
            }
            return;
        }

        // Try each street CP from oldest to newest; for each CP shuffle candidate road pieces
        // so the road shape varies while the expansion direction stays age-ordered.
        for (ConnectionPoint chosen : streetCps) {
            shuffleInPlace(streetCandidates);
            for (BuildingDef candidate : streetCandidates) {
                PlacementOutcome outcome = attemptPlacement(serverLevel, chosen, occupied, candidate);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    town.useConnection(chosen);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, new NewBuildAction(
                        candidate, chosen, s.pos(), s.rotation(), s.entryConnectorWorldPos(),
                        candidate.constructionCost, town));
                    if (s.bb() != null) town.addUnderConstruction(candidate.id, s.pos(), s.bb(), s.rotation());
                    int mySlot = town.getBuilderSlot(npc.getUUID());
                    if (mySlot >= 0) {
                        town.setActiveBuild(mySlot, new ActiveBuildState(
                            candidate.id, s.pos(), s.rotation(), chosen.pos(), chosen.direction(),
                            chosen.targetName(), s.entryConnectorWorldPos(), candidate.constructionCost, null, -1L));
                        LevelTowns.get(serverLevel).markDirty();
                    }
                    current = State.BUILD;
                    TownLogEntry streetStartLog = new TownLogEntry(TownLogType.BUILD_START, candidate.id, serverLevel.getGameTime());
                    town.addLogEntry(streetStartLog);
                    LevelTowns.get(serverLevel).markDirty();
                    NetworkHelper.pushLogEntryToWatchers(serverLevel, town, npc.getTownAnchorPos(), streetStartLog);
                    NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                    return;
                }
            }
        }

        if (town.checkVillageFullTransition()) {
            TownLogEntry fullLog = new TownLogEntry(TownLogType.VILLAGE_FULL, "", serverLevel.getGameTime());
            town.addLogEntry(fullLog);
            LevelTowns.get(serverLevel).markDirty();
            NetworkHelper.pushLogEntryToWatchers(serverLevel, town, npc.getTownAnchorPos(), fullLog);
        }
    }

    // Attempts to find a valid placement for a building at a given connection point.
    // Returns a PlacementOutcome carrying either a valid result or the specific failure reason.
    private PlacementOutcome attemptPlacement(ServerLevel serverLevel, ConnectionPoint point,
                                               List<BoundingBox> occupied,
                                               BuildingDef def) {
        List<JigsawConnector> connectors = BuildSchematic.readConnectors(serverLevel, def.nbt);
        List<JigsawConnector> compatible = connectors.stream()
            .filter(c -> point.targetName().isEmpty() || c.name().equals(point.targetName()))
            .toList();
        if (compatible.isEmpty()) return PlacementOutcome.fail(FailReason.NO_COMPATIBLE_CONNECTOR);

        // Shuffle and try every compatible connector. When multiple connectors share the same
        // name (entry + extensions on road pieces), picking the wrong one rotates the piece so
        // its body overlaps the building. The bounding-box check filters those out; the loop
        // finds the connector whose orientation actually fits.
        // Prefer terminator connectors (pool = empty) as the attachment point.
        // Active connectors (non-empty pool) must stay free for village expansion.
        List<JigsawConnector> entryConnectors = compatible.stream()
            .filter(c -> c.pool().isEmpty() || c.pool().equals("minecraft:empty"))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<JigsawConnector> shuffled = entryConnectors.isEmpty() ? new ArrayList<>(compatible) : entryConnectors;
        shuffleInPlace(shuffled);

        BlockPos attachPoint = point.pos().relative(point.direction());
        int terrainY = BuildSchematic.findGroundY(serverLevel, attachPoint);

        for (JigsawConnector chosen : shuffled) {
            Rotation rotation = BuildSchematic.computeRequiredRotation(
                chosen.facing(), point.direction().getOpposite());
            BlockPos rawPos = BuildSchematic.computeCandidatePosition(
                point.pos(), point.direction(), chosen.posInTemplate(), rotation);

            int finalY = terrainY - chosen.posInTemplate().getY();
            BlockPos finalPos = new BlockPos(rawPos.getX(), finalY, rawPos.getZ());

            Optional<BoundingBox> maybeBb = def.terrainMatching
                ? BuildSchematic.computeFootprintBoundingBox(serverLevel, finalPos, def.nbt, rotation)
                : BuildSchematic.computeBoundingBox(serverLevel, finalPos, def.nbt, rotation);
            if (maybeBb.isPresent()) {
                BoundingBox cb = maybeBb.get();
                boolean overlaps = occupied.stream().anyMatch(bb ->
                    bb.minX() < cb.maxX() && bb.maxX() > cb.minX() &&
                    bb.minZ() < cb.maxZ() && bb.maxZ() > cb.minZ()
                );
                if (overlaps) continue;
            }

            BlockPos entryConnectorWorldPos = finalPos.offset(
                StructureTemplate.transform(chosen.posInTemplate(), Mirror.NONE, rotation, BlockPos.ZERO));

            return PlacementOutcome.ok(finalPos, rotation, entryConnectorWorldPos, maybeBb.orElse(null));
        }

        return PlacementOutcome.fail(FailReason.BOUNDING_BOX_OVERLAP);
    }

    private void tryStartActivity(Town town) {
        List<ActivityDef> activities = BuilderConfigDataHandler.get().secondaryActivities;
        if (activities.isEmpty()) return;

        List<ActivityDef> candidateDefs = new ArrayList<>();
        List<PlacedBuilding> candidateBuildings = new ArrayList<>();
        for (PlacedBuilding building : town.getBuildings()) {
            if (building.bb == null) continue;
            for (ActivityDef def : activities) {
                if (def.requiredBuilding().equals(building.defId)) {
                    candidateDefs.add(def);
                    candidateBuildings.add(building);
                }
            }
        }
        if (candidateDefs.isEmpty()) return;

        int idx = npc.getRandom().nextInt(candidateDefs.size());
        ActivityDef def = candidateDefs.get(idx);
        PlacedBuilding building = candidateBuildings.get(idx);

        LOGGER.info("[OUAT-ACTIVITY] tryStartActivity: activity={} building={} buildingRef={}",
            def.animationType(), building.defId, System.identityHashCode(building));

        BoundingBox bb = building.bb;
        BlockPos target = new BlockPos(
            (bb.minX() + bb.maxX()) / 2,
            bb.minY(),
            (bb.minZ() + bb.maxZ()) / 2
        );
        GoToPosition gtp = new GoToPosition(npc, target, BuilderConfigDataHandler.get().walkSpeed, 2.0);
        currentActivity = new ActivityInstance(def, building, ActivityInstance.Phase.TRAVELING, gtp);

        if (!def.heldItem().equals("minecraft:air")) {
            BuiltInRegistries.ITEM.getOptional(new ResourceLocation(def.heldItem()))
                .ifPresent(item -> npc.holdInMainHand(new ItemStack(item)));
        }
        activityTravelTicks = 0;
        activityPerformTicks = 0;
        current = State.ACTIVITY;
    }

    private void tickActivity() {
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;
        Town town = findTown(serverLevel);

        if (town == null) {
            LOGGER.info("[OUAT-ACTIVITY] tickActivity: town is null, cancelling activity");
            cancelActivity();
            current = State.IDLE;
            return;
        }
        // Only interrupt activity when there is unclaimed work available for this builder.
        // If every queued entry is held by another builder, keep doing the activity.
        if (!town.getConstructionQueue().isEmpty()) {
            UUID myId = npc.getUUID();
            List<QueueEntry> queue = town.getConstructionQueue();
            boolean hasUnclaimedWork = false;
            for (int i = 0; i < queue.size(); i++) {
                if (!town.isQueueEntryClaimedByOther(i, myId)) {
                    hasUnclaimedWork = true;
                    break;
                }
            }
            if (hasUnclaimedWork) {
                LOGGER.info("[OUAT-ACTIVITY] tickActivity: unclaimed work found, interrupting activity");
                cancelActivity();
                current = State.IDLE;
                return;
            }
        }

        List<PlacedBuilding> currentBuildings = town.getBuildings();
        boolean buildingFound = false;
        for (PlacedBuilding b : currentBuildings) {
            if (b == currentActivity.targetBuilding) { buildingFound = true; break; }
        }
        if (!buildingFound) {
            LOGGER.warn("[OUAT-ACTIVITY] tickActivity: building {} (ref={}) not found in town list (list size={}, phase={}), cancelling",
                currentActivity.targetBuilding.defId,
                System.identityHashCode(currentActivity.targetBuilding),
                currentBuildings.size(),
                currentActivity.phase);
            cancelActivity();
            current = State.IDLE;
            return;
        }

        switch (currentActivity.phase) {
            case TRAVELING   -> tickTraveling(serverLevel);
            case APPROACHING -> tickApproaching(serverLevel);
            case PERFORMING  -> tickPerforming();
        }
    }

    private void tickTraveling(ServerLevel serverLevel) {
        activityTravelTicks++;
        if (activityTravelTicks > BuilderConfigDataHandler.get().movingTimeoutTicks) {
            cancelActivity();
            current = State.IDLE;
            return;
        }
        boolean arrived = currentActivity.goToPosition.tick();
        if (!arrived) return;

        npc.getNavigation().stop();
        activityTravelTicks = 0;

        String targetBlockId = currentActivity.def.targetBlock();
        if (targetBlockId == null) {
            LOGGER.info("[OUAT-ACTIVITY] TRAVELING -> PERFORMING (no targetBlock), activity={}", currentActivity.def.animationType());
            currentActivity.phase = ActivityInstance.Phase.PERFORMING;
            return;
        }
        LOGGER.info("[OUAT-ACTIVITY] TRAVELING -> scanning for block={}", targetBlockId);

        // Scan the building's bounding box for the closest matching block.
        Block block = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(targetBlockId)).orElse(null);
        if (block == null) {
            cancelActivity();
            current = State.IDLE;
            return;
        }

        net.minecraft.world.level.levelgen.structure.BoundingBox bb = currentActivity.targetBuilding.bb;
        BlockPos found = null;
        double bestDist = Double.MAX_VALUE;
        for (int bx = bb.minX(); bx <= bb.maxX(); bx++) {
            for (int by = bb.minY(); by <= bb.maxY(); by++) {
                for (int bz = bb.minZ(); bz <= bb.maxZ(); bz++) {
                    BlockPos p = new BlockPos(bx, by, bz);
                    if (serverLevel.getBlockState(p).is(block)) {
                        double d = npc.distanceToSqr(Vec3.atCenterOf(p));
                        if (d < bestDist) {
                            bestDist = d;
                            found = p;
                        }
                    }
                }
            }
        }

        if (found == null) {
            LOGGER.warn("[OUAT-ACTIVITY] TRAVELING: target block not found in building BB, cancelling");
            cancelActivity();
            current = State.IDLE;
            return;
        }

        LOGGER.info("[OUAT-ACTIVITY] TRAVELING -> APPROACHING, found block at {}", found);
        currentActivity.approachTargetPos = found;
        currentActivity.approachGoTo = new GoToPosition(npc, found, BuilderConfigDataHandler.get().walkSpeed, 1.5);
        currentActivity.phase = ActivityInstance.Phase.APPROACHING;
    }

    private void tickApproaching(ServerLevel serverLevel) {
        activityTravelTicks++;
        if (activityTravelTicks > BuilderConfigDataHandler.get().movingTimeoutTicks) {
            cancelActivity();
            current = State.IDLE;
            return;
        }
        boolean arrived = currentActivity.approachGoTo.tick();
        if (arrived) {
            npc.getNavigation().stop();
            activityTravelTicks = 0;
            LOGGER.info("[OUAT-ACTIVITY] APPROACHING -> PERFORMING, activity={}", currentActivity.def.animationType());
            currentActivity.phase = ActivityInstance.Phase.PERFORMING;
        }
    }

    private void tickPerforming() {
        npc.getNavigation().stop();
        if (currentActivity.def.animationType() == AnimationType.CRAFT) {
            // Look at the target block every tick to simulate focused crafting.
            BlockPos lookPos = currentActivity.approachTargetPos;
            if (lookPos != null) {
                npc.getLookControl().setLookAt(
                    lookPos.getX() + 0.5, lookPos.getY() + 0.5, lookPos.getZ() + 0.5,
                    10f, 10f
                );
            }
            if (activityPerformTicks % 25 == 0) {
                npc.notifyBlockPlaced();
            }
        } else {
            BlockPos minePos = currentActivity.approachTargetPos;
            if (minePos != null) {
                npc.getLookControl().setLookAt(
                    minePos.getX() + 0.5, minePos.getY() + 0.5, minePos.getZ() + 0.5,
                    10f, 10f
                );
            }
            if (activityPerformTicks % 25 == 0) {
                npc.swing(InteractionHand.MAIN_HAND);
                npc.notifyBlockPlaced();
            }
        }
        activityPerformTicks++;
    }

    private void cancelActivity() {
        if (currentActivity == null) return;
        LOGGER.info("[OUAT-ACTIVITY] cancelActivity: phase={} activity={} performTicks={}",
            currentActivity.phase, currentActivity.def.animationType(), activityPerformTicks);
        npc.freeHands();
        npc.getNavigation().stop();
        currentActivity = null;
        activityTravelTicks = 0;
        activityPerformTicks = 0;
    }

    private <T> void shuffleInPlace(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = npc.getRandom().nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private void tickBuild() {
        if (activeBuild == null) { current = State.IDLE; return; }
        if (activeBuild.tick()) {
            BlockPos completedPos = activeBuild.getFinalPlacementPos();
            if (npc.level() instanceof ServerLevel sl) {
                Town qTown = findTown(sl);
                // Clear the persisted build state regardless of success or failure.
                if (qTown != null) {
                    int mySlot = qTown.getBuilderSlot(npc.getUUID());
                    if (mySlot >= 0) qTown.clearActiveBuild(mySlot);
                }
                if (!activeBuild.isFailed() && activeQueueEntry != null && qTown != null) {
                    // Release the claim before consuming so indices stay consistent.
                    int claimIdx = qTown.findQueueIndex(activeQueueEntry.entryId());
                    if (claimIdx >= 0) qTown.releaseQueueClaim(claimIdx, npc.getUUID());
                    String placedDefId = activeQueueEntry instanceof QueueEntry.NewBuild nb ? nb.defId() : null;
                    TownLogType doneType = activeQueueEntry instanceof QueueEntry.Upgrade ? TownLogType.UPGRADE_DONE : TownLogType.BUILD_DONE;
                    String doneDefId = activeQueueEntry instanceof QueueEntry.NewBuild nb2 ? nb2.defId()
                        : activeQueueEntry instanceof QueueEntry.Upgrade u2 ? u2.defId() : "";
                    qTown.consumeQueueEntry(activeQueueEntry);
                    if (placedDefId != null) qTown.onBuildingPlaced(placedDefId);
                    TownLogEntry doneLog = new TownLogEntry(doneType, doneDefId, sl.getGameTime());
                    qTown.addLogEntry(doneLog);
                    NetworkHelper.pushLogEntryToWatchers(sl, qTown, npc.getTownAnchorPos(), doneLog);
                    LevelTowns.get(sl).markDirty();
                } else if (activeBuild.isFailed() && activeQueueEntry != null && qTown != null) {
                    // Release claim on failure so another builder can attempt this entry.
                    int claimIdx = qTown.findQueueIndex(activeQueueEntry.entryId());
                    if (claimIdx >= 0) qTown.releaseQueueClaim(claimIdx, npc.getUUID());
                }
            }
            // Remove construction/upgrade markers and fire targeted packets.
            if (npc.level() instanceof ServerLevel sl) {
                Town doneTown = findTown(sl);
                if (doneTown != null) {
                    doneTown.removeUnderConstruction(completedPos);
                    doneTown.removeUnderUpgrade(completedPos);
                    BlockPos anchor = npc.getTownAnchorPos();
                    NetworkHelper.pushBuildingListToWatchers(sl, doneTown, anchor);
                    NetworkHelper.pushStockToWatchers(sl, doneTown, anchor);
                    // If a house was just built, total resident count changed.
                    if (activeQueueEntry instanceof QueueEntry.NewBuild nb) {
                        org.dawnoftime.onceuponatown.datapack.BuildingDataHandler.get(nb.defId()).ifPresent(def -> {
                            if (def.residents > 0) {
                                NetworkHelper.pushCitizenUpdateToWatchers(sl, doneTown, anchor);
                            }
                        });
                    }
                }
            }
            activeQueueEntry = null;
            activeBuild = null;
            current = State.IDLE;
        }
    }

    private Town findTown(ServerLevel level) {
        net.minecraft.core.BlockPos anchor = npc.getTownAnchorPos();
        if (anchor != null) {
            return LevelTowns.get(level).getTownAt(anchor)
                .filter(t -> t.getBuilderNpcIds().contains(npc.getUUID()))
                .orElse(null);
        }
        // Fallback for builders loaded from saves predating anchor tracking.
        return LevelTowns.get(level).getAllTowns().stream()
            .filter(t -> t.getBuilderNpcIds().contains(npc.getUUID()))
            .findFirst().orElse(null);
    }
}
