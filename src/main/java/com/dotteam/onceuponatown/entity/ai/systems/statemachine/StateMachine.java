package com.dotteam.onceuponatown.entity.ai.systems.statemachine;

import java.util.ArrayList;
import java.util.List;

public class StateMachine {
    private String name;
    private int size;
    private List<State> states = new ArrayList<>();
    private List<Transition> transitions = new ArrayList<>();
    private State initialState;
    private State currentState;

    private StateMachine(String name, int size, State initialState) {
        this.name = name;
        this.size = size;
        this.states.add(initialState);
        this.initialState = initialState;
        this.currentState = initialState;
    }

    public static StateMachine create(String name, int size, State pinitialstate) {
        return new StateMachine(name, size, pinitialstate);
    }

    public StateMachine addState(State state) {
        if (!this.states.contains(state)) {
            this.states.add(state);
        }
        return this;
    }

    public StateMachine addTransition(State fromState, State toState, Condition condition) {
        this.transitions.add(new Transition(fromState, toState, condition));
        return this;
    }

    public StateMachine addTransition(String name, State fromState, State toState, Condition condition) {
        this.transitions.add(new Transition(name, fromState, toState, condition));
        return this;
    }

    public void tick() {
        updateState();
        this.currentState.runTickCode();
    }

    public void updateState() {
        List<Transition> toEvaluate = new ArrayList<>();
        for (Transition transition : this.transitions) {
            if (transition.getOrigin() == this.currentState) {
                toEvaluate.add(transition);
            }
        }
        for (Transition transition : toEvaluate) {
            if (transition.validate() && (transition.getDestination() != this.currentState)) {
                this.currentState.runExitCode();
                this.currentState = transition.getDestination();
                this.currentState.runEntryCode();
                return;
            }
        }
    }

    public void reset() {
        this.currentState = this.initialState;
    }

    public State getCurrentState() {return this.currentState;}
}
