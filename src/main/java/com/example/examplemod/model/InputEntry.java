package com.example.examplemod.model;

public class InputEntry
{
    public final int mode;
    public final String text;

    public InputEntry(int mode, String text)
    {
        this.mode = mode;
        this.text = text == null ? "" : text;
    }
}
