package org.dawnoftime.onceuponatown;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ClientBuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ClientItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.gui.tooltip.ItemAndTitleTooltip;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.client.screen.TownHubScreen;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.C2SAdvanceEraPacket;
import org.dawnoftime.onceuponatown.network.C2SDepositPacket;
import org.dawnoftime.onceuponatown.network.C2SQueueBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SRemoveQueuedBuildingPacket;
import org.dawnoftime.onceuponatown.network.C2SUpgradeBuildingPacket;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class OuatForgeClient {

    // EntityRenderersEvent fires during the initial resource reload, before FMLCommonSetupEvent sets
    // the common static fields -- use ForgeRegistries directly (populated by DeferredRegister)
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityType<Npc> npc = (EntityType<Npc>) ForgeRegistries.ENTITY_TYPES
            .getValue(new ResourceLocation(Constants.MOD_ID, "npc"));
        event.registerEntityRenderer(npc, NpcRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
    }

    // FMLClientSetupEvent fires after FMLCommonSetupEvent, so MenuRegistry.TOWN_HUB is set
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {

            Block townAnchor = ForgeRegistries.BLOCKS
                .getValue(new ResourceLocation(Constants.MOD_ID, "town_anchor"));
            ItemBlockRenderTypes.setRenderLayer(townAnchor, RenderType.cutout());
            MenuScreens.register(MenuRegistry.TOWN_HUB, TownHubScreen::new);
            NetworkHelper.sendQueueBuildingPacket = (pos, defId) ->
                OuatForge.CHANNEL.sendToServer(new C2SQueueBuildingPacket(pos, defId));
            NetworkHelper.sendRemoveQueuedBuildingPacket = (pos, index) ->
                OuatForge.CHANNEL.sendToServer(new C2SRemoveQueuedBuildingPacket(pos, index));
            NetworkHelper.sendUpgradeBuildingPacket = (pos, worldPosLong) ->
                OuatForge.CHANNEL.sendToServer(new C2SUpgradeBuildingPacket(pos, worldPosLong));
            NetworkHelper.sendAdvanceEraPacket = (pos, pathId) ->
                OuatForge.CHANNEL.sendToServer(new C2SAdvanceEraPacket(pos, pathId));
            NetworkHelper.sendDepositPacket = pos ->
                OuatForge.CHANNEL.sendToServer(new C2SDepositPacket(pos));
            OuatForge.wireBuyPacket();
            OuatForge.wireContributeQuestPacket();
            OuatForge.wireToggleChatBroadcastPacket();
        });
    }

    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ItemAndTitleTooltip.class, ClientItemAndTitleTooltip::new);
        event.register(BuildingProductionTooltip.class, ClientBuildingProductionTooltip::new);
    }
}
