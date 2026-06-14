package github.meloweh.antigrieflever.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import github.meloweh.antigrieflever.Config;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerFinderSavedDataTest {
    @Test
    void defaultsToThreeMinutesOfTracking() {
        assertEquals(3, Config.PLAYER_FINDER_ACTIVE_MINUTES.getDefault());
    }

    @Test
    void formatsTrackingAndMinecraftDayDurations() {
        assertEquals("5m 0s", PlayerFinderSavedData.formatTicks(5L * 60L * 20L));
        assertEquals("10d 0m 0s", PlayerFinderSavedData.formatTicks(10L * 24_000L));
        assertEquals("59s", PlayerFinderSavedData.formatTicks(59L * 20L));
    }

    @Test
    void cooldownStartsWhenTrackingEnds() {
        PlayerFinderSavedData data = new PlayerFinderSavedData();
        UUID player = UUID.randomUUID();
        long now = 1_000L;

        data.activate(player, "Player1", now);
        PlayerFinderSavedData.Session session = data.session(player);

        assertEquals(now + Config.playerFinderActiveTicks(), session.activeUntil());
        assertEquals(session.activeUntil() + Config.playerFinderCooldownTicks(), session.cooldownUntil());
        assertEquals("Player1", session.targetName());
    }
}
