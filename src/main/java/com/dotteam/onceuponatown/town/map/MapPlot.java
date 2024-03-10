package com.dotteam.onceuponatown.town.map;

import net.minecraft.core.BlockPos;

import java.util.HashSet;

;

public abstract class MapPlot extends MapBuild {
    public  MapPlot(int sizeXNorth, int sizeZNorth) {
        super(sizeXNorth, sizeZNorth);
    }

    @Override
    protected void onAddedToMap(TownMap map) {
        // We try to find all the adjacent MapPath to extend them and add the Buds.
        // We will iterate on a one block bigger rectangle to find all the adjacent MapBuild.
        HashSet<Integer> ids = new HashSet<>();
        for(BlockPos.MutableBlockPos pos : TownMapUtils.rectangularPosIterator(this.getOriginPos().north().west(), this.getSizeX() + 2, this.getSizeZ() + 2)) {
            ids.add(map.getIDInMapPos(pos));
        }
        for(int id : ids){
            if(map.getBuild(id) instanceof MapPath path){
                path.update(map);
            }
        }
    }
}
