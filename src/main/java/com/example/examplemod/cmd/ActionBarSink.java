package com.example.examplemod.cmd;

@FunctionalInterface
public interface ActionBarSink
{
    void show(boolean primary, String text, long durationMs);
}

