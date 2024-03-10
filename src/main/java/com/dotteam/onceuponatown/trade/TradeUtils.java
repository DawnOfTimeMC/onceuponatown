package com.dotteam.onceuponatown.trade;

import com.dotteam.onceuponatown.registry.OuatItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TradeUtils {
    public static BuyDeal buyDeal(Item bought, int emeraldCost) {
        return new BuyDeal.Builder(new ItemStack(Items.EMERALD,emeraldCost), new ItemStack(bought,1)).build();
    }

    public static BuyDeal buyDeal(Item bought, int amount, int costShards, int costEmeralds, int costBlocks) {
        return new BuyDeal.Builder(new ItemStack(OuatItems.EMERALD_SHARD.get(), costShards), new ItemStack(bought,amount))
                .secondInput(new ItemStack(Items.EMERALD, costEmeralds))
                .thirdInput(new ItemStack(Items.EMERALD_BLOCK, costBlocks))
                .build();
    }

    public static SellDeal sellDeal(Item good, int valueEmeralds) {
        return new SellDeal.Builder(new ItemStack(good,1), new ItemStack(Items.EMERALD, valueEmeralds)).build();
    }

    public static SellDeal sellDeal(Item good, int amount, int valueShards, int valueEmeralds, int valueBlocks) {
        return new SellDeal.Builder(new ItemStack(good, amount), new ItemStack(Items.EMERALD, valueEmeralds))
                .valueShards(new ItemStack(OuatItems.EMERALD_SHARD.get(), valueShards))
                .valueBlocks(new ItemStack(Items.EMERALD_BLOCK, valueBlocks))
                .build();
    }

    public static void writeBuyDealsToStream(List<BuyDeal> deals, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeCollection(deals, (buffer, deal) -> {
            buffer.writeItem(deal.getInputA());
            buffer.writeItem(deal.getInputB());
            buffer.writeItem(deal.getInputC());
            buffer.writeItem(deal.getResult());
        });
    }
    public static List<BuyDeal> createBuyDealsFromStream(FriendlyByteBuf friendlyByteBuf) {
        return friendlyByteBuf.readCollection(ArrayList::new, (buffer) -> {
            ItemStack inputA = buffer.readItem();
            ItemStack inputB = buffer.readItem();
            ItemStack inputC = buffer.readItem();
            ItemStack result = buffer.readItem();
            return new BuyDeal.Builder(inputA, result).secondInput(inputB).thirdInput(inputC).build();
        });
    }

    public static void writeSellDealsToStream(List<SellDeal> deals, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeCollection(deals, (buffer, deal) -> {
            buffer.writeItem(deal.getGood());
            buffer.writeItem(deal.getValueShards());
            buffer.writeItem(deal.getValueEmeralds());
            buffer.writeItem(deal.getValueBlocks());
        });
    }

    public static List<SellDeal> createSellDealsFromStream(FriendlyByteBuf friendlyByteBuf) {
        return friendlyByteBuf.readCollection(ArrayList::new, (buffer) -> {
            ItemStack good = buffer.readItem();
            ItemStack valueShards = buffer.readItem();
            ItemStack valueEmeralds = buffer.readItem();
            ItemStack valueBlocks = buffer.readItem();
            return new SellDeal.Builder(good, valueEmeralds).valueShards(valueShards).valueBlocks(valueBlocks).build();
        });
    }

    public static CompoundTag createBuyDealsTag(List<BuyDeal> deals) {
        CompoundTag tag = new CompoundTag();
        ListTag buyDealsTag = new ListTag();
        for(int i = 0; i < deals.size(); ++i) {
            BuyDeal deal = deals.get(i);
            buyDealsTag.add(deal.createTag());
        }
        tag.put("BuyDeals", buyDealsTag);
        return tag;
    }

    public static List<BuyDeal> createBuyDealsFromTag(CompoundTag nbt) {
        List<BuyDeal> deals = new ArrayList<>();
        ListTag listTag = nbt.getList("BuyDeals", 10);
        for (int i = 0; i < listTag.size(); ++i) {
            deals.add(new BuyDeal(listTag.getCompound(i)));
        }
        return deals;
    }

    public static CompoundTag createSellDealsTag(List<SellDeal> deals) {
        CompoundTag tag = new CompoundTag();
        ListTag sellDealsTag = new ListTag();
        for(int i = 0; i < deals.size(); ++i) {
            SellDeal deal = deals.get(i);
            sellDealsTag.add(deal.createTag());
        }
        tag.put("SellDeals", sellDealsTag);
        return tag;
    }

    public static List<SellDeal> createSellDealsFromTag(CompoundTag nbt) {
        List<SellDeal> deals = new ArrayList<>();
        ListTag listTag = nbt.getList("SellDeals", 10);
        for (int i = 0; i < listTag.size(); ++i) {
            deals.add(new SellDeal(listTag.getCompound(i)));
        }
        return deals;
    }

    @Nullable
    public static BuyDeal getBuyDealFor(List<BuyDeal> deals, ItemStack stackA, ItemStack stackB, ItemStack stackC, int index) {
        if (index > 0 && index < deals.size()) {
            BuyDeal deal = deals.get(index);
            return deal.isSatisfiedBy(stackA, stackB, stackC) ? deal : null;
        } else {
            for(int i = 0; i < deals.size(); ++i) {
                BuyDeal deal = deals.get(i);
                if (deal.isSatisfiedBy(stackA, stackB, stackC)) {
                    return deal;
                }
            }
            return null;
        }
    }
}
