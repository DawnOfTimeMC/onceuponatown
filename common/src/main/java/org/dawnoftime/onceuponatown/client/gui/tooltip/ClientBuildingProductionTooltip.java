package org.dawnoftime.onceuponatown.client.gui.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.dawnoftime.onceuponatown.client.gui.widgets.NbtPreviewWidget;
import org.joml.Matrix4f;

public class ClientBuildingProductionTooltip implements ClientTooltipComponent {
    private static final int ROW_HEIGHT    = 18;
    private static final int ICON_SIZE     = 16;
    private static final int GAP           = 3;
    /** ARGB green used for section header text. */
    private static final int COLOR_HEADER  = 0xFF55FF55;
    /** ARGB light-gray used for regular item and perk rows. */
    private static final int COLOR_CONTENT = 0xFFAAAAAA;
    /** ARGB dark-gray used for locked item rows. */
    private static final int COLOR_LOCKED  = 0xFF666666;

    private final BuildingProductionTooltip data;

    public ClientBuildingProductionTooltip(BuildingProductionTooltip data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return data.rows().size() * ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int max = 0;
        for (BuildingProductionTooltip.Row row : data.rows()) {
            // Headers have no icon prefix, item rows do.
            int w = (row.isHeader() ? 0 : ICON_SIZE + GAP) + font.width(row.text());
            if (w > max) max = w;
        }
        return max;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        for (int i = 0; i < data.rows().size(); i++) {
            BuildingProductionTooltip.Row row = data.rows().get(i);
            if (!row.isHeader()) {
                graphics.renderItem(row.stack(), x, y + i * ROW_HEIGHT + 1);
                if (row.locked()) {
                    NbtPreviewWidget.drawPadlockIcon(graphics, x + 4, y + i * ROW_HEIGHT + 4);
                }
            }
        }
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix4f, MultiBufferSource.BufferSource bufferSource) {
        for (int i = 0; i < data.rows().size(); i++) {
            BuildingProductionTooltip.Row row = data.rows().get(i);
            // Headers start at x with no icon offset and use green color.
            int textX = row.isHeader() ? x : x + ICON_SIZE + GAP;
            int color  = row.isHeader() ? COLOR_HEADER : (row.locked() ? COLOR_LOCKED : COLOR_CONTENT);
            font.drawInBatch(
                row.text().getVisualOrderText(),
                textX,
                y + i * ROW_HEIGHT + 5,
                color,
                true,
                matrix4f,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
            );
        }
    }
}
