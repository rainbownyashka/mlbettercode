package com.example.examplemod.model;

public class Rect
{
    public final int x;
    public final int y;
    public final int w;
    public final int h;

    public Rect(int x, int y, int w, int h)
    {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int right()
    {
        return x + w;
    }

    public int bottom()
    {
        return y + h;
    }

    public boolean intersects(Rect other)
    {
        return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
    }
}

