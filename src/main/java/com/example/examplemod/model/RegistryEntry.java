package com.example.examplemod.model;

public class RegistryEntry
{
    public final String name;
    public final int port;
    public final String mode;
    public long lastSeenMs;

    public RegistryEntry(String name, int port, String mode)
    {
        this.name = name;
        this.port = port;
        this.mode = mode == null ? "" : mode;
    }
}

