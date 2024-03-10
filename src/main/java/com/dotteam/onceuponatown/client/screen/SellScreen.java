package com.dotteam.onceuponatown.client.screen;

import com.dotteam.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import com.dotteam.onceuponatown.menu.SellMenu;
import com.dotteam.onceuponatown.network.C2SSellScreenPacket;
import com.dotteam.onceuponatown.network.OuatNetwork;
import com.dotteam.onceuponatown.trade.SellDeal;
import com.dotteam.onceuponatown.util.OuatUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public class SellScreen extends CitizenBaseScreen<SellMenu> {
    private static final ResourceLocation TEXTURE = OuatUtils.resource("textures/gui/sell_screen.png");
    private static final int BUY_SCREEN_TEXTURE_WIDTH = 299;
    private static final int BUY_SCREEN_TEXTURE_HEIGHT = 174;
    // Texture size and offset in file
    private static final int SCROLLER_HEIGHT = 27;
    private static final int SCROLLER_WIDTH = 6;
    private static final int SCROLLER_OFFSET_X = 287;
    private static final int SCROLLER_OFFSET_Y = 60;
    private static final int SCROLLER_AVAIL_OFFSET_X = 281;
    private static final int SCROLLER_AVAIL_OFFSET_Y = 60;
    // Positions of elements in gui
    private static final int GRID_X = 8;
    private static final int GRID_Y = 26;
    private static final int SCROLLER_X = 101;
    private static final int SCROLL_BAR_TOP_Y = 26;
    private static final int SCROLL_BAR_HEIGHT = 139;
    private static final int SCROLL_BAR_BOTTOM_Y = 99;
    private Component citizenInfo = Component.literal("Armorer");
    private Component citizenSentence;
    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROWS = 7;
    public static final int NUMBER_OF_DEAL_BUTTONS = GRID_ROWS * GRID_COLUMNS;
    private final DealButton[] dealButtons = new DealButton[NUMBER_OF_DEAL_BUTTONS];
    private int selectedDealIndex = -1;
    private int scrollOff;
    private boolean isDragging;

    public SellScreen(SellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CitizenTab.SELL);
        this.imageWidth = 281;
        this.imageHeight = 174;
        this.inventoryLabelX = 113;
        this.inventoryLabelY = 80;
        this.titleLabelX = 44;
        this.titleLabelY = 5;
        this.citizen = menu.getCitizen().getCitizen();
    }

    protected void init() {
        super.init();
        //this.leftPos = this.leftPos - 80;
        //this.leftPos = 167 + (this.width - this.imageWidth - 200) / 2;
        createButtons();
        this.addRenderableWidget(new Button.Builder(Component.literal("\u2191"),(button -> onSellEverythingButtonClick()))
                .bounds(this.leftPos + 169,this.topPos + 77,12,13)
                .tooltip(Tooltip.create(Component.literal("Sell all wanted items"))).build());
    }

    private void createButtons() {
        int x, startX = this.leftPos + GRID_X;
        int y = this.topPos + GRID_Y;
        int buttonIndex = 0;
        for (int i = 0; i < GRID_ROWS; ++i) {
            x = startX;
            for(int j = 0; j < GRID_COLUMNS; ++j) {
                this.dealButtons[buttonIndex] = this.addRenderableWidget(new DealButton(x, y, buttonIndex, (button) -> onDealButtonLeftClick((DealButton)button)));
                ++buttonIndex;
                x += BuyScreen.DealButton.DEAL_BUTTON_WIDTH;
            }
            y += BuyScreen.DealButton.DEAL_BUTTON_HEIGHT;
        }
    }

    private void onDealButtonLeftClick(DealButton button) {
        this.selectedDealIndex = button.getIndex() + (this.scrollOff * GRID_COLUMNS);
        this.menu.handleClientAction(this.selectedDealIndex, (hasShiftDown() ? C2SSellScreenPacket.RequestType.ONE_TRADE_SELL_EVERYTHING : C2SSellScreenPacket.RequestType.ONE_TRADE_SELL_ONE));
        OuatNetwork.sendToServer(new C2SSellScreenPacket(this.selectedDealIndex, (hasShiftDown() ? C2SSellScreenPacket.RequestType.ONE_TRADE_SELL_EVERYTHING : C2SSellScreenPacket.RequestType.ONE_TRADE_SELL_ONE)));
    }

    private void onDealButtonRightClick(DealButton button) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.7F));
        this.selectedDealIndex = button.getIndex() + (this.scrollOff * GRID_COLUMNS);
        this.menu.handleClientAction(this.selectedDealIndex, (hasShiftDown() ? C2SSellScreenPacket.RequestType.ONE_TRADE_REMOVE_EVERYTHING : C2SSellScreenPacket.RequestType.ONE_TRADE_REMOVE_ONE));
        OuatNetwork.sendToServer(new C2SSellScreenPacket(this.selectedDealIndex, (hasShiftDown() ? C2SSellScreenPacket.RequestType.ONE_TRADE_REMOVE_EVERYTHING : C2SSellScreenPacket.RequestType.ONE_TRADE_REMOVE_ONE)));
    }

    private void onSellEverythingButtonClick() {
        this.menu.handleClientAction(this.selectedDealIndex, C2SSellScreenPacket.RequestType.ALL_TRADES_SELL_EVERYTHING);
        OuatNetwork.sendToServer(new C2SSellScreenPacket(this.selectedDealIndex, C2SSellScreenPacket.RequestType.ALL_TRADES_SELL_EVERYTHING));
    }

    private int nbOfDeals() {
        return this.menu.getDeals().size();
    }

    private boolean canScroll() {
        return nbOfDeals() > NUMBER_OF_DEAL_BUTTONS;
    }

    private int nbOfTimesCanScrollDown() {
        return Mth.ceil((((double)this.menu.getDeals().size() - (double)NUMBER_OF_DEAL_BUTTONS) / 6));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.canScroll()) {
            this.scrollOff = Mth.clamp((int)((double)this.scrollOff - delta), 0, nbOfTimesCanScrollDown());
        }
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (DealButton dealButton : this.dealButtons) {
            if (dealButton.isHovered() && button == 1) {
                onDealButtonRightClick(dealButton);
            }
        }
        this.isDragging = false;
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        if (this.canScroll() && mouseX > (double)(i + 119) && mouseX < (double)(i + 119 + 6) && mouseY > (double)(j + 24) && mouseY <= (double)(j + 24 + 139 + 1)) {
            this.isDragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            int j = this.topPos + 24;
            int k = j + SCROLL_BAR_HEIGHT;
            int l = nbOfTimesCanScrollDown();
            float f = ((float)mouseY - (float)j - 13.5F) / ((float)(k - j) - 27.0F);
            f = f * (float)l + 0.5F;
            this.scrollOff = Mth.clamp((int)f, 0, l);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
    }

    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(graphics);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
        if (this.menu.slots.get(SellMenu.VALUE_BLOCKS_SLOT).hasItem()) {
            graphics.blit(TEXTURE, this.leftPos + 219, this.topPos + 49 , 112, 91, 18, 18, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
        }

        renderTabs(graphics);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
        graphics.drawString(this.font, Component.literal("Goods"), this.inventoryLabelX, 28, 4210752, false);
        graphics.drawString(this.font, Component.literal("Value"), 220, 38, 4210752, false);
        graphics.drawString(this.font, title, 132, titleLabelY, 4210752, false);
        graphics.drawString(this.font, "Needed", 37, 14, 4210752, false);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderScroller(graphics);

        for (DealButton button : dealButtons) {
            button.visible = (button.index + this.scrollOff * GRID_COLUMNS) < nbOfDeals();
        }

        List<SellDeal> deals = this.menu.getDeals();
        if (!deals.isEmpty()) {
            int startX  = leftPos + GRID_X + 1;
            int x = startX;
            int y = topPos + GRID_Y + 1 + 1;

            int index = 0;
            for(SellDeal deal : deals) {
                if (!this.canScroll() || index >= (this.scrollOff * GRID_COLUMNS) && index < NUMBER_OF_DEAL_BUTTONS + (this.scrollOff * GRID_COLUMNS)) {
                    ItemStack good = deal.getGood();
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0F, 0.0F, 100.0F);
                    //graphics.setColor((float)0.8,(float)0.3,(float)0.3, (float)1);
                    graphics.renderFakeItem(good, x, y);
                    renderGoodItemDecorations(graphics, this.font, good, x, y);
                    //graphics.setColor((float)1,(float)1,(float)1, 1);
                    graphics.pose().popPose();

                    x += BuyScreen.DealButton.DEAL_BUTTON_WIDTH;
                    if (x == startX + (BuyScreen.DealButton.DEAL_BUTTON_WIDTH * GRID_COLUMNS)) {
                        x = startX;
                        y += BuyScreen.DealButton.DEAL_BUTTON_HEIGHT;
                    }
                }
                ++index;
            }
            RenderSystem.enableDepthTest();
        }
        renderTooltip(graphics, mouseX, mouseY);

        //graphics.pose().translate(0.0F, 0.0F, 100.0F);
        if (this.menu.isGoodsGridLocked()) {
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            graphics.blit(OuatUtils.resource("textures/gui/lock.png"), this.leftPos + 143, this.topPos + 48 , 0, 0, 10, 14, 10, 14);
        }
        graphics.drawString(this.font, Component.literal("Simir Kurtmar, ").withStyle(ChatFormatting.GOLD).append(Component.literal("Inkeeper").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC)), leftPos + 305, topPos+18, 4210752, false);
        //graphics.drawString(this.font, Component.literal("                               ").withStyle(ChatFormatting.WHITE).withStyle(ChatFormatting.UNDERLINE),leftPos + 305, topPos +2, 4210752, false);
        graphics.drawWordWrap(this.font, Component.literal("Welcome to Rochecolombe, dear traveler! It's a pleasure to have you here in our quaint town. Whether you've come from near or far, we're delighted to extend our warmest hospitality to you.").withStyle(ChatFormatting.GRAY),leftPos + 305, topPos +35, 120,4210752);
        graphics.drawString(this.font, Component.literal(" > I'll have a drink").withStyle(ChatFormatting.WHITE).withStyle(ChatFormatting.ITALIC), leftPos + 300, topPos +150, 4210752, false);
        graphics.drawString(this.font, Component.literal(" > Good bye").withStyle(ChatFormatting.WHITE), leftPos +300, topPos +160, 4210752, false);

    }

    private void renderScroller(GuiGraphics graphics) {
        int i = 1 + nbOfTimesCanScrollDown();
        if (i > 1) {
            int j = SCROLL_BAR_HEIGHT - (SCROLLER_HEIGHT + (i - 1) * SCROLL_BAR_HEIGHT / i);
            int k = 1 + j / i + SCROLL_BAR_HEIGHT / i;
            int scrollerOffset = Math.min(SCROLL_BAR_BOTTOM_Y, this.scrollOff * k);
            if (this.scrollOff == i - 1) {
                scrollerOffset = SCROLL_BAR_BOTTOM_Y;
            }

            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP_Y + scrollerOffset, 0, SCROLLER_AVAIL_OFFSET_X, SCROLLER_AVAIL_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
        } else {
            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP_Y, 0, SCROLLER_OFFSET_X, SCROLLER_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
        }

    }

    private void renderGoodItemDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!stack.isEmpty() && stack.getCount() > 1) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.drawString(font, String.valueOf(stack.getCount()), x + 20 - 3 - font.width(String.valueOf(stack.getCount())), y + 6 + 3, 16777215, true);
            graphics.pose().popPose();
        }
    }

    class DealButton extends Button {
        public static final int DEAL_BUTTON_WIDTH = 18;
        public static final int DEAL_BUTTON_HEIGHT = 20;
        public static final int DEAL_BUTTON_OFFSET_X = 281;
        public static final int DEAL_BUTTON_OFFSET_Y = 0;
        public static final int DEAL_BUTTON_SEL_OFFSET_X = 281;
        public static final int DEAL_BUTTON_SEL_OFFSET_Y = 20;
        public static final int DEAL_BUTTON_HOVER_OFFSET_X = 281;
        public static final int DEAL_BUTTON_HOVER_OFFSET_Y = 40;
        final int index;

        public DealButton(int x, int y, int index, OnPress onPress) {
            super(x, y, DEAL_BUTTON_WIDTH, DEAL_BUTTON_HEIGHT, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
            this.index = index;
            this.visible = false;
        }

        public int getIndex() {
            return this.index;
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.visible) {
                this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;

                int offsetX;
                int offsetY;
                if (this.isHovered && (this.index + SellScreen.this.scrollOff * GRID_COLUMNS) != SellScreen.this.selectedDealIndex) {
                    offsetX = DEAL_BUTTON_HOVER_OFFSET_X;
                    offsetY = DEAL_BUTTON_HOVER_OFFSET_Y;
                } else if ((this.index + SellScreen.this.scrollOff * GRID_COLUMNS) == SellScreen.this.selectedDealIndex) {
                    offsetX = DEAL_BUTTON_SEL_OFFSET_X;
                    offsetY = DEAL_BUTTON_SEL_OFFSET_Y;

                } else {
                    offsetX = DEAL_BUTTON_OFFSET_X;
                    offsetY = DEAL_BUTTON_OFFSET_Y;
                }

                graphics.blit(TEXTURE, getX(), getY(), offsetX, offsetY, this.width, this.height, SellScreen.BUY_SCREEN_TEXTURE_WIDTH, SellScreen.BUY_SCREEN_TEXTURE_HEIGHT);
                if (isHoveredOrFocused()) {
                    renderToolTip(graphics,mouseX,mouseY);
                }
            }

        }

        public void renderToolTip(GuiGraphics graphics, int mouseX, int mouseY) {
            if (this.isHovered && SellScreen.this.menu.getDeals().size() > this.index + (SellScreen.this.scrollOff * GRID_COLUMNS)) {
                ItemStack good = SellScreen.this.menu.getDeals().get(this.index + (SellScreen.this.scrollOff * GRID_COLUMNS)).getGood();
                ItemStack valueShards = SellScreen.this.menu.getDeals().get(this.index + (SellScreen.this.scrollOff * GRID_COLUMNS)).getValueShards();
                ItemStack valueEmeralds = SellScreen.this.menu.getDeals().get(this.index + (SellScreen.this.scrollOff * GRID_COLUMNS)).getValueEmeralds();
                ItemStack valueBlocks = SellScreen.this.menu.getDeals().get(this.index + (SellScreen.this.scrollOff * GRID_COLUMNS)).getValueBlocks();
                List<Component> text = Screen.getTooltipFromItem(SellScreen.this.minecraft, good);
                graphics.renderTooltip(SellScreen.this.font, text, Optional.of(new TradeItemTooltip(valueShards, valueEmeralds, valueBlocks)), good, mouseX, mouseY);
            }
        }
    }
}
