package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Loads food_list.json from the config datapack folder.
// Insertion order is preserved; accessors return pre-reversed lists (strongest FUV first).
public class FoodListDataHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(FoodListDataHandler.class);

    private static Map<Item, Integer> RESIDENT_FOOD_MAP = Collections.emptyMap();
    private static Map<Item, Integer> HERD_FOOD_MAP = Collections.emptyMap();
    private static List<Long> FEEDING_SCHEDULE = Collections.emptyList();

    public static void reload(MinecraftServer server) {
        LinkedHashMap<Item, Integer> newResidentMap = new LinkedHashMap<>();
        LinkedHashMap<Item, Integer> newHerdMap = new LinkedHashMap<>();
        List<Long> newSchedule = new ArrayList<>();
        ResourceManager rm = server.getResourceManager();
        var resources = rm.listResources("config", path -> path.getPath().endsWith("food_list.json"));
        for (var entry : resources.entrySet()) {
            if (!entry.getKey().getNamespace().equals(Ouat.MOD_ID)) continue;
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json.has("feeding_schedule")) {
                    JsonArray scheduleArray = json.getAsJsonArray("feeding_schedule");
                    for (var el : scheduleArray) {
                        newSchedule.add(el.getAsLong());
                    }
                }
                if (json.has("resident_food")) {
                    parseItemList(json.getAsJsonArray("resident_food"), newResidentMap);
                }
                if (json.has("herd_food")) {
                    parseItemList(json.getAsJsonArray("herd_food"), newHerdMap);
                }
            } catch (Exception e) {
                LOGGER.error("[OUAT] Failed to load food_list.json: {}", e.getMessage());
            }
            break;
        }
        RESIDENT_FOOD_MAP = Collections.unmodifiableMap(newResidentMap);
        HERD_FOOD_MAP = Collections.unmodifiableMap(newHerdMap);
        FEEDING_SCHEDULE = Collections.unmodifiableList(newSchedule);
    }

    private static void parseItemList(JsonArray array, LinkedHashMap<Item, Integer> map) {
        for (var el : array) {
            JsonObject obj = el.getAsJsonObject();
            String itemId = obj.get("item").getAsString();
            int fuv = obj.get("fuv").getAsInt();
            ResourceLocation rl = new ResourceLocation(itemId);
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item == null) continue;
            map.put(item, fuv);
        }
    }

    public static List<Map.Entry<Item, Integer>> residentEntriesInOrder() {
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(RESIDENT_FOOD_MAP.entrySet());
        Collections.reverse(list);
        return list;
    }

    public static List<Map.Entry<Item, Integer>> herdEntriesInOrder() {
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(HERD_FOOD_MAP.entrySet());
        Collections.reverse(list);
        return list;
    }

    public static List<Long> getFeedingSchedule() {
        return FEEDING_SCHEDULE;
    }
}
