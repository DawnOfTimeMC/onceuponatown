package org.dawnoftime.onceuponatown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ClientBuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ClientItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.client.screen.TownHubScreen;
import org.dawnoftime.onceuponatown.network.C2SAdvanceEraPacket;
import org.dawnoftime.onceuponatown.network.C2SBuyPacket;
import org.dawnoftime.onceuponatown.network.C2SDepositPacket;
import org.dawnoftime.onceuponatown.network.C2SContributeQuestPacket;
import org.dawnoftime.onceuponatown.network.C2SQueueBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRemoveQueuedBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRequestStockPacket;
import org.dawnoftime.onceuponatown.network.C2SToggleChatBroadcastPacket;
import org.dawnoftime.onceuponatown.network.C2SUpgradeBuildingPacket;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CBuildingDefsPacket;
import org.dawnoftime.onceuponatown.network.S2CBuildingListPacket;
import org.dawnoftime.onceuponatown.network.S2CCitizenUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CLogEntryPacket;
import org.dawnoftime.onceuponatown.network.S2CEraUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CQuestUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CStockUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CTownHubPacket;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

public class OuatFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(EntityRegistry.NPC, NpcRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
        BlockRenderLayerMap.INSTANCE.putBlock(BlockRegistry.TOWN_ANCHOR, RenderType.cutout());
        MenuScreens.register(MenuRegistry.TOWN_HUB, TownHubScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(S2CTownHubPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CTownHubPacket packet = S2CTownHubPacket.decode(buf);
                S2CTownHubPacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CBuildingDefsPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CBuildingDefsPacket packet = S2CBuildingDefsPacket.decode(buf);
                S2CBuildingDefsPacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CStockUpdatePacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CStockUpdatePacket packet = S2CStockUpdatePacket.decode(buf);
                S2CStockUpdatePacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CBuildingListPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CBuildingListPacket packet = S2CBuildingListPacket.decode(buf);
                S2CBuildingListPacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CQuestUpdatePacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CQuestUpdatePacket packet = S2CQuestUpdatePacket.decode(buf);
                S2CQuestUpdatePacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CEraUpdatePacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CEraUpdatePacket packet = S2CEraUpdatePacket.decode(buf);
                S2CEraUpdatePacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CCitizenUpdatePacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CCitizenUpdatePacket packet = S2CCitizenUpdatePacket.decode(buf);
                S2CCitizenUpdatePacket.Handler.handle(packet);
            });
        ClientPlayNetworking.registerGlobalReceiver(S2CLogEntryPacket.ID,
            (client, handler, buf, responseSender) -> {
                S2CLogEntryPacket packet = S2CLogEntryPacket.decode(buf);
                S2CLogEntryPacket.Handler.handle(packet);
            });
        TooltipComponentCallback.EVENT.register(component -> {
            if (component instanceof ItemAndTitleTooltip t) return new ClientItemAndTitleTooltip(t);
            if (component instanceof BuildingProductionTooltip t) return new ClientBuildingProductionTooltip(t);
            return null;
        });
        NetworkHelper.sendQueueBuildingPacket = (pos, defId) -> {
            var buf = PacketByteBufs.create();
            new C2SQueueBuildingPacket(pos, defId).encode(buf);
            ClientPlayNetworking.send(C2SQueueBuildingPacket.ID, buf);
        };
        NetworkHelper.sendRemoveQueuedBuildingPacket = (pos, index) -> {
            var buf = PacketByteBufs.create();
            new C2SRemoveQueuedBuildingPacket(pos, index).encode(buf);
            ClientPlayNetworking.send(C2SRemoveQueuedBuildingPacket.ID, buf);
        };
        NetworkHelper.sendUpgradeBuildingPacket = (pos, worldPosLong) -> {
            var buf = PacketByteBufs.create();
            new C2SUpgradeBuildingPacket(pos, worldPosLong).encode(buf);
            ClientPlayNetworking.send(C2SUpgradeBuildingPacket.ID, buf);
        };
        NetworkHelper.sendAdvanceEraPacket = (pos, pathId) -> {
            var buf = PacketByteBufs.create();
            new C2SAdvanceEraPacket(pos, pathId).encode(buf);
            ClientPlayNetworking.send(C2SAdvanceEraPacket.ID, buf);
        };
        NetworkHelper.sendDepositPacket = pos -> {
            var buf = PacketByteBufs.create();
            new C2SDepositPacket(pos).encode(buf);
            ClientPlayNetworking.send(C2SDepositPacket.ID, buf);
        };
        NetworkHelper.sendRequestStockPacket = pos -> {
            var buf = PacketByteBufs.create();
            new C2SRequestStockPacket(pos).encode(buf);
            ClientPlayNetworking.send(C2SRequestStockPacket.ID, buf);
        };
        NetworkHelper.sendContributeQuestPacket = (pos, questId) -> {
            var buf = PacketByteBufs.create();
            new C2SContributeQuestPacket(pos, questId).encode(buf);
            ClientPlayNetworking.send(C2SContributeQuestPacket.ID, buf);
        };
        NetworkHelper.sendBuyPacket = (pos, items) -> {
            var buf = PacketByteBufs.create();
            new C2SBuyPacket(pos, items).encode(buf);
            ClientPlayNetworking.send(C2SBuyPacket.ID, buf);
        };
        NetworkHelper.sendToggleChatBroadcastPacket = pos -> {
            var buf = PacketByteBufs.create();
            new C2SToggleChatBroadcastPacket(pos).encode(buf);
            ClientPlayNetworking.send(C2SToggleChatBroadcastPacket.ID, buf);
        };
    }
}
