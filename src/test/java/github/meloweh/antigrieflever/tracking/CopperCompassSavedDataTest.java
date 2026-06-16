package github.meloweh.antigrieflever.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import github.meloweh.antigrieflever.Config;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CopperCompassSavedDataTest {
    @Test
    void defaultsMatchPlayerFinderTrackingRestrictions() {
        assertEquals(
            Config.PLAYER_FINDER_ACTIVE_MINUTES.getDefault(),
            Config.COPPER_COMPASS_ACTIVE_MINUTES.getDefault()
        );
        assertEquals(
            Config.PLAYER_FINDER_UPDATE_SECONDS.getDefault(),
            Config.COPPER_COMPASS_UPDATE_SECONDS.getDefault()
        );
        assertEquals(
            Config.PLAYER_FINDER_COOLDOWN_DAYS.getDefault(),
            Config.COPPER_COMPASS_COOLDOWN_DAYS.getDefault()
        );
    }

    @Test
    void cooldownStartsWhenTrackingEnds() {
        CopperCompassSavedData data = new CopperCompassSavedData();
        UUID player = UUID.randomUUID();
        long now = 1_000L;

        data.activate(player, now);
        CopperCompassSavedData.Session session = data.session(player);

        assertEquals(now + Config.copperCompassActiveTicks(), session.activeUntil());
        assertEquals(session.activeUntil() + Config.copperCompassCooldownTicks(), session.cooldownUntil());
        assertEquals(now + Config.copperCompassUpdateTicks(), session.nextUpdateAt());
    }
}
