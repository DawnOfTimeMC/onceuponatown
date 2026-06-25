package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildingListDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingListDataHandler.class);
    private static List<String> ORDER = Collections.emptyList();

    public static void reload(MinecraftServer server) {
        List<String> newOrder = new ArrayList<>();
        ResourceManager rm = server.getResourceManager();
        var resources = rm.listResources("config", path -> path.getPath().endsWith("building_list.json"));
        for (var entry : resources.entrySet()) {
            if (!entry.getKey().getNamespace().equals(Ouat.MOD_ID)) continue;
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json.has("order")) {
                    for (var el : json.getAsJsonArray("order")) {
                        newOrder.add(el.getAsString());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[OUAT] Failed to load building_list.json: {}", e.getMessage());
            }
            break;
        }
        ORDER = Collections.unmodifiableList(newOrder);
    }

    public static List<String> getOrder() {
        return ORDER;
    }

    // Returns the display index for a defId. Listed buildings return their 0-based position.
    // Unlisted buildings return Integer.MAX_VALUE so they sort after all listed ones.
    public static int getIndex(String defId) {
        int idx = ORDER.indexOf(defId);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }
}
