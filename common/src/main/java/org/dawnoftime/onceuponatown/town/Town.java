package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraDef;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Town {

    private static final Logger LOGGER = LoggerFactory.getLogger(Town.class);

    public record UnderConstructionEntry(String defId, BlockPos worldPos, BoundingBox bb, Rotation rotation) {}

    private final List<PlacedBuilding> buildings = new ArrayList<>();
    private final List<ConnectionPoint> freeConnections = new ArrayList<>();
    private final Map<Item, Integer> reserveStock = new HashMap<>();
    private final List<UnderConstructionEntry> underConstruction = new ArrayList<>();
    // Bounding boxes reserved for pieces that have no building def (starter, vanilla pieces).
    // Must be serialized - the mixin that populates these only fires during world gen, not on reload.
    private final List<BoundingBox> blockedZones = new ArrayList<>();
    // World positions of buildings currently being upgraded by an NPC (runtime-only, not persisted).
    private final Set<BlockPos> underUpgrade = new HashSet<>();
    // Ordered list of builder NPC UUIDs. Slot 0 is the primary builder (handles street expansion).
    private final List<UUID> builderNpcIds = new ArrayList<>();
    // How many builders should be active. Starts at 1, incremented by era transitions with unlock_new_builder.
    private int targetBuilderCount = 1;
    // Runtime-only claim map: queue index -> builder UUID. Prevents two builders from picking the same entry.
    // Not persisted: claims are re-established on the next idle tick after a server restart.
    private final Map<Integer, UUID> queueIndexClaims = new HashMap<>();
    private long nextEntryId = 0L;
    private String name = "Unknown Town";
    private final List<Quest> activeQuests = new ArrayList<>();
    // Tracks when each TASK quest def was last completed (def id -> game time).
    private final Map<String, Long> questDefLastCompleted = new HashMap<>();

    // Sliding window of the last 20 activity events; persisted to NBT.
    private final ArrayDeque<TownLogEntry> activityLog = new ArrayDeque<>();
    private static final int LOG_MAX = 20;

    // Players who opted in to receiving village log entries as chat messages. Persisted to NBT.
    private final Set<UUID> chatSubscribers = new HashSet<>();

    // Player-ordered queue of construction tasks (new builds and upgrades).
    // Resources are pre-reserved in queueReservedStock when an entry is added.
    private final List<QueueEntry> constructionQueue = new ArrayList<>();
    // Resources deducted from town stock when buildings are added to the queue.
    // Released back to reserveStock on removal, or cleared when NPC places the building.
    private final Map<Item, Integer> queueReservedStock = new HashMap<>();

    // Max entries the queue can hold; matches TownHubMenu.CHEST_SIZE (6x9 grid).
    public static final int QUEUE_CAPACITY = 54;

    // Current era (0 = Settlement, 1 = Village, ...). Persisted to NBT.
    private int currentEra = 0;
    // The era transition id chosen at the last fork (e.g. "agricultural_rural"). Persisted to NBT.
    private String currentEraPath = "";
    // Orientation tag for the current era branch (e.g. "agricultural", "agricultural_rural"). Persisted to NBT.
    private String currentOrientation = "";
    // Building defIds explicitly unlocked via era transition rewards. Grows over time. Persisted to NBT.
    private final Set<String> unlockedBuildingIds = new HashSet<>();
    // Fed portion of total residents, updated each dawn tick. Persisted to NBT.
    private int activeResidents = 0;
    // Current weight cap. Starts at initial_max_weight (era 0 file) and grows with each transition. Persisted to NBT.
    private int currentMaxWeight = 0;
    // Active build states keyed by builder slot index. Persisted so builds survive server restarts.
    private final Map<Integer, ActiveBuildState> activeBuilds = new HashMap<>();
    // Monotonically increasing counter stamped onto each ConnectionPoint when added to freeConnections.
    // Allows sorting by insertion age: lower = older = closer to the village center.
    private long cpInsertionCounter = 0;

    public void registerBuilding(BlockPos worldPos, String defId, List<ConnectionPoint> connections, BoundingBox bb, Rotation rotation) {
        buildings.add(new PlacedBuilding(defId, worldPos, bb, rotation));
        for (ConnectionPoint cp : connections) {
            freeConnections.add(new ConnectionPoint(cp.pos(), cp.direction(), cp.targetName(), cpInsertionCounter++));
        }
        // Remove phantom CPs whose expansion block falls inside the new building's footprint.
        // These are open connectors that point into already-occupied space and can never be used.
        if (bb != null) {
            freeConnections.removeIf(cp -> {
                net.minecraft.core.BlockPos expansion = cp.pos().relative(cp.direction());
                return bb.isInside(expansion);
            });
        }
    }

    public void addBlockedZone(BoundingBox bb) {
        blockedZones.add(bb);
    }

    public void addUnderConstruction(String defId, BlockPos pos, BoundingBox bb, Rotation rotation) {
        underConstruction.add(new UnderConstructionEntry(defId, pos, bb, rotation));
    }

    public void removeUnderConstruction(BlockPos pos) {
        underConstruction.removeIf(e -> e.worldPos().equals(pos));
    }

    public List<UnderConstructionEntry> getUnderConstructionBuildings() {
        return Collections.unmodifiableList(underConstruction);
    }

    public void addUnderUpgrade(BlockPos pos) {
        underUpgrade.add(pos);
    }

    public void removeUnderUpgrade(BlockPos pos) {
        underUpgrade.remove(pos);
    }

    // Returns the world bounding boxes of all placed buildings plus blocked zones plus in-progress builds.
    // Buildings from saves predating BB tracking have null bb - they are skipped.
    public List<BoundingBox> getOccupiedBoxes() {
        List<BoundingBox> all = new ArrayList<>();
        buildings.stream().map(b -> b.bb).filter(Objects::nonNull).forEach(all::add);
        all.addAll(blockedZones);
        underConstruction.stream().map(UnderConstructionEntry::bb).filter(Objects::nonNull).forEach(all::add);
        return all;
    }

    // Tracks whether the "village full" chat message has fired for the current empty state.
    // Reset when a new CP is added so the message can fire again if the village hits zero a second time.
    private boolean villageFullNotified = false;

    public void addFreeConnection(ConnectionPoint point) {
        freeConnections.add(new ConnectionPoint(point.pos(), point.direction(), point.targetName(), cpInsertionCounter++));
        villageFullNotified = false;
    }

    public void useConnection(ConnectionPoint point) {
        freeConnections.remove(point);
    }

    // Returns true exactly once when freeConnections transitions from non-empty to empty.
    public boolean checkVillageFullTransition() {
        if (!villageFullNotified && freeConnections.isEmpty()) {
            villageFullNotified = true;
            return true;
        }
        return false;
    }

    public List<ConnectionPoint> getAvailableConnectionPoints() {
        return Collections.unmodifiableList(freeConnections);
    }

    // Returns building defs available to build: all buildings whose construction cost is met by the town inventory.
    public List<BuildingDef> getBuildableBuildings() {
        TownInventory inv = getTownInventory();
        return BuildingDataHandler.getAll().stream()
            .filter(def -> inv.hasStock(def.constructionCost))
            .toList();
    }

    // Computes committed weight: placed buildings + NewBuild entries in the player queue.
    // Queued-but-unplaced buildings are included so tryAddToConstructionQueue enforces the
    // cap against the full committed load, not just what is already physically in the world.
    public int getCurrentWeight() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            total += def.weight;
        }
        for (QueueEntry entry : constructionQueue) {
            if (entry instanceof QueueEntry.NewBuild nb) {
                BuildingDef def = BuildingDataHandler.get(nb.defId()).orElse(null);
                if (def != null) total += def.weight;
            }
        }
        return total;
    }

    public int getCurrentEra() { return currentEra; }
    public String getCurrentEraPath() { return currentEraPath; }
    public Set<String> getUnlockedBuildingIds() { return Collections.unmodifiableSet(unlockedBuildingIds); }

    public int getCurrentMaxWeight() { return currentMaxWeight; }
    public List<BoundingBox> getBlockedZones() { return Collections.unmodifiableList(blockedZones); }
    public boolean isUnderUpgrade(BlockPos pos) { return underUpgrade.contains(pos); }

    // Seeds currentMaxWeight from the matched era 0 data file.
    // Called once at world gen after all starter buildings are registered.
    public void initFromEraDef() {
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return;
        EraDef def = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (def == null) return;
        if (def.initialMaxWeight > 0) {
            currentMaxWeight = def.initialMaxWeight;
        }
    }

    // Returns the structure label (e.g. "Settlement", "Village") for the current era and orientation.
    public String resolveStructureLabel() {
        if (currentEra == 0) {
            EraDef def = EraTransitionDataHandler.getEraDefByOrientation(getOrDeriveOrientation());
            return def != null ? def.structureLabel : "";
        }
        for (EraTransitionDef t : EraTransitionDataHandler.getAll()) {
            if (t.fromEra != currentEra - 1) continue;
            String resultOrientation = t.nextOrientation.isEmpty() ? t.fromOrientation : t.nextOrientation;
            if (resultOrientation.equals(getOrDeriveOrientation())) return t.structureLabel;
        }
        return "";
    }

    // Returns era transitions currently available given era and orientation.
    public List<EraTransitionDef> getAvailableTransitions() {
        return EraTransitionDataHandler.getAvailableTransitions(currentEra, getOrDeriveOrientation());
    }

    // Returns the current orientation, deriving it from the placed starter building on first call for old saves.
    public String getOrDeriveOrientation() {
        if (!currentOrientation.isEmpty()) return currentOrientation;
        for (PlacedBuilding b : buildings) {
            EraDef eraDef = EraTransitionDataHandler.getEraDefForStarter(b.defId);
            if (eraDef != null) {
                currentOrientation = eraDef.orientation;
                return currentOrientation;
            }
        }
        return "";
    }

    // Computes the effective minimum weight for a transition, respecting min_weight_percent if set.
    public int effectiveMinWeight(EraTransitionDef t) {
        if (t.minWeightPercent > 0) {
            return (int) Math.round(t.minWeightPercent / 100.0 * currentMaxWeight);
        }
        return 0;
    }

    // Returns true if all prereqs for the given era transition are satisfied.
    public boolean meetsEraTransitionPrereqs(EraTransitionDef t) {
        int w = getCurrentWeight();
        if (w < effectiveMinWeight(t) || w > getCurrentMaxWeight()) return false;
        if (t.requiredResidents > 0 && activeResidents < t.requiredResidents) return false;
        for (BuildingDef.BuildingRequirement req : t.requiredBuildings) {
            long count = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
            if (count < req.count()) return false;
        }
        if (!getTownInventory().hasStock(t.resourceCost)) return false;
        return true;
    }

    // Performs the era transition identified by pathId. Returns true if successful.
    public boolean advanceEra(String pathId) {
        EraTransitionDef t = EraTransitionDataHandler.get(pathId).orElse(null);
        if (t == null) return false;
        List<EraTransitionDef> available = getAvailableTransitions();
        if (available.stream().noneMatch(a -> a.id.equals(pathId))) return false;
        if (!meetsEraTransitionPrereqs(t)) return false;
        getTownInventory().removeStock(t.resourceCost);
        currentEra++;
        currentMaxWeight += t.weightCapIncrease;
        currentEraPath = t.id;
        if (!t.nextOrientation.isEmpty()) currentOrientation = t.nextOrientation;
        unlockedBuildingIds.addAll(t.unlockedBuildingIds);
        if (t.unlockNewBuilder) targetBuilderCount++;
        if (!t.autoUpgradeIds.isEmpty()) {
            for (PlacedBuilding b : buildings) {
                if (t.autoUpgradeIds.contains(b.defId)) {
                    forceQueueUpgrade(b.worldPos);
                }
            }
        }
        return true;
    }

    // Returns all building defs whose entry pool matches the connection point, regardless of cost.
    // Used to distinguish PENDING_RESOURCES (compatible building exists but unaffordable) from DEAD (no compatible building).
    public List<BuildingDef> getPotentialBuildings(ConnectionPoint point) {
        return BuildingDataHandler.getAll().stream()
            .filter(def -> point.targetName().isEmpty() || def.entryPool.equals(point.targetName()))
            .toList();
    }

    // Returns the building defIds that receive the orientation bonus.
    // Uses currentOrientation (persisted) so the result stays correct after path branching.
    public List<String> getBoostedBuildingIds() {
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return List.of();
        EraDef eraDef = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (eraDef == null) return List.of();
        return eraDef.boostedBuildings;
    }

    // Called by the NPC after a NewBuild placement succeeds.
    // Stamps the orientation bonus multiplier onto the building if its defId is boosted.
    public void onBuildingPlaced(String defId) {
        if (buildings.isEmpty()) return;
        String orientation = getOrDeriveOrientation();
        if (orientation.isEmpty()) return;
        EraDef era = EraTransitionDataHandler.getEraDefByOrientation(orientation);
        if (era == null || !era.boostedBuildings.contains(defId)) return;
        buildings.get(buildings.size() - 1).setInstanceProductionMultiplier(era.boostMultiplier);
    }

    // Computed aggregate view: buildings + floating reserve
    public TownInventory getTownInventory() {
        return new TownInventory(buildings, reserveStock);
    }

    // Player command injection - into first building if available, otherwise into reserve
    public void addStock(Item item, int quantity) {
        if (!buildings.isEmpty()) {
            buildings.get(0).forceAdd(item, quantity);
        } else {
            reserveStock.merge(item, quantity, Integer::sum);
        }
    }

    // Returns {NWCorner, SECorner} as BlockPos array derived from all occupied boxes.
    // Y is set to 0 - the map is purely 2D (XZ plane). Returns null if no boxes exist.
    public BlockPos[] getMapBounds() {
        List<BoundingBox> boxes = getOccupiedBoxes();
        if (boxes.isEmpty()) return null;
        int minX = boxes.stream().mapToInt(BoundingBox::minX).min().getAsInt();
        int minZ = boxes.stream().mapToInt(BoundingBox::minZ).min().getAsInt();
        int maxX = boxes.stream().mapToInt(BoundingBox::maxX).max().getAsInt();
        int maxZ = boxes.stream().mapToInt(BoundingBox::maxZ).max().getAsInt();
        return new BlockPos[]{ new BlockPos(minX, 0, minZ), new BlockPos(maxX, 0, maxZ) };
    }

    public CompoundTag getTownMapData() {
        return new TownHubDataBuilder(this).buildMapData();
    }

    // -------------------------------------------------------------------------
    // Player construction queue
    // -------------------------------------------------------------------------

    public List<QueueEntry> getConstructionQueue() {
        return Collections.unmodifiableList(constructionQueue);
    }

    // Checks affordability (available stock minus already-reserved amounts), reserves resources,
    // and appends a NewBuild entry to the queue. Returns false if unaffordable or queue is full.
    public boolean tryAddToConstructionQueue(String defId) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null || constructionQueue.size() >= QUEUE_CAPACITY) return false;
        // Weight cap: block new builds (not upgrades) that would exceed the era limit.
        if (getCurrentWeight() + def.weight > getCurrentMaxWeight()) return false;
        TownInventory inv = getTownInventory();
        for (ItemCost cost : def.constructionCost) {
            if (inv.getStock(cost.item()) < cost.amount()) return false;
        }
        inv.removeStock(def.constructionCost);
        for (ItemCost cost : def.constructionCost) {
            queueReservedStock.merge(cost.item(), cost.amount(), Integer::sum);
        }
        constructionQueue.add(new QueueEntry.NewBuild(nextEntryId++, defId));
        return true;
    }

    // Checks affordability and appends an Upgrade entry to the queue.
    // Returns false if: building not found, already at max level, upgrade already pending,
    // queue full, or insufficient stock. Only one upgrade per building can be queued at a time.
    public boolean tryQueueUpgrade(BlockPos worldPos) {
        PlacedBuilding building = null;
        for (PlacedBuilding b : buildings) {
            if (b.worldPos.equals(worldPos)) { building = b; break; }
        }
        if (building == null) return false;

        BuildingDef def = BuildingDataHandler.get(building.defId).orElse(null);
        if (def == null || (def.upgrades.isEmpty() && def.nbtLevels.isEmpty())) return false;

        // Block if an upgrade for this building is already pending in the queue.
        for (QueueEntry entry : constructionQueue) {
            if (entry instanceof QueueEntry.Upgrade u && u.buildingWorldPos().equals(worldPos)) {
                return false;
            }
        }

        int effectiveLevel = building.getUpgradeLevel();
        int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
        if (effectiveLevel >= maxLevel) return false;
        if (constructionQueue.size() >= QUEUE_CAPACITY) return false;

        // Visual-only upgrades (nbt_levels only, no stat upgrades) are free -- cost already paid by era advance.
        List<ItemCost> cost = effectiveLevel < def.upgrades.size()
            ? def.upgrades.get(effectiveLevel).upgradeCost()
            : List.of();
        TownInventory inv = getTownInventory();
        if (!inv.hasStock(cost)) return false;

        inv.removeStock(cost);
        for (ItemCost c : cost) {
            queueReservedStock.merge(c.item(), c.amount(), Integer::sum);
        }
        constructionQueue.add(new QueueEntry.Upgrade(nextEntryId++, building.defId, worldPos, effectiveLevel));
        return true;
    }

    // Free upgrade bypassing resource check -- cost absorbed by era transition.
    // Still verifies: building exists, not at max level, queue not full.
    public boolean forceQueueUpgrade(BlockPos worldPos) {
        PlacedBuilding building = null;
        for (PlacedBuilding b : buildings) {
            if (b.worldPos.equals(worldPos)) { building = b; break; }
        }
        if (building == null) return false;

        BuildingDef def = BuildingDataHandler.get(building.defId).orElse(null);
        if (def == null || (def.upgrades.isEmpty() && def.nbtLevels.isEmpty())) return false;

        int effectiveLevel = building.getUpgradeLevel();
        for (QueueEntry entry : constructionQueue) {
            if (entry instanceof QueueEntry.Upgrade u && u.buildingWorldPos().equals(worldPos)) {
                effectiveLevel++;
            }
        }

        int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
        if (effectiveLevel >= maxLevel) return false;
        if (constructionQueue.size() >= QUEUE_CAPACITY) return false;

        constructionQueue.add(new QueueEntry.Upgrade(nextEntryId++, building.defId, worldPos, effectiveLevel));
        return true;
    }

    // Removes entry at index, restoring its reserved resources to the floating reserve.
    public boolean removeFromConstructionQueue(int index) {
        if (index < 0 || index >= constructionQueue.size()) return false;
        QueueEntry entry = constructionQueue.get(index);
        List<ItemCost> costToRefund = getEntryCost(entry);
        for (ItemCost cost : costToRefund) {
            int reserved = queueReservedStock.getOrDefault(cost.item(), 0);
            int toRestore = Math.min(reserved, cost.amount());
            if (toRestore > 0) {
                queueReservedStock.put(cost.item(), reserved - toRestore);
                reserveStock.merge(cost.item(), toRestore, Integer::sum);
            }
        }
        constructionQueue.remove(index);
        shiftClaimsAfter(index);
        return true;
    }

    // Called by NPC after successfully processing a queued entry.
    // Removes the entry from the queue and clears its resource reservation.
    public int findQueueIndex(long entryId) {
        for (int i = 0; i < constructionQueue.size(); i++) {
            if (constructionQueue.get(i).entryId() == entryId) return i;
        }
        return -1;
    }

    public void consumeQueueEntry(QueueEntry entry) {
        int idx = findQueueIndex(entry.entryId());
        if (idx >= 0) {
            constructionQueue.remove(idx);
            shiftClaimsAfter(idx);
        }
        // Always drain the reservation even if entry was already removed by the player.
        List<ItemCost> cost = getEntryCost(entry);
        for (ItemCost c : cost) {
            int reserved = queueReservedStock.getOrDefault(c.item(), 0);
            queueReservedStock.put(c.item(), Math.max(0, reserved - c.amount()));
        }
    }

    // Returns the resource cost associated with a queue entry (construction cost or upgrade cost).
    private List<ItemCost> getEntryCost(QueueEntry entry) {
        if (entry instanceof QueueEntry.NewBuild nb) {
            BuildingDef def = BuildingDataHandler.get(nb.defId()).orElse(null);
            return def != null ? def.constructionCost : List.of();
        } else if (entry instanceof QueueEntry.Upgrade u) {
            BuildingDef def = BuildingDataHandler.get(u.defId()).orElse(null);
            if (def != null && u.fromLevel() < def.upgrades.size()) {
                return def.upgrades.get(u.fromLevel()).upgradeCost();
            }
        }
        return List.of();
    }

    // Sums resolved residents (including upgrade bonuses) for all placed buildings.
    public int getTotalResidents() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            total += def.resolveAtLevel(b.getUpgradeLevel()).resolvedResidents();
        }
        return total;
    }

    // Computes total food units demanded per day across all residential and herd buildings (unrounded float).
    public float computeTotalFoodDemandFloat() {
        float total = 0f;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(b.getUpgradeLevel());
            if (stats.resolvedResidents() > 0) {
                total += stats.resolvedResidents() * stats.resolvedConsumptionPerResident();
            }
            if (stats.resolvedHerd() > 0) {
                total += stats.resolvedHerd() * stats.resolvedConsumptionPerHerd();
            }
        }
        return total;
    }

    // Sums resolved herd count across all placed buildings.
    public int getTotalHerd() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            total += def.resolveAtLevel(b.getUpgradeLevel()).resolvedHerd();
        }
        return total;
    }

    // Sums resolved herd count for buildings whose herd was fed at last dawn.
    public int getActiveHerd() {
        int total = 0;
        for (PlacedBuilding b : buildings) {
            BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
            if (def == null) continue;
            int h = def.resolveAtLevel(b.getUpgradeLevel()).resolvedHerd();
            if (h > 0 && b.isHerdFed()) total += h;
        }
        return total;
    }

    public int getActiveResidents() { return activeResidents; }
    public void setActiveResidents(int value) { this.activeResidents = value; }

    // Returns true if all prerequisites of the given def are currently satisfied.
    // Uses activeResidents (fed population) instead of total residents.
    public boolean meetsPrerequisites(BuildingDef def) {
        if (def.requiredResidents > 0 && activeResidents < def.requiredResidents) return false;
        for (BuildingDef.BuildingRequirement req : def.requiredBuildings) {
            long count = buildings.stream().filter(b -> b.defId.equals(req.defId())).count();
            if (count < req.count()) return false;
        }
        return true;
    }

    // Returns hub data: map + era + catalog + stock + queue + summary + quests.
    public CompoundTag getHubData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildHubData(anchorPos);
    }

    // -------------------------------------------------------------------------
    // Targeted serialization helpers (used by targeted S2C packets)
    // -------------------------------------------------------------------------

    public CompoundTag getStockUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildStockUpdateData(anchorPos);
    }

    public CompoundTag getBuildingListData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildBuildingListData(anchorPos);
    }

    public CompoundTag getQuestUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildQuestUpdateData(anchorPos);
    }

    public CompoundTag getEraUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildEraUpdateData(anchorPos);
    }

    public CompoundTag getCitizenUpdateData(BlockPos anchorPos) {
        return new TownHubDataBuilder(this).buildCitizenUpdateData(anchorPos);
    }

    // -------------------------------------------------------------------------
    // Quest management
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Activity log
    // -------------------------------------------------------------------------

    public void addLogEntry(TownLogEntry entry) {
        if (activityLog.size() >= LOG_MAX) activityLog.pollFirst();
        activityLog.addLast(entry);
    }

    public List<TownLogEntry> getActivityLog() {
        return List.copyOf(activityLog);
    }

    public void addChatSubscriber(UUID playerId)    { chatSubscribers.add(playerId); }
    public void removeChatSubscriber(UUID playerId) { chatSubscribers.remove(playerId); }
    public boolean isChatSubscriber(UUID playerId)  { return chatSubscribers.contains(playerId); }
    public Set<UUID> getChatSubscribers()           { return Collections.unmodifiableSet(chatSubscribers); }

    public List<Quest> getActiveQuests() { return Collections.unmodifiableList(activeQuests); }

    public void addQuest(Quest q) { activeQuests.add(q); }

    public void removeQuest(String questId) { activeQuests.removeIf(q -> q.questId.equals(questId)); }

    public Map<String, Long> getQuestDefLastCompleted() { return questDefLastCompleted; }

    // Removes activeQuests and questDefLastCompleted entries whose definition no longer exists.
    // Called at world load after datapacks have been read. Returns true if anything was removed.
    public boolean cleanupOrphanedQuestData(Set<String> validDefIds) {
        boolean changed = activeQuests.removeIf(q -> {
            if (validDefIds.contains(q.defId)) return false;
            LOGGER.warn("[OUAT] Removing orphaned quest {}", q.defId);
            return true;
        });
        int sizeBefore = questDefLastCompleted.size();
        questDefLastCompleted.keySet().retainAll(validDefIds);
        return changed || questDefLastCompleted.size() != sizeBefore;
    }

    // Tries to add item to town stock without checking the accepted set.
    // Used after quest consumption to route any remainder into stock.
    public int tryAddToStockUnchecked(Item item, int amount) {
        TownInventory inv = getTownInventory();
        int maxStock = inv.getMaxStock(item);
        if (maxStock == 0) maxStock = 999;
        int room = maxStock - inv.getStock(item);
        if (room <= 0) return 0;
        int toAdd = Math.min(amount, room);
        inv.addStock(List.of(new ItemCost(item, toAdd)));
        return toAdd;
    }

    // Collects all items produced or transformed by currently placed buildings in this town.
    // Used server-side to validate deposit requests.
    public Set<Item> buildAcceptedItemSet() {
        Set<Item> accepted = new HashSet<>();
        for (PlacedBuilding b : buildings) {
            BuildingDataHandler.get(b.defId).ifPresent(def -> {
                def.production.forEach(p -> accepted.add(p.item()));
                def.transformations.forEach(t -> accepted.add(t.outputItem()));
            });
        }
        return accepted;
    }

    // Tries to add `amount` of `item` to the town stock.
    // Returns how many units were actually accepted (may be less if near cap, 0 if rejected).
    // Fallback cap of 999 applies for items whose getMaxStock() returns 0 (transformation outputs).
    public int tryDepositItem(Item item, int amount, Set<Item> acceptedItems) {
        if (!acceptedItems.contains(item)) return 0;
        TownInventory inv = getTownInventory();
        int maxStock = inv.getMaxStock(item);
        if (maxStock == 0) maxStock = 999;
        int room = maxStock - inv.getStock(item);
        if (room <= 0) return 0;
        int toAdd = Math.min(amount, room);
        inv.addStock(List.of(new ItemCost(item, toAdd)));
        return toAdd;
    }

    public Map<Item, Integer> getReserveStock() { return reserveStock; }

    public List<PlacedBuilding> getBuildings() { return buildings; }
    public List<UUID> getBuilderNpcIds() { return builderNpcIds; }
    public int getTargetBuilderCount() { return targetBuilderCount; }

    // Appends a builder UUID in the next available slot. No-op if already registered.
    public void addBuilderNpcId(UUID id) {
        if (!builderNpcIds.contains(id)) builderNpcIds.add(id);
    }

    // Replaces the UUID at the given slot index (used by TickScheduler on respawn).
    public void setBuilderNpcIdAtSlot(int slot, UUID id) {
        while (builderNpcIds.size() <= slot) builderNpcIds.add(null);
        builderNpcIds.set(slot, id);
    }

    // Returns the slot index for a given builder UUID, or -1 if not found.
    public int getBuilderSlot(UUID id) { return builderNpcIds.indexOf(id); }

    public void setActiveBuild(int slot, ActiveBuildState state) {
        activeBuilds.put(slot, state);
    }

    public void clearActiveBuild(int slot) {
        activeBuilds.remove(slot);
    }

    public ActiveBuildState getActiveBuild(int slot) {
        return activeBuilds.get(slot);
    }

    // Claim-lock helpers for multi-builder queue coordination.
    public boolean claimQueueEntry(int index, UUID builderId) {
        UUID existing = queueIndexClaims.get(index);
        if (existing != null && !existing.equals(builderId)) return false;
        queueIndexClaims.put(index, builderId);
        return true;
    }

    public void releaseQueueClaim(int index, UUID builderId) {
        queueIndexClaims.remove(index, builderId);
    }

    public void releaseAllClaimsForBuilder(UUID builderId) {
        queueIndexClaims.values().removeIf(id -> id.equals(builderId));
    }

    // After removing an entry at removedIdx, shift all claims at higher indices down by one
    // so they remain aligned with the new positions of their entries in the queue.
    private void shiftClaimsAfter(int removedIdx) {
        Map<Integer, UUID> shifted = new HashMap<>();
        queueIndexClaims.entrySet().removeIf(e -> {
            if (e.getKey() > removedIdx) {
                shifted.put(e.getKey() - 1, e.getValue());
                return true;
            }
            return false;
        });
        queueIndexClaims.putAll(shifted);
    }

    public boolean isQueueEntryClaimedByOther(int index, UUID builderId) {
        UUID existing = queueIndexClaims.get(index);
        return existing != null && !existing.equals(builderId);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        ListTag builderIdsTag = new ListTag();
        for (UUID id : builderNpcIds) {
            CompoundTag idTag = new CompoundTag();
            if (id != null) idTag.putUUID("Id", id);
            builderIdsTag.add(idTag);
        }
        tag.put("BuilderNpcIds", builderIdsTag);
        tag.putInt("TargetBuilderCount", targetBuilderCount);
        ListTag buildingsTag = new ListTag();
        buildings.forEach(b -> buildingsTag.add(b.toNbt()));
        tag.put("Buildings", buildingsTag);
        ListTag connTag = new ListTag();
        freeConnections.forEach(c -> connTag.add(connectionToNbt(c)));
        tag.put("FreeConnections", connTag);
        CompoundTag reserveTag = new CompoundTag();
        reserveStock.forEach((item, qty) ->
            reserveTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), qty));
        tag.put("ReserveStock", reserveTag);
        ListTag zonesTag = new ListTag();
        for (BoundingBox bb : blockedZones) {
            CompoundTag zTag = new CompoundTag();
            zTag.putInt("MinX", bb.minX()); zTag.putInt("MinY", bb.minY()); zTag.putInt("MinZ", bb.minZ());
            zTag.putInt("MaxX", bb.maxX()); zTag.putInt("MaxY", bb.maxY()); zTag.putInt("MaxZ", bb.maxZ());
            zonesTag.add(zTag);
        }
        tag.put("BlockedZones", zonesTag);
        ListTag cqTag = new ListTag();
        constructionQueue.forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        tag.put("ConstructionQueue", cqTag);
        CompoundTag qrTag = new CompoundTag();
        queueReservedStock.forEach((item, qty) ->
            qrTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), qty));
        tag.put("QueueReservedStock", qrTag);
        tag.putInt("CurrentEra", currentEra);
        tag.putString("CurrentEraPath", currentEraPath);
        tag.putString("CurrentOrientation", currentOrientation);
        ListTag unlockedTag = new ListTag();
        unlockedBuildingIds.forEach(id -> unlockedTag.add(StringTag.valueOf(id)));
        tag.put("UnlockedBuildingIds", unlockedTag);
        tag.putInt("ActiveResidents", activeResidents);
        tag.putInt("CurrentMaxWeight", currentMaxWeight);
        ListTag activeQuestsTag = new ListTag();
        activeQuests.forEach(q -> activeQuestsTag.add(q.toNbt()));
        tag.put("ActiveQuests", activeQuestsTag);
        if (!questDefLastCompleted.isEmpty()) {
            CompoundTag qdlcTag = new CompoundTag();
            questDefLastCompleted.forEach(qdlcTag::putLong);
            tag.put("QuestDefLastCompleted", qdlcTag);
        }
        if (!activityLog.isEmpty()) {
            ListTag logTag = new ListTag();
            for (TownLogEntry e : activityLog) {
                CompoundTag lt = new CompoundTag();
                lt.putString("Type", e.type().name());
                lt.putString("Param", e.param());
                lt.putLong("Tick", e.gameTick());
                logTag.add(lt);
            }
            tag.put("ActivityLog", logTag);
        }
        if (!activeBuilds.isEmpty()) {
            CompoundTag activeBuildsTag = new CompoundTag();
            activeBuilds.forEach((slot, state) -> activeBuildsTag.put(String.valueOf(slot), activeBuildStateToNbt(state)));
            tag.put("ActiveBuilds", activeBuildsTag);
        }
        if (!chatSubscribers.isEmpty()) {
            ListTag subsTag = new ListTag();
            chatSubscribers.forEach(id -> subsTag.add(StringTag.valueOf(id.toString())));
            tag.put("ChatSubscribers", subsTag);
        }
        tag.putLong("CpInsertionCounter", cpInsertionCounter);
        tag.putLong("NextEntryId", nextEntryId);
        return tag;
    }

    public static Town fromNbt(CompoundTag tag) {
        Town town = new Town();
        town.name = tag.contains("Name") ? tag.getString("Name") : "Unknown Town";
        // Backward compat: old saves stored a single BuilderNpcId UUID.
        if (tag.hasUUID("BuilderNpcId")) {
            town.builderNpcIds.add(tag.getUUID("BuilderNpcId"));
        }
        if (tag.contains("BuilderNpcIds")) {
            tag.getList("BuilderNpcIds", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag idTag = (CompoundTag) t;
                town.builderNpcIds.add(idTag.hasUUID("Id") ? idTag.getUUID("Id") : null);
            });
        }
        town.targetBuilderCount = tag.contains("TargetBuilderCount") ? tag.getInt("TargetBuilderCount") : 1;
        tag.getList("Buildings", Tag.TAG_COMPOUND)
            .forEach(t -> town.buildings.add(PlacedBuilding.fromNbt((CompoundTag) t)));
        tag.getList("FreeConnections", Tag.TAG_COMPOUND)
            .forEach(t -> town.freeConnections.add(connectionFromNbt((CompoundTag) t)));
        town.cpInsertionCounter = tag.contains("CpInsertionCounter") ? tag.getLong("CpInsertionCounter") : (long) town.freeConnections.size();
        if (tag.contains("ReserveStock")) {
            CompoundTag reserveTag = tag.getCompound("ReserveStock");
            for (String key : reserveTag.getAllKeys()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key));
                town.reserveStock.put(item, reserveTag.getInt(key));
            }
        }
        tag.getList("BlockedZones", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag zTag = (CompoundTag) t;
            town.blockedZones.add(new BoundingBox(
                zTag.getInt("MinX"), zTag.getInt("MinY"), zTag.getInt("MinZ"),
                zTag.getInt("MaxX"), zTag.getInt("MaxY"), zTag.getInt("MaxZ")
            ));
        });
        // BootstrapQueue: silently ignored - bootstrap system removed.
        if (tag.contains("ConstructionQueue")) {
            ListTag cqTagCompound = tag.getList("ConstructionQueue", Tag.TAG_COMPOUND);
            if (!cqTagCompound.isEmpty()) {
                // New format: compound tags with Type/DefId/etc fields
                cqTagCompound.forEach(t -> town.constructionQueue.add(QueueEntry.deserialize((CompoundTag) t)));
            } else {
                // Backward compat: old saves stored plain string defIds
                tag.getList("ConstructionQueue", Tag.TAG_STRING)
                    .forEach(t -> town.constructionQueue.add(new QueueEntry.NewBuild(0L, t.getAsString())));
            }
        }
        // Backward compat: old saves have no NextEntryId -- reassign sequential IDs to all entries.
        if (tag.contains("NextEntryId")) {
            town.nextEntryId = tag.getLong("NextEntryId");
        } else {
            long seq = 0L;
            List<QueueEntry> restamped = new ArrayList<>();
            for (QueueEntry e : town.constructionQueue) {
                if (e instanceof QueueEntry.NewBuild nb)
                    restamped.add(new QueueEntry.NewBuild(seq++, nb.defId()));
                else if (e instanceof QueueEntry.Upgrade u)
                    restamped.add(new QueueEntry.Upgrade(seq++, u.defId(), u.buildingWorldPos(), u.fromLevel()));
                else restamped.add(e);
            }
            town.constructionQueue.clear();
            town.constructionQueue.addAll(restamped);
            town.nextEntryId = seq;
        }
        if (tag.contains("QueueReservedStock")) {
            CompoundTag qrTag = tag.getCompound("QueueReservedStock");
            for (String key : qrTag.getAllKeys()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(key));
                town.queueReservedStock.put(item, qrTag.getInt(key));
            }
        }
        town.currentEra = tag.contains("CurrentEra") ? tag.getInt("CurrentEra") : 0;
        town.currentEraPath = tag.contains("CurrentEraPath") ? tag.getString("CurrentEraPath") : "";
        town.currentOrientation = tag.contains("CurrentOrientation") ? tag.getString("CurrentOrientation") : "";
        if (tag.contains("UnlockedBuildingIds")) {
            tag.getList("UnlockedBuildingIds", Tag.TAG_STRING)
                .forEach(t -> town.unlockedBuildingIds.add(t.getAsString()));
        }
        town.activeResidents = tag.contains("ActiveResidents") ? tag.getInt("ActiveResidents") : 0;
        // Backward compat: old saves use the formula 20 + era * 10; new saves store the explicit value.
        town.currentMaxWeight = tag.contains("CurrentMaxWeight")
            ? tag.getInt("CurrentMaxWeight")
            : 20 + town.currentEra * 10;
        if (tag.contains("ActiveQuests")) {
            tag.getList("ActiveQuests", Tag.TAG_COMPOUND)
                .forEach(t -> town.activeQuests.add(Quest.fromNbt((CompoundTag) t)));
        }
        if (tag.contains("QuestDefLastCompleted")) {
            CompoundTag qdlcTag = tag.getCompound("QuestDefLastCompleted");
            for (String key : qdlcTag.getAllKeys()) {
                town.questDefLastCompleted.put(key, qdlcTag.getLong(key));
            }
        }
        if (tag.contains("ActivityLog")) {
            tag.getList("ActivityLog", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag lt = (CompoundTag) t;
                try {
                    TownLogEntry.TownLogType type = TownLogEntry.TownLogType.valueOf(lt.getString("Type"));
                    town.activityLog.addLast(new TownLogEntry(type, lt.getString("Param"), lt.getLong("Tick")));
                } catch (IllegalArgumentException ignored) {}
            });
        }
        if (tag.contains("ActiveBuilds")) {
            CompoundTag activeBuildsTag = tag.getCompound("ActiveBuilds");
            for (String key : activeBuildsTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    ActiveBuildState state = activeBuildStateFromNbt(activeBuildsTag.getCompound(key));
                    if (state != null) town.activeBuilds.put(slot, state);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (tag.contains("ChatSubscribers")) {
            tag.getList("ChatSubscribers", Tag.TAG_STRING).forEach(t -> {
                try { town.chatSubscribers.add(UUID.fromString(t.getAsString())); }
                catch (IllegalArgumentException ignored) {}
            });
        }
        return town;
    }

    private static CompoundTag connectionToNbt(ConnectionPoint c) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(c.pos()));
        tag.putString("Dir", c.direction().getName());
        tag.putString("Pool", c.targetName());
        tag.putLong("Order", c.insertionOrder());
        return tag;
    }

    private static ConnectionPoint connectionFromNbt(CompoundTag tag) {
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("Pos"));
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(tag.getString("Dir"));
        String pool = tag.getString("Pool");
        long order = tag.getLong("Order");
        return new ConnectionPoint(pos, dir != null ? dir : net.minecraft.core.Direction.NORTH, pool, order);
    }

    private static CompoundTag activeBuildStateToNbt(ActiveBuildState s) {
        CompoundTag tag = new CompoundTag();
        tag.putString("DefId", s.defId());
        tag.put("PlacementPos", NbtUtils.writeBlockPos(s.placementPos()));
        tag.putString("Rotation", s.rotation().name());
        tag.put("ConnectionPos", NbtUtils.writeBlockPos(s.connectionPos()));
        tag.putString("ConnectionDir", s.connectionDir().getName());
        tag.putString("ConnectionTarget", s.connectionTarget());
        tag.put("EntryConnectorPos", NbtUtils.writeBlockPos(s.entryConnectorPos()));
        ListTag costTag = new ListTag();
        for (ItemCost c : s.cost()) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item()).toString());
            ct.putInt("Amount", c.amount());
            costTag.add(ct);
        }
        tag.put("Cost", costTag);
        if (s.queueDefId() != null) tag.putString("QueueDefId", s.queueDefId());
        if (s.queueEntryId() >= 0) tag.putLong("QueueEntryId", s.queueEntryId());
        return tag;
    }

    private static ActiveBuildState activeBuildStateFromNbt(CompoundTag tag) {
        String defId = tag.getString("DefId");
        if (defId.isEmpty()) return null;
        BlockPos placementPos = NbtUtils.readBlockPos(tag.getCompound("PlacementPos"));
        Rotation rotation;
        try { rotation = Rotation.valueOf(tag.getString("Rotation")); }
        catch (IllegalArgumentException e) { rotation = Rotation.NONE; }
        BlockPos connectionPos = NbtUtils.readBlockPos(tag.getCompound("ConnectionPos"));
        Direction connectionDir = Direction.byName(tag.getString("ConnectionDir"));
        if (connectionDir == null) connectionDir = Direction.NORTH;
        String connectionTarget = tag.getString("ConnectionTarget");
        BlockPos entryConnectorPos = NbtUtils.readBlockPos(tag.getCompound("EntryConnectorPos"));
        ListTag costList = tag.getList("Cost", Tag.TAG_COMPOUND);
        java.util.List<ItemCost> cost = new ArrayList<>();
        for (int i = 0; i < costList.size(); i++) {
            CompoundTag ct = costList.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(ct.getString("Item"));
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                cost.add(new ItemCost(BuiltInRegistries.ITEM.get(rl), ct.getInt("Amount")));
            }
        }
        String queueDefId = tag.contains("QueueDefId") ? tag.getString("QueueDefId") : null;
        long queueEntryId = tag.contains("QueueEntryId") ? tag.getLong("QueueEntryId") : -1L;
        return new ActiveBuildState(defId, placementPos, rotation, connectionPos, connectionDir,
            connectionTarget, entryConnectorPos, cost, queueDefId, queueEntryId);
    }
}
