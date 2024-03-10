package com.dotteam.onceuponatown.trade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

public class BuyDeal {
    private final ItemStack inputA;
    private final ItemStack inputB;
    private final ItemStack inputC;
    private final ItemStack result;

    public BuyDeal(CompoundTag tag) {
        this.inputA = ItemStack.of(tag.getCompound("inputA"));
        this.inputB = ItemStack.of(tag.getCompound("inputB"));
        this.inputC = ItemStack.of(tag.getCompound("inputC"));
        this.result = ItemStack.of(tag.getCompound("result"));
    }

    private BuyDeal(Builder builder) {
        this.inputA = builder.inputA;
        this.inputB = builder.inputB;
        this.inputC = builder.inputC;
        this.result = builder.result;
    }

    public boolean isSatisfiedBy(ItemStack offerA, ItemStack offerB, ItemStack offerC) {
        return this.isRequiredItem(offerA, getInputA()) && offerA.getCount() >= getInputA().getCount()
            && this.isRequiredItem(offerB, getInputB()) && offerB.getCount() >= getInputB().getCount()
            && this.isRequiredItem(offerC, getInputC()) && offerC.getCount() >= getInputC().getCount();
    }

    private boolean isRequiredItem(ItemStack offer, ItemStack required) {
        if (required.isEmpty() && offer.isEmpty()) {
            return true;
        } else {
            ItemStack stack = offer.copy();
            if (stack.getItem().isDamageable(stack)) {
                stack.setDamageValue(stack.getDamageValue());
            }
            return ItemStack.isSameItem(stack, required) && (!required.hasTag() || stack.hasTag() && NbtUtils.compareNbt(required.getTag(), stack.getTag(), false));
        }
    }

    public boolean makeDeal(ItemStack offerA, ItemStack offerB, ItemStack offerC) {
        if (!this.isSatisfiedBy(offerA, offerB, offerC)) {
            return false;
        } else {
            offerA.shrink(getInputA().getCount());
            if (!getInputB().isEmpty()) {
                offerB.shrink(getInputB().getCount());
            }
            if (!getInputC().isEmpty()) {
                offerC.shrink(getInputC().getCount());
            }
            return true;
        }
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public CompoundTag createTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("inputA", this.inputA.save(new CompoundTag()));
        tag.put("inputB", this.inputB.save(new CompoundTag()));
        tag.put("inputC", this.inputC.save(new CompoundTag()));
        tag.put("result", this.result.save(new CompoundTag()));
        return tag;
    }

    public ItemStack getInputA() {return this.inputA;}

    public ItemStack getInputB() {return this.inputB;}

    public ItemStack getInputC() {return this.inputC;}

    public ItemStack getResult() {return this.result;}

    public static class Builder {
        private ItemStack inputA;
        private ItemStack inputB;
        private ItemStack inputC;
        private ItemStack result;

        public Builder(ItemStack inputA, ItemStack result) {
            this.inputA = inputA;
            this.inputB = ItemStack.EMPTY;
            this.inputC = ItemStack.EMPTY;
            this.result = result;
        }

        public Builder secondInput(ItemStack inputB) {
            this.inputB = inputB;
            return this;
        }

        public Builder thirdInput(ItemStack inputC) {
            this.inputC = inputC;
            return this;
        }

        public BuyDeal build() {
            return new BuyDeal(this);
        }
    }
}