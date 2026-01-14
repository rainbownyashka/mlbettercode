package com.example.examplemod.feature.place;

import com.example.examplemod.model.PlaceEntry;

import java.util.ArrayDeque;
import java.util.Deque;

final class PlaceState
{
    final Deque<PlaceEntry> queue = new ArrayDeque<>();
    PlaceEntry current = null;
    boolean active = false;

    void reset()
    {
        active = false;
        queue.clear();
        current = null;
    }
}

