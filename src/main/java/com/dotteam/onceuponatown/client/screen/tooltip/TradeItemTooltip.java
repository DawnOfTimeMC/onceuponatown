package com.dotteam.onceuponatown.client.screen.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public class TradeItemTooltip implements TooltipComponent {
    private final ItemStack a;
    private final ItemStack b;
    private final ItemStack c;

    public TradeItemTooltip(ItemStack a, ItemStack b, ItemStack c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public ItemStack getA() {
        return this.a;
    }

    public ItemStack getB() {
        return this.b;
    }

    public ItemStack getC() {
        return this.c;
    }
}
