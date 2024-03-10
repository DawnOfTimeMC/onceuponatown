package com.dotteam.onceuponatown.construction.project;

import com.dotteam.onceuponatown.construction.BlockInfo;
import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import com.dotteam.onceuponatown.construction.ConstructionUtils;
import com.dotteam.onceuponatown.construction.EntityInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class ConstructionProject {
    /**
     * The level this project belongs
     */
    protected final ServerLevel level;
    /**
     * Unique name
     */
    protected final String name;
    /**
     * Placement settings : position, rotation...
     */
    private final BuildingPlacementSettings settings;
    /**
     * If the construction is done
     */
    protected boolean completed;
    protected List<ProjectPhase> phases = new ArrayList<>();
    protected int currentPhaseIndex;

    protected ConstructionProject(ServerLevel level, String name, BuildingPlacementSettings settings) {
        this.level = level;
        this.name = name;
        this.settings = settings;
    }

    public void stepForward(int times) {
        for (int i = 0; i < times; ++i) {
            oneStepForward();
        }
    }

    protected boolean oneStepForward() {
        boolean success = getCurrentPhase().nextStep();
        if (getCurrentPhase().isCompleted()) {
            if (getCurrentPhase() == this.phases.get(this.phases.size() - 1)) {
                this.completed = true;
                ConstructionProjectManager.get(this.level).notifyProjectCompleted(this);
            } else {
                ++this.currentPhaseIndex;
            }
        }
        return success;
    }

    public ProjectPhase getCurrentPhase() {
        return this.phases.get(this.currentPhaseIndex);
    }

    protected void onPhaseCompleted() {}

    public CompoundTag save(CompoundTag tag) {
        tag.putString("Name", this.name);
        tag.putInt("BuildX", this.settings.getPosition().getX());
        tag.putInt("BuildY", this.settings.getPosition().getY());
        tag.putInt("BuildZ", this.settings.getPosition().getZ());
        tag.putString("Rotation", this.settings.getRotation().getSerializedName());
        tag.putString("Mirror", this.settings.getMirror().getSerializedName());
        tag.putInt("Phase", this.currentPhaseIndex);
        getCurrentPhase().save(tag);
        return tag;
    }

    public BlockPos getPosition() {
        return this.settings.getPosition();
    }

    public String getName() {
        return name;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    protected class PlacingBlocksPhase extends ProjectPhase {
        private List<BlockInfo> blocksToPlace;

        PlacingBlocksPhase(List<BlockInfo> blocksToPlace) {
            this.blocksToPlace = blocksToPlace;
        }

        boolean nextStep() {
            BlockInfo toPlace = blocksToPlace.get(progression);
            if (placeBlock(toPlace.pos(), toPlace.state(), toPlace.nbt(), settings.getMirror(), settings.getRotation(), settings.getRotationPivot())) {
               ++progression;
               return true;
            }
            return false;
        }

        boolean isCompleted() {
            return progression >= blocksToPlace.size();
        }

        private boolean placeBlock(BlockPos pos, BlockState state, CompoundTag nbt, Mirror mirror, Rotation rotation, BlockPos rotationPivot) {
            pos = ConstructionUtils.transformBlockPos(pos, mirror, rotation, rotationPivot);
            state = ConstructionUtils.transformBlockState(state, mirror, rotation);
            if (nbt != null) {
                BlockEntity blockentity = level.getBlockEntity(pos);
                Clearable.tryClear(blockentity);
                level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 20);
            }
            if (level.setBlock(pos, state, 2)) {
                if (nbt != null) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        if (blockEntity instanceof RandomizableContainerBlockEntity) {
                            nbt.putLong("LootTableSeed", RandomSource.create().nextLong());
                        }

                        blockEntity.load(nbt);
                    }
                }
                return true;
            }
            return false;
        }

        public BlockInfo getNextBlockToPlace() {
            return getBlockToPlace(this.progression);
        }

        public BlockInfo getLastBlockBuilt() {
            return getBlockToPlace(this.progression - 1);
        }

        public BlockInfo getBlockToPlace(int index) {
            BlockPos pos = ConstructionUtils.transformBlockPos(this.blocksToPlace.get(index).pos(), settings.getMirror(), settings.getRotation(), settings.getRotationPivot()).offset(settings.getPosition());
            BlockState state = this.blocksToPlace.get(index).state();
            CompoundTag nbt = this.blocksToPlace.get(index).nbt();
            return new BlockInfo(pos, state, nbt);
        }
    }

    protected class PlacingEntitiesPhase extends ProjectPhase {
        private List<EntityInfo> entitiesToPlace;

        PlacingEntitiesPhase(List<EntityInfo> entitiesToPlace) {
            this.entitiesToPlace = entitiesToPlace;
        }

        boolean nextStep() {
            return false;
        }

        boolean isCompleted() {
            return false;
        }

        /*
        private void placeEntities() {
            for(EntityInfo entityInfo : this.constructionPlan.getEntities()) {
                Vec3 entityPos = ConstructionUtils.transformEntityPos(entityInfo.pos(), this.settings.getMirror(), this.settings.getRotation(), this.settings.getRotationPivot()).add(Vec3.atLowerCornerOf(this.settings.getPosition()));
                //BlockPos blockPos = ConstructionUtils.transformBlockPos(entityInfo.blockPos(), this.settings.getMirror(), this.settings.getRotation(), this.settings.getRotationPivot()).offset(this.settings.getPosition());
                CompoundTag nbt = entityInfo.nbt().copy();
                ListTag entityPosTag = new ListTag();
                entityPosTag.add(DoubleTag.valueOf(entityPos.x));
                entityPosTag.add(DoubleTag.valueOf(entityPos.y));
                entityPosTag.add(DoubleTag.valueOf(entityPos.z));
                nbt.put("Pos", entityPosTag);
                nbt.remove("UUID");
                createEntityIgnoreException(level, nbt).ifPresent((entity) -> {
                    float f = entity.rotate(this.settings.getRotation());
                    f += entity.mirror(this.settings.getMirror()) - entity.getYRot();
                    entity.moveTo(entityPos.x, entityPos.y, entityPos.z, f, entity.getXRot());
                    if (entity instanceof Mob mob) {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(entityPos)), MobSpawnType.STRUCTURE,null, nbt);
                    }
                    level.addFreshEntityWithPassengers(entity);
                });
            }
        } */

        private static Optional<Entity> createEntityIgnoreException(ServerLevelAccessor level, CompoundTag tag) {
            try {
                return EntityType.create(tag, level.getLevel());
            } catch (Exception exception) {
                return Optional.empty();
            }
        }
    }

    protected class RemovingBlocksPhase extends ProjectPhase {
        private List<BlockPos> blocksToRemove;

        RemovingBlocksPhase(List<BlockPos> blocksToRemove) {
            this.blocksToRemove = blocksToRemove;
        }

        boolean nextStep() {
            return false;
        }

        boolean isCompleted() {
            return false;
        }
    }

    protected class RemovingEntitiesPhase extends ProjectPhase {
        private List<Entity> entitiesToRemove;

        RemovingEntitiesPhase(List<Entity> entitiesToRemove) {
            this.entitiesToRemove = entitiesToRemove;
        }

        boolean nextStep() {
            return false;
        }

        boolean isCompleted() {
            return false;
        }
    }
}