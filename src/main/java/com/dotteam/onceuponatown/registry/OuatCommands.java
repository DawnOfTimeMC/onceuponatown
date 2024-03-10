package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.command.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class OuatCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(OuatConstants.MOD_ID)
                .then(TownListCommand.register())
                .then(GeneratePathCommand.register());
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(builder);
        dispatcher.register(Commands.literal("ouat").redirect(node));
    }
}
