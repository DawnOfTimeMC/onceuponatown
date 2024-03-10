package com.dotteam.onceuponatown.menu;

import com.dotteam.onceuponatown.trade.BuyDeal;
import com.dotteam.onceuponatown.trade.TradeUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.ObjectUtils;

import javax.annotation.Nullable;
import java.util.List;

public class BuyContainer implements Container {
    private final InteractableCitizen citizen;
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
    @Nullable
    private BuyDeal activeDeal;
    private int selectedDealIndex;
    private static final int INPUT_A = 0;
    private static final int INPUT_B = 1;
    private static final int INPUT_C = 2;
    private static final int RESULT = 3;

    public BuyContainer(InteractableCitizen citizen) {
        this.citizen = citizen;
    }

    public int getContainerSize() {
        return this.itemStacks.size();
    }

    public boolean isEmpty() {
        for(ItemStack stack : this.itemStacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public ItemStack getItem(int index) {
        return this.itemStacks.get(index);
    }

    public ItemStack removeItem(int index, int count) {
        ItemStack stack = this.itemStacks.get(index);
        if (index == RESULT && !stack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, index, stack.getCount());
        } else {
            ItemStack itemstack1 = ContainerHelper.removeItem(this.itemStacks, index, count);
            if (!itemstack1.isEmpty() && this.isPaymentSlot(index)) {
                this.updateResultItem();
            }

            return itemstack1;
        }
    }

    private boolean isPaymentSlot(int slot) {
        return slot == INPUT_A || slot == INPUT_B || slot == INPUT_C;
    }

    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.itemStacks, index);
    }

    public void setItem(int index, ItemStack stack) {
        this.itemStacks.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        if (this.isPaymentSlot(index)) {
            this.updateResultItem();
        }

    }

    public boolean stillValid(Player pPlayer) {
        return this.citizen.getInteractingPlayer() == pPlayer;
    }

    public void setChanged() {
        this.updateResultItem();
    }

    public void updateResultItem() {
        this.activeDeal = null;
        ItemStack stackInSlotA = this.itemStacks.get(INPUT_A);
        ItemStack stackInSlotB = this.itemStacks.get(INPUT_B);
        ItemStack stackInSlotC = this.itemStacks.get(INPUT_C);

        if (stackInSlotA.isEmpty() && stackInSlotB.isEmpty() && stackInSlotC.isEmpty()) {
            this.setItem(RESULT, ItemStack.EMPTY);
        } else {
            List<BuyDeal> deals = this.citizen.getBuyDeals();
            if (!deals.isEmpty()) {
                BuyDeal deal1 = TradeUtils.getBuyDealFor(deals, stackInSlotA, stackInSlotB, stackInSlotC, this.selectedDealIndex);
                BuyDeal deal2 = TradeUtils.getBuyDealFor(deals, stackInSlotA, stackInSlotC, stackInSlotB, this.selectedDealIndex);
                BuyDeal deal3 = TradeUtils.getBuyDealFor(deals, stackInSlotB, stackInSlotA, stackInSlotC, this.selectedDealIndex);
                BuyDeal deal4 = TradeUtils.getBuyDealFor(deals, stackInSlotB, stackInSlotC, stackInSlotA, this.selectedDealIndex);
                BuyDeal deal5 = TradeUtils.getBuyDealFor(deals, stackInSlotC, stackInSlotA, stackInSlotB, this.selectedDealIndex);
                BuyDeal deal6 = TradeUtils.getBuyDealFor(deals, stackInSlotC, stackInSlotB, stackInSlotA, this.selectedDealIndex);

                BuyDeal deal = ObjectUtils.firstNonNull(deal1, deal2, deal3, deal4, deal5, deal6);

                if (deal != null) {
                    this.activeDeal = deal;
                    this.setItem(RESULT, deal.assemble());
                } else {
                    this.setItem(RESULT, ItemStack.EMPTY);
                }
            }
            //this.traderComponent.notifyTradeUpdated(this.getItem(2));
        }
    }

    @Nullable
    public BuyDeal getActiveDeal() {
        return this.activeDeal;
    }

    public void setSelectedDealIndex(int pCurrentRecipeIndex) {
        this.selectedDealIndex = pCurrentRecipeIndex;
        this.updateResultItem();
    }

    public void clearContent() {
        this.itemStacks.clear();
    }

}
