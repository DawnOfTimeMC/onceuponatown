package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.ProductionEntry;
import org.dawnoftime.onceuponatown.town.TransformationRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BuildingDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingDataHandler.class);
    private static final Map<String, BuildingDef> REGISTRY = new HashMap<>();

    // Called from OuatFabric via ServerLifecycleEvents.SERVER_STARTING
    public static void reload(MinecraftServer server) {
        REGISTRY.clear();
        ResourceManager rm = server.getResourceManager();
        rm.listResources("buildings", path -> path.getPath().endsWith(".json"))
            .forEach((location, resource) -> {
                if (!location.getNamespace().equals(Ouat.MOD_ID)) return;
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    BuildingDef def = parseDef(json);
                    REGISTRY.put(def.id, def);
                } catch (Exception e) {
                    LOGGER.error("[OUAT] Failed to load building def {}: {}", location, e.getMessage());
                }
            });
    }

    private static BuildingDef parseDef(JsonObject json) {
        String id = json.get("id").getAsString();
        ResourceLocation nbt = new ResourceLocation(json.get("nbt").getAsString());

        List<ProductionEntry> production = new ArrayList<>();
        if (json.has("production")) {
            for (JsonElement el : json.getAsJsonArray("production")) {
                JsonObject p = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(p.get("item").getAsString()));
                int unlockAtLevel = p.has("unlock_at_level") ? p.get("unlock_at_level").getAsInt() : -1;
                production.add(new ProductionEntry(
                    item,
                    p.get("amount").getAsInt(),
                    p.get("every_ticks").getAsInt(),
                    p.get("capacity_stacks").getAsInt(),
                    unlockAtLevel
                ));
            }
        }

        List<ItemCost> costs = new ArrayList<>();
        if (json.has("construction_cost")) {
            for (JsonElement el : json.getAsJsonArray("construction_cost")) {
                JsonObject c = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(c.get("item").getAsString()));
                costs.add(new ItemCost(item, c.get("amount").getAsInt()));
            }
        }

        String entryPool = json.has("entry_pool") ? json.get("entry_pool").getAsString() : "";
        boolean terrainMatching = json.has("terrain_matching") && json.get("terrain_matching").getAsBoolean();
        String iconItem = json.has("icon_item") ? json.get("icon_item").getAsString() : "minecraft:dirt";
        String category = json.has("category") ? json.get("category").getAsString() : "";

        List<String> footprint = null;
        if (json.has("footprint")) {
            footprint = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("footprint")) {
                footprint.add(el.getAsString());
            }
        }

        List<TransformationRecipe> transformations = new ArrayList<>();
        if (json.has("transformations")) {
            for (JsonElement el : json.getAsJsonArray("transformations")) {
                JsonObject t = el.getAsJsonObject();
                List<ItemCost> inputs = new ArrayList<>();
                for (JsonElement inputEl : t.getAsJsonArray("inputs")) {
                    JsonObject inp = inputEl.getAsJsonObject();
                    Item inputItem = BuiltInRegistries.ITEM.get(new ResourceLocation(inp.get("item").getAsString()));
                    inputs.add(new ItemCost(inputItem, inp.get("amount").getAsInt()));
                }
                Item outputItem = BuiltInRegistries.ITEM.get(new ResourceLocation(t.get("output").getAsString()));
                int unlockAtLevel = t.has("unlock_at_level") ? t.get("unlock_at_level").getAsInt() : -1;
                transformations.add(new TransformationRecipe(
                    inputs,
                    outputItem,
                    t.get("output_amount").getAsInt(),
                    t.get("output_capacity_stacks").getAsInt(),
                    unlockAtLevel
                ));
            }
        }
        float transformInputRatio = json.has("transform_input_ratio") ? json.get("transform_input_ratio").getAsFloat() : 0.1f;
        int transformEveryTicks = json.has("transform_every_ticks") ? json.get("transform_every_ticks").getAsInt() : 1600;

        double productionBonus = json.has("production_bonus") ? json.get("production_bonus").getAsDouble() : 0.0;
        int stockBonus = json.has("stock_bonus") ? json.get("stock_bonus").getAsInt() : 0;
        int residents = json.has("residents") ? json.get("residents").getAsInt() : 0;

        List<BuildingDef.UpgradeLevel> upgrades = new ArrayList<>();
        if (json.has("upgrades")) {
            for (JsonElement el : json.getAsJsonArray("upgrades")) {
                JsonObject u = el.getAsJsonObject();
                float cadenceMult = u.has("cadence_multiplier") ? u.get("cadence_multiplier").getAsFloat() : 0f;
                int capAdd    = u.has("capacity_stacks_add") ? u.get("capacity_stacks_add").getAsInt() : 0;
                int amountAdd = u.has("amount_add") ? u.get("amount_add").getAsInt() : 0;
                int residentsAdd = u.has("residents_add") ? u.get("residents_add").getAsInt() : 0;
                float consumptionAdd = u.has("consumption_per_resident_add")
                    ? u.get("consumption_per_resident_add").getAsFloat() : 0f;
                double productionBonusAdd = u.has("production_bonus_add")
                    ? u.get("production_bonus_add").getAsDouble() : 0.0;
                int stockBonusAdd = u.has("stock_bonus_add") ? u.get("stock_bonus_add").getAsInt() : 0;
                int herdAdd = u.has("herd_add") ? u.get("herd_add").getAsInt() : 0;
                float consumptionPerHerdAdd = u.has("consumption_per_herd_add")
                    ? u.get("consumption_per_herd_add").getAsFloat() : 0f;
                List<String> unlockedDisplay = new ArrayList<>();
                if (u.has("unlocks_display")) {
                    for (JsonElement de : u.getAsJsonArray("unlocks_display")) {
                        unlockedDisplay.add(de.getAsString());
                    }
                }
                List<ItemCost> upgradeCost = new ArrayList<>();
                if (u.has("upgrade_cost")) {
                    for (JsonElement ce : u.getAsJsonArray("upgrade_cost")) {
                        JsonObject c = ce.getAsJsonObject();
                        Item costItem = BuiltInRegistries.ITEM.get(new ResourceLocation(c.get("item").getAsString()));
                        upgradeCost.add(new ItemCost(costItem, c.get("amount").getAsInt()));
                    }
                }
                upgrades.add(new BuildingDef.UpgradeLevel(cadenceMult, capAdd, amountAdd, residentsAdd, consumptionAdd,
                    productionBonusAdd, stockBonusAdd, herdAdd, consumptionPerHerdAdd, unlockedDisplay, upgradeCost));
            }
        }

        List<BuildingDef.NbtLevel> nbtLevels = new ArrayList<>();
        if (json.has("nbt_levels")) {
            for (JsonElement el : json.getAsJsonArray("nbt_levels")) {
                if (el.isJsonObject()) {
                    JsonObject lvl = el.getAsJsonObject();
                    ResourceLocation lvlNbt = new ResourceLocation(lvl.get("nbt").getAsString());
                    int depth = lvl.has("underground_depth") ? lvl.get("underground_depth").getAsInt() : 0;
                    nbtLevels.add(new BuildingDef.NbtLevel(lvlNbt, depth));
                } else {
                    nbtLevels.add(new BuildingDef.NbtLevel(new ResourceLocation(el.getAsString()), 0));
                }
            }
        }

        int requiredResidents = json.has("required_residents") ? json.get("required_residents").getAsInt() : 0;
        List<BuildingDef.BuildingRequirement> requiredBuildings = new ArrayList<>();
        if (json.has("required_buildings")) {
            for (JsonElement el : json.getAsJsonArray("required_buildings")) {
                JsonObject rb = el.getAsJsonObject();
                String reqDefId = rb.get("defId").getAsString();
                int reqCount = rb.has("count") ? rb.get("count").getAsInt() : 1;
                requiredBuildings.add(new BuildingDef.BuildingRequirement(reqDefId, reqCount));
            }
        }

        float consumptionPerResident = json.has("consumption_per_resident")
            ? json.get("consumption_per_resident").getAsFloat() : 0f;

        int herd = json.has("herd") ? json.get("herd").getAsInt() : 0;
        float consumptionPerHerd = json.has("consumption_per_herd")
            ? json.get("consumption_per_herd").getAsFloat() : 0f;
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;

        List<ItemCost> initialStock = new ArrayList<>();
        if (json.has("initial_stock")) {
            for (JsonElement el : json.getAsJsonArray("initial_stock")) {
                JsonObject s = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(s.get("item").getAsString()));
                initialStock.add(new ItemCost(item, s.get("amount").getAsInt()));
            }
        }

        return new BuildingDef(id, nbt, entryPool, production, costs, terrainMatching, iconItem, category, footprint,
            transformations, transformInputRatio, transformEveryTicks,
            productionBonus, stockBonus, residents, upgrades, nbtLevels,
            requiredResidents, requiredBuildings, consumptionPerResident, initialStock,
            herd, consumptionPerHerd, weight);
    }

    // Builds the NBT payload sent to the client on player join (upgrade info only).
    public static CompoundTag buildDefsPacketData() {
        CompoundTag tag = new CompoundTag();
        ListTag defsTag = new ListTag();
        for (BuildingDef def : REGISTRY.values()) {
            if (def.upgrades.isEmpty()) continue;
            CompoundTag dt = new CompoundTag();
            dt.putString("Id", def.id);
            dt.putInt("BaseCapacity", def.production.isEmpty() ? 0 : def.production.get(0).capacityStacks());
            dt.putInt("BaseAmount", def.production.isEmpty() ? 0 : def.production.get(0).amount());
            dt.putInt("BaseResidents", def.residents);
            dt.putFloat("BaseConsumption", def.consumptionPerResident);
            dt.putDouble("BaseProductionBonus", def.productionBonus);
            dt.putInt("BaseStockBonus", def.stockBonus);
            ListTag upgsTag = new ListTag();
            for (BuildingDef.UpgradeLevel upg : def.upgrades) {
                CompoundTag ut = new CompoundTag();
                ut.putFloat("CadenceMult", upg.cadenceMultiplier());
                ut.putInt("CapAdd", upg.capacityStacksAdd());
                ut.putInt("AmountAdd", upg.amountAdd());
                ut.putInt("ResidentsAdd", upg.residentsAdd());
                ut.putFloat("ConsumptionAdd", upg.consumptionPerResidentAdd());
                ut.putDouble("ProductionBonusAdd", upg.productionBonusAdd());
                ut.putInt("StockBonusAdd", upg.stockBonusAdd());
                ut.putInt("HerdAdd", upg.herdAdd());
                ut.putFloat("HerdConsumptionAdd", upg.consumptionPerHerdAdd());
                ListTag unlocksTag = new ListTag();
                for (String itemId : upg.unlockedDisplay()) {
                    unlocksTag.add(net.minecraft.nbt.StringTag.valueOf(itemId));
                }
                ut.put("UnlocksDisplay", unlocksTag);
                ListTag costTag = new ListTag();
                for (ItemCost ic : upg.upgradeCost()) {
                    CompoundTag ct = new CompoundTag();
                    ct.putString("Item", BuiltInRegistries.ITEM.getKey(ic.item()).toString());
                    ct.putInt("Amount", ic.amount());
                    costTag.add(ct);
                }
                ut.put("Cost", costTag);
                upgsTag.add(ut);
            }
            dt.put("Upgrades", upgsTag);
            defsTag.add(dt);
        }
        tag.put("Defs", defsTag);
        return tag;
    }

    public static Optional<BuildingDef> get(String id) { return Optional.ofNullable(REGISTRY.get(id)); }
    public static Collection<BuildingDef> getAll() { return REGISTRY.values(); }
}
