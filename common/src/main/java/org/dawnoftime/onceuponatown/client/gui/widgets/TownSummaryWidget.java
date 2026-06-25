package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.dawnoftime.onceuponatown.town.TownLogEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TownSummaryWidget extends DraggableWidget {

    public static final int WIDGET_W  = 175;
    public static final int VISIBLE_H = 180;

    private static final int HEADER_H    = 12;
    private static final int ITEM_H      = 16;
    private static final int CELL_SIZE   = 18;
    private static final int MAX_COLS    = 7;
    private static final int PADDING     = 3;
    private static final int SCROLLBAR_W  = 6;
    private static final int TAB_BTN_W   = 26;
    private static final int TAB_BTN_GAP = 2;
    private static final int BELL_BTN_W  = 10;

    private static final int COLOR_HEADER_BG   = 0xF52A2A2A;
    private static final int COLOR_SECTION_TEXT = 0xFFAAAAFF;
    private static final int COLOR_ORIENT_TEXT  = 0xFFEECC66;
    private static final int COLOR_CONTENT_BG   = 0xF51A1A1A;

    private static final String[] TAB_LABELS = {"Info", "Log"};

    // Active tab (session-only): false = Info, true = Log
    static boolean showLogs = false;
    // Chat broadcast toggle - set from server on hub open, toggled locally on click
    public static boolean chatBroadcastEnabled = false;

    private Runnable onBroadcastToggled = null;

    // Activity log entries: newest first (index 0 = most recent)
    private final ArrayDeque<TownLogEntry> logEntries = new ArrayDeque<>();

    private enum RowType { ITEM, SECTION_HEADER, PROD_GRID, TRANSFORM_GRID }
    private record Row(ItemStack icon, Component text, RowType type) {
        boolean isSection() { return type == RowType.SECTION_HEADER; }
    }

    // Grid cells for production and transformation sections
    private record GridCell(ItemStack stack, boolean locked) {}

    private final List<Row>      rows            = new ArrayList<>();
    private final List<GridCell> productionCells = new ArrayList<>();
    private final List<GridCell> transformCells  = new ArrayList<>();

    private int scrollPx = 0;
    private int totalH   = 0;

    // Scrollbar drag state
    private boolean draggingScrollbar = false;
    private double  dragStartMouseY   = 0;
    private int     dragStartScrollPx = 0;

    private CompoundTag cachedMapData;
    private CompoundTag cachedSummaryData;

    public TownSummaryWidget(CompoundTag mapData, CompoundTag summaryData,
                              int x, int y, int freeZoneMaxX, int screenH) {
        super(x, y, WIDGET_W, TITLE_BAR_H + VISIBLE_H, freeZoneMaxX, screenH);
        this.cachedMapData = mapData;
        this.cachedSummaryData = summaryData;
        buildRows(mapData, summaryData);
    }

    public void updateCitizenData(int totalResidents, int activeResidents, int totalFoodDemand,
                                   int totalHerd, int activeHerd) {
        cachedSummaryData.putInt("TotalResidents", totalResidents);
        cachedSummaryData.putInt("ActiveResidents", activeResidents);
        cachedSummaryData.putInt("TotalFoodDemand", totalFoodDemand);
        cachedSummaryData.putInt("TotalHerd", totalHerd);
        cachedSummaryData.putInt("ActiveHerd", activeHerd);
        rows.clear();
        buildRows(cachedMapData, cachedSummaryData);
    }

    // -------------------------------------------------------------------------
    // Title bar tab buttons (Info / Log)
    // -------------------------------------------------------------------------

    public void setOnBroadcastToggled(Runnable r) { onBroadcastToggled = r; }

    private int tabBtnX(int index) {
        return x + 1 + index * (TAB_BTN_W + TAB_BTN_GAP);
    }

    private int bellBtnX() {
        return x + 1 + 2 * (TAB_BTN_W + TAB_BTN_GAP);
    }

    @Override
    protected String getTitle() { return ""; }

    @Override
    protected void renderTitleBarExtras(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        boolean[] active = {!showLogs, showLogs};
        for (int i = 0; i < 2; i++) {
            int bx = tabBtnX(i);
            boolean hover = mouseX >= bx && mouseX < bx + TAB_BTN_W
                         && mouseY >= y  && mouseY < y + TITLE_BAR_H;
            boolean isActive = active[i];
            int bg = isActive ? (hover ? 0xFF2A5A2A : 0xFF224422)
                              : (hover ? 0xFF444444 : 0xFF333333);
            int fg = isActive ? 0xFFAAFFAA : 0xFF888888;
            g.fill(bx, y + 1, bx + TAB_BTN_W, y + TITLE_BAR_H - 1, bg);
            String label = TAB_LABELS[i];
            int textX = bx + (TAB_BTN_W - font.width(label)) / 2;
            g.drawString(font, label, textX, y + 2, fg, false);
        }

        // Bell button (chat broadcast toggle)
        int bbx = bellBtnX();
        boolean bellHover = mouseX >= bbx && mouseX < bbx + BELL_BTN_W
                         && mouseY >= y   && mouseY < y + TITLE_BAR_H;
        int bellBg = chatBroadcastEnabled ? (bellHover ? 0xFF5A4A00 : 0xFF443300)
                                          : (bellHover ? 0xFF444444 : 0xFF333333);
        g.fill(bbx, y + 1, bbx + BELL_BTN_W, y + TITLE_BAR_H - 1, bellBg);
        int bellColor = chatBroadcastEnabled ? 0xFFFFAA00 : 0xFF666666;
        drawBellIcon(g, bbx, bellColor);
    }

    private void drawBellIcon(GuiGraphics g, int bx, int color) {
        int ix = bx + 2;
        int iy = y + 2;
        // top knob (1x1)
        g.fill(ix + 2, iy,     ix + 3, iy + 1, color);
        // bell cap (3 wide)
        g.fill(ix + 1, iy + 1, ix + 4, iy + 2, color);
        // bell sides (left and right, 3 tall)
        g.fill(ix,     iy + 2, ix + 1, iy + 5, color);
        g.fill(ix + 4, iy + 2, ix + 5, iy + 5, color);
        // bell rim (5 wide)
        g.fill(ix,     iy + 5, ix + 5, iy + 6, color);
        // clapper (1x1)
        g.fill(ix + 2, iy + 7, ix + 3, iy + 8, color);
    }

    @Override
    protected boolean onTitleBarClick(double mouseX, double mouseY) {
        for (int i = 0; i < 2; i++) {
            int bx = tabBtnX(i);
            if (mouseX >= bx && mouseX < bx + TAB_BTN_W
                    && mouseY >= y && mouseY < y + TITLE_BAR_H) {
                showLogs = (i == 1);
                rows.clear();
                buildRows(cachedMapData, cachedSummaryData);
                scrollPx = 0;
                return true;
            }
        }
        int bbx = bellBtnX();
        if (mouseX >= bbx && mouseX < bbx + BELL_BTN_W
                && mouseY >= y && mouseY < y + TITLE_BAR_H) {
            chatBroadcastEnabled = !chatBroadcastEnabled;
            if (onBroadcastToggled != null) onBroadcastToggled.run();
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Log entry management
    // -------------------------------------------------------------------------

    public void appendLogEntry(TownLogEntry entry) {
        logEntries.addFirst(entry);
        if (logEntries.size() > 20) logEntries.removeLast();
        if (showLogs) {
            rows.clear();
            buildRows(cachedMapData, cachedSummaryData);
        }
    }

    // Loads the initial log snapshot (oldest to newest order) received from the hub packet.
    public void loadInitialLog(List<TownLogEntry> entries) {
        logEntries.clear();
        // entries is oldest-first from the server; insert newest-first into the deque
        for (int i = entries.size() - 1; i >= 0; i--) {
            logEntries.addLast(entries.get(i));
        }
        if (showLogs) {
            rows.clear();
            buildRows(cachedMapData, cachedSummaryData);
        }
    }

    private static int logColor(TownLogEntry.TownLogType type) {
        return switch (type) {
            case BUILD_START, UPGRADE_START -> 0xFFAAAAFF;
            case BUILD_DONE, UPGRADE_DONE   -> 0xFF55FF55;
            case FOOD_CONSUMED              -> 0xFFDDDDDD;
            case VILLAGE_FULL               -> 0xFFFF5555;
        };
    }

    private Component logEntryToComponent(TownLogEntry entry) {
        String param = entry.param();
        String resolved = param.isEmpty() ? "" : Component.translatable("onceuponatown.building." + param).getString();
        String text = switch (entry.type()) {
            case BUILD_START    -> "Builder: starting " + resolved;
            case BUILD_DONE     -> "Builder: " + resolved + " built";
            case UPGRADE_START  -> "Builder: upgrading " + resolved;
            case UPGRADE_DONE   -> "Builder: " + resolved + " upgraded";
            case FOOD_CONSUMED  -> "Village consumed " + param + " food units";
            case VILLAGE_FULL   -> "Village: no space left to expand";
        };
        int color = logColor(entry.type());
        return Component.literal(text).withStyle(s -> s.withColor(color));
    }

    // -------------------------------------------------------------------------
    // Row building
    // -------------------------------------------------------------------------

    private void buildRows(CompoundTag mapData, CompoundTag summaryData) {
        productionCells.clear();
        transformCells.clear();

        if (showLogs) {
            if (logEntries.isEmpty()) {
                rows.add(new Row(null, Component.literal("No activity yet.").withStyle(s -> s.withColor(0xFF888888)), RowType.ITEM));
            } else {
                for (TownLogEntry e : logEntries) {
                    rows.add(new Row(null, logEntryToComponent(e), RowType.ITEM));
                }
            }
            totalH = PADDING;
            for (Row r : rows) totalH += rowHeight(r);
            totalH += PADDING;
            return;
        }

        String orientation  = summaryData.getString("Orientation");
        int totalResidents  = summaryData.getInt("TotalResidents");
        int activeResidents = summaryData.getInt("ActiveResidents");
        int totalFoodDemand = summaryData.getInt("TotalFoodDemand");
        int totalHerd       = summaryData.getInt("TotalHerd");
        int activeHerd      = summaryData.getInt("ActiveHerd");

        rows.add(new Row(null,
            Component.literal("Orientation: " + capitalize(orientation))
                .withStyle(s -> s.withColor(COLOR_ORIENT_TEXT)),
            RowType.ITEM));

        if (totalResidents > 0) {
            int resColor = activeResidents >= totalResidents ? 0xFF55FF55
                         : activeResidents * 2 >= totalResidents ? 0xFFFFAA00
                         : 0xFFFF5555;
            Item egg = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:villager_spawn_egg"));
            MutableComponent txt = Component.literal("Residents: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(String.valueOf(activeResidents)).withStyle(s -> s.withColor(resColor)))
                .append(Component.literal(" / " + totalResidents).withStyle(s -> s.withColor(0xFFCCCCCC)));
            rows.add(new Row(new ItemStack(egg), txt, RowType.ITEM));
        }

        if (totalHerd > 0) {
            int herdColor = activeHerd >= totalHerd ? 0xFF55FF55
                          : activeHerd * 2 >= totalHerd ? 0xFFFFAA00
                          : 0xFFFF5555;
            Item pig = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:pig_spawn_egg"));
            MutableComponent txt = Component.literal("Herd: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(String.valueOf(activeHerd)).withStyle(s -> s.withColor(herdColor)))
                .append(Component.literal(" / " + totalHerd).withStyle(s -> s.withColor(0xFFCCCCCC)));
            rows.add(new Row(new ItemStack(pig), txt, RowType.ITEM));
        }

        if (totalFoodDemand > 0) {
            rows.add(new Row(new ItemStack(Items.BREAD),
                Component.literal(totalFoodDemand + " food units / day").withStyle(s -> s.withColor(0xFFDDDDDD)),
                RowType.ITEM));
        }

        Map<String, int[]> prodData         = new LinkedHashMap<>();
        Map<String, int[]> lockedProdData   = new LinkedHashMap<>();
        Map<String, int[]> transforms       = new LinkedHashMap<>();
        Map<String, int[]> lockedTransforms = new LinkedHashMap<>();

        ListTag elements = mapData.getList("Elements", Tag.TAG_COMPOUND);
        for (Tag rawEl : elements) {
            CompoundTag el = (CompoundTag) rawEl;
            if (el.getByte("Category") != 2) continue;

            for (Tag rawP : el.getList("Production", Tag.TAG_COMPOUND)) {
                CompoundTag pt = (CompoundTag) rawP;
                String itemId  = pt.getString("Item");
                int amount     = pt.getInt("Amount");
                int ticks      = pt.getInt("EveryTicks");
                boolean locked = pt.getBoolean("Locked");
                if (locked) {
                    lockedProdData.merge(itemId, new int[]{amount, ticks},
                        (a, b) -> new int[]{a[0] + b[0], a[1]});
                } else {
                    prodData.merge(itemId, new int[]{amount, ticks},
                        (a, b) -> new int[]{a[0] + b[0], a[1]});
                }
            }

            for (Tag rawT : el.getList("Transforms", Tag.TAG_COMPOUND)) {
                CompoundTag tt = (CompoundTag) rawT;
                String outId   = tt.getString("OutputItem");
                boolean locked = tt.getBoolean("Locked");
                if (locked) {
                    if (!lockedTransforms.containsKey(outId)) {
                        lockedTransforms.put(outId, new int[]{tt.getInt("OutputAmount"), tt.getInt("EveryTicks")});
                    }
                } else {
                    if (!transforms.containsKey(outId)) {
                        transforms.put(outId, new int[]{tt.getInt("OutputAmount"), tt.getInt("EveryTicks")});
                    }
                }
            }
        }

        if (!prodData.isEmpty() || !lockedProdData.isEmpty()) {
            rows.add(new Row(null, Component.literal("Produces / min").withStyle(s -> s.withColor(COLOR_SECTION_TEXT)), RowType.SECTION_HEADER));
            rows.add(new Row(null, null, RowType.PROD_GRID));
            for (Map.Entry<String, int[]> e : prodData.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int displayCount = perMinCount(e.getValue()[0], e.getValue()[1]);
                productionCells.add(new GridCell(new ItemStack(item, displayCount), false));
            }
            for (Map.Entry<String, int[]> e : lockedProdData.entrySet()) {
                if (prodData.containsKey(e.getKey())) continue;
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int displayCount = perMinCount(e.getValue()[0], e.getValue()[1]);
                productionCells.add(new GridCell(new ItemStack(item, displayCount), true));
            }
        }

        if (!transforms.isEmpty() || !lockedTransforms.isEmpty()) {
            rows.add(new Row(null, Component.literal("Transforms / min").withStyle(s -> s.withColor(COLOR_SECTION_TEXT)), RowType.SECTION_HEADER));
            rows.add(new Row(null, null, RowType.TRANSFORM_GRID));
            for (Map.Entry<String, int[]> e : transforms.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int displayCount = perMinCount(e.getValue()[0], e.getValue()[1]);
                transformCells.add(new GridCell(new ItemStack(item, displayCount), false));
            }
            for (Map.Entry<String, int[]> e : lockedTransforms.entrySet()) {
                if (transforms.containsKey(e.getKey())) continue;
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int displayCount = perMinCount(e.getValue()[0], e.getValue()[1]);
                transformCells.add(new GridCell(new ItemStack(item, displayCount), true));
            }
        }

        totalH = PADDING;
        for (Row r : rows) totalH += rowHeight(r);
        totalH += PADDING;
    }

    // Converts amount+ticks to per-minute integer, minimum 1.
    private static int perMinCount(int amount, int ticks) {
        if (ticks <= 0) return amount;
        float perMin = amount * (1200.0f / ticks);
        return Math.max(1, Math.round(perMin));
    }

    private int rowHeight(Row r) {
        return switch (r.type()) {
            case SECTION_HEADER -> HEADER_H;
            case PROD_GRID      -> gridHeight(productionCells.size());
            case TRANSFORM_GRID -> gridHeight(transformCells.size());
            case ITEM           -> ITEM_H;
        };
    }

    private static int gridHeight(int cellCount) {
        if (cellCount == 0) return 0;
        return (int) Math.ceil(cellCount / (double) MAX_COLS) * CELL_SIZE;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                  int mx, int my, float delta) {
        g.fill(cx, cy, cx + cw, cy + ch, COLOR_CONTENT_BG);

        boolean hasScroll = totalH > ch;
        int contentW = hasScroll ? cw - SCROLLBAR_W : cw;

        g.enableScissor(cx, cy, cx + contentW, cy + ch);

        var font = Minecraft.getInstance().font;
        int rowY = cy + PADDING - scrollPx;

        for (Row row : rows) {
            int rh = rowHeight(row);
            if (rowY + rh > cy && rowY < cy + ch) {
                switch (row.type()) {
                    case SECTION_HEADER -> {
                        g.fill(cx, rowY, cx + contentW, rowY + rh, COLOR_HEADER_BG);
                        g.drawString(font, row.text(), cx + 4, rowY + 2, 0xFFFFFFFF, false);
                    }
                    case PROD_GRID      -> renderGrid(g, productionCells, cx, rowY, contentW);
                    case TRANSFORM_GRID -> renderGrid(g, transformCells,  cx, rowY, contentW);
                    case ITEM           -> {
                        if (row.icon() != null && !row.icon().isEmpty()) {
                            g.renderFakeItem(row.icon(), cx + 2, rowY);
                            g.drawString(font, row.text(), cx + 20, rowY + 4, 0xFFFFFFFF, false);
                        } else {
                            g.drawString(font, row.text(), cx + 4, rowY + 2, 0xFFFFFFFF, false);
                        }
                    }
                }
            }
            rowY += rh;
        }

        g.disableScissor();

        if (hasScroll) {
            int maxScroll = totalH - ch;
            int thumbH    = Math.max(12, ch * ch / totalH);
            int thumbY    = cy + (int)((long) scrollPx * (ch - thumbH) / maxScroll);
            int trackX    = cx + cw - SCROLLBAR_W;

            g.fill(trackX, cy, cx + cw, cy + ch, 0x33FFFFFF);

            boolean thumbHover = !draggingScrollbar
                && mx >= trackX && mx < cx + cw
                && my >= thumbY && my < thumbY + thumbH;
            int thumbColor = draggingScrollbar ? 0xFFFFFFFF
                           : thumbHover        ? 0xCCCCCCCC
                           :                    0x99AAAAAA;
            g.fill(trackX, thumbY, cx + cw, thumbY + thumbH, thumbColor);
        }
    }

    private void renderGrid(GuiGraphics g, List<GridCell> cells, int cx, int rowY, int contentW) {
        for (int i = 0; i < cells.size(); i++) {
            int col   = i % MAX_COLS;
            int row   = i / MAX_COLS;
            int cellX = cx + col * CELL_SIZE;
            int cellY = rowY + row * CELL_SIZE;
            GridCell cell = cells.get(i);
            g.renderFakeItem(cell.stack(), cellX + 1, cellY + 1);
            g.renderItemDecorations(Minecraft.getInstance().font, cell.stack(), cellX + 1, cellY + 1);
            if (cell.locked()) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                NbtPreviewWidget.drawPadlockIcon(g, cellX + 5, cellY + 4);
                g.pose().popPose();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scrollbar interaction
    // -------------------------------------------------------------------------

    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && totalH > VISIBLE_H) {
            int cx = x;
            int cy = y + TITLE_BAR_H;
            int cw = width;
            int ch = VISIBLE_H;
            int maxScroll = totalH - ch;
            int thumbH    = Math.max(12, ch * ch / totalH);
            int thumbY    = cy + (int)((long) scrollPx * (ch - thumbH) / maxScroll);
            int trackX    = cx + cw - SCROLLBAR_W;

            if (mouseX >= trackX && mouseX < cx + cw
                    && mouseY >= thumbY && mouseY < thumbY + thumbH) {
                draggingScrollbar = true;
                dragStartMouseY   = mouseY;
                dragStartScrollPx = scrollPx;
                return true;
            }
        }
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean contentMouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (draggingScrollbar && button == 0) {
            int ch        = VISIBLE_H;
            int maxScroll = Math.max(0, totalH - ch);
            int thumbH    = Math.max(12, ch * ch / totalH);
            double trackRange = ch - thumbH;
            if (trackRange > 0) {
                double delta = mY - dragStartMouseY;
                scrollPx = (int) Math.max(0, Math.min(maxScroll,
                    dragStartScrollPx + delta * maxScroll / trackRange));
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean contentMouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean contentMouseScrolled(double mx, double my, double delta) {
        if (!isMouseOver(mx, my)) return false;
        int maxScroll = Math.max(0, totalH - VISIBLE_H);
        scrollPx = Math.max(0, Math.min(maxScroll, scrollPx - (int)(delta * 10)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
