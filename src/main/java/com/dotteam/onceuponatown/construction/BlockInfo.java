package com.dotteam.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public record BlockInfo(BlockPos pos, BlockState state, CompoundTag nbt) {
    public String toString() {
        return String.format(Locale.ROOT, "<BlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
    }
}
