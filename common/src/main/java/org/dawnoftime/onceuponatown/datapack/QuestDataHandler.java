package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.town.QuestDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class QuestDataHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestDataHandler.class);
    private static final Map<String, QuestDef> REGISTRY = new HashMap<>();

    public static void reload(MinecraftServer server) {
        REGISTRY.clear();
        ResourceManager rm = server.getResourceManager();
        rm.listResources("quests", path -> path.getPath().endsWith(".json"))
            .forEach((location, resource) -> {
                if (!location.getNamespace().equals(Ouat.MOD_ID)) return;
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    QuestDef def = parseDef(json);
                    REGISTRY.put(def.id(), def);
                } catch (Exception e) {
                    LOGGER.error("[OUAT] Failed to load quest def {}: {}", location, e.getMessage());
                }
            });
    }

    private static QuestDef parseDef(JsonObject json) {
        String id = json.get("id").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TASK";
        String titleKey = json.get("title").getAsString();
        String descKey = json.get("description").getAsString();

        // refresh_interval_ticks only applies to TASK quests; default 4500 if omitted
        long refreshIntervalTicks = json.has("refresh_interval_ticks")
            ? json.get("refresh_interval_ticks").getAsLong()
            : 4500L;

        List<QuestDef.ConditionTemplate> conditions = new ArrayList<>();
        if (json.has("conditions")) {
            for (JsonElement el : json.getAsJsonArray("conditions")) {
                JsonObject c = el.getAsJsonObject();
                conditions.add(new QuestDef.ConditionTemplate(
                    c.get("type").getAsString(),
                    c.has("item") ? c.get("item").getAsString() : null,
                    c.has("required") ? c.get("required").getAsInt() : 0,
                    c.has("send_to_stock") && c.get("send_to_stock").getAsBoolean()
                ));
            }
        }

        QuestDef.RewardTemplate reward = null;
        if (json.has("reward")) {
            JsonObject r = json.getAsJsonObject("reward");
            reward = new QuestDef.RewardTemplate(
                r.get("type").getAsString(),
                r.has("item") ? r.get("item").getAsString() : null,
                r.has("amount") ? r.get("amount").getAsInt() : 0
            );
        }

        QuestDef.Prerequisites prerequisites = QuestDef.Prerequisites.NONE;
        if (json.has("prerequisites")) {
            prerequisites = parsePrerequisites(json.getAsJsonObject("prerequisites"));
        }

        return new QuestDef(id, type, titleKey, descKey, conditions, reward, refreshIntervalTicks, prerequisites);
    }

    private static QuestDef.Prerequisites parsePrerequisites(JsonObject json) {
        int minEra = json.has("min_era") ? json.get("min_era").getAsInt() : 0;
        int maxEra = json.has("max_era") ? json.get("max_era").getAsInt() : Integer.MAX_VALUE;

        List<String> requiredBuildings = new ArrayList<>();
        if (json.has("required_buildings")) {
            for (JsonElement el : json.getAsJsonArray("required_buildings")) {
                requiredBuildings.add(el.getAsString());
            }
        }

        int minResidents = json.has("min_residents") ? json.get("min_residents").getAsInt() : 0;
        int maxResidents = json.has("max_residents") ? json.get("max_residents").getAsInt() : Integer.MAX_VALUE;

        List<String> requiredOrientations = new ArrayList<>();
        if (json.has("required_orientations")) {
            for (JsonElement el : json.getAsJsonArray("required_orientations")) {
                requiredOrientations.add(el.getAsString());
            }
        }

        List<QuestDef.StockCondition> stockConditions = new ArrayList<>();
        if (json.has("stock_conditions")) {
            for (JsonElement el : json.getAsJsonArray("stock_conditions")) {
                JsonObject sc = el.getAsJsonObject();
                String item = sc.get("item").getAsString();
                int min = sc.has("min") ? sc.get("min").getAsInt() : 0;
                int max = sc.has("max") ? sc.get("max").getAsInt() : Integer.MAX_VALUE;
                stockConditions.add(new QuestDef.StockCondition(item, min, max));
            }
        }

        return new QuestDef.Prerequisites(minEra, maxEra, requiredBuildings, minResidents, maxResidents,
            requiredOrientations, stockConditions);
    }

    public static Optional<QuestDef> get(String id) { return Optional.ofNullable(REGISTRY.get(id)); }
    public static Collection<QuestDef> getAll() { return REGISTRY.values(); }
    public static Set<String> getRegistryKeySet() { return REGISTRY.keySet(); }
}
