package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Loads data/onceuponatown/config/trade_prices.json.
// Defines which items can be traded at the village hub and their emerald buy/sell prices.
// buy = emeralds player pays to receive 1 unit from village.
// sell = emeralds player receives for sending 1 unit to village.
public class TradePriceDataHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(TradePriceDataHandler.class);

    // int[0] = buy price, int[1] = sell price, int[2] = lot quantity (default 1)
    private static Map<Item, int[]> PRICE_MAP = Collections.emptyMap();

    public static void reload(MinecraftServer server) {
        LinkedHashMap<Item, int[]> newMap = new LinkedHashMap<>();
        ResourceManager rm = server.getResourceManager();
        var resources = rm.listResources("config", path -> path.getPath().endsWith("trade_prices.json"));
        for (var entry : resources.entrySet()) {
            if (!entry.getKey().getNamespace().equals(Ouat.MOD_ID)) continue;
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json.has("prices")) {
                    for (var el : json.getAsJsonArray("prices")) {
                        JsonObject obj = el.getAsJsonObject();
                        String itemId = obj.get("item").getAsString();
                        int buy      = obj.get("buy").getAsInt();
                        int sell     = obj.get("sell").getAsInt();
                        int quantity = obj.has("quantity") ? Math.max(1, obj.get("quantity").getAsInt()) : 1;
                        Item item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemId)).orElse(null);
                        if (item == null) continue;
                        newMap.put(item, new int[]{ buy, sell, quantity });
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[OUAT] Failed to load trade_prices.json: {}", e.getMessage());
            }
            break;
        }
        PRICE_MAP = Collections.unmodifiableMap(newMap);
    }

    public static int getBuyPrice(Item item) {
        int[] p = PRICE_MAP.get(item);
        return p != null ? p[0] : 0;
    }

    public static int getSellPrice(Item item) {
        int[] p = PRICE_MAP.get(item);
        return p != null ? p[1] : 0;
    }

    public static int getQuantity(Item item) {
        int[] p = PRICE_MAP.get(item);
        return p != null ? p[2] : 1;
    }

    public static boolean isTradeable(Item item) {
        return PRICE_MAP.containsKey(item);
    }

    // Serializes the price map for client sync (embedded in hub packet).
    public static CompoundTag buildPricesTag() {
        CompoundTag tag = new CompoundTag();
        PRICE_MAP.forEach((item, prices) -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            CompoundTag entry = new CompoundTag();
            entry.putInt("buy", prices[0]);
            entry.putInt("sell", prices[1]);
            entry.putInt("quantity", prices[2]);
            tag.put(id, entry);
        });
        return tag;
    }
}
