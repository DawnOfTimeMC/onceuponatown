package com.dotteam.onceuponatown.entity.ai.systems.statemachine;

public class State {
    private String name;
    private Runnable tickCode;
    private Runnable entryCode;
    private Runnable exitCode;

    private State(String name, Runnable tickCode) {
        this.name = name;
        this.tickCode = tickCode;
    }

    public static State create(String name, Runnable tickCode) {
        return new State(name, tickCode);
    }

    public State withEntryCode(Runnable entryCode) {
        this.entryCode = entryCode;
        return this;
    }

    public State withExitCode(Runnable exitCode) {
        this.exitCode = exitCode;
        return this;
    }

    public void runTickCode() {
        if (this.tickCode != null) {
            this.tickCode.run();
        }
    }

    public void runEntryCode() {
        if (this.entryCode != null) {
            this.entryCode.run();
        }
    }

    public void runExitCode() {
        if (exitCode != null) {
            this.exitCode.run();
        }
    }

    public String getName() {return name;}
}
