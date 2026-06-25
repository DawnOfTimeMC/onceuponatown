package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.network.NetworkHelper;

import java.util.ArrayList;
import java.util.List;

public class QuestHubWidget extends DraggableWidget {

    public static final int WIDGET_W  = 175;
    public static final int VISIBLE_H = 200;

    private static final int TITLE_BAR_CARD_H = 12;
    private static final int PAD              = 5;
    private static final int COND_H           = 11;
    private static final int BTN_H            = 12;
    private static final int CARD_GAP         = 4;
    private static final int CARD_MARGIN      = 5;
    private static final int SCROLLBAR_W      = 6;
    private static final int TAB_BTN_W        = 30;
    private static final int TAB_BTN_GAP      = 2;

    private static final int COLOR_MET   = 0xFF55FF55;
    private static final int COLOR_UNMET = 0xFF555555;
    private static final int COLOR_TEXT  = 0xFF55FF55;
    private static final int COLOR_DIM   = 0xFF888888;

    private static final String[] TAB_LABELS = { "Tasks", "Notes" };

    // Session-persistent tab selection: false = Tasks, true = Notes
    static boolean showNotes = false;

    private BlockPos anchorPos = BlockPos.ZERO;

    private record CondRow(String type, String itemId, int required) {
        boolean isMet() {
            if (!"DELIVERY".equals(type)) return true;
            return countInInventory() >= required;
        }

        int countInInventory() {
            if (!"DELIVERY".equals(type) || itemId == null || itemId.isEmpty()) return 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return 0;
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return 0;
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) return 0;
            int count = 0;
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (!s.isEmpty() && s.is(item)) count += s.getCount();
            }
            return count;
        }
    }

    private record QuestRow(
        String questId,
        String questType,
        String titleKey,
        String descKey,
        List<CondRow> conditions
    ) {}

    private final List<QuestRow> taskRows = new ArrayList<>();
    private final List<QuestRow> noteRows = new ArrayList<>();

    private int scrollPx = 0;
    private int totalH   = 0;

    private boolean draggingScrollbar = false;
    private double  dragStartMouseY   = 0;
    private int     dragStartScrollPx = 0;

    // Rebuilt every frame during renderContent: {screenAbsCardY, cardH, rowIndex}
    private final List<int[]> cardBounds = new ArrayList<>();

    public QuestHubWidget(int x, int y, int freeZoneMaxX, int screenH) {
        super(x, y, WIDGET_W, TITLE_BAR_H + VISIBLE_H, freeZoneMaxX, screenH);
    }

    public void setQuests(List<CompoundTag> tags, BlockPos anchor) {
        this.anchorPos = anchor;
        taskRows.clear();
        noteRows.clear();
        for (CompoundTag tag : tags) {
            String rawType   = tag.getString("QuestType");
            String questType = rawType.isEmpty() ? "TASK" : rawType;
            List<CondRow> conds = new ArrayList<>();
            tag.getList("Conditions", Tag.TAG_COMPOUND).forEach(t -> {
                CompoundTag ct = (CompoundTag) t;
                conds.add(new CondRow(
                    ct.getString("Type"), ct.getString("Item"),
                    ct.getInt("Required")
                ));
            });
            QuestRow row = new QuestRow(
                tag.getString("QuestId"),
                questType,
                tag.getString("TitleKey"),
                tag.getString("DescKey"),
                conds
            );
            if ("NOTE".equals(questType)) noteRows.add(row);
            else taskRows.add(row);
        }
        rebuildRows();
        int maxScroll = Math.max(0, totalH - VISIBLE_H);
        if (scrollPx > maxScroll) scrollPx = maxScroll;
    }

    private void rebuildRows() {
        var font = Minecraft.getInstance().font;
        if (font == null) { totalH = 0; return; }
        List<QuestRow> active = showNotes ? noteRows : taskRows;
        totalH = PAD;
        for (int i = 0; i < active.size(); i++) {
            totalH += cardHeight(active.get(i), font);
            if (i < active.size() - 1) totalH += CARD_GAP;
        }
        totalH += PAD;
    }

    private int cardHeight(QuestRow qr, net.minecraft.client.gui.Font font) {
        int descLines = font.split(Component.translatable(qr.descKey()), WIDGET_W - CARD_MARGIN * 2 - 10).size();
        int descH = descLines * 9;
        if ("NOTE".equals(qr.questType())) {
            return TITLE_BAR_CARD_H + PAD + descH + PAD;
        } else {
            return TITLE_BAR_CARD_H + PAD + descH + PAD + qr.conditions().size() * COND_H + PAD + BTN_H + PAD;
        }
    }

    // -------------------------------------------------------------------------
    // Tab bar
    // -------------------------------------------------------------------------

    private int tabBtnX(int index) {
        return x + 1 + index * (TAB_BTN_W + TAB_BTN_GAP);
    }

    @Override
    protected String getTitle() { return ""; }

    @Override
    protected void renderTitleBarExtras(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        boolean[] active = { !showNotes, showNotes };
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
            g.drawString(font, label, bx + (TAB_BTN_W - font.width(label)) / 2, y + 2, fg, false);
        }
    }

    @Override
    protected boolean onTitleBarClick(double mouseX, double mouseY) {
        for (int i = 0; i < 2; i++) {
            int bx = tabBtnX(i);
            if (mouseX >= bx && mouseX < bx + TAB_BTN_W
                    && mouseY >= y && mouseY < y + TITLE_BAR_H) {
                showNotes = (i == 1);
                rebuildRows();
                scrollPx = 0;
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Content rendering
    // -------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                 int mx, int my, float delta) {
        cardBounds.clear();
        g.fill(cx, cy, cx + cw, cy + ch, 0xF51A1A1A);

        boolean hasScroll = totalH > VISIBLE_H;
        int contentW = hasScroll ? WIDGET_W - SCROLLBAR_W : WIDGET_W;

        g.enableScissor(cx, cy, cx + contentW, cy + VISIBLE_H);

        var font = Minecraft.getInstance().font;
        List<QuestRow> active = showNotes ? noteRows : taskRows;

        if (active.isEmpty()) {
            String msg = showNotes ? "No new notices." : "No active tasks.";
            g.drawString(font, msg, cx + (contentW - font.width(msg)) / 2, cy + VISIBLE_H / 2 - 4, 0xFF888888, false);
        } else {
            int cardY = cy + PAD - scrollPx;
            for (int i = 0; i < active.size(); i++) {
                QuestRow qr = active.get(i);
                int cardH = cardHeight(qr, font);
                cardBounds.add(new int[]{ cardY, cardH, i });
                renderCard(g, qr, cx, cardY, contentW, cardH, mx, my, font);
                cardY += cardH + CARD_GAP;
            }
        }

        g.disableScissor();

        if (hasScroll) {
            int maxScroll = totalH - VISIBLE_H;
            int thumbH    = Math.max(12, VISIBLE_H * VISIBLE_H / totalH);
            int thumbY    = cy + (int)((long) scrollPx * (VISIBLE_H - thumbH) / maxScroll);
            int trackX    = cx + WIDGET_W - SCROLLBAR_W;

            g.fill(trackX, cy, cx + WIDGET_W, cy + VISIBLE_H, 0x33FFFFFF);

            boolean thumbHover = !draggingScrollbar
                && mx >= trackX && mx < cx + WIDGET_W
                && my >= thumbY && my < thumbY + thumbH;
            int thumbColor = draggingScrollbar ? 0xFFFFFFFF
                           : thumbHover        ? 0xCCCCCCCC
                           :                    0x99AAAAAA;
            g.fill(trackX, thumbY, cx + WIDGET_W, thumbY + thumbH, thumbColor);
        }
    }

    private void renderCard(GuiGraphics g, QuestRow qr, int cx, int cardY, int contentW,
                            int cardH, int mx, int my, net.minecraft.client.gui.Font font) {
        boolean isNote  = "NOTE".equals(qr.questType());
        int bgColor     = isNote ? 0xEE071420 : 0xEE2A1A0A;
        int borderColor = isNote ? 0xFF3366BB : 0xFFAA7744;
        int headerBg    = isNote ? 0xFF0E2B50 : 0xFF6A3A12;
        int headerFg    = isNote ? 0xFF88CCFF : 0xFFFFEE88;
        int textColor   = isNote ? 0xFFCCEEFF : 0xFFDDCCAA;

        int cxm = cx + CARD_MARGIN;
        int cwm = contentW - CARD_MARGIN * 2;

        g.fill(cxm,              cardY, cxm + cwm,         cardY + cardH, bgColor);
        g.fill(cxm,              cardY, cxm + 1,           cardY + cardH, borderColor);
        g.fill(cxm + cwm - 1, cardY, cxm + cwm,            cardY + cardH, borderColor);
        g.fill(cxm, cardY + cardH - 1, cxm + cwm, cardY + cardH, borderColor);

        g.fill(cxm, cardY, cxm + cwm, cardY + TITLE_BAR_CARD_H, headerBg);

        String title = Component.translatable(qr.titleKey()).getString();
        g.drawString(font, title, cxm + PAD, cardY + 2, headerFg, false);

        // Description lines
        int lineY = cardY + TITLE_BAR_CARD_H + PAD;
        var descLines = font.split(Component.translatable(qr.descKey()), cwm - 10);
        for (var line : descLines) {
            g.drawString(font, line, cxm + PAD, lineY, textColor, false);
            lineY += 9;
        }
        lineY += PAD;

        if (isNote) return;

        // Condition rows: dot + have/required name
        boolean allMet = true;
        int maxTextW = 0;
        for (CondRow cond : qr.conditions()) {
            String text = condText(cond);
            maxTextW = Math.max(maxTextW, font.width(text));
        }
        int blockX = cxm + PAD;

        for (CondRow cond : qr.conditions()) {
            boolean met = cond.isMet();
            if (!met) allMet = false;
            renderCondRow(g, font, blockX, lineY, condText(cond), met);
            lineY += COND_H;
        }
        lineY += PAD;

        // Contribute button: green when all conditions met, gray otherwise
        int btnW = cwm - 10;
        boolean btnHover = allMet && mx >= cxm + PAD && mx < cxm + PAD + btnW
                        && my >= lineY && my < lineY + BTN_H;
        g.fill(cxm + PAD, lineY, cxm + PAD + btnW, lineY + BTN_H,
            allMet ? (btnHover ? 0xFF55BB55 : 0xFF338833) : 0xFF444444);
        String btnText = Component.translatable("onceuponatown.quest.claim").getString();
        int btnTextColor = allMet ? 0xFFFFFFFF : 0xFF666666;
        g.drawString(font, btnText, cxm + PAD + (btnW - font.width(btnText)) / 2, lineY + 2, btnTextColor, false);
    }

    private static String condText(CondRow cond) {
        if ("DELIVERY".equals(cond.type())) {
            int have = Math.min(cond.countInInventory(), cond.required());
            String name = formatId(cond.itemId().contains(":")
                ? cond.itemId().substring(cond.itemId().indexOf(':') + 1) : cond.itemId());
            return have + "/" + cond.required() + " " + name;
        }
        return cond.type();
    }

    private static void renderCondRow(GuiGraphics g, net.minecraft.client.gui.Font font,
                                      int x, int y, String text, boolean met) {
        g.fill(x, y + 2, x + 4, y + 6, met ? COLOR_MET : COLOR_UNMET);
        g.drawString(font, text, x + 7, y, met ? COLOR_TEXT : COLOR_DIM, false);
    }

    // -------------------------------------------------------------------------
    // Mouse interaction
    // -------------------------------------------------------------------------

    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return isMouseOver(mouseX, mouseY);

        int cy = y + TITLE_BAR_H;

        // Scrollbar thumb
        if (totalH > VISIBLE_H) {
            int maxScroll = totalH - VISIBLE_H;
            int thumbH    = Math.max(12, VISIBLE_H * VISIBLE_H / totalH);
            int thumbY    = cy + (int)((long) scrollPx * (VISIBLE_H - thumbH) / maxScroll);
            int trackX    = x + WIDGET_W - SCROLLBAR_W;
            if (mouseX >= trackX && mouseX < x + WIDGET_W
                    && mouseY >= thumbY && mouseY < thumbY + thumbH) {
                draggingScrollbar = true;
                dragStartMouseY   = mouseY;
                dragStartScrollPx = scrollPx;
                return true;
            }
        }

        if (mouseY < cy || mouseY >= cy + VISIBLE_H) return isMouseOver(mouseX, mouseY);

        boolean hasScroll = totalH > VISIBLE_H;
        int contentW = hasScroll ? WIDGET_W - SCROLLBAR_W : WIDGET_W;

        List<QuestRow> active = showNotes ? noteRows : taskRows;
        var font = Minecraft.getInstance().font;

        for (int[] bounds : cardBounds) {
            int cardAbsY = bounds[0];
            int cardH    = bounds[1];
            int rowIdx   = bounds[2];
            if (rowIdx >= active.size()) continue;
            if (mouseY < cardAbsY || mouseY >= cardAbsY + cardH) continue;

            QuestRow qr = active.get(rowIdx);

            if (!"NOTE".equals(qr.questType())) {
                int descLines = font.split(Component.translatable(qr.descKey()), contentW - CARD_MARGIN * 2 - 10).size();
                int btnY = cardAbsY + TITLE_BAR_CARD_H + PAD + descLines * 9 + PAD
                    + qr.conditions().size() * COND_H + PAD;
                int btnW = contentW - CARD_MARGIN * 2 - 10;
                if (mouseX >= x + CARD_MARGIN + PAD && mouseX < x + CARD_MARGIN + PAD + btnW
                        && mouseY >= btnY && mouseY < btnY + BTN_H) {
                    boolean allMet = qr.conditions().stream().allMatch(CondRow::isMet);
                    if (allMet) NetworkHelper.sendContributeQuestPacket.accept(anchorPos, qr.questId());
                    return true;
                }
            }

            return isMouseOver(mouseX, mouseY);
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean contentMouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (draggingScrollbar && button == 0) {
            int maxScroll = Math.max(0, totalH - VISIBLE_H);
            int thumbH    = Math.max(12, VISIBLE_H * VISIBLE_H / totalH);
            double trackRange = VISIBLE_H - thumbH;
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
}
