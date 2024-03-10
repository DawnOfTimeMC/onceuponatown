package com.dotteam.onceuponatown.menu;

import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.registry.OuatMenus;
import com.dotteam.onceuponatown.trade.BuyDeal;
import com.dotteam.onceuponatown.trade.TradeUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BuyMenu extends CitizenBaseMenu {
    protected static final int INPUT_A_SLOT = 0;
    protected static final int INPUT_B_SLOT = 1;
    protected static final int INPUT_C_SLOT = 2;
    protected static final int RESULT_SLOT = 3;
    protected static final int INV_SLOT_START = 4;
    protected static final int INV_SLOT_END = 30;
    protected static final int HOT_BAR_SLOT_START = 31;
    protected static final int HOT_BAR_SLOT_END = 39;
    private static final int INPUT_A_X = 128;
    private static final int INPUT_B_X = 154;
    private static final int INPUT_C_X = 180;
    private static final int RESULT_X = 238;
    private static final int ROW_Y = 45;
    private final BuyContainer buyContainer;

    public BuyMenu(int containerId, Inventory playerInventory, FriendlyByteBuf friendlyByteBuf) {
        this(containerId, playerInventory,
                new ClientSideInteractingCitizen.Builder(
                        (Citizen)(playerInventory.player.level().getEntity(friendlyByteBuf.readInt())), playerInventory.player)
                        .buyDeals(TradeUtils.createBuyDealsFromStream(friendlyByteBuf))
                        .build());
    }

    public BuyMenu(int containerId, Inventory playerInventory, InteractableCitizen citizen) {
        super(OuatMenus.BUY_MENU.get(), containerId, citizen);
        this.citizen = citizen;
        citizen.setInteractingPlayer(playerInventory.player);
        this.buyContainer = new BuyContainer(citizen);
        this.addSlot(new Slot(this.buyContainer, INPUT_A_SLOT, INPUT_A_X, ROW_Y));
        this.addSlot(new Slot(this.buyContainer, INPUT_B_SLOT, INPUT_B_X, ROW_Y));
        this.addSlot(new Slot(this.buyContainer, INPUT_C_SLOT, INPUT_C_X, ROW_Y));
        this.addSlot(new BuyResultSlot(playerInventory.player, citizen, this.buyContainer, RESULT_SLOT, RESULT_X, ROW_Y));
        for(int i = 0; i < 3; ++i) { // Inventory
            for(int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 113 + j * 18, 92 + i * 18));
            }
        }
        for(int k = 0; k < 9; ++k) { // Hot bar
            this.addSlot(new Slot(playerInventory, k, 113 + k * 18, 150));
        }
    }

    public void slotsChanged(Container container) {
        this.buyContainer.updateResultItem();
        super.slotsChanged(container);
    }

    public void setSelectedDeal(int selectedDealIndex) {
        this.buyContainer.setSelectedDealIndex(selectedDealIndex);
    }

    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack stackCopy = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            stackCopy = stackInSlot.copy();
            if (slotIndex == RESULT_SLOT) {
                if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(stackInSlot, stackCopy);
                this.playThankYouSound();
            } else if (slotIndex != INPUT_A_SLOT && slotIndex != INPUT_B_SLOT && slotIndex != INPUT_C_SLOT) { // IF SLOT IS IN PLAYER INV
                if (slotIndex >= INV_SLOT_START && slotIndex <= INV_SLOT_END) {
                    if (!this.moveItemStackTo(stackInSlot, INV_SLOT_END, HOT_BAR_SLOT_END + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex >= INV_SLOT_END && slotIndex <= HOT_BAR_SLOT_END && !this.moveItemStackTo(stackInSlot, INV_SLOT_START, INV_SLOT_END + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stackInSlot, INV_SLOT_START, HOT_BAR_SLOT_END + 1, false)) {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == stackCopy.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return stackCopy;
    }

    private void playThankYouSound() {
        if (this.citizen instanceof Citizen) {
            Citizen entity = citizen.getCitizen();
            entity.playSound(SoundEvents.VILLAGER_YES, 1.0F,1.0F);
        }
    }

    public void removed(Player player) {
        super.removed(player);
        this.citizen.setInteractingPlayer(null);
        if (!this.citizen.isClientSide()) {
            if (!player.isAlive() || player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()) {
                ItemStack itemstack = this.buyContainer.removeItemNoUpdate(INPUT_A_SLOT);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
                itemstack = this.buyContainer.removeItemNoUpdate(INPUT_B_SLOT);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
                itemstack = this.buyContainer.removeItemNoUpdate(INPUT_C_SLOT);
                if (!itemstack.isEmpty()) {
                    player.drop(itemstack, false);
                }
            } else if (player instanceof ServerPlayer) {
                player.getInventory().placeItemBackInInventory(this.buyContainer.removeItemNoUpdate(INPUT_A_SLOT));
                player.getInventory().placeItemBackInInventory(this.buyContainer.removeItemNoUpdate(INPUT_B_SLOT));
                player.getInventory().placeItemBackInInventory(this.buyContainer.removeItemNoUpdate(INPUT_C_SLOT));
            }

        }
    }

    public void tryMoveItems(int selectedDealIndex) {
        if (selectedDealIndex >= 0 && this.getDeals().size() > selectedDealIndex) {
            ItemStack stackInSlotA = this.buyContainer.getItem(INPUT_A_SLOT);
            if (!stackInSlotA.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlotA, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return;
                }

                this.buyContainer.setItem(INPUT_A_SLOT, stackInSlotA);
            }

            ItemStack stackInSlotB = this.buyContainer.getItem(INPUT_B_SLOT);
            if (!stackInSlotB.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlotB, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return;
                }

                this.buyContainer.setItem(INPUT_B_SLOT, stackInSlotB);
            }

            ItemStack stackInSlotC = this.buyContainer.getItem(INPUT_C_SLOT);
            if (!stackInSlotC.isEmpty()) {
                if (!this.moveItemStackTo(stackInSlotC, INV_SLOT_START, HOT_BAR_SLOT_END + 1, true)) {
                    return;
                }

                this.buyContainer.setItem(INPUT_C_SLOT, stackInSlotC);
            }

            if (this.buyContainer.getItem(INPUT_A_SLOT).isEmpty() && this.buyContainer.getItem(INPUT_B_SLOT).isEmpty() && this.buyContainer.getItem(INPUT_C_SLOT).isEmpty()) {
                ItemStack requiredA = this.getDeals().get(selectedDealIndex).getInputA();
                this.moveFromInventoryToPaymentSlot(INPUT_A_SLOT, requiredA);
                ItemStack requiredB = this.getDeals().get(selectedDealIndex).getInputB();
                this.moveFromInventoryToPaymentSlot(INPUT_B_SLOT, requiredB);
                ItemStack requiredC = this.getDeals().get(selectedDealIndex).getInputC();
                this.moveFromInventoryToPaymentSlot(INPUT_C_SLOT, requiredC);
            }

        }
    }

    private void moveFromInventoryToPaymentSlot(int pPaymentSlotIndex, ItemStack pPaymentSlot) {
        if (!pPaymentSlot.isEmpty()) {
            for(int i = INV_SLOT_START; i < HOT_BAR_SLOT_END + 1; ++i) {
                ItemStack itemstack = this.slots.get(i).getItem();
                if (!itemstack.isEmpty() && ItemStack.isSameItemSameTags(pPaymentSlot, itemstack)) {
                    ItemStack itemstack1 = this.buyContainer.getItem(pPaymentSlotIndex);
                    int j = itemstack1.isEmpty() ? 0 : itemstack1.getCount();
                    int k = Math.min(pPaymentSlot.getMaxStackSize() - j, itemstack.getCount());
                    ItemStack itemstack2 = itemstack.copy();
                    int l = j + k;
                    itemstack.shrink(k);
                    itemstack2.setCount(l);
                    this.buyContainer.setItem(pPaymentSlotIndex, itemstack2);
                    if (l >= pPaymentSlot.getMaxStackSize()) {
                        break;
                    }
                }
            }
        }

    }

    public void setDeals(List<BuyDeal> deals) {
    }

    public List<BuyDeal> getDeals() {
        return this.citizen.getBuyDeals();
    }

    public static class BuyResultSlot extends Slot {
        private final BuyContainer slots;
        private final Player player;
        private int removeCount;
        private final InteractableCitizen citizen;

        public BuyResultSlot(Player player, InteractableCitizen citizen, BuyContainer buyContainer, int slot, int posX, int posY) {
            super(buyContainer, slot, posX, posY);
            this.player = player;
            this.citizen = citizen;
            this.slots = buyContainer;
        }

        /**
         * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
         */
        public boolean mayPlace(ItemStack pStack) {
            return false;
        }

        /**
         * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new stack.
         */
        public ItemStack remove(int pAmount) {
            if (this.hasItem()) {
                this.removeCount += Math.min(pAmount, this.getItem().getCount());
            }

            return super.remove(pAmount);
        }

        /**
         * Typically increases an internal count, then calls {@code onCrafting(item)}.
         * @param pStack the output - ie, iron ingots, and pickaxes, not ore and wood.
         */
        protected void onQuickCraft(ItemStack pStack, int pAmount) {
            this.removeCount += pAmount;
            this.checkTakeAchievements(pStack);
        }

        /**
         *
         * @param pStack the output - ie, iron ingots, and pickaxes, not ore and wood.
         */
        protected void checkTakeAchievements(ItemStack pStack) {
            pStack.onCraftedBy(this.player.level(), this.player, this.removeCount);
            this.removeCount = 0;
        }

        public void onTake(Player pPlayer, ItemStack pStack) {
            this.checkTakeAchievements(pStack);
            BuyDeal deal = this.slots.getActiveDeal();
            if (deal != null) {
                ItemStack stackA = this.slots.getItem(0);
                ItemStack stackB = this.slots.getItem(1);
                ItemStack stackC = this.slots.getItem(2);
                if (deal.makeDeal(stackA, stackB, stackC)
                        || deal.makeDeal(stackA, stackC, stackB)
                        || deal.makeDeal(stackB, stackA, stackC)
                        || deal.makeDeal(stackB, stackC, stackA)
                        || deal.makeDeal(stackC, stackA, stackB)
                        || deal.makeDeal(stackC, stackB, stackA)) {
                    this.citizen.notifyDealMade(deal);
                    pPlayer.awardStat(Stats.TRADED_WITH_VILLAGER);
                    this.slots.setItem(0, stackA);
                    this.slots.setItem(1, stackB);
                    this.slots.setItem(2, stackC);
                }
            }
        }
    }
}
