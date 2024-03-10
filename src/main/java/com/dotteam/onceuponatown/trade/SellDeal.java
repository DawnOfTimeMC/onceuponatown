package com.dotteam.onceuponatown.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SellDeal {
    private final ItemStack good;
    private final ItemStack valueShards;
    private final ItemStack valueEmeralds;
    private final ItemStack valueBlocks;

    public SellDeal(CompoundTag tag) {
        this.good = ItemStack.of(tag.getCompound("good"));
        this.valueShards = ItemStack.of(tag.getCompound("valueShards"));
        this.valueEmeralds = ItemStack.of(tag.getCompound("valueEmeralds"));
        this.valueBlocks = ItemStack.of(tag.getCompound("valueBlocks"));
    }

    private SellDeal(Builder builder) {
        this.good = builder.good;
        this.valueShards = builder.valueShards;
        this.valueEmeralds = builder.valueEmeralds;
        this.valueBlocks = builder.valueBlocks;
    }

    public int amountSellable(ItemStack good) {
        if (!ItemStack.isSameItem(good, this.good)) {
            return 0;
        }
        return (good.getCount() / this.good.getCount());
    }

    public boolean makeDeal(ItemStack good) {
        int amountSellable = amountSellable(good);
        if (amountSellable == 0) {
            return false;
        } else {
            good.shrink(amountSellable * this.good.getCount());
            return true;
        }
    }

    public List<ItemStack> assemble() {
        List<ItemStack> value = new ArrayList<>();
        value.add(this.valueShards.copy());
        value.add(this.valueEmeralds.copy());
        value.add(this.valueBlocks.copy());
        return value;
    }

    public CompoundTag createTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("good", this.good.save(new CompoundTag()));
        tag.put("valueShards", this.valueShards.save(new CompoundTag()));
        tag.put("valueEmeralds", this.valueEmeralds.save(new CompoundTag()));
        tag.put("valueBlocks", this.valueBlocks.save(new CompoundTag()));
        return tag;
    }

    public ItemStack getGood() {return this.good;}

    public ItemStack getValueShards() {return this.valueShards;}

    public ItemStack getValueEmeralds() {return this.valueEmeralds;}

    public ItemStack getValueBlocks() {return this.valueBlocks;}

    public static class Builder {
        private ItemStack good;
        private ItemStack valueShards;
        private ItemStack valueEmeralds;
        private ItemStack valueBlocks;

        public Builder(ItemStack good, ItemStack valueEmeralds) {
            this.good = good;
            this.valueShards = ItemStack.EMPTY;
            this.valueEmeralds = valueEmeralds;
            this.valueBlocks = ItemStack.EMPTY;
        }

        public Builder valueShards(ItemStack valueShards) {
            this.valueShards = valueShards;
            return this;
        }

        public Builder valueBlocks(ItemStack valueBlocks) {
            this.valueBlocks = valueBlocks;
            return this;
        }

        public SellDeal build() {
            return new SellDeal(this);
        }
    }
}