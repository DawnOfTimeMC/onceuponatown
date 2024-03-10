package com.dotteam.onceuponatown.construction;

import com.dotteam.onceuponatown.construction.project.FreshBuildingProject;
import com.dotteam.onceuponatown.util.OuatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a structure NBT file.<br>
 * Can be seen as the construction plan of a structure.<br>
 * Shared by all structures which are described by the same NBT file.<br>
 * Does not contain any information about how the structure should be placed in world (position, rotation...).<br>
 * @see FreshBuildingProject
 */
public class ConstructionPlan {
    /**
     * The structure NBT file path
     */
    private final ResourceLocation structurePath;
    /**
     * The list of blocks in this building
     */
    private final List<BlockInfo> blocks = new ArrayList<>();
    /**
     * The list of entities in this building
     */
    private final List<EntityInfo> entities = new ArrayList<>();
    /**
     * Length, width, height of this building
     */
    private Vec3i dimensions = Vec3i.ZERO;

    private ConstructionPlan(ResourceLocation structurePath) {
        this.structurePath = structurePath;
    }

    /**
     * Creates a building plan, replaces constructor
     * @param structurePath The structure NBT file path of the desired structure
     * @param resourceManager Ressource manager
     * @return a new building plan
     */
    public static ConstructionPlan create(ResourceLocation structurePath, ResourceManager resourceManager) {
        FileToIdConverter converter = new FileToIdConverter("structures", ".nbt");
        ResourceLocation resourceLocation = converter.idToFile(structurePath);
        try (InputStream inputStream = resourceManager.open(resourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ConstructionPlan constructionPlan = new ConstructionPlan(structurePath);
            HolderGetter<Block> blockLookup =  BuiltInRegistries.BLOCK.asLookup();
            constructionPlan.readStructureTag(blockLookup, tag);
            return constructionPlan.withoutAirBlocks();
        } catch (FileNotFoundException fileNotFoundException) {
            OuatLog.LOG.error("Structure not found {}", resourceLocation, fileNotFoundException);
            return null;
        } catch (Throwable throwable) {
            OuatLog.LOG.error("Could not load structure {}", resourceLocation, throwable);
            return null;
        }
    }

    /**
     * Read the structure NBT file, initialize building plan blocks and entities
     * @param structureTag The structure NBT tag
     */
    private void readStructureTag(HolderGetter<Block> blockGetter, CompoundTag structureTag) {
        // Extracting dimensions
        ListTag sizeTag = structureTag.getList("size", 3);
        this.dimensions = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        // Extracting entities
        ListTag entitiesTag = structureTag.getList("entities", 10);
        for(int i = 0; i < entitiesTag.size(); ++i) {
            CompoundTag entityTag = entitiesTag.getCompound(i);
            ListTag posTag = entityTag.getList("pos", 6);
            Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
            ListTag blockPosTag = entityTag.getList("blockPos", 3);
            BlockPos blockPos = new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2));
            if (entityTag.contains("nbt")) {
                CompoundTag entityNBT = entityTag.getCompound("nbt");
                this.entities.add(new EntityInfo(pos, blockPos, entityNBT));
            }
        }
        // Extracting blocks
        ListTag blocksTag = structureTag.getList("blocks", 10);
        // Extracting palette
        ListTag paletteTag;
        if (structureTag.contains("palettes", 9)) {
            ListTag palettesTag = structureTag.getList("palettes", 9);
            paletteTag = palettesTag.getList(0);
        } else {
            paletteTag = structureTag.getList("palette", 10);
        }
        buildBlocksList(blockGetter, paletteTag, blocksTag);
    }

    /**
     * Initialize the blocks list
     * @param paletteTag The structure blocks palette
     * @param blocksTag The structure blocks tag
     */
    private void buildBlocksList(HolderGetter<Block> blockGetter, ListTag paletteTag, ListTag blocksTag) {
        Palette palette = new Palette();
        for(int i = 0; i < paletteTag.size(); ++i) {
            palette.addMapping(NbtUtils.readBlockState(blockGetter, paletteTag.getCompound(i)), i);
        }
        List<BlockInfo> blockInfoList = new ArrayList<>();
        for(int i = 0; i < blocksTag.size(); ++i) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            ListTag posTag = blockTag.getList("pos", 3);
            BlockPos blockPos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            BlockState blockState = palette.stateFor(blockTag.getInt("state"));
            CompoundTag blockNBT;
            if (blockTag.contains("nbt")) {
                blockNBT = blockTag.getCompound("nbt");
            } else {
                blockNBT = null;
            }
            BlockInfo blockInfo = new BlockInfo(blockPos, blockState, blockNBT);
            blockInfoList.add(blockInfo);
        }
        sortBlocks(blockInfoList);
        this.blocks.addAll(blockInfoList);
    }

    /**
     * Sort the list of blocks in the structure
     * @param blockList The list of blocks in the structure
     */
    private void sortBlocks(List<BlockInfo> blockList) {
        Comparator<BlockInfo> comparator = Comparator.<BlockInfo>comparingInt((pY) -> {
            return pY.pos().getY();
        }).thenComparingInt((pX) -> {
            return pX.pos().getX();
        }).thenComparingInt((pZ) -> {
            return pZ.pos().getZ();
        });
        blockList.sort(comparator);
    }

    /**
     * @return This building plan without air blocks
     */
    public ConstructionPlan withoutAirBlocks() {
        List<BlockInfo> toRemove = new ArrayList<>();
        for (BlockInfo blockInfo : this.blocks) {
            if (blockInfo.state().isAir()) {
                toRemove.add(blockInfo);
            }
        }
        this.blocks.removeAll(toRemove);
        return this;
    }

    public int numberOfBlocksInPlan() {
        return this.blocks.size();
    }

    public Vec3i getDimensions() {
        return this.dimensions;
    }

    public Block getBlock(int index) {
        return this.blocks.get(index).state().getBlock();
    }

    public BlockPos getBlockPos(int index) {
        return this.blocks.get(index).pos();
    }

    public BlockState getBlockState(int index) {
        return this.blocks.get(index).state();
    }

    public CompoundTag getBlockNBT(int index) {
        return this.blocks.get(index).nbt();
    }

    public List<EntityInfo> getEntities() {
        return this.entities;
    }

    public List<BlockInfo> getBlocks() {
        return this.blocks;
    }

    public ResourceLocation getStructurePath() {
        return this.structurePath;
    }

    static class Palette implements Iterable<BlockState> {
        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final IdMapper<BlockState> ids = new IdMapper<>(16);
        private int lastId;

        public int idFor(BlockState state) {
            int i = this.ids.getId(state);
            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(state, i);
            }

            return i;
        }

        public BlockState stateFor(int id) {
            BlockState blockstate = this.ids.byId(id);
            return blockstate == null ? DEFAULT_BLOCK_STATE : blockstate;
        }

        public Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState state, int id) {
            this.ids.addMapping(state, id);
        }
    }
}
