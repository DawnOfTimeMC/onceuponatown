package com.dotteam.onceuponatown.mixin;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.config.OuatConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Mixin(LocateCommand.class)
public class LocateVillageMixin {
    private static List<ResourceLocation> DISABLED_VILLAGES = new ArrayList<>(Arrays.asList(
            new ResourceLocation("village_plains"),
            new ResourceLocation("village_desert"),
            new ResourceLocation("village_savanna"),
            new ResourceLocation("village_taiga"),
            new ResourceLocation("village_snowy")));

    private static final SimpleCommandExceptionType WRONG_PLAINS_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("Vanilla villages are replaced by the ")
                    .append(Component.literal("Once upon a town").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" mod.\nUse "))
                    .append(Component.literal("/locate structure " + OuatConstants.MOD_ID + ":plains_village").withStyle((style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/locate structure " + OuatConstants.MOD_ID + ":plains_village"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to locate"))))))
                    .append(Component.literal(" instead.")));

    private static final SimpleCommandExceptionType WRONG_DESERT_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("Vanilla villages are replaced by the ")
                    .append(Component.literal("Once upon a town").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" mod.\nUse "))
                    .append(Component.literal("/locate structure " + OuatConstants.MOD_ID + ":desert_village").withStyle((style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/locate structure " + OuatConstants.MOD_ID + ":desert_village"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to locate"))))))
                    .append(Component.literal(" instead.")));

    private static final SimpleCommandExceptionType WRONG_SAVANNA_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("Vanilla villages are replaced by the ")
                    .append(Component.literal("Once upon a town").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" mod.\nUse "))
                    .append(Component.literal("/locate structure " + OuatConstants.MOD_ID + ":savanna_village").withStyle((style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/locate structure " + OuatConstants.MOD_ID + ":savanna_village"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to locate"))))))
                    .append(Component.literal(" instead.")));

    private static final SimpleCommandExceptionType WRONG_TAIGA_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("Vanilla villages are replaced by the ")
                    .append(Component.literal("Once upon a town").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" mod.\nUse "))
                    .append(Component.literal("/locate structure " + OuatConstants.MOD_ID + ":taiga_village").withStyle((style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/locate structure " + OuatConstants.MOD_ID + ":taiga_village"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to locate"))))))
                    .append(Component.literal(" instead.")));

    private static final SimpleCommandExceptionType WRONG_SNOWY_VILLAGE =
            new SimpleCommandExceptionType(Component.literal("Vanilla villages are replaced by the ")
                    .append(Component.literal("Once upon a town").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" mod.\nUse "))
                    .append(Component.literal("/locate structure " + OuatConstants.MOD_ID + ":snowy_village").withStyle((style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/locate structure " + OuatConstants.MOD_ID + ":snowy_village"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to locate"))))))
                    .append(Component.literal(" instead.")));



    @Inject(method = "locateStructure", at = @At(value = "HEAD"))
    private static void notifyWrongVillageCommand(CommandSourceStack sourceStack, ResourceOrTagKeyArgument.Result<Structure> result, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if (OuatConfig.disableVanillaVillages) {
            Optional<ResourceKey<Structure>> optional = result.unwrap().left();
            for (ResourceLocation resourceLocation : DISABLED_VILLAGES) {
                if (optional.isPresent() && optional.get().location().equals(resourceLocation)) {
                    switch (resourceLocation.getPath()) {
                        case "village_plains" -> throw WRONG_PLAINS_VILLAGE.create();
                        case "village_desert" -> throw WRONG_DESERT_VILLAGE.create();
                        case "village_savanna" -> throw WRONG_SAVANNA_VILLAGE.create();
                        case "village_taiga" -> throw WRONG_TAIGA_VILLAGE.create();
                        case "village_snowy" -> throw WRONG_SNOWY_VILLAGE.create();
                    }
                }
            }
        }
    }
}
