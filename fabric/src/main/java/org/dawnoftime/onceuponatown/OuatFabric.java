package org.dawnoftime.onceuponatown;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import org.dawnoftime.onceuponatown.command.TownCommand;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingListDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDataHandler;
import org.dawnoftime.onceuponatown.datapack.FoodListDataHandler;
import org.dawnoftime.onceuponatown.datapack.QuestDataHandler;
import org.dawnoftime.onceuponatown.datapack.TradePriceDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.C2SAdvanceEraPacket;
import org.dawnoftime.onceuponatown.network.C2SBuyPacket;
import org.dawnoftime.onceuponatown.network.C2SDepositPacket;
import org.dawnoftime.onceuponatown.network.C2SContributeQuestPacket;
import org.dawnoftime.onceuponatown.network.C2SQueueBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRemoveQueuedBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRequestStockPacket;
import org.dawnoftime.onceuponatown.network.C2SToggleChatBroadcastPacket;
import org.dawnoftime.onceuponatown.network.C2SUpgradeBuildingPacket;
import org.dawnoftime.onceuponatown.network.S2CBuildingDefsPacket;
import org.dawnoftime.onceuponatown.network.S2CBuildingListPacket;
import org.dawnoftime.onceuponatown.network.S2CCitizenUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CLogEntryPacket;
import org.dawnoftime.onceuponatown.network.S2CEraUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CQuestUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CStockUpdatePacket;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CTownHubPacket;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;
import org.dawnoftime.onceuponatown.tick.TickScheduler;

public class OuatFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Ouat.init();
        MenuRegistry.TOWN_HUB = ScreenHandlerRegistry.registerSimple(
            Ouat.modResource("town_hub"),
            (syncId, inv) -> new TownHubMenu(syncId, inv)
        );
        ItemRegistry.TOWN_ANCHOR = Registry.register(BuiltInRegistries.ITEM,
            Ouat.modResource("town_anchor"),
            new BlockItem(BlockRegistry.TOWN_ANCHOR, new net.minecraft.world.item.Item.Properties()));
        FabricDefaultAttributeRegistry.register(EntityRegistry.NPC, Npc.createAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, context, env) ->
            TownCommand.register(dispatcher, context));
        ServerLifecycleEvents.SERVER_STARTING.register(BuilderConfigDataHandler::reload);
        ServerLifecycleEvents.SERVER_STARTING.register(BuildingDataHandler::reload);
        ServerLifecycleEvents.SERVER_STARTING.register(BuildingListDataHandler::reload);
        ServerLifecycleEvents.SERVER_STARTING.register(EraTransitionDataHandler::reload);
        ServerLifecycleEvents.SERVER_STARTING.register(FoodListDataHandler::reload);
        ServerLifecycleEvents.SERVER_STARTING.register(QuestDataHandler::reload);
        ServerLifecycleEvents.SERVER_STARTING.register(TradePriceDataHandler::reload);
        ServerTickEvents.END_SERVER_TICK.register(TickScheduler::tick);
        NetworkHelper.sendTownHubPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CTownHubPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CTownHubPacket.ID, buf);
        };
        ServerPlayNetworking.registerGlobalReceiver(C2SQueueBuildingPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SQueueBuildingPacket packet = C2SQueueBuildingPacket.decode(buf);
                server.execute(() -> C2SQueueBuildingPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SRemoveQueuedBuildingPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SRemoveQueuedBuildingPacket packet = C2SRemoveQueuedBuildingPacket.decode(buf);
                server.execute(() -> C2SRemoveQueuedBuildingPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SUpgradeBuildingPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SUpgradeBuildingPacket packet = C2SUpgradeBuildingPacket.decode(buf);
                server.execute(() -> C2SUpgradeBuildingPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SAdvanceEraPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SAdvanceEraPacket packet = C2SAdvanceEraPacket.decode(buf);
                server.execute(() -> C2SAdvanceEraPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SDepositPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SDepositPacket packet = C2SDepositPacket.decode(buf);
                server.execute(() -> C2SDepositPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SContributeQuestPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SContributeQuestPacket packet = C2SContributeQuestPacket.decode(buf);
                server.execute(() -> C2SContributeQuestPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SRequestStockPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SRequestStockPacket packet = C2SRequestStockPacket.decode(buf);
                server.execute(() -> C2SRequestStockPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SBuyPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SBuyPacket packet = C2SBuyPacket.decode(buf);
                server.execute(() -> C2SBuyPacket.Handler.handle(packet, player));
            });
        ServerPlayNetworking.registerGlobalReceiver(C2SToggleChatBroadcastPacket.ID,
            (server, player, handler, buf, responseSender) -> {
                C2SToggleChatBroadcastPacket packet = C2SToggleChatBroadcastPacket.decode(buf);
                server.execute(() -> C2SToggleChatBroadcastPacket.Handler.handle(packet, player));
            });
        NetworkHelper.sendBuildingDefsPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CBuildingDefsPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CBuildingDefsPacket.ID, buf);
        };
        NetworkHelper.sendStockUpdatePacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CStockUpdatePacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CStockUpdatePacket.ID, buf);
        };
        NetworkHelper.sendBuildingListPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CBuildingListPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CBuildingListPacket.ID, buf);
        };
        NetworkHelper.sendQuestUpdatePacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CQuestUpdatePacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CQuestUpdatePacket.ID, buf);
        };
        NetworkHelper.sendEraUpdatePacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CEraUpdatePacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CEraUpdatePacket.ID, buf);
        };
        NetworkHelper.sendCitizenUpdatePacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CCitizenUpdatePacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CCitizenUpdatePacket.ID, buf);
        };
        NetworkHelper.sendLogEntryPacket = (player, data) -> {
            var buf = PacketByteBufs.create();
            new S2CLogEntryPacket(data).encode(buf);
            ServerPlayNetworking.send(player, S2CLogEntryPacket.ID, buf);
        };
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var buf = PacketByteBufs.create();
            new S2CBuildingDefsPacket(BuildingDataHandler.buildDefsPacketData()).encode(buf);
            ServerPlayNetworking.send(handler.getPlayer(), S2CBuildingDefsPacket.ID, buf);
        });
    }
}
