package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EraProgressDraggableWidget extends DraggableWidget {

    private static final int COLOR_MET         = 0xFF55CC55;
    private static final int COLOR_UNMET       = 0xFF555555;
    private static final int COLOR_TEXT        = 0xFFCCCCCC;
    private static final int COLOR_DIM         = 0xFF888888;
    private static final int COLOR_CARD_BG     = 0xFF1A1A1A;
    private static final int COLOR_CARD_SEL    = 0xFF223322;
    private static final int COLOR_CARD_BORDER = 0xFF55AA55;
    private static final int CARD_PADDING      = 8;
    private static final int BTN_H             = 12;
    private static final int ADVANCE_BTN_H     = 14;
    private static final int ADVANCE_BTN_GAP   = 6;
    private static final int GAP_BETWEEN_CARDS = 6;
    private static final int OUTER_PADDING     = 4;

    public record CostRow(String itemId, int amount, int have) {}
    public record ReqBuildRow(String defId, int count, int have) {}
    public record SimpleCost(String itemId, int amount) {}
    public record SimpleReq(String defId, int count) {}

    public record UnlockEntry(
        String defId, String iconItem, String category,
        List<SimpleCost> cost, int requiredResidents,
        List<SimpleReq> requiredBuildings, boolean hasProduction
    ) {
        public String buildingName() { return formatBuildingId(defId); }
    }

    public record EraPathOption(
        String id,
        String orientationLabel,
        String iconItem,
        boolean prereqsMet,
        int requiredWeight,
        int currentWeight,
        int maxWeight,
        boolean weightMet,
        List<CostRow> resourceCost,
        int requiredResidents,
        int activeResidents,
        boolean residentsMet,
        List<ReqBuildRow> requiredBuildings,
        List<UnlockEntry> unlocked
    ) {}

    private int currentEra;
    private List<EraPathOption> pathOptions = new ArrayList<>();
    private String selectedPathId = null;
    private final Consumer<String> onAdvance;

    private int contentHeight = 0;
    private final List<CardBounds> cardBounds = new ArrayList<>();
    private final List<int[]> selectBtnBounds = new ArrayList<>();
    private int[] advanceBtnBounds = null;

    private record CardBounds(int x, int y, int w, int h, int cardIndex) {}

    public EraProgressDraggableWidget(int x, int y, int freeZoneMaxX, int screenH,
                                       int currentEra, List<EraPathOption> pathOptions,
                                       Consumer<String> onAdvance) {
        super(x, y, computeWidgetW(pathOptions), TITLE_BAR_H + computeContentH(pathOptions), freeZoneMaxX, screenH);
        this.currentEra = currentEra;
        this.pathOptions = new ArrayList<>(pathOptions);
        this.onAdvance = onAdvance;
        this.contentHeight = computeContentH(pathOptions);
    }

    public void updateData(int currentEra, List<EraPathOption> pathOptions) {
        this.currentEra = currentEra;
        this.pathOptions = new ArrayList<>(pathOptions);
        this.contentHeight = computeContentH(pathOptions);
        this.width = computeWidgetW(pathOptions);
        setHeight(TITLE_BAR_H + this.contentHeight);
        if (selectedPathId != null && pathOptions.stream().noneMatch(p -> p.id().equals(selectedPathId))) {
            selectedPathId = null;
        }
    }

    public void updateResidents(int activeResidents) {
        List<EraPathOption> updated = new ArrayList<>();
        for (EraPathOption opt : pathOptions) {
            boolean newResidentsMet = opt.requiredResidents() <= 0 || activeResidents >= opt.requiredResidents();
            boolean resourcesMet = opt.resourceCost().stream().allMatch(cr -> cr.have() >= cr.amount());
            boolean buildingsMet = opt.requiredBuildings().stream().allMatch(rb -> rb.have() >= rb.count());
            boolean newPrereqsMet = opt.weightMet() && resourcesMet && newResidentsMet && buildingsMet;
            updated.add(new EraPathOption(
                opt.id(), opt.orientationLabel(), opt.iconItem(),
                newPrereqsMet, opt.requiredWeight(), opt.currentWeight(), opt.maxWeight(), opt.weightMet(),
                opt.resourceCost(), opt.requiredResidents(), activeResidents,
                newResidentsMet, opt.requiredBuildings(), opt.unlocked()
            ));
        }
        this.pathOptions = updated;
    }

    // -------------------------------------------------------------------------
    // Layout computation
    // -------------------------------------------------------------------------

    private static int maxCondTextW(EraPathOption opt) {
        var font = Minecraft.getInstance().font;
        int max = font.width(opt.currentWeight() + "/" + opt.maxWeight() + " weight");
        for (CostRow cr : opt.resourceCost())
            max = Math.max(max, font.width(cr.have() + "/" + cr.amount() + " " + formatItemId(cr.itemId())));
        if (opt.requiredResidents() > 0)
            max = Math.max(max, font.width(opt.activeResidents() + "/" + opt.requiredResidents() + " residents"));
        for (ReqBuildRow rb : opt.requiredBuildings())
            max = Math.max(max, font.width(rb.have() + "/" + rb.count() + " " + formatBuildingId(rb.defId())));
        return max;
    }

    private static int computeCardW(EraPathOption opt) {
        var font = Minecraft.getInstance().font;
        int headerW = 18 + font.width(opt.orientationLabel()); // icon(16) + gap(2) + label
        int condBlockW = 4 + 3 + maxCondTextW(opt);           // square + gap + longest text
        return Math.max(headerW, condBlockW) + CARD_PADDING * 2;
    }

    public static int computeWidgetW(List<EraPathOption> options) {
        if (options.isEmpty()) return 160;
        int total = OUTER_PADDING * 2;
        for (int i = 0; i < options.size(); i++) {
            total += computeCardW(options.get(i));
            if (i < options.size() - 1) total += GAP_BETWEEN_CARDS;
        }
        var font = Minecraft.getInstance().font;
        int minForTitle = font.width("Era Progress") + 40;
        return Math.max(total, minForTitle);
    }

    private static int computeCardH(EraPathOption opt) {
        int h = CARD_PADDING;
        h += 10 + 8; // icon+name row + gap
        h += 11;     // weight row
        h += opt.resourceCost().size() * 11;
        if (opt.requiredResidents() > 0) h += 11;
        h += opt.requiredBuildings().size() * 11;
        h += 5 + BTN_H;
        h += CARD_PADDING;
        return h;
    }

    private static int computeContentH(List<EraPathOption> options) {
        if (options.isEmpty()) return 30;
        int maxCardH = options.stream().mapToInt(EraProgressDraggableWidget::computeCardH).max().orElse(60);
        return OUTER_PADDING + maxCardH + ADVANCE_BTN_GAP + ADVANCE_BTN_H + OUTER_PADDING;
    }

    // -------------------------------------------------------------------------
    // Titlebar: Advance Era button is in the content area
    // -------------------------------------------------------------------------

    @Override
    protected void renderTitleBarExtras(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    protected boolean onTitleBarClick(double mouseX, double mouseY) {
        return false;
    }

    private boolean getActivePrereqsMet() {
        if (pathOptions.size() == 1) return pathOptions.get(0).prereqsMet();
        if (selectedPathId != null) {
            return pathOptions.stream()
                .filter(p -> p.id().equals(selectedPathId))
                .findFirst()
                .map(EraPathOption::prereqsMet)
                .orElse(false);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Content rendering
    // -------------------------------------------------------------------------

    @Override
    protected String getTitle() { return "Era Progress"; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                  int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        g.fill(cx, cy, cx + cw, cy + ch, 0xF5111111);

        cardBounds.clear();
        selectBtnBounds.clear();
        advanceBtnBounds = null;

        if (pathOptions.isEmpty()) {
            g.drawString(font, "No transitions available", cx + OUTER_PADDING, cy + 10, COLOR_DIM, false);
            return;
        }

        int maxCardH = pathOptions.stream().mapToInt(EraProgressDraggableWidget::computeCardH).max().orElse(60);
        boolean multiPath = pathOptions.size() > 1;

        int xCursor = cx + OUTER_PADDING;
        for (int ci = 0; ci < pathOptions.size(); ci++) {
            EraPathOption opt = pathOptions.get(ci);
            int cardW = computeCardW(opt);
            boolean selected = opt.id().equals(selectedPathId);
            renderCard(g, font, xCursor, cy + OUTER_PADDING, cardW, maxCardH, mx, my, opt, ci, selected, multiPath);
            xCursor += cardW + GAP_BETWEEN_CARDS;
        }

        // Advance Era button below the cards
        int advBtnY = cy + OUTER_PADDING + maxCardH + ADVANCE_BTN_GAP;
        int advBtnW = cw - OUTER_PADDING * 2;
        int advBtnX = cx + OUTER_PADDING;
        boolean canAdvance = getActivePrereqsMet();
        boolean advHover = canAdvance && mx >= advBtnX && mx < advBtnX + advBtnW
                && my >= advBtnY && my < advBtnY + ADVANCE_BTN_H;
        int advBgColor = canAdvance ? (advHover ? 0xFF338833 : 0xFF225522) : 0xFF222222;
        g.fill(advBtnX, advBtnY, advBtnX + advBtnW, advBtnY + ADVANCE_BTN_H, advBgColor);
        String advText = "Advance Era";
        g.drawString(font, advText,
                advBtnX + (advBtnW - font.width(advText)) / 2,
                advBtnY + (ADVANCE_BTN_H - 8) / 2,
                canAdvance ? 0xFF88FF88 : 0xFF555555, false);
        advanceBtnBounds = new int[]{ advBtnX, advBtnY, advBtnW, ADVANCE_BTN_H };
    }

    private void renderCard(GuiGraphics g, net.minecraft.client.gui.Font font,
                             int cx, int cy, int cw, int fixedH,
                             int mx, int my, EraPathOption opt, int cardIdx,
                             boolean selected, boolean selectable) {
        boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + fixedH;
        g.fill(cx, cy, cx + cw, cy + fixedH, selected ? COLOR_CARD_SEL : COLOR_CARD_BG);
        if (selected || hover) {
            g.fill(cx,           cy,              cx + cw,    cy + 1,           COLOR_CARD_BORDER);
            g.fill(cx,           cy + fixedH - 1, cx + cw,    cy + fixedH,      COLOR_CARD_BORDER);
            g.fill(cx,           cy,              cx + 1,     cy + fixedH,      COLOR_CARD_BORDER);
            g.fill(cx + cw - 1, cy,               cx + cw,    cy + fixedH,      COLOR_CARD_BORDER);
        }

        cardBounds.add(new CardBounds(cx, cy, cw, fixedH, cardIdx));

        int rowY = cy + CARD_PADDING;

        // Icon + label, centered as a unit
        int iconNameW = 18 + font.width(opt.orientationLabel());
        int iconX = cx + (cw - iconNameW) / 2;
        renderItemIcon(g, opt.iconItem(), iconX, rowY);
        g.drawString(font, opt.orientationLabel(), iconX + 18, rowY + 3, 0xFFEEEEEE, false);
        rowY += 10 + 8;

        // Condition block: all squares share the same x axis, block centered in the card
        int maxTW = maxCondTextW(opt);
        int blockW = 4 + 3 + maxTW;
        int blockX = cx + (cw - blockW) / 2;

        String weightText = opt.currentWeight() + "/" + opt.maxWeight() + " weight";
        renderCondRow(g, font, blockX, rowY, weightText, opt.weightMet());
        rowY += 11;

        for (CostRow cr : opt.resourceCost()) {
            String text = cr.have() + "/" + cr.amount() + " " + formatItemId(cr.itemId());
            renderCondRow(g, font, blockX, rowY, text, cr.have() >= cr.amount());
            rowY += 11;
        }

        if (opt.requiredResidents() > 0) {
            String text = opt.activeResidents() + "/" + opt.requiredResidents() + " residents";
            renderCondRow(g, font, blockX, rowY, text, opt.residentsMet());
            rowY += 11;
        }

        for (ReqBuildRow rb : opt.requiredBuildings()) {
            String text = rb.have() + "/" + rb.count() + " " + formatBuildingId(rb.defId());
            renderCondRow(g, font, blockX, rowY, text, rb.have() >= rb.count());
            rowY += 11;
        }

        // Select button, stretches to card width with padding
        int btnY = cy + fixedH - CARD_PADDING - BTN_H;
        int btnW = cw - CARD_PADDING * 2;
        int btnX = cx + CARD_PADDING;
        boolean isSelected = opt.id().equals(selectedPathId);
        boolean btnHover = selectable && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + BTN_H;

        int btnColor;
        if (!selectable)       btnColor = 0xFF222222;
        else if (isSelected)   btnColor = btnHover ? 0xFF55AA55 : 0xFF336633;
        else                   btnColor = btnHover ? 0xFF555555 : 0xFF333333;

        g.fill(btnX, btnY, btnX + btnW, btnY + BTN_H, btnColor);
        String btnText      = (!selectable || isSelected) ? "Selected" : "Select";
        int    btnTextColor = !selectable ? 0xFF444444 : (isSelected ? 0xFF88FF88 : 0xFFCCCCCC);
        g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2, btnTextColor, false);
        selectBtnBounds.add(new int[]{ btnX, btnY, btnW, BTN_H, cardIdx });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void renderCondRow(GuiGraphics g, net.minecraft.client.gui.Font font,
                                       int x, int y, String text, boolean met) {
        g.fill(x, y + 2, x + 4, y + 6, met ? COLOR_MET : COLOR_UNMET);
        g.drawString(font, text, x + 7, y, met ? COLOR_TEXT : COLOR_DIM, false);
    }

    private static void renderItemIcon(GuiGraphics g, String iconItemId, int x, int y) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(iconItemId));
            if (item != net.minecraft.world.item.Items.AIR) {
                g.renderFakeItem(new ItemStack(item), x, y);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return isMouseOver(mouseX, mouseY);

        if (advanceBtnBounds != null) {
            int[] b = advanceBtnBounds;
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                if (getActivePrereqsMet()) {
                    if (pathOptions.size() == 1) {
                        onAdvance.accept(pathOptions.get(0).id());
                    } else if (selectedPathId != null) {
                        onAdvance.accept(selectedPathId);
                    }
                }
                return true;
            }
        }

        if (pathOptions.size() > 1) {
            for (int[] bb : selectBtnBounds) {
                if (mouseX >= bb[0] && mouseX < bb[0] + bb[2]
                        && mouseY >= bb[1] && mouseY < bb[1] + bb[3]) {
                    int cardIdx = bb[4];
                    if (cardIdx < pathOptions.size()) {
                        selectedPathId = pathOptions.get(cardIdx).id();
                    }
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    private static String formatItemId(String itemId) {
        String name = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String formatBuildingId(String id) { return formatItemId(id); }
}
