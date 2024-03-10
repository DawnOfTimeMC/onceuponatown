package com.dotteam.onceuponatown.construction.project;

import com.dotteam.onceuponatown.building.Building;
import com.dotteam.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for adding and removing building sites in a given dimension <br>
 * Each dimension has a different BuildingSiteManager <br>
 * Saves building site data to disk
 */
public class ConstructionProjectManager extends SavedData {
    private final ServerLevel level;
    private final List<ConstructionProject> projects = new ArrayList<>();

    public static ConstructionProjectManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        // First argument is load function, second is create function
        return storage.computeIfAbsent((tag) -> new ConstructionProjectManager(level, tag), () -> new ConstructionProjectManager(level), "ConstructionProjects");
    }

    private ConstructionProjectManager(ServerLevel level) { // Creating the manager if it doesn't exist yet
        this.level = level;
        setDirty();
    }

    private ConstructionProjectManager(ServerLevel level, CompoundTag tag) { // Loading already saved data in the manager
        this(level);
        ListTag projectsTag = tag.getList("Projects", 10);
        for(int i = 0; i < projectsTag.size(); ++i) {
            CompoundTag projectTag = projectsTag.getCompound(i);
            loadProject(level, projectTag);
        }
    }

    public boolean containsProject(ConstructionProject project) {
        return this.projects.contains(project);
    }

    public ConstructionProject getProjectByName(String name) {
        for (ConstructionProject project : this.projects) {
            if (project.getName().equals(name)) {
                return project;
            }
        }
        return null;
    }

    public List<String> getExistentProjectsNames() {
        List<String> names = new ArrayList<>();
        for (ConstructionProject project : this.projects) {
            names.add(project.getName());
        }
        return names;
    }

    public boolean isValidProjectName(String name) {
        for (ConstructionProject project : this.projects) {
            if (project.getName().equals(name)) {
                return false;
            }
        }
        return true;
    }

    public boolean newFreshBuildingProject(ServerLevel level, String name, ResourceLocation structureNbtFile, BuildingPlacementSettings settings) {
        if (isValidProjectName(name)) {
            FreshBuildingProject site = FreshBuildingProject.createProject(level, name, structureNbtFile, settings);
            if (this.projects.add(site)) {
                setDirty();
                return true;
            }
        }
        return false;
    }

    public boolean newRepairProject(ServerLevel level, String name, Building building) {
        return false;
    }

    public boolean newUpgradeProject(ServerLevel level, String name, Building building) {
        return false;
    }

    public boolean newUpgradeProject(ServerLevel level, String name, Building building, int upgradeLevel) {
        return false;
    }

    public boolean newDemolitionProject(ServerLevel level, String name, Building building) {
        return false;
    }

    private void loadProject(ServerLevel level, CompoundTag projectTag) {
        ConstructionProject project = switch (projectTag.getString("ProjectType")) {
            case "NewBuilding" -> FreshBuildingProject.loadProject(level, projectTag);
            default -> throw new IllegalStateException("Unexpected value: " + projectTag.getString("ProjectType"));
        };

        if (!this.projects.contains(project)) {
            this.projects.add(project);
        }
        setDirty();
    }

    public void notifyProjectCompleted(ConstructionProject project) {
        removeProject(project);
    }

    public void removeProject(ConstructionProject project) {
        this.projects.remove(project);
        setDirty();
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag projectsTag = new ListTag();
        for(ConstructionProject project : this.projects) {
            CompoundTag projectTag = new CompoundTag();
            project.save(projectTag);
            projectsTag.add(projectTag);
        }
        tag.put("Projects", projectsTag);
        return tag;
    }

    public List<ConstructionProject> getProjects() {
        return this.projects;
    }
}
