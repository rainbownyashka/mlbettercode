package com.rainbow_universe.bettercode.core;

import com.rainbow_universe.bettercode.core.place.PlaceRuntimeEntry;

import java.nio.file.Path;
import java.util.List;

public interface GameBridge {
    String currentDimension();
    Path runDirectory();
    List<String> scoreboardLines();
    boolean supportsPlacePlanExecution();
    PlaceExecResult executePlacePlan(List<PlaceOp> ops, boolean checkOnly);
    PlaceExecResult executePlaceStep(PlaceRuntimeEntry entry, boolean checkOnly);
    boolean executeClientCommand(String command);
    void sendChat(String message);
    void sendActionBar(String message);
}
