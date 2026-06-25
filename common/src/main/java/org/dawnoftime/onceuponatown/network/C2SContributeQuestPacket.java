package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Quest;
import org.dawnoftime.onceuponatown.town.Town;

// Sent when the player clicks the Contribute button on a TASK quest.
// The server validates inventory, takes all required items, and grants the reward atomically.
public record C2SContributeQuestPacket(BlockPos anchorPos, String questId) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_contribute_quest");

    public static C2SContributeQuestPacket decode(FriendlyByteBuf buf) {
        return new C2SContributeQuestPacket(buf.readBlockPos(), buf.readUtf(64));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeUtf(questId, 64);
    }

    public static class Handler {
        public static void handle(C2SContributeQuestPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            if (!(player.containerMenu instanceof TownHubMenu)) return;

            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;

            Quest quest = null;
            for (Quest q : town.getActiveQuests()) {
                if (q.questId.equals(packet.questId())) { quest = q; break; }
            }
            if (quest == null) return;

            // Validate player has all required items before taking anything
            for (Quest.Condition cond : quest.conditions) {
                if ("DELIVERY".equals(cond.type) && cond.item != null) {
                    if (countInInventory(player, cond.item) < cond.required) return;
                }
            }

            // Take items and optionally route to stock
            boolean stockUpdated = false;
            for (Quest.Condition cond : quest.conditions) {
                if (!"DELIVERY".equals(cond.type) || cond.item == null) continue;
                int toRemove = cond.required;
                for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (!s.isEmpty() && s.getItem() == cond.item) {
                        int r = Math.min(toRemove, s.getCount());
                        s.shrink(r);
                        toRemove -= r;
                        if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                if (cond.sendToStock) {
                    town.tryAddToStockUnchecked(cond.item, cond.required);
                    stockUpdated = true;
                }
            }

            // Give reward directly to player inventory, drop if full
            if (quest.reward != null && "PLAYER".equals(quest.reward.type) && quest.reward.item != null) {
                ItemStack rewardStack = new ItemStack(quest.reward.item, quest.reward.amount);
                if (!player.getInventory().add(rewardStack)) {
                    player.drop(rewardStack, false);
                }
            }

            town.removeQuest(packet.questId());
            town.getQuestDefLastCompleted().put(quest.defId, level.getGameTime());
            LevelTowns.get(level).markDirty();
            NetworkHelper.pushQuestUpdateToWatchers(level, town, packet.anchorPos());
            if (stockUpdated) NetworkHelper.pushStockToWatchers(level, town, packet.anchorPos());
        }

        private static int countInInventory(ServerPlayer player, Item item) {
            int count = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.getItem() == item) count += s.getCount();
            }
            return count;
        }
    }
}
