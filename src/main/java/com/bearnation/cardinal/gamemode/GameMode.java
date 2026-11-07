package com.bearnation.cardinal.gamemode;

import com.bearnation.cardinal.map.MapInfo;
import com.velocitypowered.api.proxy.Player;

import java.util.List;

public interface GameMode {

    /**
     * @return the unique identifier of this game mode (e.g. "CTF", "KOTH", etc)
     */
    String getId();

    /**
     * @return a display name for the game mode
     */
    String getDisplayName();

    /**
     * @return the number of teams this mode supports
     */
    int getTeamCount();

    /**
     * Called when a match with this game mode starts.
     * You can use this to initialize game-specific mechanics.
     */
    void onMatchStart(MapInfo map);

    /**
     * Called periodically during a match (optional).
     */
    default void onMatchTick(MapInfo map) {
    }

    /**
     * Called when the match ends or is cancelled.
     */
    void onMatchEnd(MapInfo map);

    /**
     * Returns the team names for this game mode (e.g. ["Red", "Blue"] or ["Red", "Blue", "Green", "Yellow"])
     */
    List<String> getTeamNames();

    void onPlayerJoin(Player player);

    default void initialize(MapInfo map) {
    }

    ;


    default void start(MapInfo map) {
        onMatchStart(map);
    }

    default void stop(MapInfo map) {
        onMatchEnd(map);
    }

    default boolean isTeamBased() {
        return getTeamCount() > 1;
    }
}
