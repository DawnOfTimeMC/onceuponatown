package com.dotteam.onceuponatown.world;

import com.dotteam.onceuponatown.building.BuildingType;
import com.dotteam.onceuponatown.building.HardCodedBuildingTypes;
import com.dotteam.onceuponatown.registry.OuatStructures;
import com.dotteam.onceuponatown.town.map.MapBuild;
import com.dotteam.onceuponatown.town.map.MapBuilding;
import com.dotteam.onceuponatown.town.map.TownMap;
import com.dotteam.onceuponatown.town.map.TownMapUtils;
import com.dotteam.onceuponatown.util.OuatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TownPieces {
    public static final int STARTER_PACK_SIZE = 6;
    public record TestBuilding(ResourceLocation name, int sizeXNorth, int sizeZNorth) {}
    public static final TestBuilding[] TEST_BUILDINGS = new TestBuilding[] {
            new TestBuilding(OuatUtils.resource("tests/house"), 8, 7),
            new TestBuilding(OuatUtils.resource("tests/duplex_house"), 11, 7),
            new TestBuilding(OuatUtils.resource("tests/tannery"), 10, 8),
            new TestBuilding(OuatUtils.resource("tests/builders_house"), 9, 8),
            new TestBuilding(OuatUtils.resource("tests/forge"), 11, 9),
            new TestBuilding(OuatUtils.resource("tests/church"), 7, 10),
    };

    public static void generateGridPlanTownPieces(StructureTemplateManager manager, BlockPos townCenterPos, StructurePieceAccessor pieces) {
        int[] starterPack = new int[STARTER_PACK_SIZE];
        for (int i = 0; i < starterPack.length; ++i) {
            int randomBuilding = Mth.nextInt(RandomSource.create(), 0, TEST_BUILDINGS.length - 1);
            starterPack[i] = randomBuilding;
        }

        TownMap townMap = createTownMap(townCenterPos, starterPack);
        List<MapBuilding> mapBuildingList = new ArrayList<>();
        HashMap<Integer, MapBuild> mapBuilds = townMap.getBuilds();
        for (Integer key : mapBuilds.keySet()) {
            MapBuild build = mapBuilds.get(key);
            if (build instanceof MapBuilding building) mapBuildingList.add(building);
        }

        for (int i = 0; i < starterPack.length; ++i) {
            MapBuilding building = mapBuildingList.get(i);
            Rotation rotation = rotFromDir(building.getDirection() != null ? building.getDirection() : Direction.NORTH);
            TownMapUtils.Corner corner = cornerFromDir(building.getDirection() != null ? building.getDirection() : Direction.NORTH);
            ResourceLocation buildingName = TEST_BUILDINGS[starterPack[i]].name;
            TownPiece piece = new TownPiece(manager, buildingName, building.getCornerPos(corner), rotation, HardCodedBuildingTypes.STANDARD_TYPE);
            if (i == starterPack.length - 1) {
                // Adding town data to the last piece for registering town later (after chunk generation)
                // Ugly code, find a better way to register town instances
                piece.addFutureTownData(new FutureTownData(townMap));
            }
            pieces.addPiece(piece);
        }
    }

    private static Rotation rotFromDir(Direction dir) {
        return switch (dir) {
            case NORTH, DOWN, UP -> Rotation.NONE;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            case EAST -> Rotation.CLOCKWISE_90;
        };
    }

    private static TownMapUtils.Corner cornerFromDir(Direction dir) {
        return switch (dir) {
            case NORTH, DOWN, UP -> TownMapUtils.Corner.NORTH_WEST;
            case SOUTH -> TownMapUtils.Corner.SOUTH_EAST;
            case WEST -> TownMapUtils.Corner.SOUTH_WEST;
            case EAST -> TownMapUtils.Corner.NORTH_EAST;
        };
    }

    private static TownMap createTownMap(BlockPos townCenterPos, int[] starterPack) {
        TownMap townMap = new TownMap(townCenterPos);
        for (int building : starterPack) {
            townMap.addBuilding(new MapBuilding(TEST_BUILDINGS[building].sizeXNorth, TEST_BUILDINGS[building].sizeZNorth));
        }
        return townMap;
    }

    public static class FutureTownData {
        private final TownMap townMap;

        public FutureTownData(TownMap townMap) {
            this.townMap = townMap;
        }

        public FutureTownData(CompoundTag tag) {
            this.townMap = new TownMap(tag.getCompound("TownMap"));
        }

        public void saveNBT(CompoundTag tag) {
            CompoundTag townMapTag = new CompoundTag();
            this.townMap.saveToNBT(townMapTag);
            tag.put("TownMap", townMapTag);
        }

        public TownMap getTownMap() {
            return townMap;
        }
    }

    public static class TownPiece extends TemplateStructurePiece {
        private BuildingType buildingType;
        private FutureTownData futureTownData;

        private TownPiece(StructureTemplateManager manager, ResourceLocation resourceLocation, BlockPos pos, Rotation rotation, BuildingType buildingType) {
            super(OuatStructures.TOWN_PIECE.get(), 0, manager, resourceLocation, resourceLocation.toString(), new StructurePlaceSettings().setRotation(rotation), pos);
            this.buildingType = buildingType;
        }

        public TownPiece(StructureTemplateManager manager, CompoundTag tag) {
            super(OuatStructures.TOWN_PIECE.get(), tag, manager, (p) -> new StructurePlaceSettings().setRotation(Rotation.valueOf(tag.getString("Rot"))));
            //TODO: Read building type
            CompoundTag compoundTag = tag.getCompound("FutureTownData");
            if (!compoundTag.isEmpty()) {
                this.futureTownData = new FutureTownData(compoundTag);
            }
        }

        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rot", this.placeSettings.getRotation().name());
            if(this.futureTownData != null) {
                CompoundTag futureTownData = new CompoundTag();
                this.futureTownData.saveNBT(futureTownData);
                tag.put("FutureTownData", futureTownData);
            }
        }

        private void addFutureTownData(FutureTownData data) {
            this.futureTownData = data;
        }

        public boolean shouldRegisterTownInstance() {
            return this.futureTownData != null;
        }

        public void setTownRegistered() {
            this.futureTownData = null;
        }

        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor levelAccessor, RandomSource random, BoundingBox box) {}

        public void postProcess(WorldGenLevel worldGenLevel, StructureManager manager, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
            super.postProcess(worldGenLevel, manager, chunkGenerator, random, box, chunkPos, pos);
        }

        public FutureTownData getFutureTownData() {
            return this.futureTownData;
        }

        public BuildingType getBuildingType() {
            return this.buildingType;
        }
    }
}
