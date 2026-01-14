package com.example.examplemod.model;

import java.util.List;

public class LayoutResult
{
    public final List<PlacedLabel> placed;
    public final List<Label> overflow;

    public LayoutResult(List<PlacedLabel> placed, List<Label> overflow)
    {
        this.placed = placed;
        this.overflow = overflow;
    }
}

