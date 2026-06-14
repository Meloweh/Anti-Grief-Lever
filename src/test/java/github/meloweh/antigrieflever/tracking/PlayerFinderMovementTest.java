package github.meloweh.antigrieflever.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlayerFinderMovementTest {
    @Test
    void convertsDistanceToNormalWalkingTime() {
        assertEquals(100L, PlayerFinderMovement.walkingTimeTicks(4.317 * 5.0));
    }

    @Test
    void onlyTreatsLargePositionSkipsAsUnknownTeleports() {
        assertFalse(PlayerFinderMovement.isPositionDiscontinuity(16.0, 1L));
        assertFalse(PlayerFinderMovement.isPositionDiscontinuity(32.0, 2L));
        assertTrue(PlayerFinderMovement.isPositionDiscontinuity(16.01, 1L));
        assertTrue(PlayerFinderMovement.isPositionDiscontinuity(40.0, 2L));
    }

    @Test
    void deduplicatesEventHookAndPositionFallbackForOneTeleport() {
        PlayerFinderSavedData.TargetTeleportTracker tracker =
            new PlayerFinderSavedData.TargetTeleportTracker();
        Vec3 destination = new Vec3(100.0, 70.0, -30.0);

        assertTrue(tracker.record(100L, destination));
        assertFalse(tracker.record(101L, destination));
        assertTrue(tracker.record(101L, destination.add(1.0, 0.0, 0.0)));
        assertTrue(tracker.record(103L, destination));
    }

    @Test
    void extensionMovesActiveEndAndCooldownTogether() {
        PlayerFinderSavedData.Session session =
            new PlayerFinderSavedData.Session("Player1", 1_000L, 25_000L, 500L, 600L, 42.0, false);

        PlayerFinderSavedData.Session extended = session.extend(200L, 800L);

        assertEquals(1_200L, extended.activeUntil());
        assertEquals(25_200L, extended.cooldownUntil());
        assertEquals(800L, extended.nextMovementCheckAt());
        assertEquals(0.0, extended.pendingTeleportDistance());
    }
}
