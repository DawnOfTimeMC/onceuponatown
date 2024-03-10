package com.dotteam.onceuponatown.town.map;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static com.dotteam.onceuponatown.town.map.TownMapUtils.Corner;
import static com.dotteam.onceuponatown.town.map.TownMapUtils.MAXI_Y_DIFFERENCE;

public class MapBuilding extends MapPlot {

    public MapBuilding(int sizeXNorth, int sizeZNorth) {
        super(sizeXNorth, sizeZNorth);
    }

    /**
     * @param originPos North-West BlockPos of the building.
     * @param dir Direction of the building.
     * @return The BlockPos of the door of this MapBuilding, with the given parameters.
     */
    private BlockPos getDoorYPos(BlockPos originPos, Direction dir){
        //TODO Replace this function with the real position of the Door.

        //TODO Fix this function, it doesn't seem to work properly
        // I will assume the door is in the middle of the North side.
        int offset = dir.getAxis() == Direction.Axis.X ? this.getSizeZ(dir) : this.getSizeX(dir);
        return Corner.NORTH_WEST.getCornerPos(originPos, this, dir, Corner.getCornerNextToDir(dir.getOpposite(), false)).relative(dir.getClockWise(), offset / 2);
    }

    @Override
    public int findYForPos(BlockPos originPos, Direction dir) {
        return this.getDoorYPos(originPos, dir).getY();
    }

    @Override
    public boolean canNotBeBuiltOnPos(TownMap map, BlockPos originPos, BlockPos testedPos) {
        return super.canNotBeBuiltOnPos(map, originPos, testedPos) || Math.abs(testedPos.getY() - originPos.getY()) > MAXI_Y_DIFFERENCE;
    }
}
