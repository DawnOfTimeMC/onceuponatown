package com.dotteam.onceuponatown.command;

import com.dotteam.onceuponatown.town.Town;
import com.dotteam.onceuponatown.town.TownManager;
import com.dotteam.onceuponatown.town.map.MapBuild;
import com.dotteam.onceuponatown.town.map.MapGarden;
import com.dotteam.onceuponatown.town.map.MapPath;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;

public class GeneratePathCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("generatePath")
                .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> generatePath(context.getSource(), StringArgumentType.getString(context, "name"))));
    }

    private static int generatePath(CommandSourceStack source, String townId) {
        List<Town> towns = TownManager.getTownList(source.getLevel());
        for(Town town : towns) {
            if (town.getName().equals(townId)) {
                HashMap<Integer, MapBuild> mapBuilds = town.townMap.getBuilds();

                for (Integer key : mapBuilds.keySet()) {
                    MapBuild build = mapBuilds.get(key);
                    if (build instanceof MapPath path) {
                        int sizeX = path.getSizeX();
                        int sizeZ = path.getSizeZ();
                        BlockPos pos = path.getOriginPos();
                        for (int x = pos.getX(); x < pos.getX()+sizeX; ++x) {
                            for (int z = pos.getZ(); z < pos.getZ()+ sizeZ; ++z) {
                                town.level.setBlock(new BlockPos(x,pos.getY(),z), Blocks.DIRT_PATH.defaultBlockState(),2);
                            }
                        }
                    }
                    if (build instanceof MapGarden garden) {
                        int sizeX = garden.getSizeX();
                        int sizeZ = garden.getSizeZ();
                        BlockPos pos = garden.getOriginPos();
                        for (int x = pos.getX(); x < pos.getX()+sizeX; ++x) {
                            for (int z = pos.getZ(); z < pos.getZ()+ sizeZ; ++z) {
                                town.level.setBlock(new BlockPos(x,pos.getY()+1,z), Blocks.PINK_PETALS.defaultBlockState(),2);
                            }
                        }
                    }
                }
            }
            source.sendSuccess(() -> Component.literal("Path generated"), false);
        }
        return 1;
    }
}