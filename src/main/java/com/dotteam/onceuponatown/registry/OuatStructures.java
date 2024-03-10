package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.world.TownPieces;
import com.dotteam.onceuponatown.world.TownStructure;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class OuatStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, OuatConstants.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, OuatConstants.MOD_ID);

    public static final RegistryObject<StructureType<TownStructure>> TOWN_STRUCTURE = STRUCTURE_TYPES.register("town", () -> get(TownStructure.CODEC));
    public static final RegistryObject<StructurePieceType> TOWN_PIECE = STRUCTURE_PIECES.register("town_piece", () -> (StructurePieceType.StructureTemplateType) TownPieces.TownPiece::new);

    private static <T extends Structure> StructureType<T> get(Codec<T> codec) {
        return () -> codec;
    }

    public static void register(IEventBus bus) {
        STRUCTURE_TYPES.register(bus);
        STRUCTURE_PIECES.register(bus);
    }
}

