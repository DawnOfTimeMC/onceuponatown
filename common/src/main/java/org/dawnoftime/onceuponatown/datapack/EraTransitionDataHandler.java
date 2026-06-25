package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class EraTransitionDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(EraTransitionDataHandler.class);
    private static final Map<String, EraTransitionDef> REGISTRY = new HashMap<>();
    // EraDef keyed by starter_building_id (e.g. "settlement", "settlement_2", "settlement_3")
    private static final Map<String, EraDef> ERA_DEF_BY_STARTER = new HashMap<>();
    // EraDef keyed by orientation (e.g. "industrial", "pastoral", "agricultural")
    private static final Map<String, EraDef> ERA_DEF_BY_ORIENTATION = new HashMap<>();

    public static void reload(MinecraftServer server) {
        REGISTRY.clear();
        ERA_DEF_BY_STARTER.clear();
        ERA_DEF_BY_ORIENTATION.clear();
        ResourceManager rm = server.getResourceManager();
        rm.listResources("eras", path -> path.getPath().endsWith(".json"))
            .forEach((location, resource) -> {
                if (!location.getNamespace().equals(Ouat.MOD_ID)) return;
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("era")) {
                        EraDef def = parseEraDef(json);
                        ERA_DEF_BY_STARTER.put(def.starterBuildingId, def);
                        ERA_DEF_BY_ORIENTATION.put(def.orientation, def);
                    } else {
                        EraTransitionDef def = parseTransitionDef(location, json);
                        REGISTRY.put(def.id, def);
                    }
                } catch (Exception e) {
                    LOGGER.error("[OUAT] Failed to load era def {}: {}", location, e.getMessage());
                }
            });
    }

    private static EraDef parseEraDef(JsonObject json) {
        int era = json.get("era").getAsInt();
        String orientation = json.get("orientation").getAsString();
        String orientationLabel = json.has("orientation_label") ? json.get("orientation_label").getAsString() : orientation;
        String structureLabel = json.has("structure_label") ? json.get("structure_label").getAsString() : "";
        String iconItem = json.has("icon_item") ? json.get("icon_item").getAsString() : "minecraft:dirt";
        String starterBuildingId = json.get("starter_building_id").getAsString();
        List<String> boostedBuildings = new ArrayList<>();
        if (json.has("boosted_buildings")) {
            for (JsonElement el : json.getAsJsonArray("boosted_buildings")) {
                boostedBuildings.add(el.getAsString());
            }
        }
        int initialMaxWeight = json.has("initial_max_weight") ? json.get("initial_max_weight").getAsInt() : 20;
        double boostMultiplier = json.has("boost_multiplier") ? json.get("boost_multiplier").getAsDouble() : 1.0;
        return new EraDef(era, orientation, orientationLabel, structureLabel, iconItem, starterBuildingId,
            boostedBuildings, initialMaxWeight, boostMultiplier);
    }

    // Derives the transition id from the file path (e.g. "eras/2_rural.json" -> "2_rural").
    private static EraTransitionDef parseTransitionDef(ResourceLocation location, JsonObject json) {
        String path = location.getPath();
        String id = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
        int fromEra = json.get("from_era").getAsInt();
        String fromOrientation = json.has("from_orientation") ? json.get("from_orientation").getAsString() : "";
        String orientationLabel = json.has("orientation_label") ? json.get("orientation_label").getAsString() : id;
        String iconItem = json.has("icon_item") ? json.get("icon_item").getAsString() : "minecraft:dirt";
        int minWeightPercent = json.has("min_weight_percent") ? json.get("min_weight_percent").getAsInt() : 0;
        String nextOrientation = json.has("next_orientation") ? json.get("next_orientation").getAsString() : "";
        int weightCapIncrease = json.has("weight_cap_increase") ? json.get("weight_cap_increase").getAsInt() : 0;

        List<ItemCost> resourceCost = new ArrayList<>();
        if (json.has("resource_cost")) {
            for (JsonElement el : json.getAsJsonArray("resource_cost")) {
                JsonObject c = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(c.get("item").getAsString()));
                resourceCost.add(new ItemCost(item, c.get("amount").getAsInt()));
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

        List<String> unlockedBuildingIds = new ArrayList<>();
        if (json.has("unlocked_building_ids")) {
            for (JsonElement el : json.getAsJsonArray("unlocked_building_ids")) {
                unlockedBuildingIds.add(el.getAsString());
            }
        }

        List<String> autoUpgradeIds = new ArrayList<>();
        if (json.has("auto_upgrade_ids")) {
            for (JsonElement el : json.getAsJsonArray("auto_upgrade_ids")) {
                autoUpgradeIds.add(el.getAsString());
            }
        }

        String structureLabel = json.has("structure_label")
            ? json.get("structure_label").getAsString()
            : "";
        boolean unlockNewBuilder = json.has("unlock_new_builder") && json.get("unlock_new_builder").getAsBoolean();

        return new EraTransitionDef(id, fromEra, fromOrientation, orientationLabel, iconItem,
            resourceCost, requiredResidents, requiredBuildings, Collections.unmodifiableList(unlockedBuildingIds),
            nextOrientation, weightCapIncrease, minWeightPercent, structureLabel, unlockNewBuilder,
            Collections.unmodifiableList(autoUpgradeIds));
    }

    public static List<EraTransitionDef> getAvailableTransitions(int fromEra, String currentOrientation) {
        List<EraTransitionDef> result = new ArrayList<>();
        for (EraTransitionDef def : REGISTRY.values()) {
            if (def.fromEra != fromEra) continue;
            if (!def.fromOrientation.isEmpty() && !def.fromOrientation.equals(currentOrientation)) continue;
            result.add(def);
        }
        return result;
    }

    public static Optional<EraTransitionDef> get(String id) { return Optional.ofNullable(REGISTRY.get(id)); }
    public static Collection<EraTransitionDef> getAll() { return REGISTRY.values(); }

    public static EraDef getEraDefForStarter(String starterBuildingId) {
        return ERA_DEF_BY_STARTER.get(starterBuildingId);
    }

    public static EraDef getEraDefByOrientation(String orientation) {
        return ERA_DEF_BY_ORIENTATION.get(orientation);
    }

    // Returns the set of all building IDs that are gated behind at least one era transition.
    // Buildings NOT in this set are available from era 0 (always visible in the catalog).
    public static Set<String> getAllGatedBuildingIds() {
        Set<String> gated = new HashSet<>();
        for (EraTransitionDef def : REGISTRY.values()) {
            gated.addAll(def.unlockedBuildingIds);
        }
        return gated;
    }
}
