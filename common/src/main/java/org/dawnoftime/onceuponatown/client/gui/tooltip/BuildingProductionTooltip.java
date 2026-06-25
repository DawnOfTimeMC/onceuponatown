package org.dawnoftime.onceuponatown.client.gui.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record BuildingProductionTooltip(List<Row> rows) implements TooltipComponent {
    /**
     * A single tooltip row.
     * When {@code stack} is null the row is a section header: no icon, text starts at x=0.
     * When {@code stack} is non-null it is rendered as an item icon with text beside it.
     */
    public record Row(ItemStack stack, Component text, boolean locked) {
        public Row(ItemStack stack, Component text) { this(stack, text, false); }
        public boolean isHeader() { return stack == null; }
    }
}
