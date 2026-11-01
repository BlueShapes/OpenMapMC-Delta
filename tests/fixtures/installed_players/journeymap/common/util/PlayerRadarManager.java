package journeymap.common.util;

import java.util.Map;
import java.util.UUID;

/** Test-only approximation of the public JourneyMap player manager signature. */
public final class PlayerRadarManager {
    private static final PlayerRadarManager INSTANCE = new PlayerRadarManager();
    private Map<UUID, ?> players = Map.of();

    private PlayerRadarManager() { }

    public static PlayerRadarManager getInstance() { return INSTANCE; }
    public Map<UUID, ?> getPlayers() { return players; }
    public static void setPlayers(Map<UUID, ?> players) { INSTANCE.players = players; }
}
