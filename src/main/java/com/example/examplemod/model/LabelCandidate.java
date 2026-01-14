package com.example.examplemod.model;

public class LabelCandidate
{
    public final int slot;
    public final String text;
    public final int w;
    public final int h;
    public final int x;
    public final int y;

    public LabelCandidate(int slot, String text, int w, int h, int x, int y)
    {
        this.slot = slot;
        this.text = text;
        this.w = w;
        this.h = h;
        this.x = x;
        this.y = y;
    }
}

