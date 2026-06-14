package github.meloweh.antigrieflever.tracking;

public final class PlayerFinderMovement {
    static final long CHECK_INTERVAL_TICKS = 3L * 20L;
    static final long DEDUPLICATION_WINDOW_TICKS = 2L;
    private static final double MAX_CONTINUOUS_DISTANCE_PER_TICK = 16.0;
    private static final double NORMAL_WALK_SPEED_BLOCKS_PER_SECOND = 4.317;

    private PlayerFinderMovement() {
    }

    static long walkingTimeTicks(double distance) {
        return Math.max(0L, (long) Math.ceil(distance / NORMAL_WALK_SPEED_BLOCKS_PER_SECOND * 20.0));
    }

    static boolean isPositionDiscontinuity(double distance, long elapsedTicks) {
        return distance > MAX_CONTINUOUS_DISTANCE_PER_TICK * Math.max(1L, elapsedTicks);
    }
}
