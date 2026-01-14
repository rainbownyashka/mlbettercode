package com.example.examplemod.model;

public class CommandResult
{
    public final boolean executed;
    public final String mode;

    public CommandResult(boolean executed, String mode)
    {
        this.executed = executed;
        this.mode = mode;
    }
}

