package com.dotteam.onceuponatown.town.map;

import com.dotteam.onceuponatown.town.map.TownMapUtils.Corner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

import static com.dotteam.onceuponatown.town.map.TownMapUtils.SIDE_SIZE_MAX_GARDEN;

/**
 * Each bud is a point at the intersection of two paths, or a path and a plot border.
 * Buds serve as corners for placing a new parcel, and this class contains the function to place correctly the buildings.
 */
public class Bud {

    private final BlockPos realPos;
    private int squaredDistToCenter;
    private final Corner corner;
    private final Direction[] adjacentPaths;
    private final BudType type;

    private Bud(TownMap map, BudType type, BlockPos realPos, Corner corner, Direction[] adjacentPaths){
        this.type = type;
        this.realPos = realPos;
        this.corner = corner;
        this.setSquaredDistToCenter(map);
        this.adjacentPaths = adjacentPaths;
        map.addToBuds(this);
    }

    /**
     * Create a new instance of a Bud and adds it to the townMap. If a similar Bud exists, returns null.
     * @param map townMap of the bud.
     * @param type Type of this bud, depending on the building it will be able to support.
     * @param realPos BlockPos of the bud.
     * @param corner Corner type of this bud.
     * @param adjacentPaths Direction where there is a MapPath from the realPos.
     * @return The new instance of Bud or null.
     */
    @Nullable
    public static Bud createBud(TownMap map, BudType type, BlockPos realPos, Corner corner, Direction[] adjacentPaths){
        for(Bud bud : map.getBuds()){
            if(bud.realPos.getX() == realPos.getX() && bud.realPos.getZ() == realPos.getZ()){
                return null;
            }
        }
        //TODO Replace the method to get Y with a MC one !
        return new Bud(map, type, realPos/*townMapDisplay.getSurfaceY(realPos))*/, corner, adjacentPaths);
        //return new Bud(map, type, realPos.atY(80)/*townMapDisplay.getSurfaceY(realPos))*/, corner, adjacentPaths);
    }

    /**
     * Create a new instance of a Bud of DEFAULT type, and adds it to the townMap. If a similar Bud exists, returns null.
     * @param map townMap of the bud.
     * @param realPos BlockPos of the bud.
     * @param corner Corner type of this bud.
     * @param adjacentPaths Direction where there is a MapPath from the realPos.
     * @return The new instance of Bud or null.
     */
    @Nullable
    public static Bud createBud(TownMap map, BlockPos realPos, Corner corner, Direction[] adjacentPaths){
        return createBud(map, BudType.DEFAULT, realPos, corner, adjacentPaths);
    }

    /**
     * @return The squared distance to the town center.
     */
    public int getSquaredDistToCenter() {
        return this.squaredDistToCenter;
    }

    /**
     * Function that updates the squared distance between this bud and the town center.
     * @param map townMap of the bud.
     */
    public void setSquaredDistToCenter(TownMap map){
        this.squaredDistToCenter = this.getSquaredDistTo(map.getTownCenter());
    }

    /**
     * @param pos BlockPos to which we want to compute the horizontal distance.
     * @return The squared distance between the given pos and this Bud.
     */
    public int getSquaredDistTo(BlockPos pos){
        int difX = this.realPos.getX() - pos.getX();
        int difZ = this.realPos.getZ() - pos.getZ();
        return difX * difX + difZ * difZ;
    }

    /**
     * @return Returns a list that contains the direction of the adjacent MapPaths.
     */
    public Direction[] getAdjacentPaths() {
        return this.adjacentPaths;
    }

    /**
     * @return This Bud's Corner type.
     */
    public Corner getCorner() {
        return this.corner;
    }

    /**
     * @return The real BlockPos of this Bud.
     */
    public BlockPos getRealPos(){
        return this.realPos;
    }

    /**
     * Function to get the NORTH_WEST real position of a given MapBuild placed on this Bud with the given rotation.
     * @param build MapBuild to place.
     * @param dir Direction of the MapBuild, used to get the size on X and Z axis.
     * @return The BlockPos of the origin of the MapBuild, at the correct Y.
     */
    public BlockPos findOriginPos(MapBuild build, Direction dir){
        BlockPos origin = this.corner.getOrigin(this.realPos, build, dir);
        return origin.atY(build.findYForPos(origin, dir));
    }

    /**
     * Creates a MapGarden instance adapted to this Bud generated with the given rotation.
     * @param map The townMap of this Bud.
     * @param clockwise True if rotating clockwise, false for counterclockwise.
     * @return A MapGarden object with the biggest sizes in X and Z that can fit.
     */
    public MapGarden createAdaptedGarden(TownMap map, boolean clockwise) {
        int[] sizes = new int[4];
        Direction dir = clockwise ? this.getCorner().getLeftDirection() : this.getCorner().getRightDirection();
        dir = dir.getOpposite();
        BlockPos.MutableBlockPos cursor = this.getRealPos().mutable();
        int sideIndex = 0;
        // The last value of sizes will be different to 0 only if the loop was able to create a rectangle.
        while (sizes[3] == 0) {
            int max = sideIndex < 2 ? SIDE_SIZE_MAX_GARDEN : sizes[sideIndex - 2];
            sizes[sideIndex] = map.getEmptyLength(cursor, dir, max);
            // If the opposite side has a different size, we come back 2 side before, and restart the process with a shorter size.
            if(sideIndex >= 2){
                if(sizes[sideIndex] != sizes[sideIndex - 2]){
                    sizes[sideIndex - 2] = sizes[sideIndex];
                    sizes[sideIndex - 1] = 0;
                    sizes[sideIndex] = 0;
                    sideIndex -= 2;
                    dir = dir.getOpposite();
                }
            }

            // For next loop.
            sideIndex++;
            dir = clockwise ? dir.getClockWise() : dir.getCounterClockWise();
        }
        return dir.getAxis() == Direction.Axis.X ? new MapGarden(sizes[0], sizes[1]) : new MapGarden(sizes[1], sizes[0]);
    }

    /**
     * @return The type of this Bud.
     */
    public BudType getType() {
        return this.type;
    }

    public enum BudType{

        DEFAULT(),
        AGRICULTURAL(),
        BRIDGE();

        BudType(){

        }
    }
}
