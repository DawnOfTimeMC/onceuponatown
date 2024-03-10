package com.dotteam.onceuponatown.world;

import com.dotteam.onceuponatown.building.Building;
import com.dotteam.onceuponatown.building.BuildingType;
import com.dotteam.onceuponatown.culture.Culture;
import com.dotteam.onceuponatown.registry.OuatStructures;
import com.dotteam.onceuponatown.town.Town;
import com.dotteam.onceuponatown.town.TownManager;
import com.dotteam.onceuponatown.town.map.TownMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

public class TownStructure extends Structure {
    public final String cultureID;
    public static final Codec<TownStructure> CODEC = RecordCodecBuilder.create((p) -> p.group(settingsCodec(p), Codec.STRING.fieldOf("culture").forGetter((p2) -> p2.cultureID)).apply(p, TownStructure::new));

    public TownStructure(StructureSettings settings, String cultureID) {
        super(settings);
        this.cultureID = cultureID;
    }

    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, (builder) -> this.generatePieces(builder, context));
    }

    @Override
    public void afterPlace(WorldGenLevel worldGenLevel, StructureManager structureManager, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox boundingBox, ChunkPos chunkPos, PiecesContainer piecesContainer) {
        for(StructurePiece piece : piecesContainer.pieces()) {
            if (piece instanceof TownPieces.TownPiece townPiece && townPiece.shouldRegisterTownInstance()) {
                registerTownInstance(worldGenLevel.getLevel(), townPiece.getFutureTownData().getTownMap(), piecesContainer);
                townPiece.setTownRegistered();
                return;
            }
        }
    }

    private void registerTownInstance(ServerLevel level, TownMap townMap, PiecesContainer piecesContainer) {
        //OuatLog.info("Registering new town at " + townMap.getTownCenter());
        String biome = level.getBiome(townMap.getTownCenter()).unwrapKey().get().location().getPath();
        Town town = TownManager.createTownWorldGen(level, Culture.PLAINS, biome, townMap);
        if (town != null) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(town.getName() + " discovered at " + town.getCenterPosition().toShortString()), false);
            for (StructurePiece piece : piecesContainer.pieces()) {
                if (piece instanceof TownPieces.TownPiece townPiece) {
                    BuildingType buildingType = townPiece.getBuildingType();
                    Building building = Building.create(buildingType);
                    town.addBuilding(building);
                }
            }
        }
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        int townHeight = context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());
        TownPieces.generateGridPlanTownPieces(context.structureTemplateManager(), townCenterPos, builder);
    }

    public StructureType<?> type() {
        return OuatStructures.TOWN_STRUCTURE.get();
    }
}
