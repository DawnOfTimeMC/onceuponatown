package com.dotteam.onceuponatown.town.map;

import com.dotteam.onceuponatown.town.map.TownMapUtils.Corner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

import static com.dotteam.onceuponatown.town.map.TownMapUtils.rectangularPosIterator;

public abstract class MapBuild {
    private final int sizeXNorth;
    private int sizeZNorth;
    private BlockPos originPos;
    private int id;
    private Direction direction;
    public MapBuild(int sizeXNorth, int sizeZNorth){
        this.sizeXNorth = sizeXNorth;
        this.sizeZNorth = sizeZNorth;
    }

    /**
     * @return The id of the Build. 0 if the Build is not yet on the townMap.
     */
    public int getId(){
        return this.id;
    }

    /**
     * @return The BlockPos of the NORTH_WEST corner of this MapBuild (its origin).
     */
    public BlockPos getOriginPos(){
        return this.originPos;
    }

    /**
     * @return The BlockPos of the given corner of this MapBuild.
     */
    public BlockPos getCornerPos(Corner corner){
        return Corner.NORTH_WEST.getCornerPos(this.originPos, this, this.direction, corner);
    }

    /**
     * @return The Direction of the MapBuild. Null if the MapBuild is not on the townMap yet.
     */
    @Nullable
    public Direction getDirection(){
        return this.direction;
    }

    /**
     * @param originPos BlockPos that we want to test in order to find the correct Y coordinate.
     * @param dir Direction of this MapBuild we are testing.
     * @return The Y coordinate adapted to this build and the given BlockPos.
     */
    public int findYForPos(BlockPos originPos, Direction dir){
        return originPos.getY();
    }

    /**
     * @param dir Direction in which we want the size of this MapBuild.
     * @return The width of the side of this MapBuild facing the given Direction.
     */
    public int getSize(@Nullable Direction dir){
        if(dir == null){
            dir = Direction.NORTH;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getSizeX() : this.getSizeZ();
    }

    /**
     * @param dir Direction of the MapBuild. If null, returns the size corresponding to the direction North.
     * @return the size of the side of this MapBuild on the X Axis.
     */
    public int getSizeX(@Nullable Direction dir){
        if(dir == null){
            return this.sizeXNorth;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.sizeXNorth : this.sizeZNorth;
    }

    /**
     * @return The current size of this MapBuild on the Axis X based on its direction.
     */
    public int getSizeX(){
        return this.getSizeX(this.getDirection());
    }

    /**
     * @param dir Direction of the MapBuild. If null, returns the size corresponding to the direction North.
     * @return the size of the side of this MapBuild on the Z Axis.
     */
    public int getSizeZ(@Nullable Direction dir) {
        if(dir == null){
            return this.sizeZNorth;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.sizeZNorth : this.sizeXNorth;
    }

    /**
     * @return The current size of this MapBuild on the Axis Z based on its direction.
     */
    public int getSizeZ() {
        return this.getSizeZ(this.getDirection());
    }

    /**
     * Function called to add this MapBuild to the given townMap, knowing that it can be added with the given parameters.
     * @param map townMap in which this MapBuild will be added.
     * @param bud Bud used to put set this Building on the townMap.
     * @param dir Direction corresponding to the orientation of this MapBuild.
     */
    public void addToMap(TownMap map, Bud bud, Direction dir){
        this.originPos = bud.findOriginPos(this, dir);
        this.direction = dir;
        this.id = map.generateNewID();
        map.addNewBuilds(this.id, this);
        this.onAddedToMap(map);
    }

    /**
     * Replace the NW Corner BlockPos with the given position. Used when the MapBuild must be extended.
     * @param newOrigin BlockPos from which this MapBuild now starts.
     */
    public void setOriginPos(BlockPos newOrigin){
        this.originPos = newOrigin;
    }

    /**
     * Extends the Z size for North direction by the given extensionSizeZ.
     * @param extensionSizeZ New size on Z axis.
     */
    protected void extendSizeZNorth(int extensionSizeZ){
        this.sizeZNorth += extensionSizeZ;
    }

    /**
     * Function called just after this MapBuild was added to the townMap.
     * Override it to add post placement steps, like Buds generation.
     * @param map townMap in which we add the Build.
     */
    protected abstract void onAddedToMap(TownMap map);

    /**
     * Check whenever the given MapBuild can be placed on the given Bud. I.e., we will test if the map is empty or if the
     * terrain si flat enough.
     * @param map townMap where we are trying to build the MapBuild.
     * @param bud Bud that we are testing with the given direction.
     * @param dir Direction of the MapPath to which the MapBuild will be connected. The Y position of the MapBuild will correspond
     *            to the Y value of this MapPath at this MapBuild's DoorPoint.
     * @return True if the surface is indeed empty, false otherwise.
     */
    public boolean canBeBuiltOnBud(TownMap map, Bud bud, Direction dir){
        BlockPos testedOriginPos = bud.findOriginPos(this, dir);
        for(BlockPos.MutableBlockPos testedPos : rectangularPosIterator(testedOriginPos, this.getSizeX(dir), this.getSizeZ(dir))) {
            if(this.canNotBeBuiltOnPos(map, testedOriginPos, testedPos)){
                return false;
            }
        }
        return true;
    }

    /**
     * Function used to check complementary conditions to place a MapBuild at a given position.
     * @param map townMap in which this MapBuild will be added.
     * @param originPos BlockPos of the NW Corner currently used.
     * @param testedPos BlockPos of the current BlockPos when want to check.
     * @return True if the MapBuild can not be placed on this position, false otherwise.
     */
    public boolean canNotBeBuiltOnPos(TownMap map, BlockPos originPos, BlockPos testedPos) {
        return !map.isEmpty(testedPos);
    }
}
