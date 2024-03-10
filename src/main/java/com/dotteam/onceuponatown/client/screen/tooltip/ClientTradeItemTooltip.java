package com.dotteam.onceuponatown.client.screen.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ClientTradeItemTooltip implements ClientTooltipComponent {
    private final ItemStack a;
    private final ItemStack b;
    private final ItemStack c;


    public ClientTradeItemTooltip(TradeItemTooltip tooltip) {
        this.a = tooltip.getA();
        this.b = tooltip.getB();
        this.c = tooltip.getC();
    }

    @Override
    public void renderImage(Font font, int pX, int pY, GuiGraphics grpahics) {
        //grpahics.drawString(font, "Worth", pX, pY, 43520, false);
        int leftOffset = 3;
        int separation = 18;
        List<ItemStack> stacks = new ArrayList<>();
        if (!this.a.isEmpty()) stacks.add(this.a);
        if (!this.b.isEmpty()) stacks.add(this.b);
        if (!this.c.isEmpty()) stacks.add(this.c);
        int i = 0;
        for (ItemStack stack : stacks) {
            grpahics.renderItem(stack, pX - leftOffset + i * separation, pY + 1, 1);
            grpahics.renderItemDecorations(font, stack, pX - leftOffset + i * separation, pY + 1);
            ++i;
        }
    }

    @Override
    public int getHeight() {
        return 19;
    }

    @Override
    public int getWidth(Font pFont) {
        int i = 0;
        if (!this.a.isEmpty()) {
            i+= 18;
        }
        if (!this.b.isEmpty()) {
            i+= 18;
        }
        if (!this.c.isEmpty()) {
            i+= 18;
        }
        return i - 4;
    }
}
