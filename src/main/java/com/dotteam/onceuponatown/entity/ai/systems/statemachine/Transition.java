package com.dotteam.onceuponatown.entity.ai.systems.statemachine;

public class Transition {
    private String name = "Unnamed";
    private State fromState;
    private State toState;
    private Condition condition;

    public Transition(String name, State fromState, State toState, Condition condition) {
        this.name = name;
        this.fromState = fromState;
        this.toState = toState;
        this.condition = condition;
    }

    public Transition(State fromState, State toState, Condition condition) {
        this.fromState = fromState;
        this.toState = toState;
        this.condition = condition;
    }

    public boolean validate() {
        return this.condition.validate();
    }

    public State getOrigin() {return this.fromState;}

    public State getDestination() {return this.toState;}
}
