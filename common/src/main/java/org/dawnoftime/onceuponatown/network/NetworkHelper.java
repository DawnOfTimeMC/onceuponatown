package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownLogEntry;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NetworkHelper {
    // S2C delegates (set by each platform server-side init)
    public static BiConsumer<ServerPlayer, CompoundTag> sendTownHubPacket       = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendBuildingDefsPacket  = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendStockUpdatePacket   = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendBuildingListPacket  = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendQuestUpdatePacket   = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendEraUpdatePacket     = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendCitizenUpdatePacket = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendLogEntryPacket     = (player, data) -> {};

    // C2S delegates (set by each platform client-side init)
    public static Consumer<BlockPos>            sendToggleChatBroadcastPacket  = pos              -> {};
    public static BiConsumer<BlockPos, String>  sendQueueBuildingPacket        = (pos, defId)     -> {};
    public static BiConsumer<BlockPos, Integer> sendRemoveQueuedBuildingPacket = (pos, index)     -> {};
    public static BiConsumer<BlockPos, Long>    sendUpgradeBuildingPacket      = (pos, worldPos)  -> {};
    public static BiConsumer<BlockPos, String>  sendAdvanceEraPacket           = (pos, pathId)    -> {};
    public static Consumer<BlockPos>            sendDepositPacket              = pos              -> {};
    public static BiConsumer<BlockPos, String>  sendContributeQuestPacket      = (pos, questId)   -> {};
    public static Consumer<BlockPos>            sendRequestStockPacket         = pos              -> {};
    // Carries requested items for BUY mode: List<(itemId, count)> encoded via C2SBuyPacket
    public static BiConsumer<BlockPos, List<C2SBuyPacket.Entry>> sendBuyPacket = (pos, items) -> {};

    // Sends a fresh full hub packet to every player watching this town's hub.
    // Each player gets a personalized copy with their ChatSubscribed flag.
    public static void pushHubToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag hubData = town.getHubData(anchorPos);
        for (ServerPlayer watcher : watchers) {
            CompoundTag perPlayer = hubData.copy();
            perPlayer.putBoolean("ChatSubscribed", town.isChatSubscriber(watcher.getUUID()));
            sendTownHubPacket.accept(watcher, perPlayer);
        }
    }

    // Sends a targeted stock update to every watcher.
    public static void pushStockToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getStockUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendStockUpdatePacket.accept(w, data);
    }

    // Sends a targeted building list update (map + queue + upgrades) to every watcher.
    public static void pushBuildingListToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getBuildingListData(anchorPos);
        for (ServerPlayer w : watchers) sendBuildingListPacket.accept(w, data);
    }

    // Sends a quest update to every player watching this town's hub (mirrors all other push methods).
    public static void pushQuestUpdateToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getQuestUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendQuestUpdatePacket.accept(w, data);
    }

    // Sends a targeted era update to every watcher.
    public static void pushEraUpdateToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getEraUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendEraUpdatePacket.accept(w, data);
    }

    // Sends a targeted citizen update to every watcher.
    public static void pushCitizenUpdateToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getCitizenUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendCitizenUpdatePacket.accept(w, data);
    }

    // Sends a log entry to every player watching this town's hub, and sends a
    // chat message to subscribed players who do not currently have the hub open.
    public static void pushLogEntryToWatchers(ServerLevel level, Town town, BlockPos anchorPos, TownLogEntry entry) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (!watchers.isEmpty()) {
            CompoundTag data = new CompoundTag();
            data.putLong("AnchorPos", anchorPos.asLong());
            data.putString("Type", entry.type().name());
            data.putString("Param", entry.param());
            data.putLong("Tick", entry.gameTick());
            for (ServerPlayer w : watchers) sendLogEntryPacket.accept(w, data);
        }

        Set<UUID> subscribers = town.getChatSubscribers();
        if (!subscribers.isEmpty()) {
            Set<UUID> watcherIds = watchers.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
            Component chatMsg = formatLogEntryForChat(entry);
            for (ServerPlayer player : level.players()) {
                if (subscribers.contains(player.getUUID()) && !watcherIds.contains(player.getUUID())) {
                    player.sendSystemMessage(chatMsg);
                }
            }
        }
    }

    private static Component formatLogEntryForChat(TownLogEntry entry) {
        MutableComponent prefix = Component.literal("[Village] ").withStyle(s -> s.withColor(0xFFAA00));
        String param = entry.param();
        MutableComponent body = switch (entry.type()) {
            case BUILD_START   -> Component.literal("Builder: starting ").append(Component.translatable("onceuponatown.building." + param));
            case BUILD_DONE    -> Component.literal("Builder: ").append(Component.translatable("onceuponatown.building." + param)).append(" built");
            case UPGRADE_START -> Component.literal("Builder: upgrading ").append(Component.translatable("onceuponatown.building." + param));
            case UPGRADE_DONE  -> Component.literal("Builder: ").append(Component.translatable("onceuponatown.building." + param)).append(" upgraded");
            case FOOD_CONSUMED -> Component.literal("Village consumed " + param + " food units");
            case VILLAGE_FULL  -> Component.literal("No space left to expand");
        };
        int color = switch (entry.type()) {
            case BUILD_START, UPGRADE_START -> 0xAAAAFF;
            case BUILD_DONE, UPGRADE_DONE   -> 0x55FF55;
            case FOOD_CONSUMED              -> 0xDDDDDD;
            case VILLAGE_FULL               -> 0xFF5555;
        };
        return prefix.append(body.withStyle(s -> s.withColor(color)));
    }

    private static List<ServerPlayer> getWatchers(ServerLevel level, BlockPos anchorPos) {
        return level.players().stream()
            .filter(p -> p.containerMenu instanceof TownHubMenu m && anchorPos.equals(m.getAnchorPos()))
            .toList();
    }
}
