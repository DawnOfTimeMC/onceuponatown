package com.dotteam.onceuponatown.menu;

import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.registry.OuatItems;
import com.dotteam.onceuponatown.trade.SellDeal;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SellContainer implements Container {
    private final InteractableCitizen citizen;
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(11, ItemStack.EMPTY);
    @Nullable
    private SellDeal activeDeal;
    private int selectedDealIndex;
    private static final int GOOD_1_SLOT = 0;
    private static final int GOOD_2_SLOT = 1;
    private static final int GOOD_3_SLOT = 2;
    private static final int GOOD_4_SLOT = 3;
    private static final int GOOD_5_SLOT = 4;
    private static final int GOOD_6_SLOT = 5;
    private static final int GOOD_7_SLOT = 6;
    private static final int GOOD_8_SLOT = 7;
    public static final int VALUE_SHARDS_SLOT = 8;
    public static final int VALUE_EMERALDS_SLOT = 9;
    public static final int VALUE_BLOCKS_SLOT = 10;
    List<ItemStack> futureStacks = new ArrayList<>();
    public boolean hasConcludedTrade;

    public SellContainer(InteractableCitizen citizen) {
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
        if ((index == VALUE_SHARDS_SLOT || index == VALUE_EMERALDS_SLOT || index == VALUE_BLOCKS_SLOT) && !stack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, index, stack.getCount());
        } else {
            ItemStack stackInGridSlot = ContainerHelper.removeItem(this.itemStacks, index, count);
            if (!stackInGridSlot.isEmpty() && this.isGoodGridSlot(index) && !hasConcludedTrade) {
                this.updateValue();
            }
            return stackInGridSlot;
        }
    }

    private boolean isGoodGridSlot(int slot) {
        return slot >= GOOD_1_SLOT & slot <= GOOD_8_SLOT;
    }

    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.itemStacks, index);
    }

    public void setItem(int index, ItemStack stack) {
        //ModLogger.info(String.valueOf(index));
        this.itemStacks.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        if (this.isGoodGridSlot(index) && !this.hasConcludedTrade) {
            this.updateValue();
        }
        // 168 2 40
        // 458 7 10
        // 108 1 44
    }

    public boolean stillValid(Player player) {
        return this.citizen.getInteractingPlayer() == player;
    }

    public void setChanged() {
        //this.updateValue();
    }

    public void updateValue() {
        /*
        //ModLogger.info("UPDATE VALUE");
        List<SellDeal> deals = this.citizen.getSellDeals();
        if (deals.isEmpty()) return;
        int valueShards = 0;
        int valueEmeralds = 0;
        int valueEmeraldBlocks = 0;
        NonNullList<ItemStack> newStacks = NonNullList.withSize(8, ItemStack.EMPTY);
        for (int i = GOOD_1_SLOT; i < GOOD_8_SLOT; ++i) {
            newStacks.set(i, this.itemStacks.get(i));
        }
        for (SellDeal deal : deals) {
            for (int i = GOOD_1_SLOT; i <= GOOD_8_SLOT; ++i) {
                ItemStack wantedItem = deal.getGood();
                ItemStack stackInGoodSlot = this.itemStacks.get(i);
                if (!stackInGoodSlot.isEmpty() && ItemStack.isSameItemSameTags(stackInGoodSlot, wantedItem)) {
                    int remainingShards = ModItems.EMERALD_SHARD.get().getMaxStackSize() - valueShards;
                    int remainingEmeralds = Items.EMERALD.getMaxStackSize() - valueEmeralds;
                    int remainingBlocks = Items.EMERALD_BLOCK.getMaxStackSize() - valueEmeraldBlocks;

                    int maxSellable = 64;
                    if (deal.getValueShards().getCount() > 0) {
                        maxSellable = remainingShards / deal.getValueShards().getCount();
                    }
                    if (deal.getValueEmeralds().getCount() > 0) {
                        maxSellable = Math.min(maxSellable, remainingEmeralds / deal.getValueEmeralds().getCount());
                    }
                    if (deal.getValueBlocks().getCount() > 0) {
                        maxSellable = Math.min(maxSellable, remainingBlocks / deal.getValueBlocks().getCount());
                    }
                    ModLogger.info(String.valueOf(maxSellable));

                    int timesSellable = stackInGoodSlot.getCount() / wantedItem.getCount();
                    timesSellable = Math.min(timesSellable, maxSellable);
                    if (timesSellable > 0) {
                        ItemStack newStack = stackInGoodSlot.copy();
                        newStack.shrink(timesSellable * wantedItem.getCount());
                        newStacks.set(i, newStack);
                        valueEmeralds = valueEmeralds + (deal.getValueEmeralds().getCount() * timesSellable);
                        valueShards = valueShards + (deal.getValueShards().getCount() * timesSellable);
                        valueEmeraldBlocks = valueEmeraldBlocks + (deal.getValueBlocks().getCount() * timesSellable);
                    }
                }

            }
        }
        ItemStack shards = (valueShards <= 0) ? ItemStack.EMPTY : new ItemStack(ModItems.EMERALD_SHARD.get(), valueShards);
        ItemStack emeralds = (valueEmeralds <= 0) ? ItemStack.EMPTY : new ItemStack(Items.EMERALD, valueEmeralds);
        ItemStack emeraldBlocks = (valueEmeraldBlocks <= 0) ? ItemStack.EMPTY : new ItemStack(Items.EMERALD_BLOCK, valueEmeraldBlocks);
        this.itemStacks.set(VALUE_SHARDS_SLOT, shards);
        this.itemStacks.set(VALUE_EMERALDS_SLOT, emeralds);
        this.itemStacks.set(VALUE_BLOCKS_SLOT, emeraldBlocks);
        this.futureStacks = newStacks;

         */
        List<SellDeal> deals = this.citizen.getSellDeals();
        if (deals.isEmpty()) return;
        int valueShards = 0;
        int valueEmeralds = 0;
        int valueEmeraldBlocks = 0;
        List<ItemStack> newStacks = new ArrayList<>();
        for (int i = GOOD_1_SLOT; i <= GOOD_8_SLOT; ++i) {
            newStacks.add(i, this.itemStacks.get(i).copy());
        }
        for (SellDeal deal : deals) {
            ItemStack wantedItem = deal.getGood();
            int available = 0;
            for (int i = GOOD_1_SLOT; i <= GOOD_8_SLOT; ++i) {
                ItemStack stackInGoodSlot = this.itemStacks.get(i);
                if (!stackInGoodSlot.isEmpty() && ItemStack.isSameItemSameTags(stackInGoodSlot, wantedItem)) {
                    available = available + stackInGoodSlot.getCount();
                }
            }
            if (available == 0) continue;
            int timesSellable = available / wantedItem.getCount();

            int maxShards = OuatItems.EMERALD_SHARD.get().getMaxStackSize() - valueShards;
            int maxEmeralds = Items.EMERALD.getMaxStackSize() - valueEmeralds;
            int maxBlocks = Items.EMERALD_BLOCK.getMaxStackSize() - valueEmeraldBlocks;

            int maxSellable = 64;
            if (deal.getValueShards().getCount() > 0) {
                maxSellable = maxShards / deal.getValueShards().getCount();
            }
            if (deal.getValueEmeralds().getCount() > 0) {
                maxSellable = Math.min(maxSellable, maxEmeralds / deal.getValueEmeralds().getCount());
            }
            if (deal.getValueBlocks().getCount() > 0) {
                maxSellable = Math.min(maxSellable, maxBlocks / deal.getValueBlocks().getCount());
            }

            int finalSellable = Math.min(timesSellable, maxSellable);
            if (finalSellable > 0) {
                int totalShrinkable = finalSellable * wantedItem.getCount();
                for (int i = GOOD_8_SLOT; i >= GOOD_1_SLOT; --i) {
                    if (totalShrinkable <= 0) {
                        break;
                    }
                    ItemStack stack = newStacks.get(i);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, wantedItem)) {
                        int decrement = Math.min(totalShrinkable, stack.getCount());
                        stack.shrink(decrement);
                        if (stack.isEmpty()) {
                        stack = ItemStack.EMPTY;
                        }
                        totalShrinkable = totalShrinkable - decrement;
                    }
                }
                /*
                int newCount = available - (finalSellable * wantedItem.getCount());
                int fullStacks = newCount / wantedItem.getMaxStackSize();
                int remainder = newCount % wantedItem.getMaxStackSize();

                for (int i = 0; i < fullStacks; ++i) {
                    ItemStack copy = deal.getGood().copy();
                    copy.setCount(deal.getGood().getMaxStackSize());
                    newStacks.add(copy);
                }
                ItemStack leftOver = deal.getGood().copy();
                leftOver.setCount(remainder);
                newStacks.add(leftOver);
                */
                valueEmeralds = valueEmeralds + (deal.getValueEmeralds().getCount() * finalSellable);
                valueShards = valueShards + (deal.getValueShards().getCount() * finalSellable);
                valueEmeraldBlocks = valueEmeraldBlocks + (deal.getValueBlocks().getCount() * finalSellable);

            }
        }

        ItemStack shards = (valueShards <= 0) ? ItemStack.EMPTY : new ItemStack(OuatItems.EMERALD_SHARD.get(), valueShards);
        ItemStack emeralds = (valueEmeralds <= 0) ? ItemStack.EMPTY : new ItemStack(Items.EMERALD, valueEmeralds);
        ItemStack emeraldBlocks = (valueEmeraldBlocks <= 0) ? ItemStack.EMPTY : new ItemStack(Items.EMERALD_BLOCK, valueEmeraldBlocks);
        this.itemStacks.set(VALUE_SHARDS_SLOT, shards);
        this.itemStacks.set(VALUE_EMERALDS_SLOT, emeralds);
        this.itemStacks.set(VALUE_BLOCKS_SLOT, emeraldBlocks);
        this.futureStacks = newStacks;
    }

    public void onValueSlotTake() {

        if (!this.hasConcludedTrade) {
            if (this.citizen instanceof Citizen) {
                Citizen entity = citizen.getCitizen();
                entity.playSound(SoundEvents.VILLAGER_YES, 1.0F,1.0F);
            }
            this.hasConcludedTrade = true;
            for (int i = GOOD_1_SLOT; i <= GOOD_8_SLOT; ++i) {
                this.itemStacks.set(i, this.futureStacks.get(i));
            }
        }
        if (this.itemStacks.get(VALUE_SHARDS_SLOT).isEmpty() && this.itemStacks.get(VALUE_EMERALDS_SLOT).isEmpty() && this.itemStacks.get(VALUE_BLOCKS_SLOT).isEmpty()) {
            this.hasConcludedTrade = false;
            updateValue();
        }
    }
    public void clearContent() {
        this.itemStacks.clear();
    }
}
