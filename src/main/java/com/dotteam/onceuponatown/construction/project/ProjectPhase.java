package com.dotteam.onceuponatown.construction.project;

import net.minecraft.nbt.CompoundTag;

public abstract class ProjectPhase {
    protected int progression;

    abstract boolean nextStep();

    abstract boolean isCompleted();

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("PhaseProgression", this.progression);
        return tag;
    }

    public int getProgression() {
        return this.progression;
    }

    void setProgression(int progression) {
        this.progression = progression;
    }
}
