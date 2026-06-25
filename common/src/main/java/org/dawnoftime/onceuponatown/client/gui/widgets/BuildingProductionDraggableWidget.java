package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.dawnoftime.onceuponatown.client.gui.tooltip.BuildingProductionTooltip;

import java.util.List;

public class BuildingProductionDraggableWidget extends DraggableWidget {

    public static final int WIDGET_W      = 170;
    private static final int PADDING       = 4;
    private static final int HEADER_ROW_H  = 12;
    private static final int ITEM_ROW_H    = 16;

    private final String title;
    private final List<BuildingProductionTooltip.Row> rows;

    public BuildingProductionDraggableWidget(String title, List<BuildingProductionTooltip.Row> rows,
                                             int x, int y, int freeZoneMaxX, int screenHeight) {
        super(x, y, WIDGET_W, computeHeight(rows), freeZoneMaxX, screenHeight);
        this.title = title;
        this.rows  = rows;
    }

    public static int computeHeight(List<BuildingProductionTooltip.Row> rows) {
        int contentH = PADDING;
        for (BuildingProductionTooltip.Row row : rows) {
            contentH += row.isHeader() ? HEADER_ROW_H : ITEM_ROW_H;
        }
        contentH += PADDING;
        return TITLE_BAR_H + Math.max(contentH, 20);
    }

    @Override
    protected String getTitle() { return title; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                 int mouseX, int mouseY, float delta) {
        g.fill(cx, cy, cx + cw, cy + ch, 0xF51A1A1A);

        var font = Minecraft.getInstance().font;
        int rowY = cy + PADDING;

        for (BuildingProductionTooltip.Row row : rows) {
            if (row.isHeader()) {
                g.drawString(font, row.text(), cx + 4, rowY + 2, 0xFFDDDDDD, false);
                rowY += HEADER_ROW_H;
            } else {
                g.renderFakeItem(row.stack(), cx + 2, rowY);
                if (row.locked()) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    NbtPreviewWidget.drawPadlockIcon(g, cx + 2 + 4, rowY + 3);
                    g.pose().popPose();
                    g.drawString(font, row.text(), cx + 20, rowY + 4, 0xFF666666, false);
                } else {
                    g.drawString(font, row.text(), cx + 20, rowY + 4, 0xFFAAAAAA, false);
                }
                rowY += ITEM_ROW_H;
            }
        }
    }
}
