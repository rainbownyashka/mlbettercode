package com.rainbow_universe.bettercode.core;

import java.nio.file.Path;
import java.util.List;

public interface GameBridge {
    String currentDimension();
    Path runDirectory();
    List<String> scoreboardLines();
    boolean executeClientCommand(String command);
    void sendChat(String message);
    void sendActionBar(String message);
}
