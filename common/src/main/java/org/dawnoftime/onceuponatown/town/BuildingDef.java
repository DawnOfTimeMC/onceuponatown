package org.dawnoftime.onceuponatown.town;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;

public class BuildingDef {
    public final String id;
    public final ResourceLocation nbt;
    // Pool name of this building's entry jigsaw. Must match the targetPool of the connection point.
    // Empty string means this building can be placed at any connection regardless of pool.
    public final String entryPool;
    public final List<ProductionEntry> production;
    public final List<ItemCost> constructionCost;
    // When true, placement snaps Y to ground level (for streets that must follow terrain).
    public final boolean terrainMatching;
    // Item resource location shown as icon in the map tooltip, e.g. "minecraft:yellow_bed".
    public final String iconItem;
    // Logical group for map display: "buildings" or "gardens". Roads are identified by terrainMatching.
    public final String category;
    // XZ footprint for map rendering: list of strings, one per Z row, each char is an X column.
    // '1' = road cell rendered on map, '0' = gap. Null means render bounding box as solid rectangle.
    public final List<String> footprint;
    // Ordered list of transformation recipes. Empty = not a transformer.
    public final List<TransformationRecipe> transformations;
    // Fraction of town stock taken as budget per input item at the start of each transform pass.
    public final float transformInputRatio;
    // Ticks between each transformation pass.
    public final int transformEveryTicks;
    // Additive bonus applied to village-wide production amounts (e.g. 0.03 = +3% per building).
    public final double productionBonus;
    // Extra capacity stacks added to every productive building in the village (e.g. 1 = +64 items per building).
    public final int stockBonus;
    // Number of resident slots this building adds to the village.
    public final int residents;
    // Number of animals this building holds. 0 = no herd logic.
    public final int herd;
    // Food units consumed per animal per day.
    public final float consumptionPerHerd;
    // Ordered upgrade levels. Entry [0] = level 1, [1] = level 2, etc. Empty = not upgradable.
    public final List<UpgradeLevel> upgrades;
    // NBT files for each visual upgrade tier. Entry [0] = visual for level 1, [1] = visual for level 2, etc.
    // If absent for a given level, the upgrade is stat-only (no visual change).
    public final List<NbtLevel> nbtLevels;

    // Per-level NBT metadata. undergroundDepth = how many blocks deeper than the base template this level goes.
    // Used to shift the upgrade diff origin downward so underground galleries land at the correct Y.
    public record NbtLevel(ResourceLocation nbt, int undergroundDepth) {}
    // Minimum total village residents required before this building can be constructed.
    public final int requiredResidents;
    // Specific buildings that must already exist in the village before this one can be constructed.
    public final List<BuildingRequirement> requiredBuildings;
    // Food units consumed per resident per day at level 0. Only meaningful for residential buildings.
    public final float consumptionPerResident;
    // Items injected into the town stock when this building is placed as the village starter. Empty for non-starters.
    public final List<ItemCost> initialStock;
    // Weight units this building consumes from the era cap when placed or queued.
    public final int weight;

    // One upgrade step: cost + what it changes. All fields are additive deltas.
    public record UpgradeLevel(float cadenceMultiplier, int capacityStacksAdd, int amountAdd,
                                int residentsAdd, float consumptionPerResidentAdd,
                                double productionBonusAdd,
                                int stockBonusAdd,
                                int herdAdd, float consumptionPerHerdAdd,
                                List<String> unlockedDisplay,
                                List<ItemCost> upgradeCost) {}

    // A single building prerequisite: N copies of a given defId must already be placed.
    public record BuildingRequirement(String defId, int count) {}

    // Effective stats for a building at a given upgrade level (cached by PlacedBuilding).
    public record ResolvedBuildingStats(List<ProductionEntry> production, double totalCadenceMultiplier,
                                         int resolvedResidents, float resolvedConsumptionPerResident,
                                         int resolvedHerd, float resolvedConsumptionPerHerd) {}

    public BuildingDef(String id, ResourceLocation nbt, String entryPool,
                       List<ProductionEntry> production, List<ItemCost> constructionCost,
                       boolean terrainMatching, String iconItem, String category,
                       List<String> footprint,
                       List<TransformationRecipe> transformations,
                       float transformInputRatio, int transformEveryTicks,
                       double productionBonus, int stockBonus, int residents,
                       List<UpgradeLevel> upgrades, List<NbtLevel> nbtLevels,
                       int requiredResidents, List<BuildingRequirement> requiredBuildings,
                       float consumptionPerResident, List<ItemCost> initialStock,
                       int herd, float consumptionPerHerd, int weight) {
        this.id = id;
        this.nbt = nbt;
        this.entryPool = entryPool;
        this.production = production;
        this.constructionCost = constructionCost;
        this.terrainMatching = terrainMatching;
        this.iconItem = iconItem;
        this.category = category;
        this.footprint = footprint;
        this.transformations = transformations;
        this.transformInputRatio = transformInputRatio;
        this.transformEveryTicks = transformEveryTicks;
        this.productionBonus = productionBonus;
        this.stockBonus = stockBonus;
        this.residents = residents;
        this.upgrades = upgrades;
        this.nbtLevels = nbtLevels;
        this.requiredResidents = requiredResidents;
        this.requiredBuildings = requiredBuildings;
        this.consumptionPerResident = consumptionPerResident;
        this.initialStock = initialStock;
        this.herd = herd;
        this.consumptionPerHerd = consumptionPerHerd;
        this.weight = weight;
    }

    // Returns effective production, cadence, residents, consumption, herd, and herd consumption at a given upgrade level.
    // Level 0 = base stats with no upgrades applied.
    public ResolvedBuildingStats resolveAtLevel(int level) {
        List<ProductionEntry> activeProduction = production.stream()
            .filter(e -> e.unlockAtLevel() == -1 || e.unlockAtLevel() <= level)
            .toList();
        if (level <= 0 || upgrades.isEmpty()) {
            return new ResolvedBuildingStats(activeProduction, 0.0, residents, consumptionPerResident, herd, consumptionPerHerd);
        }
        int capped = Math.min(level, upgrades.size());
        double totalCadence = 0.0;
        int totalCapAdd = 0;
        int totalAmountAdd = 0;
        int totalResidentsAdd = 0;
        float totalConsumptionAdd = 0f;
        int totalHerdAdd = 0;
        float totalHerdConsumptionAdd = 0f;
        for (int i = 0; i < capped; i++) {
            totalCadence             += upgrades.get(i).cadenceMultiplier();
            totalCapAdd              += upgrades.get(i).capacityStacksAdd();
            totalAmountAdd           += upgrades.get(i).amountAdd();
            totalResidentsAdd        += upgrades.get(i).residentsAdd();
            totalConsumptionAdd      += upgrades.get(i).consumptionPerResidentAdd();
            totalHerdAdd             += upgrades.get(i).herdAdd();
            totalHerdConsumptionAdd  += upgrades.get(i).consumptionPerHerdAdd();
        }
        int resolvedResidents       = residents + totalResidentsAdd;
        float resolvedConsumption   = consumptionPerResident + totalConsumptionAdd;
        int resolvedHerd            = herd + totalHerdAdd;
        float resolvedHerdConsumption = consumptionPerHerd + totalHerdConsumptionAdd;
        if (totalCapAdd == 0 && totalAmountAdd == 0) {
            return new ResolvedBuildingStats(activeProduction, totalCadence, resolvedResidents, resolvedConsumption, resolvedHerd, resolvedHerdConsumption);
        }
        int finalCapAdd    = totalCapAdd;
        int finalAmountAdd = totalAmountAdd;
        List<ProductionEntry> adjusted = activeProduction.stream()
            .map(e -> new ProductionEntry(
                e.item(),
                e.amount() + finalAmountAdd,
                e.everyTicks(),
                e.capacityStacks() + finalCapAdd,
                e.unlockAtLevel()))
            .toList();
        return new ResolvedBuildingStats(adjusted, totalCadence, resolvedResidents, resolvedConsumption, resolvedHerd, resolvedHerdConsumption);
    }

    public boolean isTransformer() { return !transformations.isEmpty(); }
}
