package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingListDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDef;
import org.dawnoftime.onceuponatown.datapack.QuestDataHandler;
import org.dawnoftime.onceuponatown.datapack.TradePriceDataHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// Pure projection: reads Town state and serializes it into CompoundTag packets for the client.
// No game state is modified here. Town keeps thin delegation wrappers for backward compatibility.
public class TownHubDataBuilder {

    private final Town town;

    public TownHubDataBuilder(Town town) {
        this.town = town;
    }

    // Full hub packet: map + era + catalog + stock + queue + summary + quests.
    public CompoundTag buildHubData(BlockPos anchorPos) {
        CompoundTag hub = new CompoundTag();
        hub.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        hub.put("MapData", buildMapData());
        hub.putInt("CurrentEra", town.getCurrentEra());
        hub.putString("CurrentOrientation", town.getOrDeriveOrientation());
        hub.putInt("CurrentWeight", town.getCurrentWeight());
        hub.putInt("MaxWeight", town.getCurrentMaxWeight());

        ListTag eraTransitionsTag = new ListTag();
        TownInventory invForEra = town.getTownInventory();
        for (EraTransitionDef t : town.getAvailableTransitions()) {
            eraTransitionsTag.add(buildEraTransitionTag(t, invForEra));
        }
        hub.put("EraTransitions", eraTransitionsTag);
        ListTag boostedTag = new ListTag();
        for (String id : town.getBoostedBuildingIds()) boostedTag.add(StringTag.valueOf(id));
        hub.put("BoostedBuildings", boostedTag);

        ListTag cqTag = new ListTag();
        town.getConstructionQueue().forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        hub.put("ConstructionQueue", cqTag);

        TownInventory inv = town.getTownInventory();
        String orientation = town.getOrDeriveOrientation();
        Set<String> gatedIds = EraTransitionDataHandler.getAllGatedBuildingIds();
        Set<String> nextEraIds = new HashSet<>();
        for (EraTransitionDef t : EraTransitionDataHandler.getAvailableTransitions(town.getCurrentEra(), orientation)) {
            nextEraIds.addAll(t.unlockedBuildingIds);
        }
        nextEraIds.removeAll(town.getUnlockedBuildingIds());
        List<BuildingDef> sortedDefs = BuildingDataHandler.getAll().stream()
            .filter(def -> {
                if (def.terrainMatching) return false;
                if ("town_center".equals(def.category)) {
                    return town.getBuildings().stream().anyMatch(b -> b.defId.equals(def.id));
                }
                return !gatedIds.contains(def.id)
                    || town.getUnlockedBuildingIds().contains(def.id)
                    || nextEraIds.contains(def.id);
            })
            .sorted(Comparator.<BuildingDef, Boolean>comparing(def -> "town_center".equals(def.category))
                .reversed()
                .thenComparing(def -> nextEraIds.contains(def.id))
                .thenComparingInt(def -> BuildingListDataHandler.getIndex(def.id)))
            .toList();
        ListTag catalogTag = new ListTag();
        for (BuildingDef def : sortedDefs) {
            catalogTag.add(buildCatalogEntry(def, nextEraIds));
        }
        hub.put("BuildingCatalog", catalogTag);
        hub.put("StockSnapshot", buildStockSnapshotTag(inv));
        hub.put("TradePrices", TradePriceDataHandler.buildPricesTag());
        hub.put("UpgradeBuildings", buildUpgradeBuildingsTag());

        CompoundTag summaryData = new CompoundTag();
        summaryData.putString("Orientation", orientation);
        int totalRes = town.getTotalResidents();
        int foodDemand = (int) Math.ceil(town.computeTotalFoodDemandFloat()) * FoodRegistry.getFeedingSchedule().size();
        int totalHerd = town.getTotalHerd();
        int activeHerd = town.getActiveHerd();
        summaryData.putInt("TotalResidents", totalRes);
        summaryData.putInt("ActiveResidents", town.getActiveResidents());
        summaryData.putInt("TotalFoodDemand", foodDemand);
        summaryData.putInt("TotalHerd", totalHerd);
        summaryData.putInt("ActiveHerd", activeHerd);
        hub.put("SummaryData", summaryData);
        hub.putInt("TotalResidents", totalRes);
        hub.putInt("ActiveResidents", town.getActiveResidents());
        hub.putInt("TotalFoodDemand", foodDemand);
        hub.putInt("TotalHerd", totalHerd);
        hub.putInt("ActiveHerd", activeHerd);

        hub.put("Quests", buildQuestsTag());
        hub.put("ActivityLog", buildActivityLogTag());
        return hub;
    }

    // Targeted stock snapshot: produced items first, then items needed for costs.
    public CompoundTag buildStockUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        TownInventory inv = town.getTownInventory();
        CompoundTag stockTag = new CompoundTag();
        Set<Item> produced = new LinkedHashSet<>();
        for (PlacedBuilding b : town.getBuildings()) {
            BuildingDataHandler.get(b.defId).ifPresent(def -> {
                def.production.forEach(p -> produced.add(p.item()));
                def.transformations.forEach(t -> produced.add(t.outputItem()));
            });
        }
        produced.forEach(item ->
            stockTag.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        Stream.concat(
            BuildingDataHandler.getAll().stream()
                .flatMap(def -> Stream.concat(
                    def.constructionCost.stream().map(ItemCost::item),
                    def.upgrades.stream().flatMap(u -> u.upgradeCost().stream().map(ItemCost::item))
                )),
            EraTransitionDataHandler.getAll().stream()
                .flatMap(t -> t.resourceCost.stream().map(ItemCost::item))
        ).distinct()
            .filter(item -> !produced.contains(item))
            .forEach(item -> stockTag.putInt(
                BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        tag.put("StockSnapshot", stockTag);
        return tag;
    }

    // Map + queue + upgrade list (sent after construction changes).
    public CompoundTag buildBuildingListData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.put("MapData", buildMapData());
        tag.putInt("CurrentWeight", town.getCurrentWeight());
        tag.putInt("MaxWeight", town.getCurrentMaxWeight());
        ListTag cqTag = new ListTag();
        town.getConstructionQueue().forEach(e -> cqTag.add(QueueEntry.serialize(e)));
        tag.put("ConstructionQueue", cqTag);
        tag.put("UpgradeBuildings", buildUpgradeBuildingsTag());
        CompoundTag buildingCountsTag = new CompoundTag();
        for (PlacedBuilding b : town.getBuildings()) {
            buildingCountsTag.putInt(b.defId, buildingCountsTag.getInt(b.defId) + 1);
        }
        tag.put("BuildingCounts", buildingCountsTag);
        return tag;
    }

    // Active quest list (sent after quest state changes).
    public CompoundTag buildQuestUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.put("Quests", buildQuestsTag());
        return tag;
    }

    // Era progress + available transitions + updated building catalog (sent after era changes or queue updates).
    public CompoundTag buildEraUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.putInt("CurrentEra", town.getCurrentEra());
        tag.putString("CurrentOrientation", town.getOrDeriveOrientation());
        tag.putInt("CurrentWeight", town.getCurrentWeight());
        tag.putInt("MaxWeight", town.getCurrentMaxWeight());
        ListTag eraTransitionsTag = new ListTag();
        TownInventory invForEra = town.getTownInventory();
        for (EraTransitionDef t : town.getAvailableTransitions()) {
            eraTransitionsTag.add(buildEraTransitionTag(t, invForEra));
        }
        tag.put("EraTransitions", eraTransitionsTag);
        ListTag boostedTag = new ListTag();
        for (String id : town.getBoostedBuildingIds()) boostedTag.add(StringTag.valueOf(id));
        tag.put("BoostedBuildings", boostedTag);

        String orientation = town.getOrDeriveOrientation();
        Set<String> gatedIds = EraTransitionDataHandler.getAllGatedBuildingIds();
        Set<String> nextEraIds = new HashSet<>();
        for (EraTransitionDef t : EraTransitionDataHandler.getAvailableTransitions(town.getCurrentEra(), orientation)) {
            nextEraIds.addAll(t.unlockedBuildingIds);
        }
        nextEraIds.removeAll(town.getUnlockedBuildingIds());
        List<BuildingDef> sortedDefs = BuildingDataHandler.getAll().stream()
            .filter(def -> {
                if (def.terrainMatching) return false;
                if ("town_center".equals(def.category)) {
                    return town.getBuildings().stream().anyMatch(b -> b.defId.equals(def.id));
                }
                return !gatedIds.contains(def.id)
                    || town.getUnlockedBuildingIds().contains(def.id)
                    || nextEraIds.contains(def.id);
            })
            .sorted(Comparator.<BuildingDef, Boolean>comparing(def -> "town_center".equals(def.category))
                .reversed()
                .thenComparing(def -> nextEraIds.contains(def.id))
                .thenComparingInt(def -> BuildingListDataHandler.getIndex(def.id)))
            .toList();
        ListTag catalogTag = new ListTag();
        for (BuildingDef def : sortedDefs) {
            catalogTag.add(buildCatalogEntry(def, nextEraIds));
        }
        tag.put("BuildingCatalog", catalogTag);
        return tag;
    }

    // Resident + food demand + herd summary (sent after dawn tick).
    public CompoundTag buildCitizenUpdateData(BlockPos anchorPos) {
        CompoundTag tag = new CompoundTag();
        tag.put("AnchorPos", NbtUtils.writeBlockPos(anchorPos));
        tag.putInt("TotalResidents", town.getTotalResidents());
        tag.putInt("ActiveResidents", town.getActiveResidents());
        tag.putInt("TotalFoodDemand", (int) Math.ceil(town.computeTotalFoodDemandFloat()) * FoodRegistry.getFeedingSchedule().size());
        tag.putInt("TotalHerd", town.getTotalHerd());
        tag.putInt("ActiveHerd", town.getActiveHerd());
        return tag;
    }

    // Map data: bounds + all map elements (buildings, blocked zones, in-progress builds).
    public CompoundTag buildMapData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", town.getName());
        BlockPos[] bounds = town.getMapBounds();
        if (bounds != null) {
            tag.put("NWCorner", NbtUtils.writeBlockPos(bounds[0]));
            tag.put("SECorner", NbtUtils.writeBlockPos(bounds[1]));
        } else {
            tag.put("NWCorner", NbtUtils.writeBlockPos(BlockPos.ZERO));
            tag.put("SECorner", NbtUtils.writeBlockPos(BlockPos.ZERO));
        }
        ListTag elements = new ListTag();
        for (PlacedBuilding b : town.getBuildings()) {
            elements.add(buildingToMapElement(b));
        }
        for (BoundingBox bz : town.getBlockedZones()) {
            elements.add(blockedZoneToMapElement(bz));
        }
        for (Town.UnderConstructionEntry uc : town.getUnderConstructionBuildings()) {
            elements.add(underConstructionToMapElement(uc));
        }
        tag.put("Elements", elements);
        return tag;
    }

    // -------------------------------------------------------------------------
    // Private serialization helpers
    // -------------------------------------------------------------------------

    private CompoundTag buildEraTransitionTag(EraTransitionDef t, TownInventory inv) {
        CompoundTag tt = new CompoundTag();
        tt.putString("Id", t.id);
        tt.putString("OrientationLabel", t.orientationLabel);
        tt.putString("IconItem", t.iconItem);
        int effMinWeight = town.effectiveMinWeight(t);
        tt.putBoolean("PrereqsMet", town.meetsEraTransitionPrereqs(t));
        tt.putInt("RequiredWeight", effMinWeight);
        tt.putInt("CurrentWeight", town.getCurrentWeight());
        tt.putInt("MaxWeight", town.getCurrentMaxWeight());
        tt.putBoolean("WeightMet", town.getCurrentWeight() >= effMinWeight && town.getCurrentWeight() <= town.getCurrentMaxWeight());
        ListTag costTag = new ListTag();
        for (ItemCost ic : t.resourceCost) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
            ct.putInt("Amount", ic.amount());
            ct.putInt("Have", inv.getStock(ic.item()));
            costTag.add(ct);
        }
        tt.put("ResourceCost", costTag);
        tt.putInt("RequiredResidents", t.requiredResidents);
        tt.putInt("ActiveResidents", town.getActiveResidents());
        tt.putBoolean("ResidentsMet", t.requiredResidents <= 0 || town.getActiveResidents() >= t.requiredResidents);
        ListTag reqBuildTag = new ListTag();
        for (BuildingDef.BuildingRequirement req : t.requiredBuildings) {
            CompoundTag rt = new CompoundTag();
            rt.putString("DefId", req.defId());
            rt.putInt("Count", req.count());
            long have = town.getBuildings().stream().filter(b -> b.defId.equals(req.defId())).count();
            rt.putInt("Have", (int) have);
            reqBuildTag.add(rt);
        }
        tt.put("RequiredBuildings", reqBuildTag);
        ListTag unlockedTag = new ListTag();
        for (String bId : t.unlockedBuildingIds) {
            BuildingDataHandler.get(bId).ifPresent(def -> {
                CompoundTag ut = new CompoundTag();
                ut.putString("DefId", bId);
                ut.putString("IconItem", def.iconItem);
                ut.putString("Category", def.category);
                ListTag unlockCostTag = new ListTag();
                for (ItemCost c : def.constructionCost) {
                    CompoundTag ct = new CompoundTag();
                    ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item()).toString());
                    ct.putInt("Amount", c.amount());
                    unlockCostTag.add(ct);
                }
                ut.put("Cost", unlockCostTag);
                ut.putInt("RequiredResidents", def.requiredResidents);
                ListTag unlockReqBuildTag = new ListTag();
                for (BuildingDef.BuildingRequirement r : def.requiredBuildings) {
                    CompoundTag rt2 = new CompoundTag();
                    rt2.putString("DefId", r.defId());
                    rt2.putInt("Count", r.count());
                    unlockReqBuildTag.add(rt2);
                }
                ut.put("RequiredBuildings", unlockReqBuildTag);
                ut.putBoolean("HasProduction", !def.production.isEmpty());
                unlockedTag.add(ut);
            });
        }
        tt.put("UnlockedBuildings", unlockedTag);
        return tt;
    }

    private CompoundTag buildCatalogEntry(BuildingDef def, Set<String> nextEraIds) {
        CompoundTag dt = new CompoundTag();
        dt.putString("Id", def.id);
        dt.putString("Category", def.category);
        dt.putString("IconItem", def.iconItem);
        dt.putString("Nbt", def.nbt.toString());
        dt.putBoolean("HasBuilt", town.getBuildings().stream().anyMatch(b -> b.defId.equals(def.id)));
        dt.putInt("BuiltCount", (int) town.getBuildings().stream().filter(b -> b.defId.equals(def.id)).count());
        if (!def.nbtLevels.isEmpty()) {
            ListTag nbtLevelsTag = new ListTag();
            for (BuildingDef.NbtLevel nl : def.nbtLevels) {
                nbtLevelsTag.add(StringTag.valueOf(nl.nbt().toString()));
            }
            dt.put("NbtLevels", nbtLevelsTag);
        }
        if (nextEraIds.contains(def.id)) dt.putBoolean("NextEra", true);
        ListTag costTag = new ListTag();
        for (ItemCost ic : def.constructionCost) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
            ct.putInt("Amount", ic.amount());
            costTag.add(ct);
        }
        dt.put("ConstructionCost", costTag);
        int catalogLevel = town.getBuildings().stream()
            .filter(b -> b.defId.equals(def.id))
            .mapToInt(PlacedBuilding::getUpgradeLevel)
            .max()
            .orElse(0);
        dt.putInt("CurrentLevel", catalogLevel);
        ListTag prodTag = new ListTag();
        for (ProductionEntry pe : def.production) {
            CompoundTag pt = new CompoundTag();
            pt.putString("Item", BuiltInRegistries.ITEM.getKey(pe.item()).toString());
            pt.putInt("Amount", pe.amount());
            pt.putInt("EveryTicks", pe.everyTicks());
            pt.putInt("UnlockAtLevel", pe.unlockAtLevel());
            prodTag.add(pt);
        }
        dt.put("Production", prodTag);
        ListTag transTag = new ListTag();
        for (TransformationRecipe tr : def.transformations) {
            CompoundTag tt = new CompoundTag();
            ListTag inputsTag = new ListTag();
            for (ItemCost ic : tr.inputs()) {
                CompoundTag inp = new CompoundTag();
                inp.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                inp.putInt("Amount", ic.amount());
                inputsTag.add(inp);
            }
            tt.put("Inputs", inputsTag);
            tt.putString("OutputItem", BuiltInRegistries.ITEM.getKey(tr.outputItem()).toString());
            tt.putInt("OutputAmount", tr.outputAmount());
            tt.putInt("EveryTicks", def.transformEveryTicks);
            tt.putInt("UnlockAtLevel", tr.unlockAtLevel());
            transTag.add(tt);
        }
        dt.put("Transformations", transTag);
        dt.putDouble("ProductionBonus", def.productionBonus);
        if (def.residents > 0) {
            dt.putInt("Residents", def.residents);
            dt.putFloat("BaseConsumptionPerResident", def.consumptionPerResident);
            int maxLevel = def.upgrades.size();
            if (maxLevel > 0) {
                BuildingDef.ResolvedBuildingStats maxStats = def.resolveAtLevel(maxLevel);
                dt.putFloat("MaxConsumptionPerResident", maxStats.resolvedConsumptionPerResident());
                dt.putInt("MaxResidents", maxStats.resolvedResidents());
            }
        }
        if (def.herd > 0) {
            dt.putInt("Herd", def.herd);
            dt.putFloat("BaseConsumptionPerHerd", def.consumptionPerHerd);
        }
        if (def.requiredResidents > 0) {
            dt.putInt("RequiredResidents", def.requiredResidents);
        }
        if (!def.requiredBuildings.isEmpty()) {
            ListTag reqBuildingsTag = new ListTag();
            for (BuildingDef.BuildingRequirement req : def.requiredBuildings) {
                CompoundTag rt = new CompoundTag();
                rt.putString("DefId", req.defId());
                rt.putInt("Count", req.count());
                long have = town.getBuildings().stream().filter(b -> b.defId.equals(req.defId())).count();
                rt.putInt("Have", (int) have);
                reqBuildingsTag.add(rt);
            }
            dt.put("RequiredBuildings", reqBuildingsTag);
        }
        dt.putInt("Weight", def.weight);
        return dt;
    }

    private CompoundTag buildStockSnapshotTag(TownInventory inv) {
        CompoundTag stockTag = new CompoundTag();
        Stream.concat(
            BuildingDataHandler.getAll().stream()
                .flatMap(def -> Stream.concat(
                    def.constructionCost.stream().map(ItemCost::item),
                    def.upgrades.stream().flatMap(u -> u.upgradeCost().stream().map(ItemCost::item))
                )),
            EraTransitionDataHandler.getAll().stream()
                .flatMap(t -> t.resourceCost.stream().map(ItemCost::item))
        ).distinct()
            .forEach(item -> stockTag.putInt(
                BuiltInRegistries.ITEM.getKey(item).toString(), inv.getStock(item)));
        return stockTag;
    }

    private ListTag buildUpgradeBuildingsTag() {
        List<PlacedBuilding> upgradeBuildings = town.getBuildings().stream()
            .filter(b -> {
                BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
                return bDef != null && !bDef.terrainMatching && !bDef.upgrades.isEmpty();
            })
            .sorted((a, b2) -> {
                BuildingDef da = BuildingDataHandler.get(a.defId).orElse(null);
                BuildingDef db = BuildingDataHandler.get(b2.defId).orElse(null);
                int maxA = da != null ? Math.max(da.upgrades.size(), da.nbtLevels.size()) : 0;
                int maxB = db != null ? Math.max(db.upgrades.size(), db.nbtLevels.size()) : 0;
                boolean atMaxA = maxA > 0 && a.getUpgradeLevel() >= maxA;
                boolean atMaxB = maxB > 0 && b2.getUpgradeLevel() >= maxB;
                if (atMaxA != atMaxB) return atMaxA ? 1 : -1;
                return Integer.compare(BuildingListDataHandler.getIndex(a.defId), BuildingListDataHandler.getIndex(b2.defId));
            })
            .toList();
        ListTag tag = new ListTag();
        for (PlacedBuilding b : upgradeBuildings) {
            BuildingDef bDef = BuildingDataHandler.get(b.defId).orElse(null);
            if (bDef == null) continue;
            CompoundTag ubt = new CompoundTag();
            ubt.putString("DefId", b.defId);
            ubt.putLong("WorldPos", b.worldPos.asLong());
            ubt.putInt("UpgradeLevel", b.getUpgradeLevel());
            ubt.putString("Category", bDef.category);
            ubt.putString("IconItem", bDef.iconItem);
            tag.add(ubt);
        }
        return tag;
    }

    private ListTag buildQuestsTag() {
        ListTag questsTag = new ListTag();
        for (Quest q : town.getActiveQuests()) {
            CompoundTag qt = new CompoundTag();
            qt.putString("QuestId", q.questId);
            qt.putString("DefId", q.defId);
            qt.putString("QuestType", q.questType != null ? q.questType : "TASK");
            QuestDataHandler.get(q.defId).ifPresent(def -> {
                qt.putString("TitleKey", def.titleKey());
                qt.putString("DescKey", def.descKey());
            });
            ListTag condsTag = new ListTag();
            for (Quest.Condition c : q.conditions) {
                CompoundTag ct = new CompoundTag();
                ct.putString("Type", c.type);
                if (c.item != null) ct.putString("Item", BuiltInRegistries.ITEM.getKey(c.item).toString());
                ct.putInt("Required", c.required);
                condsTag.add(ct);
            }
            qt.put("Conditions", condsTag);
            if (q.reward != null) {
                CompoundTag rt = new CompoundTag();
                rt.putString("Type", q.reward.type);
                if (q.reward.item != null) rt.putString("Item", BuiltInRegistries.ITEM.getKey(q.reward.item).toString());
                rt.putInt("Amount", q.reward.amount);
                qt.put("Reward", rt);
            }
            questsTag.add(qt);
        }
        return questsTag;
    }

    private CompoundTag buildingToMapElement(PlacedBuilding b) {
        CompoundTag el = new CompoundTag();
        BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
        boolean isStreet = def != null && def.terrainMatching;
        el.putByte("Category", isStreet ? MapCategory.ROAD : MapCategory.BUILDING);
        el.putString("BuildType", b.defId);
        el.putLong("WorldPos", b.worldPos.asLong());
        BlockPos origin = b.bb != null
            ? new BlockPos(b.bb.minX(), b.bb.minY(), b.bb.minZ())
            : b.worldPos;
        el.put("OriginPos", NbtUtils.writeBlockPos(origin));
        if (b.bb != null) {
            el.putInt("SizeX", b.bb.maxX() - b.bb.minX());
            el.putInt("SizeZ", b.bb.maxZ() - b.bb.minZ());
        } else {
            el.putInt("SizeX", 5);
            el.putInt("SizeZ", 5);
        }
        el.putInt("Level", b.getUpgradeLevel() + 1);
        if (def != null) {
            el.putString("IconItem", def.iconItem);
        }
        if (!isStreet && def != null) {
            el.putString("BuildingCategory", def.category);

            int currentLevel = b.getUpgradeLevel();
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(currentLevel);
            // Build a lookup of unlocked entries (with upgrade-adjusted amounts) by item.
            Map<Item, ProductionEntry> unlockedAdj = new LinkedHashMap<>();
            for (ProductionEntry pe : stats.production()) unlockedAdj.put(pe.item(), pe);

            ListTag prodTag = new ListTag();
            for (ProductionEntry pe : def.production) {
                boolean locked = pe.unlockAtLevel() >= 0 && currentLevel < pe.unlockAtLevel();
                CompoundTag pt = new CompoundTag();
                pt.putString("Item", BuiltInRegistries.ITEM.getKey(pe.item()).toString());
                if (locked) {
                    pt.putInt("Amount", pe.amount());
                    pt.putInt("EveryTicks", pe.everyTicks());
                    pt.putBoolean("Locked", true);
                } else {
                    ProductionEntry adj = unlockedAdj.getOrDefault(pe.item(), pe);
                    int effectiveTicks = stats.totalCadenceMultiplier() > 0
                        ? (int) Math.max(1, Math.round(adj.everyTicks() / (1.0 + stats.totalCadenceMultiplier())))
                        : adj.everyTicks();
                    pt.putInt("Amount", adj.amount());
                    pt.putInt("EveryTicks", effectiveTicks);
                }
                prodTag.add(pt);
            }
            el.put("Production", prodTag);

            if (!def.transformations.isEmpty()) {
                ListTag transformsTag = new ListTag();
                for (TransformationRecipe tr : def.transformations) {
                    CompoundTag tt = new CompoundTag();
                    ListTag inputsTag = new ListTag();
                    for (ItemCost ic : tr.inputs()) {
                        CompoundTag it = new CompoundTag();
                        it.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                        it.putInt("Amount", ic.amount());
                        inputsTag.add(it);
                    }
                    tt.put("Inputs", inputsTag);
                    tt.putString("OutputItem", BuiltInRegistries.ITEM.getKey(tr.outputItem()).toString());
                    tt.putInt("OutputAmount", tr.outputAmount());
                    tt.putInt("EveryTicks", def.transformEveryTicks);
                    if (!tr.isActive(currentLevel)) tt.putBoolean("Locked", true);
                    transformsTag.add(tt);
                }
                el.put("Transforms", transformsTag);
            }

            if (def.productionBonus > 0) {
                el.putDouble("ProductionBonus", def.productionBonus);
            }
            if (def.residents > 0) {
                el.putInt("Residents", def.residents);
            }
            if (def.herd > 0) {
                el.putInt("Herd", def.herd);
            }
        }
        if (b.getInstanceProductionMultiplier() > 1.0) {
            el.putDouble("InstanceBonus", b.getInstanceProductionMultiplier() - 1.0);
        }
        if (town.isUnderUpgrade(b.worldPos)) {
            el.putBoolean("IsUpgrading", true);
        }
        if (isStreet && def != null && def.footprint != null && !def.footprint.isEmpty()) {
            List<String> rotatedFootprint = rotateFootprint(def.footprint, b.rotation);
            ListTag footprintTag = new ListTag();
            for (String row : rotatedFootprint) {
                footprintTag.add(StringTag.valueOf(row));
            }
            el.put("Footprint", footprintTag);
        }
        return el;
    }

    private CompoundTag underConstructionToMapElement(Town.UnderConstructionEntry uc) {
        CompoundTag el = new CompoundTag();
        el.putByte("Category", MapCategory.UNDER_CONSTRUCTION);
        el.putString("BuildType", uc.defId());
        el.put("OriginPos", NbtUtils.writeBlockPos(new BlockPos(uc.bb().minX(), uc.bb().minY(), uc.bb().minZ())));
        el.putInt("SizeX", uc.bb().maxX() - uc.bb().minX());
        el.putInt("SizeZ", uc.bb().maxZ() - uc.bb().minZ());
        return el;
    }

    private CompoundTag blockedZoneToMapElement(BoundingBox bz) {
        CompoundTag el = new CompoundTag();
        el.putByte("Category", MapCategory.BUILDING);
        el.putString("BuildType", "starter");
        el.put("OriginPos", NbtUtils.writeBlockPos(new BlockPos(bz.minX(), bz.minY(), bz.minZ())));
        el.putInt("SizeX", bz.maxX() - bz.minX());
        el.putInt("SizeZ", bz.maxZ() - bz.minZ());
        el.putInt("Level", 1);
        el.putString("IconItem", "minecraft:bell");
        el.putString("BuildingCategory", "town_center");
        el.put("Production", new ListTag());
        return el;
    }

    private ListTag buildActivityLogTag() {
        ListTag logTag = new ListTag();
        for (TownLogEntry e : town.getActivityLog()) {
            CompoundTag lt = new CompoundTag();
            lt.putString("Type", e.type().name());
            lt.putString("Param", e.param());
            lt.putLong("Tick", e.gameTick());
            logTag.add(lt);
        }
        return logTag;
    }

    // Rotates a footprint grid (rows of '0'/'1' chars) by the given Rotation enum value.
    private static List<String> rotateFootprint(List<String> footprint, Rotation rotation) {
        int times = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        List<String> result = footprint;
        for (int t = 0; t < times; t++) {
            result = rotateCW90(result);
        }
        return result;
    }

    // Rotates a 2D char grid 90 degrees clockwise. new[col][rows-1-row] = old[row][col]
    private static List<String> rotateCW90(List<String> grid) {
        int rows = grid.size();
        int cols = rows == 0 ? 0 : grid.get(0).length();
        char[][] rotated = new char[cols][rows];
        for (int i = 0; i < rows; i++) {
            String row = grid.get(i);
            for (int j = 0; j < cols && j < row.length(); j++) {
                rotated[j][rows - 1 - i] = row.charAt(j);
            }
        }
        List<String> result = new ArrayList<>();
        for (char[] row : rotated) {
            result.add(new String(row));
        }
        return result;
    }
}
