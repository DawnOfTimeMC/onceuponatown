package com.dotteam.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public record EntityInfo(Vec3 pos, BlockPos blockPos, CompoundTag nbt) {
    public String toString() {
        return String.format(Locale.ROOT, "<EntityInfo | %s | %s | %s>", this.pos, this.blockPos, this.nbt);
    }
}
