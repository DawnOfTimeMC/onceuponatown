package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientBuildingDefsRegistry;
import org.dawnoftime.onceuponatown.client.TownHubClientState;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;
import org.dawnoftime.onceuponatown.client.gui.widgets.DraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.EraProgressDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.MapDraggableWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.NbtPreviewWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.QuestHubWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.TownSummaryWidget;
import org.dawnoftime.onceuponatown.network.C2SBuyPacket;
import org.dawnoftime.onceuponatown.town.TownLogEntry;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TownHubScreen extends AbstractContainerScreen<TownHubMenu> {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/town_hub.png");
    private static final ResourceLocation TEXTURE_CONSTRUCTION =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/town_construction.png");
    private static final ResourceLocation TEXTURE_UPGRADE =
        new ResourceLocation(Ouat.MOD_ID, "textures/gui/town_upgrade.png");

    // Category colors matching the map widget palette
    private static final int COLOR_TOWN_CENTER = 0xFFFFAA00;
    private static final int COLOR_JOBS        = 0xFFCC3333;
    private static final int COLOR_GARDENS     = 0xFF33AA33;
    private static final int COLOR_NATURALS     = 0xFF666666;
    private static final int COLOR_BUILDINGS   = 0xFF885533;
    private static final int COLOR_UNKNOWN     = 0xFF888888;

    private static final int COLOR_SLOT_HOVER  = 0x40FFFFFF;
    private static final int COLOR_SLOT_EMPTY  = 0x20FFFFFF;
    private static final int COLOR_TAB_ACTIVE  = 0xFFD0C080;
    private static final int COLOR_TAB_INACTIVE = 0xFF807850;
    private static final int COLOR_TAB_TEXT    = 0xFF1A1A1A;

    // Grid layout constants (relative to panel top-left)
    private static final int QUEUE_GRID_X      = 8;
    private static final int QUEUE_GRID_Y      = 8;
    private static final int QUEUE_ROWS        = 6;
    private static final int QUEUE_COLS        = 9;
    private static final int AVAIL_GRID_Y_ROW0 = 140;
    private static final int AVAIL_ROWS        = 3;   // upgrade building grid rows
    private static final int CATALOG_ROWS      = 3;   // construction tab: player inv rows only (no hotbar)
    private static final int AVAIL_COLS        = 9;
    private static final int CELL              = 18;

    // Session-persistent widget layout (reset on Minecraft restart)
    private static int savedMapX = -1, savedMapY = -1;
    private static int savedSummaryX = -1, savedSummaryY = -1;
    private static int savedEraX = -1, savedEraY = -1;
    private static boolean savedMapOpen = true;
    private static boolean savedSummaryOpen = true;
    private static boolean savedEraOpen = false;
    private static int     savedQuestHubX    = -1;
    private static int     savedQuestHubY    = -1;
    private static boolean savedQuestHubOpen = true;
    private static int savedActiveTab = 0;
    private static final List<String> savedWidgetOrder = new ArrayList<>();

    private static final float L1_Z_BASE =    0f;
    private static final float L1_Z_STEP =  500f;
    private static final int   L1_MAX    =   10;
    private static final float L2_Z_BASE = L1_Z_BASE + L1_MAX * L1_Z_STEP; // 5000
    private static final float L3_Z_BASE = L2_Z_BASE + L1_Z_STEP;          // 5500
    private static final float L3_Z_STEP =  500f;

    private final List<DraggableWidget> layer1Widgets = new ArrayList<>();
    private boolean mapWidgetCreated = false;
    private int mapInitialHeight = 0;

    private CompoundTag cachedHubData;
    private boolean mapClosed = false;
    private boolean summaryClosed = false;
    private boolean eraClosed = true;
    private boolean questHubClosed = false;
    private EraProgressDraggableWidget eraWidget = null;
    private QuestHubWidget questHubWidget = null;

    // Era state parsed from hub data
    private int currentEra = 0;
    private int totalResidents = 0;
    private int activeResidents = 0;
    private int totalFoodDemand = 0;
    private int totalHerd = 0;
    private int activeHerd = 0;
    private int currentWeight = 0;
    private int maxWeight = 20;
    private List<EraProgressDraggableWidget.EraPathOption> eraTransitions = new ArrayList<>();
    private int lastKnownEra = -1;

    // Construction tab state (parsed from hub data)
    private int activeTab = 0; // 0 = Stock, 1 = Construction, 2 = Upgrade
    private BlockPos anchorPos = BlockPos.ZERO;
    private final List<BuildingEntry> buildingCatalog = new ArrayList<>();
    private final List<ClientQueueEntry> constructionQueueClient = new ArrayList<>();
    private final Map<String, Integer> stockSnapshot = new HashMap<>();
    private final List<String> boostedBuildingIds = new ArrayList<>();
    private int catalogScrollOffset = 0;

    // Trade tab state (stock tab, tab 0)
    // false = SELL (player sends items to village), true = BUY (player buys from village)
    private boolean buyMode = false;
    // itemId -> {buy price, sell price} received from hub packet
    private final Map<String, int[]> tradePrices = new HashMap<>();
    // Client-side buy request: item -> requested count (BUY mode only, not backed by deposit container)
    private final LinkedHashMap<Item, Integer> buyRequest = new LinkedHashMap<>();

    // Slot index of the queue entry the cursor is hovering over (-1 = none)
    private int hoveredQueueSlot = -1;
    private int hoveredCatalogSlot = -1;
    private String selectedCatalogBuildingId = null;
    private NbtPreviewWidget constructionPreview = null;

    private final List<UpgradeBuildingEntry> upgradeBuildingsList = new ArrayList<>();
    private long selectedUpgradeBuildingPos = -1L;
    private int upgradeGridScrollOffset = 0;
    private int hoveredUpgradeSlot = -1;

    // Expanded NBT view overlay state
    private boolean expandedViewOpen = false;
    private NbtPreviewWidget expandedWidget = null;
    private int expandedViewLevel = 0;
    private int expandedPanelX = 0, expandedPanelY = 0, expandedPanelSize = 0;

    // Upgrade tab bar hover state (populated each frame in renderUpgradeTab, read in renderUpgradeTooltips)
    private final int[] barLabelYs = new int[8];
    private final String[] barTooltips = new String[8];
    private int numActiveBars = 0;
    // Unlock icon hover state
    private final List<int[]> unlockIconBounds = new ArrayList<>(); // {x, y}
    private final List<String> unlockIconItemIds = new ArrayList<>();

    private record BuildingEntry(String id, String category, String iconItem,
                                 List<CostEntry> cost,
                                 List<BuildingProductionTooltip.Row> productionRows,
                                 List<ProductionCell> productionCells,
                                 int requiredResidents,
                                 List<ReqBuildingEntry> requiredBuildings,
                                 double productionBonus,
                                 float baseConsumption,
                                 float maxConsumption,
                                 int maxResidents,
                                 boolean nextEra,
                                 String nbtPath,
                                 boolean hasBuilt,
                                 List<String> nbtLevels,
                                 int builtCount,
                                 int weight) {}
    private record CostEntry(String itemId, int amount) {}
    private record ReqBuildingEntry(String defId, int required, int have) {}
    private record ProductionCell(Item item, int amount, boolean locked) {}
    private record UpgradeBuildingEntry(String defId, long worldPosLong, int upgradeLevel,
                                        String category, String iconItem) {}
    private record ClientQueueEntry(String type, String defId, long buildingWorldPos) {
        boolean isUpgrade() { return "upgrade".equals(type); }
    }

    public TownHubScreen(TownHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = this.width - this.imageWidth - 10;
        activeTab = savedActiveTab;
        if (TownHubClientState.pendingHubData != null) {
            CompoundTag hub = TownHubClientState.pendingHubData;
            TownHubClientState.pendingHubData = null;
            parseHubData(hub);
            tryCreateMapWidget(hub);
        }
    }

    private void tryCreateMapWidget(CompoundTag hub) {
        if (mapWidgetCreated) return;
        cachedHubData = hub;
        int freeZoneW = this.leftPos;
        int initialW = Math.min(160, freeZoneW - 20);
        if (initialW > 50) {
            mapInitialHeight = initialW + DraggableWidget.TITLE_BAR_H;
            List<DraggableWidget> newWidgets = new ArrayList<>();
            if (savedMapOpen) {
                int startX = (savedMapX >= 0) ? Math.min(savedMapX, Math.max(0, freeZoneW - initialW)) : centerX(freeZoneW, initialW);
                int startY = (savedMapY >= 0) ? Math.min(savedMapY, Math.max(0, this.height - mapInitialHeight)) : centerY(this.height, mapInitialHeight);
                MapDraggableWidget mapWidget = new MapDraggableWidget(startX, startY, initialW, mapInitialHeight, freeZoneW, this.height, hub.getCompound("MapData"));
                mapWidget.setOnBuildingClicked((pos, defId) -> {
                    activeTab = 1;
                    BuildingEntry catalogEntry = findCatalogEntry(defId);
                    if (catalogEntry != null) {
                        selectedCatalogBuildingId = defId;
                        updateConstructionPreview(catalogEntry);
                    }
                });
                mapWidget.setOnBuildingRightClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                newWidgets.add(mapWidget);
            }
            if (hub.contains("SummaryData") && savedSummaryOpen) {
                int summaryH = DraggableWidget.TITLE_BAR_H + TownSummaryWidget.VISIBLE_H;
                int startX = (savedSummaryX >= 0) ? Math.min(savedSummaryX, Math.max(0, freeZoneW - TownSummaryWidget.WIDGET_W)) : centerX(freeZoneW, TownSummaryWidget.WIDGET_W);
                int startY = (savedSummaryY >= 0) ? Math.min(savedSummaryY, Math.max(0, this.height - summaryH)) : centerY(this.height, summaryH);
                TownSummaryWidget summaryWidget = new TownSummaryWidget(hub.getCompound("MapData"), hub.getCompound("SummaryData"), startX, startY, freeZoneW, this.height);
                if (hub.contains("ActivityLog")) {
                    summaryWidget.loadInitialLog(parseActivityLog(hub));
                }
                summaryWidget.setOnBroadcastToggled(() -> NetworkHelper.sendToggleChatBroadcastPacket.accept(anchorPos));
                newWidgets.add(summaryWidget);
            }
            if (savedEraOpen) {
                int eraW = EraProgressDraggableWidget.computeWidgetW(eraTransitions);
                int startX = (savedEraX >= 0) ? Math.min(savedEraX, Math.max(0, freeZoneW - eraW)) : centerX(freeZoneW, eraW);
                int startY = (savedEraY >= 0) ? savedEraY : centerY(this.height, DraggableWidget.TITLE_BAR_H + 80);
                eraWidget = new EraProgressDraggableWidget(startX, startY, freeZoneW, this.height,
                    currentEra, eraTransitions,
                    pathId -> NetworkHelper.sendAdvanceEraPacket.accept(anchorPos, pathId));
                newWidgets.add(eraWidget);
            }
            if (savedQuestHubOpen) {
                int questH = DraggableWidget.TITLE_BAR_H + QuestHubWidget.VISIBLE_H;
                int startX = (savedQuestHubX >= 0)
                    ? Math.min(savedQuestHubX, Math.max(0, freeZoneW - QuestHubWidget.WIDGET_W))
                    : centerX(freeZoneW, QuestHubWidget.WIDGET_W);
                int startY = (savedQuestHubY >= 0)
                    ? Math.min(savedQuestHubY, Math.max(0, this.height - questH))
                    : centerY(this.height, questH);
                questHubWidget = new QuestHubWidget(startX, startY, freeZoneW, this.height);
                newWidgets.add(questHubWidget);
            }
            if (!savedWidgetOrder.isEmpty()) {
                newWidgets.sort((a, b) -> {
                    int ia = savedWidgetOrder.indexOf(a.getClass().getSimpleName());
                    int ib = savedWidgetOrder.indexOf(b.getClass().getSimpleName());
                    if (ia < 0) ia = Integer.MAX_VALUE;
                    if (ib < 0) ib = Integer.MAX_VALUE;
                    return Integer.compare(ia, ib);
                });
            }
            layer1Widgets.addAll(newWidgets);
            mapWidgetCreated = true;
            if (hub.contains("Quests") && questHubWidget != null) {
                List<CompoundTag> tags = new ArrayList<>();
                hub.getList("Quests", Tag.TAG_COMPOUND).forEach(t -> tags.add((CompoundTag) t));
                questHubWidget.setQuests(tags, anchorPos);
            }
        }
    }

    // Parses incoming hub CompoundTag into local construction tab fields.
    // Safe to call both at init and during render (live refresh from C2S responses).
    private void parseHubData(CompoundTag hub) {
        anchorPos = NbtUtils.readBlockPos(hub.getCompound("AnchorPos"));
        int prevEra = currentEra;
        currentEra      = hub.getInt("CurrentEra");
        totalResidents  = hub.getInt("TotalResidents");
        activeResidents = hub.getInt("ActiveResidents");
        totalFoodDemand = hub.getInt("TotalFoodDemand");
        totalHerd       = hub.getInt("TotalHerd");
        activeHerd      = hub.getInt("ActiveHerd");
        currentWeight  = hub.getInt("CurrentWeight");
        maxWeight      = hub.getInt("MaxWeight");
        eraTransitions = parseEraTransitions(hub);

        constructionQueueClient.clear();
        hub.getList("ConstructionQueue", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag qt = (CompoundTag) raw;
            String type = qt.getString("Type");
            String defId = qt.getString("DefId");
            long worldPos = "upgrade".equals(type) ? qt.getLong("BuildingWorldPos") : 0L;
            constructionQueueClient.add(new ClientQueueEntry(type, defId, worldPos));
        });

        parseBuildingCatalog(hub);

        stockSnapshot.clear();
        CompoundTag stockTag = hub.getCompound("StockSnapshot");
        for (String key : stockTag.getAllKeys()) {
            stockSnapshot.put(key, stockTag.getInt(key));
        }

        boostedBuildingIds.clear();
        hub.getList("BoostedBuildings", Tag.TAG_STRING).forEach(t -> boostedBuildingIds.add(t.getAsString()));

        tradePrices.clear();
        if (hub.contains("TradePrices")) {
            CompoundTag pricesTag = hub.getCompound("TradePrices");
            for (String itemId : pricesTag.getAllKeys()) {
                CompoundTag priceEntry = pricesTag.getCompound(itemId);
                tradePrices.put(itemId, new int[]{ priceEntry.getInt("buy"), priceEntry.getInt("sell"), Math.max(1, priceEntry.getInt("quantity")) });
            }
        }

        upgradeBuildingsList.clear();
        hub.getList("UpgradeBuildings", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag ubt = (CompoundTag) raw;
            upgradeBuildingsList.add(new UpgradeBuildingEntry(
                ubt.getString("DefId"),
                ubt.getLong("WorldPos"),
                ubt.getInt("UpgradeLevel"),
                ubt.getString("Category"),
                ubt.getString("IconItem")
            ));
        });

        // Update era widget with fresh data
        if (eraWidget != null) {
            eraWidget.updateData(currentEra, eraTransitions);
        }
        // Play firework sound when era advances
        if (lastKnownEra >= 0 && currentEra > prevEra) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                mc.level.playLocalSound(mc.player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
        }
        lastKnownEra = currentEra;

        if (questHubWidget != null) {
            List<CompoundTag> questTagList = new ArrayList<>();
            hub.getList("Quests", Tag.TAG_COMPOUND).forEach(t -> questTagList.add((CompoundTag) t));
            questHubWidget.setQuests(questTagList, anchorPos);
        }

        TownSummaryWidget.chatBroadcastEnabled = hub.getBoolean("ChatSubscribed");
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation tex = switch (activeTab) {
            case 1  -> TEXTURE_CONSTRUCTION;
            case 2  -> TEXTURE_UPGRADE;
            default -> TEXTURE;
        };
        guiGraphics.blit(tex, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (TownHubClientState.pendingHubData != null) {
            CompoundTag hub = TownHubClientState.pendingHubData;
            TownHubClientState.pendingHubData = null;
            parseHubData(hub);
            tryCreateMapWidget(hub);
        }
        if (TownHubClientState.pendingStockUpdate != null) {
            CompoundTag data = TownHubClientState.pendingStockUpdate;
            TownHubClientState.pendingStockUpdate = null;
            applyStockUpdate(data);
        }
        if (TownHubClientState.pendingBuildingList != null) {
            CompoundTag data = TownHubClientState.pendingBuildingList;
            TownHubClientState.pendingBuildingList = null;
            applyBuildingListUpdate(data);
        }
        if (!expandedViewOpen && TownHubClientState.pendingQuestUpdate != null) {
            CompoundTag data = TownHubClientState.pendingQuestUpdate;
            TownHubClientState.pendingQuestUpdate = null;
            applyQuestUpdate(data);
        }
        if (TownHubClientState.pendingEraUpdate != null) {
            CompoundTag data = TownHubClientState.pendingEraUpdate;
            TownHubClientState.pendingEraUpdate = null;
            applyEraUpdate(data);
        }
        if (TownHubClientState.pendingCitizenUpdate != null) {
            CompoundTag data = TownHubClientState.pendingCitizenUpdate;
            TownHubClientState.pendingCitizenUpdate = null;
            applyCitizenUpdate(data);
        }
        if (TownHubClientState.pendingLogEntry != null) {
            CompoundTag data = TownHubClientState.pendingLogEntry;
            TownHubClientState.pendingLogEntry = null;
            applyLogEntry(data);
        }

        this.renderBackground(guiGraphics);

        // Remove closed layer 1 widgets, saving their last-known state
        layer1Widgets.removeIf(w -> {
            if (w.isClosed()) {
                if (w instanceof MapDraggableWidget) {
                    savedMapX = w.getX();
                    savedMapY = w.getY();
                    savedMapOpen = false;
                }
                if (w instanceof TownSummaryWidget) {
                    savedSummaryX = w.getX();
                    savedSummaryY = w.getY();
                    savedSummaryOpen = false;
                }
                if (w instanceof EraProgressDraggableWidget) {
                    savedEraX = w.getX();
                    savedEraY = w.getY();
                    savedEraOpen = false;
                    eraWidget = null;
                }
                if (w instanceof QuestHubWidget) {
                    savedQuestHubX    = w.getX();
                    savedQuestHubY    = w.getY();
                    savedQuestHubOpen = false;
                    questHubWidget = null;
                }
            }
            return w.isClosed();
        });
        mapClosed       = layer1Widgets.stream().noneMatch(w -> w instanceof MapDraggableWidget);
        summaryClosed   = layer1Widgets.stream().noneMatch(w -> w instanceof TownSummaryWidget);
        eraClosed       = layer1Widgets.stream().noneMatch(w -> w instanceof EraProgressDraggableWidget);
        questHubClosed  = layer1Widgets.stream().noneMatch(w -> w instanceof QuestHubWidget);

        // Layer 1: left draggable widgets (Map, Summary, Era, QuestHub)
        for (int i = 0; i < layer1Widgets.size(); i++) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, L1_Z_BASE + i * L1_Z_STEP);
            layer1Widgets.get(i).render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.pose().popPose();
        }

        // Layer 2: right fixed panel -- single z-band always above Layer 1
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, L2_Z_BASE);
        if (activeTab == 1 || activeTab == 2) {
            // Skip inventory slot rendering for non-stock tabs
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
        } else {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderTradeZone(guiGraphics, mouseX, mouseY);
        }
        renderReopenButtons(guiGraphics, mouseX, mouseY);
        renderTabs(guiGraphics, mouseX, mouseY);
        if (activeTab == 1) {
            renderConstructionTab(guiGraphics, mouseX, mouseY);
            renderWeightBar(guiGraphics, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderUpgradeTab(guiGraphics, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();

        // Tooltips above everything
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, L3_Z_BASE + L3_Z_STEP);
        if (activeTab == 1) {
            renderConstructionTooltips(guiGraphics, mouseX, mouseY);
        } else if (activeTab == 2) {
            renderUpgradeTooltips(guiGraphics, mouseX, mouseY);
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }

        guiGraphics.pose().popPose();

        // Expanded NBT view: fullscreen overlay above everything
        if (expandedViewOpen) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, L3_Z_BASE + 5 * L3_Z_STEP);
            renderExpandedView(guiGraphics, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    // -------------------------------------------------------------------------
    // Targeted delta update methods (called from render on new pending packets)
    // -------------------------------------------------------------------------

    private void applyStockUpdate(CompoundTag data) {
        stockSnapshot.clear();
        CompoundTag stockTag = data.getCompound("StockSnapshot");
        for (String key : stockTag.getAllKeys()) {
            stockSnapshot.put(key, stockTag.getInt(key));
        }
        if (cachedHubData != null) cachedHubData.put("StockSnapshot", stockTag);
        this.menu.rebuildFromStock(stockTag);
        refreshEraWidgetFromStock();
    }

    private void applyBuildingListUpdate(CompoundTag data) {
        CompoundTag mapData = data.getCompound("MapData");

        // Update map widget in place
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof MapDraggableWidget mdw) {
                mdw.updateMapData(mapData);
                break;
            }
        }
        if (cachedHubData != null) cachedHubData.put("MapData", mapData);

        // Update construction queue
        constructionQueueClient.clear();
        data.getList("ConstructionQueue", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag qt = (CompoundTag) raw;
            String type = qt.getString("Type");
            String defId = qt.getString("DefId");
            long worldPos = "upgrade".equals(type) ? qt.getLong("BuildingWorldPos") : 0L;
            constructionQueueClient.add(new ClientQueueEntry(type, defId, worldPos));
        });

        // Update upgrade buildings list
        upgradeBuildingsList.clear();
        data.getList("UpgradeBuildings", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag ubt = (CompoundTag) raw;
            upgradeBuildingsList.add(new UpgradeBuildingEntry(
                ubt.getString("DefId"),
                ubt.getLong("WorldPos"),
                ubt.getInt("UpgradeLevel"),
                ubt.getString("Category"),
                ubt.getString("IconItem")
            ));
        });

        // Sync weight and building prerequisite counts in the era widget
        if (data.contains("CurrentWeight")) {
            currentWeight = data.getInt("CurrentWeight");
            maxWeight = data.getInt("MaxWeight");
            refreshEraWidgetFromWeight();
        }
        if (data.contains("BuildingCounts")) {
            refreshEraWidgetFromBuildingCounts(data.getCompound("BuildingCounts"));
        }
    }

    private void applyQuestUpdate(CompoundTag data) {
        BlockPos packetAnchor = NbtUtils.readBlockPos(data.getCompound("AnchorPos"));
        if (!anchorPos.equals(packetAnchor)) return;
        if (questHubWidget != null) {
            List<CompoundTag> tags = new ArrayList<>();
            data.getList("Quests", Tag.TAG_COMPOUND).forEach(t -> tags.add((CompoundTag) t));
            questHubWidget.setQuests(tags, anchorPos);
        }
    }

    private void applyEraUpdate(CompoundTag data) {
        int prevEra = currentEra;
        currentEra     = data.getInt("CurrentEra");
        currentWeight  = data.getInt("CurrentWeight");
        maxWeight      = data.getInt("MaxWeight");
        eraTransitions = parseEraTransitions(data);
        if (eraWidget != null) eraWidget.updateData(currentEra, eraTransitions);
        if (data.contains("BuildingCatalog")) {
            parseBuildingCatalog(data);
            catalogScrollOffset = 0;
            selectedCatalogBuildingId = null;
            constructionPreview = null;
        }
        if (lastKnownEra >= 0 && currentEra > prevEra) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                mc.level.playLocalSound(mc.player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f, false);
            }
        }
        lastKnownEra = currentEra;
        if (cachedHubData != null) {
            cachedHubData.putInt("CurrentEra", currentEra);
            cachedHubData.putInt("CurrentWeight", currentWeight);
            cachedHubData.putInt("MaxWeight", maxWeight);
        }
    }

    private void applyCitizenUpdate(CompoundTag data) {
        totalResidents  = data.getInt("TotalResidents");
        activeResidents = data.getInt("ActiveResidents");
        totalFoodDemand = data.getInt("TotalFoodDemand");
        totalHerd       = data.getInt("TotalHerd");
        activeHerd      = data.getInt("ActiveHerd");
        if (cachedHubData != null) {
            cachedHubData.putInt("TotalResidents", totalResidents);
            cachedHubData.putInt("ActiveResidents", activeResidents);
            cachedHubData.putInt("TotalFoodDemand", totalFoodDemand);
            cachedHubData.putInt("TotalHerd", totalHerd);
            cachedHubData.putInt("ActiveHerd", activeHerd);
            // Keep SummaryData in sync so the widget reflects correct values on next open
            CompoundTag summary = cachedHubData.getCompound("SummaryData");
            summary.putInt("TotalResidents", totalResidents);
            summary.putInt("ActiveResidents", activeResidents);
            summary.putInt("TotalFoodDemand", totalFoodDemand);
            summary.putInt("TotalHerd", totalHerd);
            summary.putInt("ActiveHerd", activeHerd);
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof TownSummaryWidget sw) {
                sw.updateCitizenData(totalResidents, activeResidents, totalFoodDemand, totalHerd, activeHerd);
                break;
            }
        }
        if (eraWidget != null) eraWidget.updateResidents(activeResidents);
    }

    private List<TownLogEntry> parseActivityLog(CompoundTag hub) {
        List<TownLogEntry> result = new ArrayList<>();
        hub.getList("ActivityLog", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag lt = (CompoundTag) raw;
            try {
                TownLogEntry.TownLogType type = TownLogEntry.TownLogType.valueOf(lt.getString("Type"));
                result.add(new TownLogEntry(type, lt.getString("Param"), lt.getLong("Tick")));
            } catch (IllegalArgumentException ignored) {}
        });
        return result;
    }

    private void applyLogEntry(CompoundTag data) {
        TownLogEntry.TownLogType type;
        try { type = TownLogEntry.TownLogType.valueOf(data.getString("Type")); }
        catch (IllegalArgumentException e) { return; }
        TownLogEntry entry = new TownLogEntry(type, data.getString("Param"), data.getLong("Tick"));
        if (cachedHubData != null) {
            net.minecraft.nbt.ListTag log = cachedHubData.getList("ActivityLog", Tag.TAG_COMPOUND);
            log.add(0, data);
            cachedHubData.put("ActivityLog", log);
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof TownSummaryWidget sw) {
                sw.appendLogEntry(entry);
                break;
            }
        }
    }

    private void parseBuildingCatalog(CompoundTag hub) {
        buildingCatalog.clear();
        hub.getList("BuildingCatalog", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag dt = (CompoundTag) raw;
            String id = dt.getString("Id");
            String category = dt.getString("Category");
            String iconItem = dt.getString("IconItem");
            List<CostEntry> cost = new ArrayList<>();
            dt.getList("ConstructionCost", Tag.TAG_COMPOUND)
                .forEach(cr -> {
                    CompoundTag ct = (CompoundTag) cr;
                    cost.add(new CostEntry(ct.getString("Item"), ct.getInt("Amount")));
                });

            int currentLevel = dt.getInt("CurrentLevel");
            List<BuildingProductionTooltip.Row> productionRows = new ArrayList<>();
            List<ProductionCell> productionCells = new ArrayList<>();
            ListTag prod = dt.getList("Production", Tag.TAG_COMPOUND);
            if (!prod.isEmpty()) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.produces")));
                for (Tag t : prod) {
                    CompoundTag pt = (CompoundTag) t;
                    String itemId     = pt.getString("Item");
                    int amount        = pt.getInt("Amount");
                    int seconds       = pt.getInt("EveryTicks") / 20;
                    int unlockAtLevel = pt.getInt("UnlockAtLevel");
                    boolean locked    = unlockAtLevel >= 0 && currentLevel < unlockAtLevel;
                    Item item         = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
                    MutableComponent text = Component.literal("x" + amount + " ")
                        .append(Component.translatable(item.getDescriptionId()))
                        .append(Component.literal(" / " + seconds + "s"))
                        .withStyle(ChatFormatting.GRAY);
                    productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(item), text, locked));
                    productionCells.add(new ProductionCell(item, amount, locked));
                }
            }
            ListTag transforms = dt.getList("Transformations", Tag.TAG_COMPOUND);
            if (!transforms.isEmpty()) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.transforms")));
                for (Tag t : transforms) {
                    CompoundTag tt = (CompoundTag) t;
                    String outputId   = tt.getString("OutputItem");
                    int outputAmount  = tt.getInt("OutputAmount");
                    int seconds       = tt.getInt("EveryTicks") / 20;
                    int unlockAtLevel = tt.getInt("UnlockAtLevel");
                    boolean locked    = unlockAtLevel >= 0 && currentLevel < unlockAtLevel;
                    Item outputItem   = BuiltInRegistries.ITEM.get(new ResourceLocation(outputId));
                    MutableComponent text = Component.literal("x" + outputAmount + " ")
                        .append(Component.translatable(outputItem.getDescriptionId()))
                        .append(Component.literal(" / " + seconds + "s"))
                        .withStyle(ChatFormatting.GRAY);
                    productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(outputItem), text, locked));
                    productionCells.add(new ProductionCell(outputItem, outputAmount, locked));
                }
            }
            double productionBonus = dt.getDouble("ProductionBonus");
            if (productionBonus > 0) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.perks")));
                int percent = (int) Math.round(productionBonus * 100);
                productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(Items.NETHER_STAR),
                    Component.translatable("onceuponatown.tooltip.production_bonus", percent)
                        .withStyle(ChatFormatting.GRAY)));
                productionCells.add(new ProductionCell(Items.NETHER_STAR, percent, false));
            }
            int residents = dt.getInt("Residents");
            if (residents > 0) {
                productionRows.add(new BuildingProductionTooltip.Row(null,
                    Component.translatable("onceuponatown.tooltip.adds")));
                net.minecraft.world.item.Item villagerEgg = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    new ResourceLocation("minecraft:villager_spawn_egg"));
                productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(villagerEgg),
                    Component.translatable("onceuponatown.tooltip.residents", residents)
                        .withStyle(ChatFormatting.GRAY)));
                productionCells.add(new ProductionCell(villagerEgg, residents, false));
            }
            int herd = dt.getInt("Herd");
            if (herd > 0) {
                if (residents == 0) {
                    productionRows.add(new BuildingProductionTooltip.Row(null,
                        Component.translatable("onceuponatown.tooltip.adds")));
                }
                Item pigEgg = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:pig_spawn_egg"));
                productionRows.add(new BuildingProductionTooltip.Row(new ItemStack(pigEgg),
                    Component.translatable("onceuponatown.tooltip.herd", herd)
                        .withStyle(ChatFormatting.GRAY)));
                productionCells.add(new ProductionCell(pigEgg, herd, false));
            }

            int requiredResidents = dt.getInt("RequiredResidents");
            List<ReqBuildingEntry> requiredBuildings = new ArrayList<>();
            dt.getList("RequiredBuildings", Tag.TAG_COMPOUND).forEach(rt -> {
                CompoundTag req = (CompoundTag) rt;
                requiredBuildings.add(new ReqBuildingEntry(
                    req.getString("DefId"), req.getInt("Count"), req.getInt("Have")));
            });

            float baseConsumption = dt.getFloat("BaseConsumptionPerResident");
            float maxConsumption  = dt.getFloat("MaxConsumptionPerResident");
            int maxResidents      = dt.getInt("MaxResidents");
            boolean nextEra       = dt.getBoolean("NextEra");
            String nbtPath        = dt.getString("Nbt");
            boolean hasBuilt      = dt.getBoolean("HasBuilt");
            int builtCount        = dt.getInt("BuiltCount");
            List<String> nbtLevels = new ArrayList<>();
            dt.getList("NbtLevels", Tag.TAG_STRING).forEach(t -> nbtLevels.add(t.getAsString()));
            int weight            = dt.contains("Weight") ? dt.getInt("Weight") : 1;

            buildingCatalog.add(new BuildingEntry(id, category, iconItem, cost, productionRows,
                productionCells, requiredResidents, requiredBuildings,
                productionBonus, baseConsumption, maxConsumption, maxResidents, nextEra,
                nbtPath, hasBuilt, nbtLevels, builtCount, weight));
        });
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int btnX = leftPos - 16;
        int[] tabYs = { topPos + 20, topPos + 46, topPos + 72 };
        int btnW = 14, btnH = 24;

        for (int i = 0; i < 3; i++) {
            int btnY = tabYs[i];
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, activeTab == i ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        }

        drawChestIcon(g, btnX + 2, tabYs[0] + 8);
        drawHouseIcon(g, btnX + 2, tabYs[1] + 8);
        drawUpArrowIcon(g, btnX + 3, tabYs[2] + 8);
    }

    private void renderConstructionTab(GuiGraphics g, int mx, int my) {
        hoveredQueueSlot = -1;
        hoveredCatalogSlot = -1;

        // Zone C: single queue row at row 1 (row 0 = weight bar)
        for (int col = 0; col < QUEUE_COLS; col++) {
            int sx = leftPos + QUEUE_GRID_X + col * CELL;
            int sy = topPos + QUEUE_GRID_Y + CELL - 5;

            if (col < constructionQueueClient.size()) {
                ClientQueueEntry qe = constructionQueueClient.get(col);
                BuildingEntry entry = findCatalogEntry(qe.defId());
                int color = entry != null ? categoryColor(entry.category()) : COLOR_UNKNOWN;
                g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                if (entry != null) renderItemIcon(g, entry.iconItem(), sx, sy);
                if (qe.isUpgrade()) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    String badge = "UP";
                    g.drawString(font, badge, sx + CELL - 2 - font.width(badge) - 1, sy + CELL - 10, 0xFF55FFFF, true);
                    g.pose().popPose();
                }
            } else {
                g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, COLOR_SLOT_EMPTY);
            }

            if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
                g.fill(sx, sy, sx + CELL, sy + CELL, COLOR_SLOT_HOVER);
                hoveredQueueSlot = col;
            }
        }

        // Zone B: building info panel
        renderConstructionInfoPanel(g, mx, my);

        // Zone A: catalog grid
        int visibleStart = catalogScrollOffset * AVAIL_COLS;
        int[] rowYOffsets = {AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36};
        for (int row = 0; row < CATALOG_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int catalogIdx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];

                if (catalogIdx < buildingCatalog.size()) {
                    BuildingEntry entry = buildingCatalog.get(catalogIdx);
                    boolean affordable = isAffordable(entry) && meetsPrerequisites(entry);
                    boolean selected = entry.id().equals(selectedCatalogBuildingId);
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    if (selected) g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, 0x40FFFFFF);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (entry.nextEra()) {
                        g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, 0x99111111);
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        drawPadlockIcon(g, sx + 4, sy + 2);
                        g.pose().popPose();
                    }

                    if (boostedBuildingIds.contains(entry.id())) {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        int bx = sx + 1;
                        int by = sy + 1;
                        int bc = 0xFFFFDD44;
                        g.fill(bx + 1, by,     bx + 2, by + 1, bc);
                        g.fill(bx,     by + 1, bx + 3, by + 2, bc);
                        g.fill(bx + 1, by + 2, bx + 2, by + 3, bc);
                        g.pose().popPose();
                    }

                    if (entry.builtCount() > 0) {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        g.pose().scale(0.5f, 0.5f, 1.0f);
                        String countText = String.valueOf(entry.builtCount());
                        int textW = font.width(countText);
                        g.drawString(font, countText, (sx + CELL - 3) * 2 - textW, (sy + CELL - 8) * 2, 0xFFFFFF, true);
                        g.pose().popPose();
                    }
                } else {
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, COLOR_SLOT_EMPTY);
                }

                if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
                    g.fill(sx, sy, sx + CELL, sy + CELL, COLOR_SLOT_HOVER);
                    hoveredCatalogSlot = visibleStart + row * AVAIL_COLS + col;
                }
            }
        }

        // Scroll indicator
        int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > CATALOG_ROWS) {
            String scrollText = (catalogScrollOffset + 1) + "/" + (totalRows - CATALOG_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 201, 0xFFAAAAAA, false);
        }
    }

    private void renderConstructionInfoPanel(GuiGraphics g, int mx, int my) {
        int panelX = leftPos + QUEUE_GRID_X;
        int panelY = topPos + QUEUE_GRID_Y + CELL * 2;
        int panelW = AVAIL_COLS * CELL;                // 162
        int panelH = 82;
        int leftW  = 76;
        int rightX = panelX + leftW;
        int rightW = panelW - leftW;

        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;

        if (sel != null) {
            // 3D NBT preview fills the left zone
            if (constructionPreview != null) {
                constructionPreview.render(g, mx, my, 0f);
            }

            // Right zone: 5-column x 4-row production grid
            int[] colXs = { leftPos + 80, leftPos + 98, leftPos + 116, leftPos + 134, leftPos + 152 };
            int[] rowYs = { topPos + 54,  topPos + 72,  topPos + 90,   topPos + 108 };
            List<ProductionCell> cells = sel.productionCells();
            int cellIdx = 0;
            outer:
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 5; col++) {
                    if (cellIdx >= cells.size()) break outer;
                    ProductionCell cell = cells.get(cellIdx++);
                    ItemStack stack = new ItemStack(cell.item(), cell.amount());
                    g.renderFakeItem(stack, colXs[col], rowYs[row]);
                    g.renderItemDecorations(font, stack, colXs[col], rowYs[row]);
                    if (cell.locked()) {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        NbtPreviewWidget.drawPadlockIcon(g, colXs[col] + 4, rowYs[row] + 3);
                        g.pose().popPose();
                    }
                }
            }
        }

        // Construct button
        int btnW = 46;
        int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
        int btnY = topPos + 126;
        int btnH = 11;
        boolean canConstruct = sel != null && !sel.nextEra() && !("town_center".equals(sel.category()) && sel.hasBuilt()) && isAffordable(sel) && meetsPrerequisites(sel);
        boolean btnHover = canConstruct && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
        int btnColor = canConstruct ? (btnHover ? 0xFF55BB55 : 0xFF337733) : 0xFF444444;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
        String btnText = (sel != null && sel.nextEra()) ? "Next era" : "Build";
        g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2,
            canConstruct ? 0xFFFFFFFF : 0xFF888888, false);

        // Expand button: bottom-right corner of the NBT preview zone
        if (sel != null && constructionPreview != null) {
            int expBtnX = leftPos + 64;
            int expBtnY = topPos + 110;
            boolean expHover = mx >= expBtnX && mx < expBtnX + 12 && my >= expBtnY && my < expBtnY + 12;
            g.fill(expBtnX, expBtnY, expBtnX + 12, expBtnY + 12, expHover ? 0xFF666666 : 0xFF333333);
            drawExpandIcon(g, expBtnX + 2, expBtnY + 2);
        }
    }

    private void renderConstructionTooltips(GuiGraphics g, int mx, int my) {
        if (hoveredQueueSlot >= 0 && hoveredQueueSlot < constructionQueueClient.size()) {
            ClientQueueEntry qe = constructionQueueClient.get(hoveredQueueSlot);
            BuildingEntry entry = findCatalogEntry(qe.defId());
            List<Component> lines = new ArrayList<>();
            if (qe.isUpgrade()) {
                lines.add(Component.literal("Upgrade: " + formatId(qe.defId())).withStyle(s -> s.withBold(true)));
            } else {
                lines.add(Component.literal(formatId(qe.defId())).withStyle(s -> s.withBold(true)));
            }
            lines.add(Component.literal("Shift + Right-click to remove").withStyle(s -> s.withColor(0x888888)));
            g.renderComponentTooltip(font, lines, mx, my);
        } else if (hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
            BuildingEntry entry = buildingCatalog.get(hoveredCatalogSlot);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(formatId(entry.id())).withStyle(s -> s.withBold(true)));
            if (entry.nextEra()) {
                lines.add(Component.literal("Unlocks at next era").withStyle(s -> s.withColor(0xAAAAAA)));
                g.renderComponentTooltip(font, lines, mx, my);
                return;
            }
            for (CostEntry ce : entry.cost()) {
                int have = stockSnapshot.getOrDefault(ce.itemId(), 0);
                boolean ok = have >= ce.amount();
                int color = ok ? 0x55FF55 : 0xFF5555;
                String itemName = ce.itemId().contains(":")
                    ? ce.itemId().substring(ce.itemId().indexOf(':') + 1) : ce.itemId();
                lines.add(Component.literal(have + "/" + ce.amount() + " " + formatId(itemName))
                    .withStyle(s -> s.withColor(color)));
            }
            if (entry.requiredResidents() > 0) {
                boolean met = activeResidents >= entry.requiredResidents();
                lines.add(Component.literal(activeResidents + "/" + entry.requiredResidents() + " active residents")
                    .withStyle(s -> s.withColor(met ? 0x55FF55 : 0xFF5555)));
            }
            for (ReqBuildingEntry req : entry.requiredBuildings()) {
                boolean met = req.have() >= req.required();
                lines.add(Component.literal(req.have() + "/" + req.required() + " " + formatId(req.defId()))
                    .withStyle(s -> s.withColor(met ? 0x55FF55 : 0xFF5555)));
            }
            int wCost = entry.weight();
            boolean weightOk = currentWeight + wCost <= maxWeight;
            lines.add(Component.literal(wCost + " weight")
                .withStyle(s -> s.withColor(weightOk ? 0x55FF55 : 0xFF5555)));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    private void renderUpgradeTab(GuiGraphics g, int mx, int my) {
        hoveredUpgradeSlot = -1;
        numActiveBars = 0;
        unlockIconBounds.clear();
        unlockIconItemIds.clear();

        UpgradeBuildingEntry sel = getSelectedUpgradeEntry();
        if (sel != null) {
            ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(sel.defId());
            int maxLevel = defEntry != null ? defEntry.upgrades().size() : 0;
            boolean atMax = maxLevel > 0 && sel.upgradeLevel() >= maxLevel;

            if (defEntry != null && maxLevel > 0) {
                boolean pending = isUpgradePending(sel);
                boolean canAfford = !atMax && !pending && canAffordUpgrade(sel, defEntry);

                int btnW = 46;
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
                int btnY = topPos + 126;
                int btnH = 11;
                boolean btnActive = !atMax && !pending;
                boolean btnHover = btnActive && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;

                // Right zone: stat gauges (ghost fill shows next-level delta on button hover)
                renderUpgradeGaugeBars(g, sel, defEntry, btnHover, canAfford);

                // Unlock row: items unlocked at the next upgrade level
                if (!atMax && !pending) renderUnlockRow(g, mx, my, sel, defEntry);

                // Upgrade button
                int btnColor = (atMax || pending) ? 0xFF444444 : (canAfford ? (btnHover ? 0xFF55BB55 : 0xFF337733) : 0xFF444444);
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
                String btnText = atMax ? "MAX" : (pending ? "Queued" : "Upgrade");
                int btnTextColor = (atMax || pending || !canAfford) ? 0xFF888888 : 0xFFFFFFFF;
                g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2, btnTextColor, false);
            }
        }

        renderUpgradeBuildingGrid(g, mx, my);

        // Era 0 lock overlay: covers the tab content so it is visible but non-interactive.
        if (currentEra == 0) {
            g.fill(leftPos + 7, topPos + 17, leftPos + 7 + 163, topPos + 17 + 176, 0xC0222222);
            int lockX = leftPos + 7 + 163 / 2 - 4;
            int lockY = topPos + 17 + 176 / 2 - 5;
            drawPadlockIcon(g, lockX, lockY);
        }
    }

    // Renders stat gauge bars spanning the full gauge zone (x=8, y=19, w=158, h=76).
    // Bars are only created for stats that have a non-zero total range across all upgrade levels.
    // Ghost fill extends each bar to show the next level delta when the upgrade button is hovered.
    private void renderUpgradeGaugeBars(GuiGraphics g, UpgradeBuildingEntry sel,
            ClientBuildingDefsRegistry.DefEntry defEntry, boolean showGhost, boolean canAfford) {
        int barX = leftPos + 12;
        int barW = 152;
        int barH = 3;
        int gaugeY = topPos + 22;
        int ghostColor = canAfford ? 0x6655BB55 : 0x66BB5555;

        int currentLevel = sel.upgradeLevel();

        float totalCadence = 0f, curCadence = 0f, ghostCadence = 0f;
        int totalAmount = 0, curAmount = 0, ghostAmount = 0;
        int totalCapacity = 0, curCapacity = 0, ghostCapacity = 0;
        int totalResidents = 0, curResidents = 0, ghostResidentsVal = 0;
        float totalFood = 0f, curFood = 0f, ghostFood = 0f;
        double totalStock = 0.0, curStock = 0.0, ghostStock = 0.0;
        int totalHerd = 0, curHerd = 0, ghostHerd = 0;
        float totalHerdFood = 0f, curHerdFood = 0f, ghostHerdFood = 0f;

        for (int i = 0; i < defEntry.upgrades().size(); i++) {
            var lvl = defEntry.upgrades().get(i);
            totalCadence   += lvl.cadenceMultiplier();
            totalAmount    += lvl.amountAdd();
            totalCapacity  += lvl.capacityStacksAdd();
            totalResidents += lvl.residentsAdd();
            totalFood      += lvl.consumptionPerResidentAdd();
            totalStock     += lvl.productionBonusAdd();
            totalHerd      += lvl.herdAdd();
            totalHerdFood  += lvl.consumptionPerHerdAdd();
            if (i < currentLevel) {
                curCadence   += lvl.cadenceMultiplier();
                curAmount    += lvl.amountAdd();
                curCapacity  += lvl.capacityStacksAdd();
                curResidents += lvl.residentsAdd();
                curFood      += lvl.consumptionPerResidentAdd();
                curStock     += lvl.productionBonusAdd();
                curHerd      += lvl.herdAdd();
                curHerdFood  += lvl.consumptionPerHerdAdd();
            }
            if (showGhost && i == currentLevel) {
                ghostCadence      = lvl.cadenceMultiplier();
                ghostAmount       = lvl.amountAdd();
                ghostCapacity     = lvl.capacityStacksAdd();
                ghostResidentsVal = lvl.residentsAdd();
                ghostFood         = lvl.consumptionPerResidentAdd();
                ghostStock        = lvl.productionBonusAdd();
                ghostHerd         = lvl.herdAdd();
                ghostHerdFood     = lvl.consumptionPerHerdAdd();
            }
        }

        if (totalCadence > 0.001f) {
            float fill = curCadence / totalCadence;
            float ghost = showGhost ? ghostCadence / totalCadence : 0f;
            g.drawString(font, "Speed", barX, gaugeY, 0xFFFF8800, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFFF8800, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = (int)(curCadence * 100) + "% faster  (max " + (int)(totalCadence * 100) + "%)";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalAmount > 0) {
            float fill = (float) curAmount / totalAmount;
            float ghost = showGhost ? (float) ghostAmount / totalAmount : 0f;
            g.drawString(font, "Output", barX, gaugeY, 0xFFFFFF00, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFFFFF00, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curAmount + " output  (max +" + totalAmount + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalCapacity > 0) {
            float fill = (float) curCapacity / totalCapacity;
            float ghost = showGhost ? (float) ghostCapacity / totalCapacity : 0f;
            g.drawString(font, "Capacity", barX, gaugeY, 0xFF4488FF, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFF4488FF, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curCapacity + " stacks  (max +" + totalCapacity + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalResidents > 0) {
            float fill = (float) curResidents / totalResidents;
            float ghost = showGhost ? (float) ghostResidentsVal / totalResidents : 0f;
            g.drawString(font, "Residents", barX, gaugeY, 0xFFAA7744, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFAA7744, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curResidents + " residents  (max +" + totalResidents + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalFood > 0.001f) {
            float fill = curFood / totalFood;
            float ghost = showGhost ? ghostFood / totalFood : 0f;
            g.drawString(font, "Food", barX, gaugeY, 0xFFCC3333, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFCC3333, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = String.format("+%.2f food/res  (max +%.2f)", curFood, totalFood);
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalStock > 0.001) {
            float fill = (float)(curStock / totalStock);
            float ghost = showGhost ? (float)(ghostStock / totalStock) : 0f;
            g.drawString(font, "Stock", barX, gaugeY, 0xFFFFCC00, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost,0xFFFFCC00, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + (int)(curStock * 100) + "% village stock  (max +" + (int)(totalStock * 100) + "%)";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalHerd > 0) {
            float fill = (float) curHerd / totalHerd;
            float ghost = showGhost ? (float) ghostHerd / totalHerd : 0f;
            g.drawString(font, "Herd", barX, gaugeY, 0xFF88BB44, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost, 0xFF88BB44, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = "+" + curHerd + " animals  (max +" + totalHerd + ")";
            numActiveBars++;
            gaugeY += barH + 6;
        }
        if (totalHerdFood > 0.001f) {
            float fill = curHerdFood / totalHerdFood;
            float ghost = showGhost ? ghostHerdFood / totalHerdFood : 0f;
            g.drawString(font, "Herd Food", barX, gaugeY, 0xFFCC6633, false);
            gaugeY += 13;
            renderStatBar(g, barX, gaugeY, barW, barH, fill, ghost, 0xFFCC6633, ghostColor);
            barLabelYs[numActiveBars] = gaugeY - 7;
            barTooltips[numActiveBars] = String.format("+%.2f food/animal  (max +%.2f)", curHerdFood, totalHerdFood);
            numActiveBars++;
        }
    }

    // Renders the unlock row at y=107: items from unlocks_display of the NEXT upgrade level.
    // Shows item icons with their production amount (looked up from the building catalog).
    // Skipped when the building is at max level or the next level has nothing to unlock.
    private void renderUnlockRow(GuiGraphics g, int mx, int my,
            UpgradeBuildingEntry sel, ClientBuildingDefsRegistry.DefEntry defEntry) {
        int nextLevel = sel.upgradeLevel();
        if (nextLevel >= defEntry.upgrades().size()) return;
        List<String> unlocks = defEntry.upgrades().get(nextLevel).unlockedDisplay();
        if (unlocks.isEmpty()) return;

        int rowX = leftPos + QUEUE_GRID_X;
        int labelY = topPos + 99;
        int rowY = labelY + font.lineHeight;

        g.drawString(font, "Unlocks:", rowX, labelY, 0xFFFFFFFF, false);

        BuildingEntry catalogEntry = findCatalogEntry(sel.defId());
        for (int i = 0; i < unlocks.size() && i < AVAIL_COLS; i++) {
            int sx = rowX + i * CELL;
            String itemId = unlocks.get(i);
            try {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
                if (item != Items.AIR) {
                    int amount = 0;
                    if (catalogEntry != null) {
                        for (ProductionCell pc : catalogEntry.productionCells()) {
                            if (pc.item() == item) { amount = pc.amount(); break; }
                        }
                    }
                    ItemStack stack = amount > 0 ? new ItemStack(item, amount) : new ItemStack(item);
                    g.renderFakeItem(stack, sx, rowY);
                    if (amount > 0) g.renderItemDecorations(font, stack, sx, rowY);
                    unlockIconBounds.add(new int[]{sx, rowY});
                    unlockIconItemIds.add(itemId);
                }
            } catch (Exception ignored) {}
        }
    }

    // Returns only the placed buildings that have at least one upgrade level defined.
    // Buildings without upgrades are excluded from the upgrade catalog per the spec.
    private List<UpgradeBuildingEntry> getUpgradeableBuildings() {
        List<UpgradeBuildingEntry> result = new ArrayList<>();
        for (UpgradeBuildingEntry e : upgradeBuildingsList) {
            ClientBuildingDefsRegistry.DefEntry def = ClientBuildingDefsRegistry.get(e.defId());
            if (def != null && !def.upgrades().isEmpty()) result.add(e);
        }
        return result;
    }

    private void renderStatBar(GuiGraphics g, int x, int y, int w, int h,
                                float fill, float ghost, int barColor, int ghostColor) {
        g.fill(x, y, x + w, y + h, 0xFF222222);
        int fillW = (int)(w * Math.min(1f, Math.max(0f, fill)));
        if (fillW > 0) g.fill(x, y, x + fillW, y + h, barColor);
        if (ghost > 0f) {
            int ghostW = (int)(w * Math.min(1f - fill, Math.max(0f, ghost)));
            if (ghostW > 0) g.fill(x + fillW, y, x + fillW + ghostW, y + h, ghostColor);
        }
    }

    private void renderUpgradeBuildingGrid(GuiGraphics g, int mx, int my) {
        List<UpgradeBuildingEntry> list = getUpgradeableBuildings();
        int[] rowYOffsets = { AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36 };
        int visibleStart = upgradeGridScrollOffset * AVAIL_COLS;

        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int idx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];

                if (idx < list.size()) {
                    UpgradeBuildingEntry entry = list.get(idx);
                    ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(entry.defId());
                    boolean atMax = defEntry != null && entry.upgradeLevel() >= defEntry.upgrades().size();
                    boolean affordable = atMax || canAffordUpgrade(entry, defEntry);
                    int color = affordable ? categoryColor(entry.category()) : dim(categoryColor(entry.category()));
                    if (atMax) color = dim(color);
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, color);
                    renderItemIcon(g, entry.iconItem(), sx, sy);
                    if (entry.upgradeLevel() > 0) {
                        String lvl = "+" + entry.upgradeLevel();
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        int lvlColor = atMax ? 0xFF888844 : 0xFFFFFF55;
                        g.drawString(font, lvl, sx + CELL - 2 - font.width(lvl) - 1, sy + CELL - 10, lvlColor, true);
                        g.pose().popPose();
                    }
                } else {
                    g.fill(sx, sy, sx + CELL - 2, sy + CELL - 2, COLOR_SLOT_EMPTY);
                }

                if (mx >= sx && mx < sx + CELL && my >= sy && my < sy + CELL) {
                    g.fill(sx, sy, sx + CELL, sy + CELL, COLOR_SLOT_HOVER);
                    hoveredUpgradeSlot = idx;
                }
            }
        }

        int totalRows = (list.size() + AVAIL_COLS - 1) / AVAIL_COLS;
        if (totalRows > AVAIL_ROWS) {
            String scrollText = (upgradeGridScrollOffset + 1) + "/" + (totalRows - AVAIL_ROWS + 1);
            g.drawString(font, scrollText, leftPos + imageWidth - 4 - font.width(scrollText),
                topPos + 201, 0xFFAAAAAA, false);
        }
    }

    private void renderUpgradeTooltips(GuiGraphics g, int mx, int my) {
        if (currentEra == 0
                && mx >= leftPos && mx < leftPos + imageWidth
                && my >= topPos + 16 && my < topPos + imageHeight) {
            g.renderTooltip(font, Component.literal("Unlockable at Era 1"), mx, my);
            return;
        }

        // Per-bar hover tooltips (right zone, y=20 to y=93)
        for (int i = 0; i < numActiveBars; i++) {
            int labelY = barLabelYs[i];
            if (my >= labelY && my < labelY + 13 && mx >= leftPos + 12 && mx < leftPos + imageWidth - 8) {
                g.renderTooltip(font, Component.literal(barTooltips[i]).withStyle(s -> s.withColor(0xCCCCCC)), mx, my);
                return;
            }
        }

        // Unlock icon hover: show item name
        for (int i = 0; i < unlockIconBounds.size(); i++) {
            int[] b = unlockIconBounds.get(i);
            if (mx >= b[0] && mx < b[0] + 16 && my >= b[1] && my < b[1] + 16) {
                try {
                    Item hovItem = BuiltInRegistries.ITEM.get(new ResourceLocation(unlockIconItemIds.get(i)));
                    if (hovItem != net.minecraft.world.item.Items.AIR) {
                        g.renderTooltip(font, Component.translatable(hovItem.getDescriptionId()), mx, my);
                    }
                } catch (Exception ignored) {}
                return;
            }
        }

        List<UpgradeBuildingEntry> list = getUpgradeableBuildings();
        if (hoveredUpgradeSlot < 0 || hoveredUpgradeSlot >= list.size()) return;
        UpgradeBuildingEntry entry = list.get(hoveredUpgradeSlot);
        ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(entry.defId());
        int maxLevel = defEntry != null ? defEntry.upgrades().size() : 0;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(formatId(entry.defId()))
            .withStyle(s -> s.withBold(true)));

        if (defEntry != null && maxLevel > 0 && entry.upgradeLevel() < maxLevel) {
            List<ClientBuildingDefsRegistry.CostEntry> costs = defEntry.upgrades().get(entry.upgradeLevel()).upgradeCost();
            for (ClientBuildingDefsRegistry.CostEntry ce : costs) {
                int have = stockSnapshot.getOrDefault(ce.itemId(), 0);
                boolean ok = have >= ce.amount();
                String itemName = ce.itemId().contains(":")
                    ? ce.itemId().substring(ce.itemId().indexOf(':') + 1) : ce.itemId();
                lines.add(Component.literal(ce.amount() + "x " + formatId(itemName))
                    .withStyle(s -> s.withColor(ok ? 0x55FF55 : 0xFF5555)));
            }
            if (!canAffordUpgrade(entry, defEntry)) {
                lines.add(Component.literal("Not enough resources").withStyle(s -> s.withColor(0xFF5555)));
            }
        } else if (maxLevel > 0 && entry.upgradeLevel() >= maxLevel) {
            lines.add(Component.literal("Fully upgraded").withStyle(s -> s.withColor(0xFFFFDD44)));
        }
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private void handleUpgradeTabClick(double mX, double mY, int button) {
        if (button != 0) return;
        if (currentEra == 0) return;

        UpgradeBuildingEntry sel = getSelectedUpgradeEntry();
        if (sel != null) {
            ClientBuildingDefsRegistry.DefEntry defEntry = ClientBuildingDefsRegistry.get(sel.defId());
            if (defEntry != null && sel.upgradeLevel() < defEntry.upgrades().size()) {
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - 46;
                int btnY = topPos + 126;
                if (mX >= btnX && mX < btnX + 46 && mY >= btnY && mY < btnY + 11) {
                    if (!isUpgradePending(sel) && canAffordUpgrade(sel, defEntry)) {
                        NetworkHelper.sendUpgradeBuildingPacket.accept(anchorPos, sel.worldPosLong());
                    }
                    return;
                }
            }
        }

        List<UpgradeBuildingEntry> list = getUpgradeableBuildings();
        int[] rowYOffsets = { AVAIL_GRID_Y_ROW0, AVAIL_GRID_Y_ROW0 + 18, AVAIL_GRID_Y_ROW0 + 36 };
        int visibleStart = upgradeGridScrollOffset * AVAIL_COLS;
        for (int row = 0; row < AVAIL_ROWS; row++) {
            for (int col = 0; col < AVAIL_COLS; col++) {
                int idx = visibleStart + row * AVAIL_COLS + col;
                int sx = leftPos + QUEUE_GRID_X + col * CELL;
                int sy = topPos + rowYOffsets[row];
                if (mX >= sx && mX < sx + CELL && mY >= sy && mY < sy + CELL) {
                    if (idx < list.size()) {
                        selectedUpgradeBuildingPos = list.get(idx).worldPosLong();
                    }
                    return;
                }
            }
        }
    }

    private UpgradeBuildingEntry getSelectedUpgradeEntry() {
        if (selectedUpgradeBuildingPos == -1L) return null;
        for (UpgradeBuildingEntry e : upgradeBuildingsList) {
            if (e.worldPosLong() == selectedUpgradeBuildingPos) return e;
        }
        return null;
    }

    private boolean isUpgradePending(UpgradeBuildingEntry entry) {
        for (ClientQueueEntry qe : constructionQueueClient) {
            if (qe.isUpgrade() && qe.buildingWorldPos() == entry.worldPosLong()) return true;
        }
        return false;
    }

    private boolean canAffordUpgrade(UpgradeBuildingEntry entry, ClientBuildingDefsRegistry.DefEntry defEntry) {
        if (defEntry == null || entry.upgradeLevel() >= defEntry.upgrades().size()) return false;
        for (ClientBuildingDefsRegistry.CostEntry ce : defEntry.upgrades().get(entry.upgradeLevel()).upgradeCost()) {
            if (stockSnapshot.getOrDefault(ce.itemId(), 0) < ce.amount()) return false;
        }
        return true;
    }

    private void renderItemIcon(GuiGraphics g, String iconItemId, int x, int y) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(iconItemId));
            if (item != net.minecraft.world.item.Items.AIR) {
                g.renderFakeItem(new ItemStack(item), x, y);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Texture has no title bar
    }

    @Override
    public void onClose() {
        savedActiveTab = activeTab;
        savedWidgetOrder.clear();
        for (DraggableWidget w : layer1Widgets) {
            if (w instanceof MapDraggableWidget) {
                savedMapX = w.getX();
                savedMapY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof TownSummaryWidget) {
                savedSummaryX = w.getX();
                savedSummaryY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof EraProgressDraggableWidget) {
                savedEraX = w.getX();
                savedEraY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            } else if (w instanceof QuestHubWidget) {
                savedQuestHubX = w.getX();
                savedQuestHubY = w.getY();
                savedWidgetOrder.add(w.getClass().getSimpleName());
            }
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expandedViewOpen && keyCode == 256) { // GLFW_KEY_ESCAPE
            expandedViewOpen = false;
            expandedWidget = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mX, double mY, int button) {
        // Expanded view intercepts all clicks
        if (expandedViewOpen) {
            if (button == 0) handleExpandedViewClick(mX, mY);
            return true;
        }

        // Reopen buttons (anchored at bottom-left, stacking upward)
        if (button == 0 && cachedHubData != null) {
            int btnX = leftPos - 18;
            int btnY = topPos + imageHeight - 16;
            if (mapClosed) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int startX = (savedMapX >= 0) ? savedMapX : centerX(freeZoneW, mapInitialHeight);
                    int startY = (savedMapY >= 0) ? savedMapY : centerY(this.height, mapInitialHeight);
                    MapDraggableWidget reopenedMap = new MapDraggableWidget(startX, startY, Math.min(160, freeZoneW - 20), mapInitialHeight, freeZoneW, this.height, cachedHubData.getCompound("MapData"));
                    reopenedMap.setOnBuildingClicked((pos, defId) -> {
                        activeTab = 1;
                        BuildingEntry catalogEntry = findCatalogEntry(defId);
                        if (catalogEntry != null) {
                            selectedCatalogBuildingId = defId;
                            updateConstructionPreview(catalogEntry);
                        }
                    });
                    reopenedMap.setOnBuildingRightClicked(pos -> { activeTab = 2; selectedUpgradeBuildingPos = pos; });
                    layer1Widgets.add(0, reopenedMap);
                    savedMapOpen = true;
                    mapClosed = false;
                    return true;
                }
                btnY -= 18;
            }
            if (summaryClosed && cachedHubData.contains("SummaryData")) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int summaryH = DraggableWidget.TITLE_BAR_H + TownSummaryWidget.VISIBLE_H;
                    int startX = (savedSummaryX >= 0) ? savedSummaryX : centerX(freeZoneW, TownSummaryWidget.WIDGET_W);
                    int startY = (savedSummaryY >= 0) ? savedSummaryY : centerY(this.height, summaryH);
                    TownSummaryWidget sw = new TownSummaryWidget(cachedHubData.getCompound("MapData"), cachedHubData.getCompound("SummaryData"), startX, startY, freeZoneW, this.height);
                    if (cachedHubData.contains("ActivityLog")) {
                        sw.loadInitialLog(parseActivityLog(cachedHubData));
                    }
                    sw.setOnBroadcastToggled(() -> NetworkHelper.sendToggleChatBroadcastPacket.accept(anchorPos));
                    layer1Widgets.add(0, sw);
                    savedSummaryOpen = true;
                    summaryClosed = false;
                    return true;
                }
                btnY -= 18;
            }
            if (eraClosed) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int startX = (savedEraX >= 0) ? savedEraX : centerX(freeZoneW, EraProgressDraggableWidget.computeWidgetW(eraTransitions));
                    int startY = (savedEraY >= 0) ? savedEraY : centerY(this.height, DraggableWidget.TITLE_BAR_H + 80);
                    eraWidget = new EraProgressDraggableWidget(startX, startY, freeZoneW, this.height,
                        currentEra, eraTransitions,
                        pathId -> NetworkHelper.sendAdvanceEraPacket.accept(anchorPos, pathId));
                    layer1Widgets.add(0, eraWidget);
                    savedEraOpen = true;
                    eraClosed = false;
                    return true;
                }
                btnY -= 18;
            }
            if (questHubClosed) {
                if (mX >= btnX && mX < btnX + 14 && mY >= btnY && mY < btnY + 14) {
                    int freeZoneW = this.leftPos;
                    int questH = DraggableWidget.TITLE_BAR_H + QuestHubWidget.VISIBLE_H;
                    int startX = (savedQuestHubX >= 0)
                        ? Math.min(savedQuestHubX, Math.max(0, freeZoneW - QuestHubWidget.WIDGET_W))
                        : centerX(freeZoneW, QuestHubWidget.WIDGET_W);
                    int startY = (savedQuestHubY >= 0)
                        ? Math.min(savedQuestHubY, Math.max(0, this.height - questH))
                        : centerY(this.height, questH);
                    questHubWidget = new QuestHubWidget(startX, startY, freeZoneW, this.height);
                    if (cachedHubData != null && cachedHubData.contains("Quests")) {
                        List<CompoundTag> tags = new ArrayList<>();
                        cachedHubData.getList("Quests", Tag.TAG_COMPOUND).forEach(t -> tags.add((CompoundTag) t));
                        questHubWidget.setQuests(tags, anchorPos);
                    }
                    layer1Widgets.add(0, questHubWidget);
                    savedQuestHubOpen = true;
                    questHubClosed = false;
                    return true;
                }
            }
        }

        // Tab switching (vertical left-side tabs)
        int tabBtnX = leftPos - 16;
        int[] tabBtnYs = { topPos + 20, topPos + 46, topPos + 72 };
        if (mX >= tabBtnX && mX < tabBtnX + 14) {
            for (int i = 0; i < 3; i++) {
                if (mY >= tabBtnYs[i] && mY < tabBtnYs[i] + 24) {
                    if (i == 0 && activeTab != 0) {
                        catalogScrollOffset = 0;
                        constructionPreview = null;
                        buyMode = false;
                        buyRequest.clear();
                        NetworkHelper.sendRequestStockPacket.accept(anchorPos);
                    } else if (i == 2 && activeTab != 2) {
                        constructionPreview = null;
                    }
                    activeTab = i;
                    return true;
                }
            }
        }

        // Layer 1: only route clicks inside the left free zone
        if (mX < leftPos) {
            for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
                DraggableWidget w = layer1Widgets.get(i);
                if (w.mouseClicked(mX, mY, button)) {
                    if (i != layer1Widgets.size() - 1) {
                        layer1Widgets.remove(i);
                        layer1Widgets.add(w);
                    }
                    return true;
                }
            }
        }

        if (activeTab == 2) {
            handleUpgradeTabClick(mX, mY, button);
            return true;
        }

        if (activeTab == 1) {
            // Queue slot: shift + right-click removes
            if (button == 1 && hasShiftDown() && hoveredQueueSlot >= 0
                    && hoveredQueueSlot < constructionQueueClient.size()) {
                NetworkHelper.sendRemoveQueuedBuildingPacket.accept(anchorPos, hoveredQueueSlot);
                return true;
            }
            // Expand button: opens fullscreen NBT view for selected building
            if (button == 0 && selectedCatalogBuildingId != null && constructionPreview != null) {
                int expBtnX = leftPos + 64;
                int expBtnY = topPos + 110;
                if (mX >= expBtnX && mX < expBtnX + 12 && mY >= expBtnY && mY < expBtnY + 12) {
                    openExpandedView();
                    return true;
                }
            }
            // Construction preview: forward clicks so drag-to-rotate works
            if (constructionPreview != null && constructionPreview.mouseClicked(mX, mY, button)) return true;
            // Catalog slot: left-click selects and populates Zone B
            if (button == 0 && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
                BuildingEntry catalogEntry = buildingCatalog.get(hoveredCatalogSlot);
                selectedCatalogBuildingId = catalogEntry.id();
                updateConstructionPreview(catalogEntry);
                return true;
            }
            // Construct button: left-click queues the selected building
            if (button == 0) {
                int btnW = 46;
                int btnX = leftPos + QUEUE_GRID_X + (AVAIL_COLS * CELL) - 2 - btnW;
                int btnY = topPos + 126;
                if (mX >= btnX && mX < btnX + btnW && mY >= btnY && mY < btnY + 11) {
                    BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
                    if (sel != null && !("town_center".equals(sel.category()) && sel.hasBuilt()) && isAffordable(sel) && meetsPrerequisites(sel)) {
                        NetworkHelper.sendQueueBuildingPacket.accept(anchorPos, sel.id());
                    }
                    return true;
                }
            }
            // Block item interaction in construction tab
            return true;
        }

        if (activeTab == 0) {
            // Toggle and confirm buttons: left click only
            if (button == 0) {
                int toggleX = leftPos + 151;
                int toggleY = topPos + 107;
                if (mX >= toggleX && mX < toggleX + 18 && mY >= toggleY && mY < toggleY + 18) {
                    if (buyMode) {
                        buyRequest.clear();
                        buyMode = false;
                    } else {
                        returnDepositToPlayer();
                        buyMode = true;
                        buyRequest.clear();
                    }
                    return true;
                }

                int confirmX = leftPos + 151;
                int confirmY = topPos + 89;
                if (mX >= confirmX && mX < confirmX + 18 && mY >= confirmY && mY < confirmY + 18) {
                    if (buyMode && !buyRequest.isEmpty()) {
                        List<C2SBuyPacket.Entry> entries = new ArrayList<>();
                        buyRequest.forEach((item, count) -> {
                            String id = BuiltInRegistries.ITEM.getKey(item).toString();
                            entries.add(new C2SBuyPacket.Entry(id, count));
                        });
                        NetworkHelper.sendBuyPacket.accept(anchorPos, entries);
                        buyRequest.clear();
                    } else if (!buyMode) {
                        NetworkHelper.sendDepositPacket.accept(anchorPos);
                    }
                    return true;
                }
            }

            // BUY mode: unified left/right/shift interactions on chest and blue zone
            if (buyMode && (button == 0 || button == 1)) {
                // Chest slots: add to buy request (capped at 64 per slot, overflow to next slot)
                for (int i = 0; i < TownHubMenu.CHEST_SIZE; i++) {
                    Slot slot = this.menu.slots.get(i);
                    if (slot.hasItem() && isHoveringSlot(slot, mX, mY)) {
                        ItemStack stack = slot.getItem();
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (tradePrices.containsKey(itemId)) {
                            int[] prices  = tradePrices.get(itemId);
                            int qty       = prices[2];
                            int inStock   = stockSnapshot.getOrDefault(itemId, 0);
                            int current   = buyRequest.getOrDefault(stack.getItem(), 0);
                            int add;
                            if (button == 0 && hasShiftDown()) add = (inStock / qty) * qty;
                            else                               add = qty;
                            // Cap by available display slots and stock, then floor to lot boundary
                            int slotsUsedByOthers = expandBuySlots().size() - (int)Math.ceil((double)current / 64);
                            int maxSlots = Math.max(0, TownHubMenu.DEPOSIT_SLOTS - slotsUsedByOthers);
                            int maxCount = (Math.min(inStock, maxSlots * 64) / qty) * qty;
                            int newCount = Math.min(current + add, maxCount);
                            if (newCount > 0) buyRequest.put(stack.getItem(), newCount);
                        }
                        return true;
                    }
                }

                // Blue zone: remove from buy request using the expanded slot view
                int blueZoneX = leftPos + 8;
                int blueZoneY = topPos + 90;
                if (mX >= blueZoneX && mX < blueZoneX + TownHubMenu.DEPOSIT_SLOTS * 18
                        && mY >= blueZoneY && mY < blueZoneY + 18) {
                    int col = (int)(mX - blueZoneX) / 18;
                    List<Map.Entry<Item, Integer>> slots = expandBuySlots();
                    if (col < slots.size()) {
                        Item item = slots.get(col).getKey();
                        int slotCount = slots.get(col).getValue();
                        int totalCurrent = buyRequest.get(item);
                        String rmItemId  = BuiltInRegistries.ITEM.getKey(item).toString();
                        int[] rmPrices   = tradePrices.get(rmItemId);
                        int rmQty        = (rmPrices != null) ? rmPrices[2] : 1;
                        int remove;
                        if (button == 0 && hasShiftDown()) remove = totalCurrent;
                        else                               remove = rmQty;
                        int newCount = totalCurrent - remove;
                        if (newCount <= 0) buyRequest.remove(item);
                        else buyRequest.put(item, newCount);
                    }
                    return true;
                }

                // Block vanilla slot behavior on deposit slots in BUY mode
                int depositStart = TownHubMenu.CHEST_SIZE;
                int depositEnd   = TownHubMenu.CHEST_SIZE + TownHubMenu.DEPOSIT_SLOTS;
                for (int i = depositStart; i < depositEnd; i++) {
                    if (isHoveringSlot(this.menu.slots.get(i), mX, mY)) return true;
                }
            }
        }

        return super.mouseClicked(mX, mY, button);
    }

    @Override
    public boolean mouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (expandedViewOpen && expandedWidget != null) {
            expandedWidget.mouseDragged(mX, mY, button, dX, dY);
            return true;
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w.isDragging()) return w.mouseDragged(mX, mY, button, dX, dY);
        }
        for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
            if (layer1Widgets.get(i).mouseDragged(mX, mY, button, dX, dY)) return true;
        }
        if (activeTab == 1 && constructionPreview != null && constructionPreview.mouseDragged(mX, mY, button, dX, dY)) return true;
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseDragged(mX, mY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mX, double mY, int button) {
        if (expandedViewOpen && expandedWidget != null) {
            expandedWidget.mouseReleased(mX, mY, button);
            return true;
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w.isDragging()) return w.mouseReleased(mX, mY, button);
        }
        for (DraggableWidget w : layer1Widgets) {
            if (w.mouseReleased(mX, mY, button)) return true;
        }
        if (activeTab == 1 && constructionPreview != null && constructionPreview.mouseReleased(mX, mY, button)) return true;
        if (activeTab == 1 || activeTab == 2) return true;
        return super.mouseReleased(mX, mY, button);
    }

    @Override
    public boolean mouseScrolled(double mX, double mY, double delta) {
        if (expandedViewOpen && expandedWidget != null) {
            expandedWidget.mouseScrolled(mX, mY, delta);
            return true;
        }
        for (int i = layer1Widgets.size() - 1; i >= 0; i--) {
            if (layer1Widgets.get(i).mouseScrolled(mX, mY, delta)) return true;
        }
        if (activeTab == 1) {
            if (constructionPreview != null && constructionPreview.mouseScrolled(mX, mY, delta)) return true;
            int totalRows = (buildingCatalog.size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - CATALOG_ROWS);
            catalogScrollOffset = Math.max(0, Math.min(maxOffset,
                catalogScrollOffset - (int) Math.signum(delta)));
            return true;
        }
        if (activeTab == 2) {
            int totalRows = (getUpgradeableBuildings().size() + AVAIL_COLS - 1) / AVAIL_COLS;
            int maxOffset = Math.max(0, totalRows - AVAIL_ROWS);
            upgradeGridScrollOffset = Math.max(0, Math.min(maxOffset,
                upgradeGridScrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mX, mY, delta);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // Rebuilds the have-values of each resource cost row from the local stock snapshot,
    // then pushes updated transitions to the era widget. Called on every stock packet.
    private void refreshEraWidgetFromStock() {
        if (eraWidget == null || eraTransitions.isEmpty()) return;
        List<EraProgressDraggableWidget.EraPathOption> updated = new ArrayList<>();
        for (EraProgressDraggableWidget.EraPathOption opt : eraTransitions) {
            List<EraProgressDraggableWidget.CostRow> updatedCost = new ArrayList<>();
            boolean resourcesMet = true;
            for (EraProgressDraggableWidget.CostRow cr : opt.resourceCost()) {
                int have = stockSnapshot.getOrDefault(cr.itemId(), 0);
                updatedCost.add(new EraProgressDraggableWidget.CostRow(cr.itemId(), cr.amount(), have));
                if (have < cr.amount()) resourcesMet = false;
            }
            boolean buildingsMet = opt.requiredBuildings().stream().allMatch(rb -> rb.have() >= rb.count());
            boolean newPrereqsMet = opt.weightMet() && resourcesMet && opt.residentsMet() && buildingsMet;
            updated.add(new EraProgressDraggableWidget.EraPathOption(
                opt.id(), opt.orientationLabel(), opt.iconItem(),
                newPrereqsMet, opt.requiredWeight(), opt.currentWeight(), opt.maxWeight(), opt.weightMet(),
                updatedCost, opt.requiredResidents(), opt.activeResidents(),
                opt.residentsMet(), opt.requiredBuildings(), opt.unlocked()
            ));
        }
        eraTransitions = updated;
        eraWidget.updateData(currentEra, eraTransitions);
    }

    // Rebuilds the requiredBuildings.have counts from the server-provided building counts map,
    // then pushes updated transitions to the era widget. Called on every building list packet.
    private void refreshEraWidgetFromBuildingCounts(net.minecraft.nbt.CompoundTag counts) {
        if (eraWidget == null || eraTransitions.isEmpty()) return;
        List<EraProgressDraggableWidget.EraPathOption> updated = new ArrayList<>();
        for (EraProgressDraggableWidget.EraPathOption opt : eraTransitions) {
            List<EraProgressDraggableWidget.ReqBuildRow> updatedBuildings = new ArrayList<>();
            boolean buildingsMet = true;
            for (EraProgressDraggableWidget.ReqBuildRow rb : opt.requiredBuildings()) {
                int have = counts.getInt(rb.defId());
                updatedBuildings.add(new EraProgressDraggableWidget.ReqBuildRow(rb.defId(), rb.count(), have));
                if (have < rb.count()) buildingsMet = false;
            }
            boolean resourcesMet = opt.resourceCost().stream().allMatch(cr -> cr.have() >= cr.amount());
            boolean newPrereqsMet = opt.weightMet() && resourcesMet && opt.residentsMet() && buildingsMet;
            updated.add(new EraProgressDraggableWidget.EraPathOption(
                opt.id(), opt.orientationLabel(), opt.iconItem(),
                newPrereqsMet, opt.requiredWeight(), opt.currentWeight(), opt.maxWeight(), opt.weightMet(),
                opt.resourceCost(), opt.requiredResidents(), opt.activeResidents(),
                opt.residentsMet(), updatedBuildings, opt.unlocked()
            ));
        }
        eraTransitions = updated;
        eraWidget.updateData(currentEra, eraTransitions);
    }

    // Rebuilds the currentWeight/maxWeight fields of each path option from the locally
    // tracked weight values, then pushes updated transitions to the era widget.
    // Called when a building list packet arrives (building placed or queued).
    private void refreshEraWidgetFromWeight() {
        if (eraWidget == null || eraTransitions.isEmpty()) return;
        List<EraProgressDraggableWidget.EraPathOption> updated = new ArrayList<>();
        for (EraProgressDraggableWidget.EraPathOption opt : eraTransitions) {
            boolean newWeightMet = currentWeight >= opt.requiredWeight() && currentWeight <= maxWeight;
            boolean resourcesMet = opt.resourceCost().stream().allMatch(cr -> cr.have() >= cr.amount());
            boolean buildingsMet = opt.requiredBuildings().stream().allMatch(rb -> rb.have() >= rb.count());
            boolean newPrereqsMet = newWeightMet && resourcesMet && opt.residentsMet() && buildingsMet;
            updated.add(new EraProgressDraggableWidget.EraPathOption(
                opt.id(), opt.orientationLabel(), opt.iconItem(),
                newPrereqsMet, opt.requiredWeight(), currentWeight, maxWeight, newWeightMet,
                opt.resourceCost(), opt.requiredResidents(), opt.activeResidents(),
                opt.residentsMet(), opt.requiredBuildings(), opt.unlocked()
            ));
        }
        eraTransitions = updated;
        eraWidget.updateData(currentEra, eraTransitions);
    }

    private static List<EraProgressDraggableWidget.EraPathOption> parseEraTransitions(CompoundTag hub) {
        List<EraProgressDraggableWidget.EraPathOption> result = new ArrayList<>();
        int cw = hub.getInt("CurrentWeight");
        int mw = hub.getInt("MaxWeight");
        hub.getList("EraTransitions", Tag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag tt = (CompoundTag) raw;
            String id = tt.getString("Id");
            String orientationLabel = tt.getString("OrientationLabel");
            String iconItem = tt.getString("IconItem");
            boolean prereqsMet = tt.getBoolean("PrereqsMet");
            int reqWeight = tt.getInt("RequiredWeight");
            boolean weightMet = tt.getBoolean("WeightMet");
            List<EraProgressDraggableWidget.CostRow> costRows = new ArrayList<>();
            tt.getList("ResourceCost", Tag.TAG_COMPOUND).forEach(cr -> {
                CompoundTag c = (CompoundTag) cr;
                costRows.add(new EraProgressDraggableWidget.CostRow(
                    c.getString("Item"), c.getInt("Amount"), c.getInt("Have")));
            });
            int reqRes = tt.getInt("RequiredResidents");
            int activeRes = tt.getInt("ActiveResidents");
            boolean resMet = tt.getBoolean("ResidentsMet");
            List<EraProgressDraggableWidget.ReqBuildRow> reqBuilds = new ArrayList<>();
            tt.getList("RequiredBuildings", Tag.TAG_COMPOUND).forEach(rb -> {
                CompoundTag r = (CompoundTag) rb;
                reqBuilds.add(new EraProgressDraggableWidget.ReqBuildRow(
                    r.getString("DefId"), r.getInt("Count"), r.getInt("Have")));
            });
            List<EraProgressDraggableWidget.UnlockEntry> unlocked = new ArrayList<>();
            tt.getList("UnlockedBuildings", Tag.TAG_COMPOUND).forEach(u -> {
                CompoundTag ut = (CompoundTag) u;
                List<EraProgressDraggableWidget.SimpleCost> cost = new ArrayList<>();
                ut.getList("Cost", Tag.TAG_COMPOUND).forEach(cr -> {
                    CompoundTag ct = (CompoundTag) cr;
                    cost.add(new EraProgressDraggableWidget.SimpleCost(ct.getString("Item"), ct.getInt("Amount")));
                });
                List<EraProgressDraggableWidget.SimpleReq> unlockReqBuilds = new ArrayList<>();
                ut.getList("RequiredBuildings", Tag.TAG_COMPOUND).forEach(rb -> {
                    CompoundTag rt = (CompoundTag) rb;
                    unlockReqBuilds.add(new EraProgressDraggableWidget.SimpleReq(rt.getString("DefId"), rt.getInt("Count")));
                });
                unlocked.add(new EraProgressDraggableWidget.UnlockEntry(
                    ut.getString("DefId"), ut.getString("IconItem"), ut.getString("Category"),
                    cost, ut.getInt("RequiredResidents"), unlockReqBuilds, ut.getBoolean("HasProduction")));
            });
            result.add(new EraProgressDraggableWidget.EraPathOption(
                id, orientationLabel, iconItem, prereqsMet, reqWeight, cw, mw, weightMet,
                costRows, reqRes, activeRes, resMet, reqBuilds, unlocked));
        });
        return result;
    }

    private void updateConstructionPreview(BuildingEntry entry) {
        if (constructionPreview == null) {
            constructionPreview = new NbtPreviewWidget(leftPos + 8, topPos + 44, 76, 82);
        }
        if (entry == null) {
            constructionPreview = null;
            return;
        }
        constructionPreview.setStructure(entry.nbtPath());
        constructionPreview.setLocked(entry.nextEra() || !entry.hasBuilt());
    }

    private BuildingEntry findCatalogEntry(String defId) {
        for (BuildingEntry e : buildingCatalog) {
            if (e.id().equals(defId)) return e;
        }
        return null;
    }

    private boolean meetsPrerequisites(BuildingEntry entry) {
        if (entry.requiredResidents() > 0 && activeResidents < entry.requiredResidents()) return false;
        for (ReqBuildingEntry req : entry.requiredBuildings()) {
            if (req.have() < req.required()) return false;
        }
        return true;
    }

    private boolean isAffordable(BuildingEntry entry) {
        for (CostEntry ce : entry.cost()) {
            if (stockSnapshot.getOrDefault(ce.itemId(), 0) < ce.amount()) return false;
        }
        return currentWeight + entry.weight() <= maxWeight;
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case "town_center" -> COLOR_TOWN_CENTER;
            case "jobs"        -> COLOR_JOBS;
            case "gardens"     -> COLOR_GARDENS;
            case "naturals"     -> COLOR_NATURALS;
            case "buildings"   -> COLOR_BUILDINGS;
            default            -> COLOR_UNKNOWN;
        };
    }

    private static int centerX(int freeZoneW, int widgetW) {
        return Math.max(0, (freeZoneW - widgetW) / 2);
    }

    private static int centerY(int screenH, int widgetH) {
        return Math.max(0, (screenH - widgetH) / 2);
    }

    // Returns a dimmed version of an ARGB color (halves RGB channels).
    private static int dim(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) / 2;
        int g = ((argb >> 8) & 0xFF) / 2;
        int b = (argb & 0xFF) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Converts a snake_case id to Title Case for display.
    private static String formatId(String id) {
        if (id == null || id.isEmpty()) return id;
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // Weight bar rendered inside the construction tab, occupying the first chest row.
    // Shows current era, weight fill, and a ghost preview when hovering a catalog slot.
    private void renderWeightBar(GuiGraphics g, int mx, int my) {
        int barX = leftPos + QUEUE_GRID_X - 1;
        int barH = 9;
        int barY = topPos + QUEUE_GRID_Y + (CELL - 7) / 2 - 5;
        int barW = QUEUE_COLS * CELL;

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF111111);

        float fill = maxWeight > 0 ? Math.min(1f, (float) currentWeight / maxWeight) : 0f;
        int fillPx = (int)(barW * fill);
        int fillColor = (currentWeight >= maxWeight) ? 0xFF884400 : 0xFF335533;
        if (fillPx > 0) g.fill(barX, barY, barX + fillPx, barY + barH, fillColor);

        // Ghost preview when hovering a catalog slot (Build tab only)
        if (activeTab == 1 && hoveredCatalogSlot >= 0 && hoveredCatalogSlot < buildingCatalog.size()) {
            BuildingEntry hov = buildingCatalog.get(hoveredCatalogSlot);
            int weightDelta = hov.weight();
            if (weightDelta > 0 && maxWeight > 0) {
                float ghostFrac = (float) weightDelta / maxWeight;
                int ghostPx = (int)(barW * Math.min(1f - fill, ghostFrac));
                if (ghostPx > 0) {
                    // Transparent stripe so the dark background shows through, distinguishing ghost from solid fill
                    int ghostColor = isAffordable(hov) ? 0x2255BB55 : 0x22BB5555;
                    g.fill(barX + fillPx, barY, barX + fillPx + ghostPx, barY + barH, ghostColor);
                }
            }
        }

        String label = "ERA " + currentEra + "  " + currentWeight + "/" + maxWeight;
        g.drawString(font, label, barX + 3, barY + 1, 0xFFCCCCCC, false);
    }

    // Returns true if the mouse is hovering over the given slot (relative to leftPos/topPos).
    private boolean isHoveringSlot(net.minecraft.world.inventory.Slot slot, double mx, double my) {
        int sx = leftPos + slot.x;
        int sy = topPos + slot.y;
        return mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16;
    }

    // Returns all items from the deposit container back to the player (called on SELL->BUY switch).
    private void returnDepositToPlayer() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        var deposit = this.menu.getDepositContainer();
        for (int i = 0; i < deposit.getContainerSize(); i++) {
            ItemStack stack = deposit.getItem(i);
            if (!stack.isEmpty()) {
                mc.player.addItem(stack.copy());
                deposit.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // Overrides vanilla tooltip to inject trade price icons when hovering a tradeable chest slot.
    // Uses this.hoveredSlot (same detection as vanilla) to avoid any zone mismatch.
    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        if (activeTab == 0 && hoveredSlot != null && hoveredSlot.hasItem()
                && hoveredSlot.index < TownHubMenu.CHEST_SIZE) {
            String itemId = BuiltInRegistries.ITEM.getKey(hoveredSlot.getItem().getItem()).toString();
            int[] prices = tradePrices.get(itemId);
            if (prices != null) {
                int buy = prices[0], sell = prices[1], qty = prices[2];
                Item item = hoveredSlot.getItem().getItem();

                String labelBuy  = net.minecraft.network.chat.Component.translatable("onceuponatown.tooltip.trade_buy").getString();
                String labelSell = net.minecraft.network.chat.Component.translatable("onceuponatown.tooltip.trade_sell").getString();
                int labelW = Math.max(font.width(labelBuy), font.width(labelSell));

                int rowH = 18;
                int panW = 4 + labelW + 3 + 16 + 5 + 5 + 16 + 4;
                int panH = 4 + rowH + rowH + 4;
                int panX = mx + 10;
                int panY = my - panH / 2;
                if (panX + panW > this.width)  panX = mx - panW - 4;
                if (panY < 0)                  panY = 0;
                if (panY + panH > this.height) panY = this.height - panH;

                // Vanilla tooltip colors: background 0xF0100010, border gradient 0x505000FF -> 0x5028007F
                g.fill(panX,            panY,            panX + panW,     panY + panH,     0xF0100010);
                g.fill(panX + 1,        panY,            panX + panW - 1, panY + 1,        0x505000FF);
                g.fill(panX + 1,        panY + panH - 1, panX + panW - 1, panY + panH,     0x5028007F);
                g.fill(panX,            panY + 1,        panX + 1,        panY + panH - 1, 0x505000FF);
                g.fill(panX + panW - 1, panY + 1,        panX + panW,     panY + panH - 1, 0x5028007F);

                int labelX = panX + 4;
                int iconX1 = labelX + labelW + 3;
                int slashX = iconX1 + 16 + 2;
                int iconX2 = slashX + 5;

                int row1Y = panY + 4;
                g.drawString(font, labelBuy, labelX, row1Y + 5, 0xFFAAAAAA, false);
                g.renderFakeItem(new ItemStack(item, qty), iconX1, row1Y);
                g.renderItemDecorations(font, new ItemStack(item, qty), iconX1, row1Y);
                g.drawString(font, "/", slashX, row1Y + 5, 0xFFAAAAAA, false);
                g.renderFakeItem(new ItemStack(Items.EMERALD, buy), iconX2, row1Y);
                g.renderItemDecorations(font, new ItemStack(Items.EMERALD, buy), iconX2, row1Y);

                int row2Y = row1Y + rowH;
                g.drawString(font, labelSell, labelX, row2Y + 5, 0xFFAAAAAA, false);
                g.renderFakeItem(new ItemStack(item, qty), iconX1, row2Y);
                g.renderItemDecorations(font, new ItemStack(item, qty), iconX1, row2Y);
                g.drawString(font, "/", slashX, row2Y + 5, 0xFFAAAAAA, false);
                g.renderFakeItem(new ItemStack(Items.EMERALD, sell), iconX2, row2Y);
                g.renderItemDecorations(font, new ItemStack(Items.EMERALD, sell), iconX2, row2Y);
                return;
            }
        }
        super.renderTooltip(g, mx, my);
    }

    // - Buy/sell mode toggle button at (151, 89)
    // - Blue zone content overlay (BUY mode: staged buy request; SELL mode: actual deposit slots)
    // - Emerald cost/reward overlay at Y=107
    // - Confirm arrow at (151, 107)
    private void renderTradeZone(GuiGraphics g, int mx, int my) {
        int blueX = leftPos + 8;
        int blueY = topPos + 90;
        int greenY = topPos + 107;
        int toggleX = leftPos + 151;
        int toggleY = topPos + 89;
        int confirmX = leftPos + 151;
        int confirmY = topPos + 107;

        // Directional arrow blitted from texture: UP (V=1) = sell, DOWN (V=21) = buy
        int arrowV = buyMode ? 21 : 1;
        g.blit(TEXTURE, toggleX, toggleY, 177, arrowV, 18, 18);
        boolean arrowHover = mx >= toggleX && mx < toggleX + 18 && my >= toggleY && my < toggleY + 18;
        if (arrowHover) g.fill(toggleX, toggleY, toggleX + 18, toggleY + 18, 0x30FFFFFF);

        // BUY mode: render buy request split into stacks of 64 across the blue zone slots
        if (buyMode) {
            List<Map.Entry<Item, Integer>> slots = expandBuySlots();
            for (int col = 0; col < TownHubMenu.DEPOSIT_SLOTS && col < slots.size(); col++) {
                int sx = blueX + col * 18;
                ItemStack stack = new ItemStack(slots.get(col).getKey(), slots.get(col).getValue());
                g.renderFakeItem(stack, sx, blueY);
                g.renderItemDecorations(font, stack, sx, blueY);
            }
        }

        // Compute total emerald amount for overlay
        int totalEmeralds = 0;
        if (buyMode) {
            for (Map.Entry<Item, Integer> e : buyRequest.entrySet()) {
                String id = BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
                int[] prices = tradePrices.get(id);
                if (prices != null) {
                    int qty = prices[2];
                    totalEmeralds += prices[0] * (e.getValue() / qty);
                }
            }
        } else {
            // SELL mode: sum sell prices per completed lot in deposit container
            var deposit = this.menu.getDepositContainer();
            for (int i = 0; i < deposit.getContainerSize(); i++) {
                ItemStack stack = deposit.getItem(i);
                if (stack.isEmpty()) continue;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int[] prices = tradePrices.get(id);
                if (prices != null) {
                    int qty = prices[2];
                    totalEmeralds += prices[1] * (stack.getCount() / qty);
                }
            }
        }

        // Green zone: ghost emerald stacks showing cost/reward
        if (totalEmeralds > 0) {
            int remaining = totalEmeralds;
            int col = 0;
            while (remaining > 0 && col < TownHubMenu.DEPOSIT_SLOTS) {
                int stackSize = Math.min(remaining, 64);
                ItemStack emeraldStack = new ItemStack(Items.EMERALD, stackSize);
                int sx = blueX + col * 18;
                g.pose().pushPose();
                g.pose().translate(0, 0, 50);
                // Semi-transparent ghost rendering
                g.fill(sx, greenY, sx + 16, greenY + 16, 0x4400AA00);
                g.renderFakeItem(emeraldStack, sx, greenY);
                g.renderItemDecorations(font, emeraldStack, sx, greenY);
                g.pose().popPose();
                remaining -= stackSize;
                col++;
            }
        }

        // Mode toggle button (B = buy active / S = sell active) - always visible
        boolean toggleBtnHover = mx >= confirmX && mx < confirmX + 18 && my >= confirmY && my < confirmY + 18;
        g.fill(confirmX, confirmY, confirmX + 18, confirmY + 18, toggleBtnHover ? 0xFF555555 : 0xFF333333);
        String modeLabel = buyMode ? "B" : "S";
        g.drawCenteredString(font, modeLabel, confirmX + 9, confirmY + 5, 0xFFFFFFFF);
    }

    // Expands buyRequest into a list of (item, count<=64) pairs, one per visual blue-zone slot.
    private List<Map.Entry<Item, Integer>> expandBuySlots() {
        List<Map.Entry<Item, Integer>> result = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : buyRequest.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int batch = Math.min(remaining, 64);
                result.add(Map.entry(entry.getKey(), batch));
                remaining -= batch;
            }
        }
        return result;
    }

    private boolean hasSellableItems() {
        var deposit = this.menu.getDepositContainer();
        for (int i = 0; i < deposit.getContainerSize(); i++) {
            if (!deposit.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    // 10x10 up-arrow pixel art for the trade zone buttons
    private static void drawUpArrowSmall(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx + 4, by,     bx + 6, by + 1, c);
        g.fill(bx + 3, by + 1, bx + 7, by + 2, c);
        g.fill(bx + 2, by + 2, bx + 8, by + 3, c);
        g.fill(bx + 1, by + 3, bx + 9, by + 4, c);
        g.fill(bx + 4, by + 4, bx + 6, by + 9, c);
    }

    // 10x10 down-arrow pixel art for the trade zone buttons
    private static void drawDownArrowSmall(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx + 4, by,     bx + 6, by + 5, c);
        g.fill(bx + 1, by + 5, bx + 9, by + 6, c);
        g.fill(bx + 2, by + 6, bx + 8, by + 7, c);
        g.fill(bx + 3, by + 7, bx + 7, by + 8, c);
        g.fill(bx + 4, by + 8, bx + 6, by + 9, c);
    }

    private void renderReopenButtons(GuiGraphics g, int mx, int my) {
        if (cachedHubData == null) return;
        int btnX = leftPos - 18;
        int btnY = topPos + imageHeight - 16;
        if (mapClosed) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawHouseIcon(g, btnX + 2, btnY + 3);
            btnY -= 18;
        }
        if (summaryClosed && cachedHubData.contains("SummaryData")) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawInfoIcon(g, btnX + 2, btnY + 2);
            btnY -= 18;
        }
        if (eraClosed) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            drawEraIcon(g, btnX + 2, btnY + 2);
            btnY -= 18;
        }
        if (questHubClosed) {
            boolean hover = mx >= btnX && mx < btnX + 14 && my >= btnY && my < btnY + 14;
            g.fill(btnX, btnY, btnX + 14, btnY + 14, hover ? 0xFF555555 : 0xFF333333);
            g.drawString(Minecraft.getInstance().font, "Q", btnX + 4, btnY + 3, 0xFFFFFFFF, false);
        }
    }

    // Pixel-art house icon (10x7 px)
    private static void drawHouseIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx + 4, by,     bx + 6, by + 1, c); // row 0: roof peak
        g.fill(bx + 3, by + 1, bx + 7, by + 2, c); // row 1
        g.fill(bx + 2, by + 2, bx + 8, by + 3, c); // row 2
        g.fill(bx + 1, by + 3, bx + 9, by + 4, c); // row 3: roof base
        g.fill(bx + 2, by + 4, bx + 4, by + 5, c); // row 4: left wall
        g.fill(bx + 6, by + 4, bx + 8, by + 5, c); // row 4: right wall
        g.fill(bx + 2, by + 5, bx + 4, by + 6, c); // row 5: left wall
        g.fill(bx + 6, by + 5, bx + 8, by + 6, c); // row 5: right wall
        g.fill(bx + 2, by + 6, bx + 8, by + 7, c); // row 6: floor
    }

    // Pixel-art info 'i' icon (2x5 px)
    private static void drawInfoIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx + 2, by,     bx + 8, by + 1, c); // top serif
        g.fill(bx + 4, by + 1, bx + 6, by + 6, c); // stem
        g.fill(bx + 2, by + 6, bx + 8, by + 7, c); // bottom serif
    }

    // Pixel-art star/era icon (5x5 core)
    private static void drawEraIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFDD44;
        g.fill(bx + 4, by,     bx + 6, by + 3, c); // top arm
        g.fill(bx + 4, by + 7, bx + 6, by + 10, c); // bottom arm
        g.fill(bx,     by + 4, bx + 3, by + 6, c); // left arm
        g.fill(bx + 7, by + 4, bx + 10, by + 6, c); // right arm
        g.fill(bx + 3, by + 3, bx + 7, by + 7, c); // center
    }

    // Pixel-art chest icon (10x9 px)
    private static void drawChestIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx,     by,     bx + 10, by + 1,  c); // lid top
        g.fill(bx,     by + 1, bx + 1,  by + 4,  c); // lid left
        g.fill(bx + 9, by + 1, bx + 10, by + 4,  c); // lid right
        g.fill(bx + 1, by + 3, bx + 9,  by + 4,  c); // lid bottom
        g.fill(bx + 4, by + 2, bx + 6,  by + 3,  c); // lid latch
        g.fill(bx,     by + 4, bx + 10, by + 5,  c); // body top
        g.fill(bx,     by + 5, bx + 1,  by + 9,  c); // body left
        g.fill(bx + 9, by + 5, bx + 10, by + 9,  c); // body right
        g.fill(bx,     by + 8, bx + 10, by + 9,  c); // body bottom
        g.fill(bx + 4, by + 6, bx + 6,  by + 7,  c); // body latch
    }

    // Pixel-art up-arrow icon (8x8 px)
    private static void drawUpArrowIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFFFFFFF;
        g.fill(bx + 3, by,     bx + 5, by + 1, c); // tip
        g.fill(bx + 2, by + 1, bx + 6, by + 2, c); // row 1
        g.fill(bx + 1, by + 2, bx + 7, by + 3, c); // row 2
        g.fill(bx,     by + 3, bx + 8, by + 4, c); // arrowhead base
        g.fill(bx + 3, by + 4, bx + 5, by + 8, c); // shaft
    }

    private void openExpandedView() {
        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
        if (sel == null) return;
        expandedPanelSize = Math.min(this.width, this.height) - 60;
        expandedPanelX = (this.width - expandedPanelSize) / 2;
        expandedPanelY = (this.height - expandedPanelSize) / 2 - 15;
        expandedWidget = new NbtPreviewWidget(expandedPanelX, expandedPanelY, expandedPanelSize, expandedPanelSize);
        expandedWidget.setScale(expandedPanelSize / 10.0f);
        int maxUnlocked = getMaxUnlockedForSelected(sel);
        int totalLevels = 1 + sel.nbtLevels().size();
        expandedViewLevel = sel.hasBuilt() ? Math.min(maxUnlocked, totalLevels - 1) : 0;
        applyExpandedLevel(sel);
        expandedViewOpen = true;
    }

    private int getMaxUnlockedForSelected(BuildingEntry sel) {
        if (!sel.hasBuilt()) return 0;
        int max = 0;
        for (UpgradeBuildingEntry u : upgradeBuildingsList) {
            if (u.defId().equals(sel.id())) max = Math.max(max, u.upgradeLevel());
        }
        return max;
    }

    private void applyExpandedLevel(BuildingEntry sel) {
        if (expandedWidget == null) return;
        int maxUnlocked = getMaxUnlockedForSelected(sel);
        boolean locked = !sel.hasBuilt() || expandedViewLevel > maxUnlocked;
        String nbt = (expandedViewLevel == 0 || sel.nbtLevels().isEmpty())
            ? sel.nbtPath()
            : sel.nbtLevels().get(Math.min(expandedViewLevel - 1, sel.nbtLevels().size() - 1));
        expandedWidget.setStructure(nbt);
        expandedWidget.setLocked(locked);
    }

    private void handleExpandedViewClick(double mX, double mY) {
        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
        if (sel == null) return;
        int totalLevels = 1 + sel.nbtLevels().size();
        if (totalLevels > 1) {
            int navY = expandedPanelY + expandedPanelSize + 12;
            int cx = this.width / 2;
            if (mX >= cx - 44 && mX < cx - 28 && mY >= navY && mY < navY + 16 && expandedViewLevel > 0) {
                expandedViewLevel--;
                applyExpandedLevel(sel);
                return;
            }
            if (mX >= cx + 28 && mX < cx + 44 && mY >= navY && mY < navY + 16 && expandedViewLevel < totalLevels - 1) {
                expandedViewLevel++;
                applyExpandedLevel(sel);
                return;
            }
        }
        if (expandedWidget != null) expandedWidget.mouseClicked(mX, mY, 0);
    }

    private void renderExpandedView(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        if (expandedWidget != null) expandedWidget.render(g, mx, my, 0f);

        BuildingEntry sel = selectedCatalogBuildingId != null ? findCatalogEntry(selectedCatalogBuildingId) : null;
        if (sel == null) return;

        int totalLevels = 1 + sel.nbtLevels().size();
        if (totalLevels > 1) {
            int navY = expandedPanelY + expandedPanelSize + 12;
            int cx = this.width / 2;

            boolean leftEnabled = expandedViewLevel > 0;
            boolean leftHover = leftEnabled && mx >= cx - 44 && mx < cx - 28 && my >= navY && my < navY + 16;
            g.fill(cx - 44, navY, cx - 28, navY + 16, leftHover ? 0xFF666666 : (leftEnabled ? 0xFF444444 : 0xFF222222));
            if (leftEnabled) drawLeftArrowIcon(g, cx - 40, navY + 4);

            String levelText = "Level " + (expandedViewLevel + 1) + " / " + totalLevels;
            g.drawCenteredString(font, levelText, cx, navY + 4, 0xFFCCCCCC);

            boolean rightEnabled = expandedViewLevel < totalLevels - 1;
            boolean rightHover = rightEnabled && mx >= cx + 28 && mx < cx + 44 && my >= navY && my < navY + 16;
            g.fill(cx + 28, navY, cx + 44, navY + 16, rightHover ? 0xFF666666 : (rightEnabled ? 0xFF444444 : 0xFF222222));
            if (rightEnabled) drawRightArrowIcon(g, cx + 32, navY + 4);
        }

        g.drawCenteredString(font, "ESC to close", this.width / 2,
            expandedPanelY + expandedPanelSize + (totalLevels > 1 ? 34 : 14), 0xFF666666);
    }

    // Pixel-art expand icon (8x8 px) -- corner brackets pointing outward
    private static void drawExpandIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx,     by,     bx + 3, by + 1, c);
        g.fill(bx,     by,     bx + 1, by + 3, c);
        g.fill(bx + 5, by,     bx + 8, by + 1, c);
        g.fill(bx + 7, by,     bx + 8, by + 3, c);
        g.fill(bx,     by + 7, bx + 3, by + 8, c);
        g.fill(bx,     by + 5, bx + 1, by + 8, c);
        g.fill(bx + 5, by + 7, bx + 8, by + 8, c);
        g.fill(bx + 7, by + 5, bx + 8, by + 8, c);
    }

    // Pixel-art left-arrow icon (8x7 px)
    private static void drawLeftArrowIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx + 5, by,     bx + 6, by + 1, c);
        g.fill(bx + 4, by + 1, bx + 6, by + 2, c);
        g.fill(bx + 3, by + 2, bx + 6, by + 3, c);
        g.fill(bx + 2, by + 3, bx + 6, by + 4, c);
        g.fill(bx + 3, by + 4, bx + 6, by + 5, c);
        g.fill(bx + 4, by + 5, bx + 6, by + 6, c);
        g.fill(bx + 5, by + 6, bx + 6, by + 7, c);
    }

    // Pixel-art right-arrow icon (8x7 px)
    private static void drawRightArrowIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        g.fill(bx + 2, by,     bx + 3, by + 1, c);
        g.fill(bx + 2, by + 1, bx + 4, by + 2, c);
        g.fill(bx + 2, by + 2, bx + 5, by + 3, c);
        g.fill(bx + 2, by + 3, bx + 6, by + 4, c);
        g.fill(bx + 2, by + 4, bx + 5, by + 5, c);
        g.fill(bx + 2, by + 5, bx + 4, by + 6, c);
        g.fill(bx + 2, by + 6, bx + 3, by + 7, c);
    }

    // Pixel-art padlock icon (8x8 px)
    private static void drawPadlockIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        int k = 0xFF111111;
        // Shackle
        g.fill(bx + 2, by,     bx + 6, by + 1, c);
        g.fill(bx + 1, by + 1, bx + 2, by + 4, c);
        g.fill(bx + 6, by + 1, bx + 7, by + 4, c);
        // Body
        g.fill(bx,     by + 4, bx + 8, by + 10, c);
        // Keyhole
        g.fill(bx + 3, by + 5, bx + 5, by + 7, k);
        g.fill(bx + 3, by + 7, bx + 5, by + 9, k);
    }
}
