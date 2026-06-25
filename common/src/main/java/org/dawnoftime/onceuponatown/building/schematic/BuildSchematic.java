package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;

public class BuildSchematic {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSchematic.class);

    // Skips AIR and STRUCTURE_VOID blocks during placement.
    // STRUCTURE_VOID is supposed to be a no-op natively, but behaviour varies by MC/loader version.
    // Explicitly ignoring it here guarantees existing terrain is never overwritten at void positions.
    private static final BlockIgnoreProcessor SKIP_AIR = new BlockIgnoreProcessor(List.of(Blocks.AIR, Blocks.STRUCTURE_VOID));

    // Filter A: blocks the terrain scan skips past when descending to find ground. Not deleted, not placed.
    private static final Set<Block> SCAN_IGNORE_BLOCKS;
    static {
        Set<Block> s = new HashSet<>();
        s.add(Blocks.OAK_LEAVES); s.add(Blocks.BIRCH_LEAVES); s.add(Blocks.SPRUCE_LEAVES);
        s.add(Blocks.JUNGLE_LEAVES); s.add(Blocks.ACACIA_LEAVES); s.add(Blocks.DARK_OAK_LEAVES);
        s.add(Blocks.MANGROVE_LEAVES); s.add(Blocks.CHERRY_LEAVES);
        s.add(Blocks.AZALEA_LEAVES); s.add(Blocks.FLOWERING_AZALEA_LEAVES);
        s.add(Blocks.GRASS); s.add(Blocks.TALL_GRASS); s.add(Blocks.FERN); s.add(Blocks.LARGE_FERN);
        s.add(Blocks.DEAD_BUSH); s.add(Blocks.SEAGRASS); s.add(Blocks.TALL_SEAGRASS);
        s.add(Blocks.KELP); s.add(Blocks.KELP_PLANT);
        s.add(Blocks.DANDELION); s.add(Blocks.POPPY); s.add(Blocks.BLUE_ORCHID); s.add(Blocks.ALLIUM);
        s.add(Blocks.AZURE_BLUET); s.add(Blocks.RED_TULIP); s.add(Blocks.ORANGE_TULIP);
        s.add(Blocks.WHITE_TULIP); s.add(Blocks.PINK_TULIP); s.add(Blocks.OXEYE_DAISY);
        s.add(Blocks.CORNFLOWER); s.add(Blocks.LILY_OF_THE_VALLEY); s.add(Blocks.WITHER_ROSE);
        s.add(Blocks.SUNFLOWER); s.add(Blocks.LILAC); s.add(Blocks.ROSE_BUSH); s.add(Blocks.PEONY);
        s.add(Blocks.SNOW); s.add(Blocks.VINE); s.add(Blocks.HANGING_ROOTS);
        // Tree trunks and woody stems: scan must pass through them to reach actual ground.
        // Without these, paths placed under tree canopy land on trunk logs instead of dirt.
        s.add(Blocks.OAK_LOG); s.add(Blocks.BIRCH_LOG); s.add(Blocks.SPRUCE_LOG);
        s.add(Blocks.JUNGLE_LOG); s.add(Blocks.ACACIA_LOG); s.add(Blocks.DARK_OAK_LOG);
        s.add(Blocks.MANGROVE_LOG); s.add(Blocks.CHERRY_LOG);
        s.add(Blocks.OAK_WOOD); s.add(Blocks.BIRCH_WOOD); s.add(Blocks.SPRUCE_WOOD);
        s.add(Blocks.JUNGLE_WOOD); s.add(Blocks.ACACIA_WOOD); s.add(Blocks.DARK_OAK_WOOD);
        s.add(Blocks.MANGROVE_WOOD);
        s.add(Blocks.MUSHROOM_STEM); s.add(Blocks.BROWN_MUSHROOM_BLOCK); s.add(Blocks.RED_MUSHROOM_BLOCK);
        s.add(Blocks.BAMBOO); s.add(Blocks.SUGAR_CANE); s.add(Blocks.CACTUS);
        // Connection-point artifacts: jigsaw blocks (unconsumed connectors) can sit above
        // natural terrain at connector Y levels. Scanning through them prevents paths from
        // floating at building junction positions.
        s.add(Blocks.JIGSAW);
        SCAN_IGNORE_BLOCKS = Collections.unmodifiableSet(s);
    }

    // Places the NBT structure into the world with the given rotation. Returns false on error.
    public static boolean place(ServerLevel level, BlockPos pos, ResourceLocation nbtLocation, Rotation rotation) {
        Optional<StructureTemplate> template = level.getStructureManager().get(nbtLocation);
        if (template.isEmpty()) {
            LOGGER.error("[OUAT] NBT not found: {}", nbtLocation);
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setKeepLiquids(false)
            .setIgnoreEntities(false)
            .setRotation(rotation)
            .addProcessor(SKIP_AIR);
        template.get().placeInWorld(level, pos, pos, settings, level.random, Block.UPDATE_ALL);
        replaceJigsawBlocks(level, pos, template.get(), rotation);
        return true;
    }

    // Places a terrain-matching structure (road NBT) by anchoring each XZ column to the solid terrain surface.
    // No deletions, no DIRT_PATH special-casing. Every non-air block in the NBT is placed.
    // SCAN_IGNORE_BLOCKS (vegetation, logs, surface features) are skipped during the downward terrain scan.
    public static boolean placeTerrainMatched(ServerLevel level, BlockPos originPos,
                                               ResourceLocation nbtLocation, Rotation rotation) {
        Optional<StructureTemplate> templateOpt = level.getStructureManager().get(nbtLocation);
        if (templateOpt.isEmpty()) {
            LOGGER.error("[OUAT] NBT not found: {}", nbtLocation);
            return false;
        }

        // All non-air blocks with rotation applied; jigsaw blocks already resolved to final_state.
        List<SchematicBlock> blocks = SchematicReader.readSortedBlocks(templateOpt.get(), rotation);

        // Group by XZ column using rotated local coordinates.
        Map<Long, List<SchematicBlock>> columns = new HashMap<>();
        for (SchematicBlock b : blocks) {
            long key = BlockPos.asLong(b.localPos().getX(), 0, b.localPos().getZ());
            columns.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }

        for (List<SchematicBlock> column : columns.values()) {
            // templateFloorY: lowest non-air Y in this column within the template.
            int templateFloorY = Integer.MAX_VALUE;
            for (SchematicBlock b : column) {
                if (b.localPos().getY() < templateFloorY) templateFloorY = b.localPos().getY();
            }

            int wx = originPos.getX() + column.get(0).localPos().getX();
            int wz = originPos.getZ() + column.get(0).localPos().getZ();

            // Scan downward; skip SCAN_IGNORE_BLOCKS, stop at first solid block (Filter B).
            int scanStart = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            int terrainY = Integer.MIN_VALUE;
            for (int y = scanStart; y >= level.getMinBuildHeight(); y--) {
                BlockState bs = level.getBlockState(new BlockPos(wx, y, wz));
                if (!bs.isAir() && !SCAN_IGNORE_BLOCKS.contains(bs.getBlock())) {
                    terrainY = y;
                    break;
                }
            }

            if (terrainY == Integer.MIN_VALUE) continue;

            int deltaY = terrainY - templateFloorY;

            for (SchematicBlock b : column) {
                level.setBlock(new BlockPos(wx, b.localPos().getY() + deltaY, wz), b.state(), Block.UPDATE_ALL);
            }
        }

        return true;
    }

    // Replaces jigsaw blocks with their "turns_into" target block after structure placement.
    private static void replaceJigsawBlocks(ServerLevel level, BlockPos origin,
                                             StructureTemplate template, Rotation rotation) {
        for (StructureTemplate.StructureBlockInfo info :
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
            BlockPos rotatedRel = StructureTemplate.transform(info.pos(), Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos worldPos = origin.offset(rotatedRel);
            if (info.nbt() == null) {
                level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                continue;
            }
            String finalState = info.nbt().getString("final_state");
            String blockId = finalState.contains("[")
                ? finalState.substring(0, finalState.indexOf('['))
                : finalState;
            ResourceLocation rl = ResourceLocation.tryParse(blockId);
            if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                level.setBlock(worldPos, BuiltInRegistries.BLOCK.get(rl).defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // Replaces a jigsaw block in the world (a parent bud that was just consumed) with its final_state.
    public static void replaceJigsawInWorld(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.JIGSAW)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); return; }
        CompoundTag tag = be.saveWithoutMetadata();
        String finalState = tag.getString("final_state");
        if (finalState.isEmpty()) { level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); return; }
        String blockId = finalState.contains("[") ? finalState.substring(0, finalState.indexOf('[')) : finalState;
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
            level.setBlock(pos, BuiltInRegistries.BLOCK.get(rl).defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // Reads all jigsaw connectors from a template in local (non-rotated) coordinates.
    public static List<JigsawConnector> readConnectors(ServerLevel level, ResourceLocation nbtLocation) {
        Optional<StructureTemplate> template = level.getStructureManager().get(nbtLocation);
        if (template.isEmpty()) return List.of();
        List<JigsawConnector> result = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info :
                template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
            if (info.nbt() == null) continue;
            Direction facing = info.state().getValue(JigsawBlock.ORIENTATION).front();
            String name = info.nbt().getString("name");
            String target = info.nbt().getString("target");
            String pool = info.nbt().getString("pool");
            result.add(new JigsawConnector(info.pos(), facing, name, target, pool));
        }
        return result;
    }

    // Finds the rotation R such that R.rotate(connectorFacing) == targetFacing.
    // Used to align a candidate connector to face opposite the generator jigsaw.
    public static Rotation computeRequiredRotation(Direction connectorFacing, Direction targetFacing) {
        for (Rotation r : Rotation.values()) {
            if (r.rotate(connectorFacing) == targetFacing) return r;
        }
        return Rotation.NONE;
    }

    // Computes the template's world placement origin so that its connector (after rotation)
    // ends up at the block adjacent to the generator jigsaw in its facing direction.
    // Formula: origin = (generatorPos + generatorFacing) - transform(connectorLocalPos, rotation)
    public static BlockPos computeCandidatePosition(BlockPos generatorJigsawPos, Direction generatorFacing,
                                                     BlockPos connectorLocalPos, Rotation rotation) {
        BlockPos attachPoint = generatorJigsawPos.relative(generatorFacing);
        BlockPos connectorWorldOffset = StructureTemplate.transform(connectorLocalPos, Mirror.NONE, rotation, BlockPos.ZERO);
        return attachPoint.subtract(connectorWorldOffset);
    }

    // Computes the tight world bounding box from the ground-layer blocks of the template (road footprint).
    // For terrain-matched roads, the NBT often contains large empty corners (L/T shapes) that would
    // cause false bounding-box overlaps against adjacent buildings. Using only ground-layer blocks
    // gives a tight XZ box that matches what the road physically occupies in the world.
    public static Optional<BoundingBox> computeFootprintBoundingBox(ServerLevel level, BlockPos originPos,
                                                                      ResourceLocation nbtLocation, Rotation rotation) {
        Optional<StructureTemplate> template = level.getStructureManager().get(nbtLocation);
        if (template.isEmpty()) return Optional.empty();

        List<SchematicBlock> allBlocks = SchematicReader.readSortedBlocks(template.get(), rotation);
        if (allBlocks.isEmpty()) return computeBoundingBox(level, originPos, nbtLocation, rotation);

        // Ground-layer blocks: one per XZ column, the one with the lowest Y.
        Map<Long, SchematicBlock> groundBlocks = new HashMap<>();
        for (SchematicBlock b : allBlocks) {
            long key = BlockPos.asLong(b.localPos().getX(), 0, b.localPos().getZ());
            SchematicBlock existing = groundBlocks.get(key);
            if (existing == null || b.localPos().getY() < existing.localPos().getY()) {
                groundBlocks.put(key, b);
            }
        }

        if (groundBlocks.isEmpty()) return computeBoundingBox(level, originPos, nbtLocation, rotation);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (SchematicBlock b : groundBlocks.values()) {
            int wx = originPos.getX() + b.localPos().getX();
            int wy = originPos.getY() + b.localPos().getY();
            int wz = originPos.getZ() + b.localPos().getZ();
            if (wx < minX) minX = wx; if (wx > maxX) maxX = wx;
            if (wy < minY) minY = wy; if (wy > maxY) maxY = wy;
            if (wz < minZ) minZ = wz; if (wz > maxZ) maxZ = wz;
        }

        return Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    // Computes the world bounding box of a template placed at originPos with the given rotation.
    public static Optional<BoundingBox> computeBoundingBox(ServerLevel level, BlockPos originPos,
                                                            ResourceLocation nbtLocation, Rotation rotation) {
        Optional<StructureTemplate> template = level.getStructureManager().get(nbtLocation);
        if (template.isEmpty()) return Optional.empty();
        Vec3i size = template.get().getSize();
        int sX = size.getX(), sY = size.getY(), sZ = size.getZ();
        int minX, maxX, minZ, maxZ;
        switch (rotation) {
            case CLOCKWISE_90 -> {
                minX = originPos.getX() - (sZ - 1); maxX = originPos.getX();
                minZ = originPos.getZ();             maxZ = originPos.getZ() + sX - 1;
            }
            case CLOCKWISE_180 -> {
                minX = originPos.getX() - (sX - 1); maxX = originPos.getX();
                minZ = originPos.getZ() - (sZ - 1); maxZ = originPos.getZ();
            }
            case COUNTERCLOCKWISE_90 -> {
                minX = originPos.getX();             maxX = originPos.getX() + sZ - 1;
                minZ = originPos.getZ() - (sX - 1); maxZ = originPos.getZ();
            }
            default -> {
                minX = originPos.getX(); maxX = originPos.getX() + sX - 1;
                minZ = originPos.getZ(); maxZ = originPos.getZ() + sZ - 1;
            }
        }
        return Optional.of(new BoundingBox(
            minX, originPos.getY(), minZ,
            maxX, originPos.getY() + sY - 1, maxZ
        ));
    }

    // Returns the Y where a terrain_matching structure should be placed.
    // Capped at connectionY + 3 to prevent roads from floating over buildings.
    public static int findGroundY(ServerLevel level, BlockPos pos) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        return Math.min(surface, pos.getY() + 3);
    }

    // For every DIRT_PATH in the bounding box that borders water on at least one
    // horizontal side, replaces it with OAK_PLANKS (pond-edge/dock effect).
    // Y scan range is extended +-20 from the template BB to absorb terrain snapping.
    public static void applyPondEdgeRules(ServerLevel level, BoundingBox bb) {
        int yMin = bb.minY() - 20;
        int yMax = bb.maxY() + 20;

        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(Blocks.DIRT_PATH)) continue;
                    if (bordersWater(level, pos)) {
                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static boolean bordersWater(ServerLevel level, BlockPos pos) {
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
            if (level.getBlockState(pos.relative(dir)).is(Blocks.WATER)) return true;
        }
        // Check diagonal neighbours (NE, NW, SE, SW) to fill corner gaps.
        int[][] diagonals = { {1,1},{1,-1},{-1,1},{-1,-1} };
        for (int[] d : diagonals) {
            if (level.getBlockState(pos.offset(d[0], 0, d[1])).is(Blocks.WATER)) return true;
        }
        return false;
    }

    // Repositions each dirt_path block to the true solid terrain surface.
    // Removes the path first so OCEAN_FLOOR is not polluted by the path block itself,
    // then re-places it at the correct Y. Clears non-solid residue (plants, leaves)
    // up to 2 blocks above the final path position.
    public static void applyTerrainMatching(ServerLevel level, BoundingBox bb) {
        for (int x = bb.minX(); x <= bb.maxX(); x++) {
            for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                BlockPos roadBlockPos = null;
                for (int y = bb.minY(); y <= bb.maxY(); y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (level.getBlockState(candidate).is(Blocks.DIRT_PATH)) {
                        roadBlockPos = candidate;
                        break;
                    }
                }
                if (roadBlockPos == null) continue;

                // Remove path first so OCEAN_FLOOR is not polluted by the path block itself.
                // Without this, OCEAN_FLOOR returns pathY (path IS a solid block), masking the real terrain.
                level.setBlock(roadBlockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

                // OCEAN_FLOOR now returns the true solid terrain surface (ignores plants, ferns, leaves).
                int solidSurfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;

                BlockPos finalPathPos = new BlockPos(x, solidSurfaceY, z);
                level.setBlock(finalPathPos, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);

                // Clear non-solid residue above the path (fern, tall_grass upper half, leaves).
                for (int clearY = solidSurfaceY + 1; clearY <= solidSurfaceY + 2; clearY++) {
                    BlockState above = level.getBlockState(new BlockPos(x, clearY, z));
                    if (above.isAir()) break;
                    if (!above.isSolid()) {
                        level.setBlock(new BlockPos(x, clearY, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    // Returns positions of ALL jigsaw blocks in a vanilla-placed template, including terminators.
    // Used in ChunkGeneratorMixin to detect which connections were consumed during vanilla generation.
    // Terminators (pool = empty) mark dead-end connections; their positions must still be tracked
    // so the free-connection filter knows those slots are already occupied.
    public static Set<BlockPos> readAllJigsawPositions(ServerLevel level, BlockPos bbMinPos,
                                                        String defId, Rotation pieceRotation) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) return Set.of();
        Optional<StructureTemplate> tpl = level.getStructureManager().get(def.nbt);
        if (tpl.isEmpty()) return Set.of();
        BlockPos origin = originFromBbMin(bbMinPos, tpl.get().getSize(), pieceRotation);
        Set<BlockPos> positions = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info :
                tpl.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
            BlockPos rotatedRel = StructureTemplate.transform(info.pos(), Mirror.NONE, pieceRotation, BlockPos.ZERO);
            positions.add(origin.offset(rotatedRel));
        }
        return positions;
    }

    // Converts the bounding-box minimum of a vanilla jigsaw piece to the template placement origin.
    // The origin is where local [0,0,0] of the NBT maps to in world space. For NONE rotation the
    // two are identical; for other rotations the origin is at a different corner of the bounding box.
    public static BlockPos computeOriginFromBbMin(ServerLevel level, BlockPos bbMin,
                                                   ResourceLocation nbt, Rotation rotation) {
        Optional<StructureTemplate> tpl = level.getStructureManager().get(nbt);
        if (tpl.isEmpty()) return bbMin;
        return originFromBbMin(bbMin, tpl.get().getSize(), rotation);
    }

    private static BlockPos originFromBbMin(BlockPos bbMin, Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90        -> new BlockPos(bbMin.getX() + size.getZ() - 1, bbMin.getY(), bbMin.getZ());
            case CLOCKWISE_180       -> new BlockPos(bbMin.getX() + size.getX() - 1, bbMin.getY(), bbMin.getZ() + size.getZ() - 1);
            case COUNTERCLOCKWISE_90 -> new BlockPos(bbMin.getX(), bbMin.getY(), bbMin.getZ() + size.getX() - 1);
            default                  -> bbMin;
        };
    }

    // Overload for ChunkGeneratorMixin: the piece was placed by vanilla with a known rotation,
    // bbMinPos is the bounding box minimum. Back-computes the true placement origin.
    public static List<ConnectionPoint> readJigsawPoints(ServerLevel level, BlockPos bbMinPos,
                                                          String defId, Rotation pieceRotation) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) return List.of();
        Optional<StructureTemplate> tpl = level.getStructureManager().get(def.nbt);
        if (tpl.isEmpty()) return List.of();
        BlockPos origin = originFromBbMin(bbMinPos, tpl.get().getSize(), pieceRotation);
        return readJigsawPoints(level, origin, defId, pieceRotation, BlockPos.ZERO.below(9999));
    }

    // Variant for initial village scan: includes terminator connectors (pool = empty).
    // The mixin uses Pass-2 adjacency filtering as the real free/consumed test.
    // Including terminators here allows the scan to recover free entry connectors on pieces
    // that vanilla placed backwards (exit consumed instead of entry).
    public static List<ConnectionPoint> readJigsawPointsAll(ServerLevel level, BlockPos bbMinPos,
                                                             String defId, Rotation pieceRotation) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) return List.of();
        Optional<StructureTemplate> tpl = level.getStructureManager().get(def.nbt);
        if (tpl.isEmpty()) return List.of();
        BlockPos origin = originFromBbMin(bbMinPos, tpl.get().getSize(), pieceRotation);

        List<ConnectionPoint> points = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info :
                tpl.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
            if (info.nbt() == null) continue;
            BlockPos rotatedRel = StructureTemplate.transform(info.pos(), Mirror.NONE, pieceRotation, BlockPos.ZERO);
            BlockPos worldPos = origin.offset(rotatedRel);
            Direction rawDir = info.state().getValue(JigsawBlock.ORIENTATION).front();
            Direction rotatedDir = pieceRotation.rotate(rawDir);
            String target = info.nbt().getString("target");
            points.add(new ConnectionPoint(worldPos, rotatedDir, target, 0L));
        }
        return points;
    }

    // Reads jigsaw blocks from a placed template to extract new ConnectionPoints in world coords.
    // Skips the entry jigsaw (at usedConnectorWorldPos) and terminators (pool = empty or minecraft:empty).
    public static List<ConnectionPoint> readJigsawPoints(ServerLevel level, BlockPos originPos,
                                                          String defId, Rotation rotation,
                                                          BlockPos usedConnectorWorldPos) {
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) return List.of();

        Optional<StructureTemplate> template = level.getStructureManager().get(def.nbt);
        if (template.isEmpty()) return List.of();

        List<ConnectionPoint> points = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info :
                template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
            if (info.nbt() == null) continue;

            // Skip terminators: connectors with no pool or the explicit empty pool
            String pool = info.nbt().getString("pool");
            if (pool.isEmpty() || pool.equals("minecraft:empty")) continue;

            BlockPos rotatedRel = StructureTemplate.transform(info.pos(), Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos worldPos = originPos.offset(rotatedRel);

            // Skip the entry connector that was consumed to attach this building
            if (worldPos.equals(usedConnectorWorldPos)) continue;

            Direction rawDir = info.state().getValue(JigsawBlock.ORIENTATION).front();
            Direction rotatedDir = rotation.rotate(rawDir);
            String target = info.nbt().getString("target");
            points.add(new ConnectionPoint(worldPos, rotatedDir, target, 0L));
        }
        return points;
    }

    // Reads jigsaw connection points from a specific NBT resource (used for upgrade-level NBTs).
    // Skips terminators; includes all non-terminator jigsaw blocks found in the template.
    public static List<ConnectionPoint> readJigsawPointsFromNbt(ServerLevel level, BlockPos originPos,
                                                                  ResourceLocation nbtLocation, Rotation rotation) {
        Optional<StructureTemplate> template = level.getStructureManager().get(nbtLocation);
        if (template.isEmpty()) return List.of();

        List<ConnectionPoint> points = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info :
                template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
            if (info.nbt() == null) continue;
            String pool = info.nbt().getString("pool");
            if (pool.isEmpty() || pool.equals("minecraft:empty")) continue;

            BlockPos rotatedRel = StructureTemplate.transform(info.pos(), Mirror.NONE, rotation, BlockPos.ZERO);
            BlockPos worldPos = originPos.offset(rotatedRel);
            Direction rawDir = info.state().getValue(JigsawBlock.ORIENTATION).front();
            Direction rotatedDir = rotation.rotate(rawDir);
            String target = info.nbt().getString("target");
            points.add(new ConnectionPoint(worldPos, rotatedDir, target, 0L));
        }
        return points;
    }

    // Returns only the blocks from a template that are not yet placed correctly in the world.
    // Used on resume to skip already-placed blocks without storing build_progress in NBT.
    public static List<SchematicBlock> computeRemainingBlocks(
            ServerLevel level, BlockPos origin, ResourceLocation nbtId, Rotation rotation) {
        Optional<StructureTemplate> templateOpt = level.getStructureManager().get(nbtId);
        if (templateOpt.isEmpty()) {
            LOGGER.error("[OUAT-BUILD] Template not found for remaining blocks -- nbt='{}'", nbtId);
            return List.of();
        }
        List<SchematicBlock> full = SchematicReader.readSortedBlocks(templateOpt.get(), rotation);
        List<SchematicBlock> remaining = new ArrayList<>();
        for (SchematicBlock b : full) {
            BlockPos worldPos = origin.offset(b.localPos());
            if (!level.getBlockState(worldPos).equals(b.state())) {
                remaining.add(b);
            }
        }
        return remaining;
    }

    // Result of a diff between two upgrade-level NBTs at the same origin and rotation.
    public record DiffResult(List<SchematicBlock> toAdd, List<BlockPos> toRemove) {}

    // Returns entities present in toNbt but not accounted for in fromNbt, compared by type count.
    // For each entity type: if toNbt has more than fromNbt, spawn the delta at toNbt positions.
    // This is intentionally world-agnostic: entities may have wandered, so we never read the world.
    public static List<SchematicEntity> computeEntityDiff(ServerLevel level,
                                                           ResourceLocation fromNbt,
                                                           ResourceLocation toNbt,
                                                           Rotation rotation,
                                                           BlockPos origin,
                                                           int fromYOffset) {
        Optional<StructureTemplate> fromTpl = level.getStructureManager().get(fromNbt);
        Optional<StructureTemplate> toTpl   = level.getStructureManager().get(toNbt);
        if (fromTpl.isEmpty() || toTpl.isEmpty()) return List.of();

        BlockPos toOrigin = fromYOffset == 0 ? origin : origin.offset(0, -fromYOffset, 0);

        List<SchematicEntity> fromEntities = SchematicReader.readEntities(fromTpl.get(), rotation, BlockPos.ZERO);
        List<SchematicEntity> toEntities   = SchematicReader.readEntities(toTpl.get(),   rotation, toOrigin);

        // Count entities per type in fromNbt.
        Map<String, Integer> fromCounts = new HashMap<>();
        for (SchematicEntity e : fromEntities) {
            String id = e.nbt().getString("id");
            if (!id.isEmpty()) fromCounts.merge(id, 1, Integer::sum);
        }

        // For each type in toNbt, collect entities beyond the fromNbt count.
        Map<String, Integer> toConsumed = new HashMap<>();
        List<SchematicEntity> toSpawn = new ArrayList<>();
        for (SchematicEntity e : toEntities) {
            String id = e.nbt().getString("id");
            if (id.isEmpty()) continue;
            int already = fromCounts.getOrDefault(id, 0);
            int consumed = toConsumed.getOrDefault(id, 0);
            if (consumed < already) {
                toConsumed.merge(id, 1, Integer::sum);
            } else {
                toSpawn.add(e);
            }
        }

        return toSpawn;
    }

    // Computes the visual diff between two structure NBTs (fromNbt = current level, toNbt = next level).
    // toAdd: blocks present in toNbt but absent or different in fromNbt.
    // toRemove: block positions present in fromNbt but absent in toNbt (expressed in toNbt coordinate space).
    //
    // fromYOffset: how many blocks lower the toNbt origin sits relative to fromNbt.
    // When the target level has underground_depth=N, toNbt local Y=N is the ground floor while
    // fromNbt local Y=0 is the ground floor. Shifting fromNbt positions up by N brings both
    // templates into the same coordinate space before comparing, so identical surface blocks
    // are not flagged as changed and the TownAnchorBlock is never re-placed needlessly.
    public static DiffResult computeDiff(ServerLevel level, ResourceLocation fromNbt,
                                          ResourceLocation toNbt, Rotation rotation, int fromYOffset) {
        Optional<StructureTemplate> fromTpl = level.getStructureManager().get(fromNbt);
        Optional<StructureTemplate> toTpl   = level.getStructureManager().get(toNbt);
        if (fromTpl.isEmpty() || toTpl.isEmpty()) {
            LOGGER.warn("[OUAT] computeDiff: template not found -- from='{}' to='{}'", fromNbt, toNbt);
            return new DiffResult(List.of(), List.of());
        }

        List<SchematicBlock> fromBlocks = SchematicReader.readSortedBlocks(fromTpl.get(), rotation);
        List<SchematicBlock> toBlocks   = SchematicReader.readSortedBlocks(toTpl.get(), rotation);

        // Shift fromNbt positions into toNbt coordinate space so that ground-level blocks
        // at fromNbt Y=0 align with toNbt Y=fromYOffset (the target ground floor).
        Map<BlockPos, BlockState> fromMap = new HashMap<>();
        for (SchematicBlock b : fromBlocks) {
            BlockPos shifted = fromYOffset == 0 ? b.localPos() : b.localPos().offset(0, fromYOffset, 0);
            fromMap.put(shifted, b.state());
        }

        Map<BlockPos, SchematicBlock> toMap = new HashMap<>();
        for (SchematicBlock b : toBlocks) toMap.put(b.localPos(), b);

        List<SchematicBlock> toAdd = new ArrayList<>();
        for (SchematicBlock b : toBlocks) {
            BlockState fromState = fromMap.get(b.localPos());
            if (fromState == null || !fromState.equals(b.state())) {
                toAdd.add(b);
            }
        }

        // toRemove positions are in toNbt space (shifted fromNbt positions not present in toNbt).
        List<BlockPos> toRemove = new ArrayList<>();
        for (SchematicBlock b : fromBlocks) {
            BlockPos shifted = fromYOffset == 0 ? b.localPos() : b.localPos().offset(0, fromYOffset, 0);
            if (!toMap.containsKey(shifted)) {
                toRemove.add(shifted);
            }
        }

        return new DiffResult(toAdd, toRemove);
    }
}
