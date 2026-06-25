package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.ActiveBuildState;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.datapack.QuestDataHandler;
import org.dawnoftime.onceuponatown.town.Quest;
import org.dawnoftime.onceuponatown.town.QuestDef;
import org.dawnoftime.onceuponatown.town.QuestManager;
import org.dawnoftime.onceuponatown.town.TownInventory;
import org.dawnoftime.onceuponatown.town.Town;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TickScheduler {

    // Called from OuatForge via TickEvent.ServerTickEvent (Phase.END)
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelTowns levelTowns = LevelTowns.get(level);
            long gameTime = level.getGameTime();

            for (Map.Entry<Long, Town> townEntry : levelTowns.getAllTownEntries()) {
                Town town = townEntry.getValue();
                long anchorKey = townEntry.getKey();

                ProductionManager.tick(town, level, gameTime, anchorKey);
                FoodManager.tick(town, level, gameTime, anchorKey);
                tickQuests(town, level, gameTime, anchorKey);
                EraManager.tick(town, level, gameTime, anchorKey);
            }

            // Spawn builders for each slot up to targetBuilderCount, once a player is nearby.
            if (gameTime % 20 == 0) {
                for (Map.Entry<Long, Town> entry : levelTowns.getAllTownEntries()) {
                    Town town = entry.getValue();
                    BlockPos anchorPos = BlockPos.of(entry.getKey());

                    boolean playerNearby = level.players().stream().anyMatch(p ->
                        p.distanceToSqr(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5) < 128.0 * 128.0);
                    if (!playerNearby) continue;

                    boolean dirty = false;
                    List<UUID> ids = town.getBuilderNpcIds();
                    for (int slot = 0; slot < town.getTargetBuilderCount(); slot++) {
                        UUID slotId = slot < ids.size() ? ids.get(slot) : null;
                        net.minecraft.world.entity.Entity existing = slotId != null ? level.getEntity(slotId) : null;
                        if (existing != null) continue;

                        // NPC not found in loaded entities. Check if the NPC's chunk is simply unloaded
                        // before spawning a replacement -- the builder is immortal so absence = chunk not loaded.
                        ActiveBuildState buildState = town.getActiveBuild(slot);
                        BlockPos checkPos = buildState != null ? buildState.placementPos() : anchorPos;
                        if (!areChunksLoaded(level, checkPos)) continue;

                        // All 9 chunks around the expected position are loaded but NPC is still missing:
                        // coherence issue (e.g. entity deleted externally). Spawn a replacement.
                        // Release any queue claims the dead builder held so the new one can resume.
                        if (slotId != null) town.releaseAllClaimsForBuilder(slotId);
                        Npc builder = EntityRegistry.NPC.create(level);
                        if (builder == null) continue;

                        builder.setPersistenceRequired();
                        builder.setTownAnchorPos(anchorPos);

                        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            anchorPos.getX(), anchorPos.getZ());
                        builder.moveTo(anchorPos.getX() + 0.5, surfaceY + 1.0, anchorPos.getZ() + 0.5);

                        if (level.addFreshEntity(builder)) {
                            town.setBuilderNpcIdAtSlot(slot, builder.getUUID());
                            dirty = true;
                        }
                    }
                    if (dirty) levelTowns.markDirty();
                }
            }
        }
    }

    // Returns true if all 9 chunks in the 3x3 grid around the given position are loaded.
    // Used to distinguish "NPC in unloaded chunk" from "NPC genuinely missing".
    private static boolean areChunksLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.isLoaded(new BlockPos(
                        (center.x + dx) * 16, pos.getY(), (center.z + dz) * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void tickQuests(Town town, ServerLevel level, long gameTime, long anchorKey) {
        BlockPos anchorPos = BlockPos.of(anchorKey);
        boolean changed = false;
        TownInventory inventory = town.getTownInventory();
        Map<String, Long> lastCompleted = town.getQuestDefLastCompleted();

        for (QuestDef def : QuestDataHandler.getAll()) {
            if (QuestManager.isAlreadyActive(def, town.getActiveQuests())) continue;

            if ("TASK".equals(def.type())) {
                long lastTime = lastCompleted.getOrDefault(def.id(), 0L);
                if (gameTime - lastTime < def.refreshIntervalTicks()) continue;
            }

            if (!prerequisitesMet(def.prerequisites(), town, inventory)) continue;

            Quest q = QuestManager.buildFromDef(def);
            town.addQuest(q);
            changed = true;
        }

        if (changed) {
            LevelTowns.get(level).markDirty();
            NetworkHelper.pushQuestUpdateToWatchers(level, town, anchorPos);
        }
    }

    private static boolean prerequisitesMet(QuestDef.Prerequisites prereqs, Town town, TownInventory inventory) {
        if (prereqs == null) return true;
        int era = town.getCurrentEra();
        if (era < prereqs.minEra() || era > prereqs.maxEra()) return false;
        int residents = town.getActiveResidents();
        if (residents < prereqs.minResidents() || residents > prereqs.maxResidents()) return false;
        if (!prereqs.requiredOrientations().isEmpty()) {
            String orientation = town.getOrDeriveOrientation();
            if (prereqs.requiredOrientations().stream().noneMatch(o -> o.equals(orientation))) return false;
        }
        for (String defId : prereqs.requiredBuildings()) {
            boolean found = town.getBuildings().stream().anyMatch(b -> b.getDefId().equals(defId));
            if (!found) return false;
        }
        for (QuestDef.StockCondition sc : prereqs.stockConditions()) {
            var item = BuiltInRegistries.ITEM.get(new ResourceLocation(sc.item()));
            int stock = inventory.getStock(item);
            if (stock < sc.min() || stock > sc.max()) return false;
        }
        return true;
    }
}
