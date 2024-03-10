package com.dotteam.onceuponatown.command;

import com.dotteam.onceuponatown.town.Town;
import com.dotteam.onceuponatown.town.TownManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.List;

public class TownListCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("townlist").executes(context -> listTowns(context.getSource()));
    }

    private static int listTowns(CommandSourceStack source) {
        List<Town> towns = TownManager.getTownList(source.getLevel());
        if (towns != null) {
            if (towns.size() > 0) {
                source.sendSuccess(() -> Component.literal("Following towns found (" + towns.size() + ") :"), false);
                for (Town town : towns) {
                    var townInfo = (Component.literal(town.getName()).withStyle(ChatFormatting.YELLOW))
                            .append((Component.literal(" at ")).withStyle(ChatFormatting.WHITE))
                            .append((Component.literal(town.getCenterPosition().toShortString())).withStyle((style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + town.getCenterPosition().getX() + " " + town.getCenterPosition().getY() + " " + town.getCenterPosition().getZ()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport"))))));
                            //.append((Component.literal(" | ID : " + town.getUuid())).withStyle(ChatFormatting.WHITE));

                    source.sendSuccess(() -> townInfo, false);
                }
            } else {
                source.sendSuccess(() -> Component.literal("No towns found"), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }
}
