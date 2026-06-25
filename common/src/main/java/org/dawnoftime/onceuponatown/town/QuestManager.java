package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public class QuestManager {

    // Builds and returns a Quest instance for the given def. Always succeeds.
    public static Quest buildFromDef(QuestDef def) {
        Quest q = new Quest();
        q.questId = UUID.randomUUID().toString().substring(0, 8);
        q.defId = def.id();
        q.questType = def.type() != null ? def.type() : "TASK";
        for (QuestDef.ConditionTemplate ct : def.conditions()) {
            Quest.Condition c = new Quest.Condition();
            c.type = ct.type();
            if (ct.item() != null) c.item = BuiltInRegistries.ITEM.get(new ResourceLocation(ct.item()));
            c.required = ct.required();
            c.sendToStock = ct.sendToStock();
            q.conditions.add(c);
        }
        if (def.reward() != null) {
            Quest.Reward r = new Quest.Reward();
            r.type = def.reward().type();
            if (def.reward().item() != null) r.item = BuiltInRegistries.ITEM.get(new ResourceLocation(def.reward().item()));
            r.amount = def.reward().amount();
            q.reward = r;
        }
        return q;
    }

    // Returns true if the given def already has an active quest instance in this town.
    public static boolean isAlreadyActive(QuestDef def, List<Quest> activeQuests) {
        for (Quest q : activeQuests) {
            if (q.defId.equals(def.id())) return true;
        }
        return false;
    }
}
