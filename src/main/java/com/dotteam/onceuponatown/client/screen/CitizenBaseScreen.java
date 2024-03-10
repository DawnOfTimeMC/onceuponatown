package com.dotteam.onceuponatown.client.screen;

import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.network.C2SChangeCitizenTabPacket;
import com.dotteam.onceuponatown.network.OuatNetwork;
import com.dotteam.onceuponatown.util.OuatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

public abstract class CitizenBaseScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation EMPTY_TABS_TEXTURE = OuatUtils.resource("textures/gui/tabs/empty_tabs.png");
    private static final int EMPTY_TABS_TEXTURE_WIDTH = 65;
    private static final int EMPTY_TABS_TEXTURE_HEIGHT = 52;
    private static final int ACTIVE_TAB_OFFSET_X = 30;
    private static final int ACTIVE_TAB_OFFSET_Y = 0;
    private static final int INACTIVE_TAB_OFFSET_X = 0;
    private static final int INACTIVE_TAB_OFFSET_Y = 0;
    private static final int ACTIVE_TAB_WIDTH = 35;
    private static final int ACTIVE_TAB_HEIGHT = 26;
    private static final int INACTIVE_TAB_WIDTH = 30;
    private static final int INACTIVE_TAB_HEIGHT = 26;
    private static final int CITIZEN_DOLL_X = 180;
    private static final int CITIZEN_DOLL_Y = 37;
    private static final int CITIZEN_DOLL_SCALE = 40;
    private static final int MAX_TABS = 10;
    private static final int[] TABS_X = {-32, -32, -32, -32, -32, 280, 280, 280, 280, 280};
    private static final int[] TABS_Y = {11, 38, 65, 92, 119, 11, 38, 65, 92, 119};
    private final List<DrawnTab> drawnTabs = new ArrayList<>();;
    private final CitizenTab activeTab;
    protected Citizen citizen;

    public CitizenBaseScreen(T menu, Inventory inventory, Component title, CitizenTab activeTab) {
        super(menu, inventory, title);
        this.activeTab = activeTab;
        List<CitizenTab> wantedTabs = new ArrayList<>();
        int[] tabX = {-32, -32, -32, -32, -32, 280, 280, 280, 280, 280};
        int i = 8;
        int[] tabY = {11+i, 38+i, 65+i, 92+i, 119+i, 11+i, 38+i, 65+i, 92+i, 119+i};

        wantedTabs.add(CitizenTab.INFO);
        wantedTabs.add(CitizenTab.BUY);
        wantedTabs.add(CitizenTab.SELL);
        wantedTabs.add(CitizenTab.QUESTS);
        //wantedTabs.add(CitizenTab.INFO);
        int index = 0;
        for (CitizenTab wantedTab : wantedTabs) {
            this.drawnTabs.add(new DrawnTab(index, wantedTab, tabX[index], tabY[index]));
            if (index >= MAX_TABS) {
                throw new RuntimeException("Citizen GUI can't have more than 10 tabs");
            }
            ++index;
        }
    }

    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (this.citizen != null) renderCitizenDoll(graphics, mouseX, mouseY);
        //graphics.drawString(this.font, Component.literal("Simir Kurtmar").withStyle(ChatFormatting.GRAY), leftPos + 205, topPos - 12, 4210752, false);
        //graphics.drawString(this.font, Component.literal("Toolsmith").withStyle(ChatFormatting.GOLD), leftPos + 205, topPos - 1, 4210752, false);

        addRenderableWidget(new CitizenChatWidget(leftPos+299, topPos+10, 128,163, Component.literal("test")));
        /*
        addRenderableWidget(new AbstractScrollWidget(leftPos+299, topPos+10, 128,163, Component.literal("test")) {
            @Override
            protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {

            }

            @Override
            protected int getInnerHeight() {
                return 500;
            }

            @Override
            protected double scrollRate() {
                return 50;
            }

            @Override
            protected void renderContents(GuiGraphics graphics, int pMouseX, int pMouseY, float pPartialTick) {
                //graphics.drawString(CitizenBaseScreen.this.font, Component.literal("Test"), mouseX, mouseY,4210752);

            }
        });

         */
        //graphics.drawString(this.font, Component.literal("Simir Kurtmar, ").withStyle(ChatFormatting.GOLD).append(Component.literal("Inkeeper").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC)), leftPos + 300, topPos-3, 4210752, false);
        //graphics.drawString(this.font, Component.literal("                               ").withStyle(ChatFormatting.WHITE).withStyle(ChatFormatting.UNDERLINE),leftPos + 300, topPos +2, 4210752, false);
        graphics.drawWordWrap(this.font, Component.translatable("sentence.onceuponatown.test").withStyle(ChatFormatting.GRAY),leftPos + 300, topPos +18, 130,4210752);
        graphics.drawString(this.font, Component.literal(" > Sure").withStyle(ChatFormatting.WHITE).withStyle(ChatFormatting.ITALIC), leftPos + 298, topPos +150, 4210752, false);
        graphics.drawString(this.font, Component.literal(" > No, thanks").withStyle(ChatFormatting.WHITE), leftPos +298, topPos +160, 4210752, false);

    }

    private void renderCitizenDoll(GuiGraphics graphics, int mouseX, int mouseY) {
        float lookAtX = this.leftPos - 1 + 250 - mouseX;;
        float lookAtY; // Without correction lookAtY = this.topPos - 27 - mouseY;
        if (mouseY >= 120) {
            lookAtY = this.topPos - 240 - (int)(mouseY * 0.59);
        } else {
            lookAtY = this.topPos - (int)(240 + -0.0035 * mouseY * mouseY) - mouseY;
        }
        lookAtY = this.topPos + 145 - mouseY;
        lookAtY = Math.min(lookAtY, 40);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,leftPos + 250 ,topPos + 40, 45, lookAtX, lookAtY, this.citizen);
    }

    private void renderCitizenDoll2(GuiGraphics graphics, int mouseX, int mouseY) {
        float lookAtX = this.leftPos - 1 + 470 - mouseX;;
        float lookAtY; // Without correction lookAtY = this.topPos - 27 - mouseY;
        if (mouseY >= 120) {
            lookAtY = this.topPos - 5 - (int)(mouseY * 0.59);
        } else {
            lookAtY = this.topPos - (int)(5 + -0.0035 * mouseY * mouseY) - mouseY;
        }
        // Without correction lookAtY = this.topPos - 27 - mouseY;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,leftPos + 470,topPos + 175, 65, lookAtX, lookAtY, this.citizen);
    }

    protected void renderTabs(GuiGraphics graphics) {
        this.drawnTabs.forEach((drawnTab -> renderTab(graphics, drawnTab.tab, drawnTab.x, drawnTab.y)));
    }

    private void renderTab(GuiGraphics graphics, CitizenTab tab, int tabX, int tabY) {
        int inactiveTabFoldOffsetX = 2;
        if (this.activeTab == tab) {
            graphics.blit(EMPTY_TABS_TEXTURE, this.leftPos + tabX , this.topPos + tabY, ACTIVE_TAB_OFFSET_X, ACTIVE_TAB_OFFSET_Y, ACTIVE_TAB_WIDTH, ACTIVE_TAB_HEIGHT, EMPTY_TABS_TEXTURE_WIDTH, EMPTY_TABS_TEXTURE_HEIGHT);
            graphics.blit(tab.iconTexture, this.leftPos + tabX + tab.iconOffsetX - 1, this.topPos + tabY + tab.iconOffsetY, 0, 0, tab.iconWidth, tab.iconHeight, tab.iconWidth, tab.iconHeight);
        } else {
            graphics.blit(EMPTY_TABS_TEXTURE, this.leftPos + tabX + inactiveTabFoldOffsetX , this.topPos + tabY, INACTIVE_TAB_OFFSET_X, INACTIVE_TAB_OFFSET_Y, INACTIVE_TAB_WIDTH, INACTIVE_TAB_HEIGHT, EMPTY_TABS_TEXTURE_WIDTH, EMPTY_TABS_TEXTURE_HEIGHT);
            graphics.blit(tab.iconTexture, this.leftPos + tabX + tab.iconOffsetX, this.topPos + tabY + tab.iconOffsetY, 0, 0, tab.iconWidth, tab.iconHeight, tab.iconWidth, tab.iconHeight);
        }

    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (DrawnTab drawnTab : this.drawnTabs) {
            if ((drawnTab.tab != this.activeTab)
            && (mouseX >= leftPos + drawnTab.x)
            && (mouseX <= leftPos + drawnTab.x + INACTIVE_TAB_WIDTH + 2)
            && (mouseY >= topPos + drawnTab.y)
            && (mouseY <= topPos+ drawnTab.y + INACTIVE_TAB_HEIGHT)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                OuatNetwork.sendToServer(new C2SChangeCitizenTabPacket(drawnTab.tab.ordinal()));
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private record DrawnTab(int index, CitizenTab tab, int x, int y) {}

    public enum CitizenTab {
        BUY(OuatUtils.resource("textures/gui/tabs/buy_icon.png"), 17, 20, 10, 3),
        SELL(OuatUtils.resource("textures/gui/tabs/sell_icon.png"), 14, 21, 11, 3),
        QUESTS(OuatUtils.resource("textures/gui/tabs/quests_icon.png"),15 ,12 ,10 ,7),
        INFO(new ResourceLocation("textures/item/chainmail_chestplate.png"), 16, 16, 10, 5);

        public final ResourceLocation iconTexture;
        public final int iconWidth;
        public final int iconHeight;
        public final int iconOffsetX;
        public final int iconOffsetY;

        CitizenTab(ResourceLocation iconTexture, int iconWidth, int iconHeight, int iconOffsetX, int iconOffsetY) {
            this.iconTexture = iconTexture;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
            this.iconOffsetX = iconOffsetX;
            this.iconOffsetY = iconOffsetY;
        }
    }
}
