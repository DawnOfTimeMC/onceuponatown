package org.dawnoftime.onceuponatown;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.dawnoftime.onceuponatown.block.TownAnchorBlock;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
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
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CBuildingDefsPacket;
import org.dawnoftime.onceuponatown.network.S2CBuildingListPacket;
import org.dawnoftime.onceuponatown.network.S2CCitizenUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CLogEntryPacket;
import org.dawnoftime.onceuponatown.network.S2CEraUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CQuestUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CStockUpdatePacket;
import org.dawnoftime.onceuponatown.network.S2CTownHubPacket;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;
import org.dawnoftime.onceuponatown.tick.TickScheduler;

import java.util.Optional;
import java.util.function.BiFunction;

@Mod(Constants.MOD_ID)
public class OuatForge {

    private static final String NET_PROTOCOL = "1";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Constants.MOD_ID, "main"),
        () -> NET_PROTOCOL,
        NET_PROTOCOL::equals,
        NET_PROTOCOL::equals
    );

    // DeferredRegister is the only valid registration path in Forge - direct Registry.register() is blocked
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, Constants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Constants.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, Constants.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID);

    private static final RegistryObject<Block> TOWN_ANCHOR_OBJ =
        BLOCKS.register("town_anchor",
            () -> new TownAnchorBlock(TownAnchorBlock.defaultProperties()));

    // Block registry fires before block entity registry, so TOWN_ANCHOR_OBJ.get() is safe here
    private static final RegistryObject<BlockEntityType<?>> TOWN_ANCHOR_BE_OBJ =
        BLOCK_ENTITY_TYPES.register("town_anchor", () -> {
            BiFunction<BlockPos, BlockState, TownAnchorBlockEntity> factory = TownAnchorBlockEntity::new;
            return BlockEntityType.Builder.of(factory::apply, TOWN_ANCHOR_OBJ.get()).build(null);
        });

    private static final RegistryObject<EntityType<?>> NPC_OBJ =
        ENTITY_TYPES.register("npc", () ->
            EntityType.Builder.<Npc>of(Npc::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build(new ResourceLocation(Constants.MOD_ID, "npc").toString())
        );

    private static final RegistryObject<MenuType<?>> TOWN_HUB_OBJ =
        MENU_TYPES.register("town_hub",
            () -> IForgeMenuType.create((syncId, inv, buf) -> new TownHubMenu(syncId, inv)));

    private static final RegistryObject<Item> TOWN_ANCHOR_ITEM_OBJ =
        ITEMS.register("town_anchor",
            () -> new BlockItem(TOWN_ANCHOR_OBJ.get(), new Item.Properties()));

    public OuatForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        ITEMS.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onEntityAttributes);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    // Populate the common static fields after all DeferredRegister suppliers have run
    @SuppressWarnings("unchecked")
    private void onCommonSetup(FMLCommonSetupEvent event) {
        BlockRegistry.TOWN_ANCHOR = TOWN_ANCHOR_OBJ.get();
        BlockEntityRegistry.TOWN_ANCHOR = (BlockEntityType<TownAnchorBlockEntity>) TOWN_ANCHOR_BE_OBJ.get();
        EntityRegistry.NPC = (EntityType<Npc>) NPC_OBJ.get();
        MenuRegistry.TOWN_HUB = (MenuType<TownHubMenu>) TOWN_HUB_OBJ.get();
        ItemRegistry.TOWN_ANCHOR = TOWN_ANCHOR_ITEM_OBJ.get();

        CHANNEL.registerMessage(1,
            S2CTownHubPacket.class,
            S2CTownHubPacket::encode,
            S2CTownHubPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CTownHubPacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(2,
            C2SQueueBuildingPacket.class,
            C2SQueueBuildingPacket::encode,
            C2SQueueBuildingPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> C2SQueueBuildingPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(3,
            C2SRemoveQueuedBuildingPacket.class,
            C2SRemoveQueuedBuildingPacket::encode,
            C2SRemoveQueuedBuildingPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SRemoveQueuedBuildingPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(4,
            C2SUpgradeBuildingPacket.class,
            C2SUpgradeBuildingPacket::encode,
            C2SUpgradeBuildingPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SUpgradeBuildingPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(5,
            S2CBuildingDefsPacket.class,
            S2CBuildingDefsPacket::encode,
            S2CBuildingDefsPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CBuildingDefsPacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(6,
            C2SAdvanceEraPacket.class,
            C2SAdvanceEraPacket::encode,
            C2SAdvanceEraPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SAdvanceEraPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(7,
            C2SDepositPacket.class,
            C2SDepositPacket::encode,
            C2SDepositPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SDepositPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(9,
            S2CStockUpdatePacket.class,
            S2CStockUpdatePacket::encode,
            S2CStockUpdatePacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CStockUpdatePacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(10,
            S2CBuildingListPacket.class,
            S2CBuildingListPacket::encode,
            S2CBuildingListPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CBuildingListPacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(11,
            S2CQuestUpdatePacket.class,
            S2CQuestUpdatePacket::encode,
            S2CQuestUpdatePacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CQuestUpdatePacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(12,
            S2CEraUpdatePacket.class,
            S2CEraUpdatePacket::encode,
            S2CEraUpdatePacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CEraUpdatePacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(13,
            S2CCitizenUpdatePacket.class,
            S2CCitizenUpdatePacket::encode,
            S2CCitizenUpdatePacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CCitizenUpdatePacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(14,
            C2SRequestStockPacket.class,
            C2SRequestStockPacket::encode,
            C2SRequestStockPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SRequestStockPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(15,
            C2SBuyPacket.class,
            C2SBuyPacket::encode,
            C2SBuyPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SBuyPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(16,
            C2SContributeQuestPacket.class,
            C2SContributeQuestPacket::encode,
            C2SContributeQuestPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SContributeQuestPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(17,
            S2CLogEntryPacket.class,
            S2CLogEntryPacket::encode,
            S2CLogEntryPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CLogEntryPacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        NetworkHelper.sendTownHubPacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CTownHubPacket(data));

        NetworkHelper.sendBuildingDefsPacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CBuildingDefsPacket(data));

        NetworkHelper.sendStockUpdatePacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CStockUpdatePacket(data));

        NetworkHelper.sendBuildingListPacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CBuildingListPacket(data));

        NetworkHelper.sendQuestUpdatePacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CQuestUpdatePacket(data));

        NetworkHelper.sendEraUpdatePacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CEraUpdatePacket(data));

        NetworkHelper.sendCitizenUpdatePacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CCitizenUpdatePacket(data));

        NetworkHelper.sendLogEntryPacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CLogEntryPacket(data));

        NetworkHelper.sendRequestStockPacket = pos ->
            CHANNEL.sendToServer(new C2SRequestStockPacket(pos));

        CHANNEL.registerMessage(18,
            C2SToggleChatBroadcastPacket.class,
            C2SToggleChatBroadcastPacket::encode,
            C2SToggleChatBroadcastPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() ->
                    C2SToggleChatBroadcastPacket.Handler.handle(msg, ctx.get().getSender()));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    // Called from OuatForgeClient to wire the toggle chat broadcast packet sender
    static void wireToggleChatBroadcastPacket() {
        NetworkHelper.sendToggleChatBroadcastPacket = pos ->
            CHANNEL.sendToServer(new C2SToggleChatBroadcastPacket(pos));
    }

    // Called from OuatForgeClient to wire the buy packet sender
    static void wireBuyPacket() {
        NetworkHelper.sendBuyPacket = (pos, items) ->
            CHANNEL.sendToServer(new C2SBuyPacket(pos, items));
    }

    // Called from OuatForgeClient to wire the contribute quest packet sender
    static void wireContributeQuestPacket() {
        NetworkHelper.sendContributeQuestPacket = (pos, questId) ->
            CHANNEL.sendToServer(new C2SContributeQuestPacket(pos, questId));
    }

    @SuppressWarnings("unchecked")
    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put((EntityType<Npc>) NPC_OBJ.get(), Npc.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TownCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStarting(ServerStartingEvent event) {
        BuilderConfigDataHandler.reload(event.getServer());
        BuildingDataHandler.reload(event.getServer());
        BuildingListDataHandler.reload(event.getServer());
        EraTransitionDataHandler.reload(event.getServer());
        FoodListDataHandler.reload(event.getServer());
        QuestDataHandler.reload(event.getServer());
        TradePriceDataHandler.reload(event.getServer());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TickScheduler.tick(ServerLifecycleHooks.getCurrentServer());
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            NetworkHelper.sendBuildingDefsPacket.accept(player, BuildingDataHandler.buildDefsPacketData());
        }
    }
}
