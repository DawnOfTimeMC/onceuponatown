package com.dotteam.onceuponatown.culture;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.util.OuatLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CultureManager implements PreparableReloadListener {
    private static CultureManager instance;
    private Map<String, Culture> cultures = new HashMap<>();

    public static CultureManager instance() {
        return instance == null ? new CultureManager() : instance;
    }

    public Culture getCulture(String name) {
        return this.cultures.get(name);
    }

    private static void loadCultures(List<ResourceLocation> cultureJsonList, ResourceManager resourceManager) {
        for (ResourceLocation cultureJson : cultureJsonList) {
            loadCulture(cultureJson, resourceManager);
        }
    }

    private static void loadCulture(ResourceLocation cultureJson, ResourceManager resourceManager) {
        OuatLog.info("Loading culture " + cultureJson.getPath());
        var optional = resourceManager.getResource(cultureJson);
        if (optional.isPresent()) {
            OuatLog.info("optional present");
            Resource cultureResource = optional.get();
            try {
                Reader reader = cultureResource.openAsReader();
                var rootJsonObject = GsonHelper.parse(reader);
                String cultureName = rootJsonObject.get("name").getAsString();
                OuatLog.info("Detected culture name : " + cultureName);
                if ((cultureName == null)) {
                    throw new RuntimeException("Invalid culture : unnamed culture");
                }
                if (!cultureJson.getPath().equals("cultures/" + cultureName + "/culture.json")) {
                    throw new RuntimeException("Invalid culture : wrong culture directory name");
                }
                Culture culture = new Culture(cultureName);
                /// BUILDINGS ///
                List<ResourceLocation> detectedJsons = new ArrayList<>(resourceManager.listResources("cultures/" + cultureName + "/buildings", (fileName) ->
                        fileName.getNamespace().equals(OuatConstants.MOD_ID) && fileName.getPath().endsWith(".json")).keySet());
                OuatLog.info("nb of buildings : " + detectedJsons.size());

                for (ResourceLocation potentialBuildingJson : detectedJsons) {
                    OuatLog.info("Detected building : " + potentialBuildingJson.getPath());
                    List<ResourceLocation> buildingNbt = new ArrayList<>(resourceManager.listResources("cultures/" + cultureName + "/buildings", (fileName) ->
                            fileName.getNamespace().equals(OuatConstants.MOD_ID) && fileName.getPath().endsWith(".nbt")).keySet());
                }


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
            List<ResourceLocation> detectedCultures = new ArrayList<>(resourceManager.listResources("cultures", (fileName) ->
                    fileName.getNamespace().equals(OuatConstants.MOD_ID) && fileName.getPath().endsWith("/culture.json")).keySet());

            OuatLog.info("Reloading cultures");
            for (ResourceLocation cultureJson : detectedCultures) {
                OuatLog.info("Culture detected : " + cultureJson.getPath());
            }

            loadCultures(detectedCultures, resourceManager);

        }, backgroundExecutor)).thenCompose(stage::wait);
    }
}
