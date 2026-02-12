package com.rainbow_universe.bettercode.core;

import java.util.List;

public interface GameBridge {
    String currentDimension();
    List<String> scoreboardLines();
    void sendChat(String message);
    void sendActionBar(String message);
}
