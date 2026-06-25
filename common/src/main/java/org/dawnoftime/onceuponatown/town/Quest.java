package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class Quest {

    public static class Condition {
        public String type;
        public Item item;
        public int required;
        public boolean sendToStock = false;

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type);
            if (item != null) tag.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString());
            tag.putInt("Required", required);
            if (sendToStock) tag.putBoolean("SendToStock", true);
            return tag;
        }

        public static Condition fromNbt(CompoundTag tag) {
            Condition c = new Condition();
            c.type = tag.getString("Type");
            if (tag.contains("Item")) c.item = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("Item")));
            c.required = tag.getInt("Required");
            c.sendToStock = tag.contains("SendToStock") && tag.getBoolean("SendToStock");
            return c;
        }
    }

    public static class Reward {
        public String type;
        public Item item;
        public int amount;

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Type", type);
            if (item != null) tag.putString("Item", BuiltInRegistries.ITEM.getKey(item).toString());
            tag.putInt("Amount", amount);
            return tag;
        }

        public static Reward fromNbt(CompoundTag tag) {
            Reward r = new Reward();
            r.type = tag.getString("Type");
            if (tag.contains("Item")) r.item = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("Item")));
            r.amount = tag.getInt("Amount");
            return r;
        }
    }

    public String questId;
    public String defId;
    public String questType = "TASK";
    public final List<Condition> conditions = new ArrayList<>();
    public Reward reward;

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", questId);
        tag.putString("DefId", defId);
        tag.putString("QuestType", questType != null ? questType : "TASK");
        ListTag conds = new ListTag();
        for (Condition c : conditions) conds.add(c.toNbt());
        tag.put("Conditions", conds);
        if (reward != null) tag.put("Reward", reward.toNbt());
        return tag;
    }

    public static Quest fromNbt(CompoundTag tag) {
        Quest q = new Quest();
        q.questId = tag.getString("QuestId");
        q.defId = tag.getString("DefId");
        q.questType = tag.contains("QuestType") ? tag.getString("QuestType") : "TASK";
        tag.getList("Conditions", Tag.TAG_COMPOUND).forEach(t -> q.conditions.add(Condition.fromNbt((CompoundTag) t)));
        if (tag.contains("Reward")) q.reward = Reward.fromNbt(tag.getCompound("Reward"));
        return q;
    }
}
