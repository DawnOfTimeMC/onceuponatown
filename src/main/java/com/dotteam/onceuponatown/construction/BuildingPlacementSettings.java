package com.dotteam.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Parameters for placing a building in the world : position, rotation, mirror...
 */
public class BuildingPlacementSettings {
    private final BlockPos position;
    private Mirror mirror = Mirror.NONE;
    private Rotation rotation = Rotation.NONE;
    private BlockPos rotationPivot = BlockPos.ZERO;

    public BuildingPlacementSettings(BlockPos position) {
        this.position = position;
    }

    public static BuildingPlacementSettings create(BlockPos position) {
        return new BuildingPlacementSettings(position);
    }

    public BuildingPlacementSettings mirror(Mirror mirror) {
        this.mirror = mirror;
        return this;
    }

    public BuildingPlacementSettings rotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public BuildingPlacementSettings rotationPivot(BlockPos rotationPivot) {
        this.rotationPivot = rotationPivot;
        return this;
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public BlockPos getRotationPivot() {
        return this.rotationPivot;
    }
}
