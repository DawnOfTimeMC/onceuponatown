package com.dotteam.onceuponatown.entity.ai.systems.statemachine;

@FunctionalInterface
public interface Condition {
    boolean validate();
}
