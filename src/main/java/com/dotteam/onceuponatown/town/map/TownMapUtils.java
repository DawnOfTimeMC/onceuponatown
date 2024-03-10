package com.dotteam.onceuponatown.town.map;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Collections;

import static net.minecraft.core.Direction.*;

public class TownMapUtils {
    // Width of small paths
    public static final int SMALL_WIDTH = 2;
    // Width of large paths
    public static final int BIG_WIDTH = 4;
    // Default size of paths, as well as the minimum size below which an area is transformed into a garden
    public static final int DEFAULT_PATH_LENGTH = 10;
    // Maximum vertical difference accepted between the door Y value, and any block Y value, on the border of the Building.
    public static final int MAXI_Y_DIFFERENCE = 10;
    // Maximum size of the side of a squared garden. If a Bud available space in one of the 2 directions is smaller, it creates a MapGarden.
    public static final int SIDE_SIZE_MAX_GARDEN = 10;
    // Minimum spacing between paths when the central building is placed
    public static final int MINI_PATH_SPACE = 20;
    // Probability of placing a path when adding a bud
    public static final float PATH_SPAWN_RATE = 0.25F;
    // Probability that a new path is a big path
    public static final float BIG_PATH_SPAWN_RATE = 0.3F;
    // Probability that a path stops growing when a building is placed next to it
    public static final float PATH_STOP_RATE = 0.15F;

    public static final Direction[] NW_DIR_CYCLE = new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, NORTH};

    /**
     * Function that provides an iterator of MutablePos on a rectangular shape in Clockwise order.
     * @param originPos NORTH_WEST BlockPos of the rectangle.
     * @param sizeX Horizontal size of the rectangle.
     * @param sizeZ Vertical size of the rectangle.
     * @return An iterator that provides a Mutable BlockPos following the rectangle shape. Be careful to not move the provided
     * Mutable BlockPos, because it's position is not checked in this function.
     */
    public static Iterable<BlockPos.MutableBlockPos> rectangularPosIterator(BlockPos originPos, int sizeX, int sizeZ){
        // If, for some reason, the building has a size of 1×1, we just return the originPos.
        if(sizeX < 2 && sizeZ < 2){
            return Collections.singletonList(originPos.mutable());
        }
        return () -> new AbstractIterator<>() {
            private final BlockPos.MutableBlockPos cursor = originPos.mutable();
            private int cursorDir;
            private final int[] moves = new int[]{sizeX - 1, sizeZ - 1, sizeX - 1, sizeZ - 1};

            @Override
            protected BlockPos.MutableBlockPos computeNext() {
                while(this.moves[this.cursorDir] <= 0){
                    this.cursorDir++;
                    if(this.cursorDir > 3){
                        return this.endOfData();
                    }
                }
                this.cursor.move(NW_DIR_CYCLE[this.cursorDir]);
                this.moves[this.cursorDir]--;
                return this.cursor;
            }
        };
    }

    public enum Corner{
        NORTH_WEST(WEST, NORTH),
        NORTH_EAST(NORTH, EAST),
        SOUTH_EAST(EAST, SOUTH),
        SOUTH_WEST(SOUTH, WEST);
        private final Direction leftDir;
        private final Direction rightDir;

        Corner(Direction leftDir, Direction rightDir){
            this.leftDir = leftDir;
            this.rightDir = rightDir;
        }

        public Direction getLeftDirection(){
            return this.leftDir;
        }

        public Direction getRightDirection(){
            return this.rightDir;
        }

        private int getStepX(){
            return this.leftDir.getStepX() + this.rightDir.getStepX();
        }

        private int getStepZ(){
            return this.leftDir.getStepZ() + this.rightDir.getStepZ();
        }

        /**
         * Function that allows to get the origin (NORTH_WEST corner) of a Build placed using this corner.
         * @param pos Position of the corner.
         * @param build Build to be placed.
         * @param buildDir Direction to which the Build will be oriented.
         * @return The position of the NORTH_WEST corner of the Build if it is placed on this corner.
         */
        public BlockPos getOrigin(BlockPos pos, MapBuild build, Direction buildDir){
            return this.getCornerPos(pos, build, buildDir, NORTH_WEST);
        }

        /**
         * Function that returns the BlockPos of the given targetCorner, based on this corner's position.
         * @param pos BlockPos of this corner.
         * @param build This corner's MapBuild.
         * @param buildDir The direction of the MapBuild.
         * @param targetCorner The corner we want to obtain.
         * @return The BlockPos of the targetCorner.
         */
        public BlockPos getCornerPos(BlockPos pos, MapBuild build, Direction buildDir, Corner targetCorner){
            int signOffsetX = (targetCorner.getStepX() - this.getStepX()) / 2;
            int signOffsetZ = (targetCorner.getStepZ() - this.getStepZ()) / 2;
            return pos.offset(signOffsetX * (build.getSizeX(buildDir) - 1), 0, signOffsetZ * (build.getSizeZ(buildDir) - 1));
        }

        /**
         * @param dirVector Direction of the vector.
         * @param cornerOnTheRight True to return the Corner on the right side of the vector direction, false
         *                         to get the Corner on the left side.
         * @return One of the 2 corners adjacent to a vector toward the given direction.
         */
        public static Corner getCornerNextToDir(Direction dirVector, boolean cornerOnTheRight) {
            return switch (dirVector){
                case NORTH -> cornerOnTheRight ? SOUTH_WEST : SOUTH_EAST;
                case EAST -> cornerOnTheRight ? NORTH_WEST : SOUTH_WEST;
                case SOUTH -> cornerOnTheRight ? NORTH_EAST : NORTH_WEST;
                case WEST -> cornerOnTheRight ? SOUTH_EAST : NORTH_EAST;
                default -> throw new IllegalStateException("Unexpected direction: " + dirVector + ". Map Corner can only exist on the horizontal plane.");
            };
        }
    }
}
