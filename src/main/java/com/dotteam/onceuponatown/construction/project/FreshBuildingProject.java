package com.dotteam.onceuponatown.construction.project;

import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import com.dotteam.onceuponatown.construction.ConstructionPlan;
import com.dotteam.onceuponatown.util.OuatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Represents a building under construction/repair/upgrade in some place in the world. <br>
 * Responsible for placing the structure in the world
 */
public class FreshBuildingProject extends ConstructionProject {
    /**
     * The structure building plan
     */
    private final ConstructionPlan constructionPlan;

    private PlacingBlocksPhase phase1;
    private PlacingEntitiesPhase phase2;

    private FreshBuildingProject(ServerLevel level, String name, BuildingPlacementSettings settings, ConstructionPlan constructionPlan) {
        super(level, name, settings);
        this.constructionPlan = constructionPlan;
        this.phase1 = new PlacingBlocksPhase(this.constructionPlan.getBlocks());
        this.phase2 = new PlacingEntitiesPhase(this.constructionPlan.getEntities());
        this.phases.add(this.phase1);
        this.phases.add(this.phase2);
    }

    static FreshBuildingProject createProject(ServerLevel level, String name, ResourceLocation structurePath, BuildingPlacementSettings settings) {
        ConstructionPlan constructionPlan = ConstructionPlan.create(structurePath, level.getServer().getResourceManager());
        FreshBuildingProject project = new FreshBuildingProject(level, name, settings, constructionPlan);
        project.currentPhaseIndex = 0;
        project.getCurrentPhase().setProgression(0);
        return project;
    }

    static FreshBuildingProject loadProject(ServerLevel level, CompoundTag buildingSiteTag) {
        BlockPos buildPos = new BlockPos(buildingSiteTag.getInt("BuildX"), buildingSiteTag.getInt("BuildY"), buildingSiteTag.getInt("BuildZ"));
        BuildingPlacementSettings settings = new BuildingPlacementSettings(buildPos)
                .rotation(Rotation.valueOf(buildingSiteTag.getString("Rotation").toUpperCase()))
                .mirror(Mirror.valueOf(buildingSiteTag.getString("Mirror").toUpperCase()));
        String name = buildingSiteTag.getString("Name");
        ConstructionPlan constructionPlan = ConstructionPlan.create(OuatUtils.resource(buildingSiteTag.getString("Structure")), level.getServer().getResourceManager());
        FreshBuildingProject project = new FreshBuildingProject(level, name, settings , constructionPlan);
        project.currentPhaseIndex = buildingSiteTag.getInt("Phase");
        project.getCurrentPhase().setProgression(buildingSiteTag.getInt("PhaseProgression"));
        return project;
    }

    public BlockPos getNextTarget() {
        if (getCurrentPhase() instanceof PlacingBlocksPhase phase) {
            return phase.getNextBlockToPlace().pos();
        }
        if (getCurrentPhase() instanceof PlacingEntitiesPhase phase) {
            return null;
        }
        return null;
    }

    public CompoundTag save(CompoundTag tag) {
        super.save(tag);
        tag.putString("ProjectType", "NewBuilding");
        tag.putString("Structure", this.constructionPlan.getStructurePath().getPath());
        return tag;
    }

    public ConstructionPlan getConstructionPlan() {
        return this.constructionPlan;
    }
}
