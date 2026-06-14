package github.meloweh.antigrieflever.tracking;

import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.item.PlayerFinderCompassItem;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class PlayerFinderSavedData extends SavedData {
    private static final String DATA_NAME = "antigrieflever_player_finder";
    private static final Factory<PlayerFinderSavedData> FACTORY =
        new Factory<>(PlayerFinderSavedData::new, PlayerFinderSavedData::load);
    private static final long TIME_ADDED_FLASH_TICKS = 20L;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, TargetTeleportTracker> teleportTrackers = new HashMap<>();
    private final Map<UUID, PositionSnapshot> positionSnapshots = new HashMap<>();

    public static PlayerFinderSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Nullable
    public Session session(UUID player) {
        return sessions.get(player);
    }

    public void activate(UUID player, String targetName, long now) {
        long activeUntil = now + Config.playerFinderActiveTicks();
        sessions.put(
            player,
            new Session(
                targetName,
                activeUntil,
                activeUntil + Config.playerFinderCooldownTicks(),
                now + Config.playerFinderUpdateTicks(),
                now + 5L * 20L,
                0.0,
                false
            )
        );
        setDirty();
    }

    public void observePosition(ServerPlayer target, long now) {
        if (!isActivelyTracked(target, now)) {
            positionSnapshots.remove(target.getUUID());
            return;
        }

        PositionSnapshot current = PositionSnapshot.of(target, now);
        PositionSnapshot previous = positionSnapshots.put(target.getUUID(), current);
        if (previous == null) {
            return;
        }

        double distance = previous.position().distanceTo(current.position());
        if (!previous.dimension().equals(current.dimension())) {
            recordTeleport(target, now, Math.max(1.0, distance), current.position());
        } else if (
            PlayerFinderMovement.isPositionDiscontinuity(distance, current.tick() - previous.tick())
        ) {
            recordTeleport(target, now, distance, current.position());
        }
    }

    public void recordDimensionChange(ServerPlayer target, long now) {
        PositionSnapshot current = PositionSnapshot.of(target, now);
        PositionSnapshot previous = positionSnapshots.put(target.getUUID(), current);
        double distance = previous == null ? 1.0 : Math.max(1.0, previous.position().distanceTo(current.position()));
        recordTeleport(target, now, distance, current.position());
    }

    public void recordTeleport(ServerPlayer target, long now, double distance, Vec3 destination) {
        if (distance <= 0.0) {
            return;
        }
        if (!isActivelyTracked(target, now)) {
            return;
        }

        boolean recorded = teleportTrackers.computeIfAbsent(target.getUUID(), ignored -> new TargetTeleportTracker())
            .record(now, destination);
        if (!recorded) {
            return;
        }

        String targetName = target.getGameProfile().getName();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (now < session.activeUntil() && session.targetName().equalsIgnoreCase(targetName)) {
                entry.setValue(session.withPendingTeleportDistance(session.pendingTeleportDistance() + distance));
                setDirty();
            }
        }
    }

    public void processPlayer(ServerPlayer player, long now) {
        UUID playerId = player.getUUID();
        Session session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        if (now >= session.activeUntil()) {
            if (!session.deactivationNotified()) {
                player.sendSystemMessage(
                    Component.translatable(
                        "message.antigrieflever.player_finder.deactivated",
                        formatTicks(Math.max(0L, session.cooldownUntil() - now))
                    )
                );
                player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
                session = session.withDeactivationNotified();
                sessions.put(playerId, session);
                setDirty();
            }

            if (now >= session.cooldownUntil()) {
                player.sendSystemMessage(Component.translatable("message.antigrieflever.player_finder.cooldown_finished"));
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
                sessions.remove(playerId);
                setDirty();
            }
            return;
        }

        ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(session.targetName());
        if (now >= session.nextMovementCheckAt()) {
            long nextMovementCheck = nextMovementCheck(session.nextMovementCheckAt(), now);
            double teleportDistance = session.pendingTeleportDistance();
            if (teleportDistance > 0.0) {
                long extensionTicks = PlayerFinderMovement.walkingTimeTicks(teleportDistance);
                session = session.extend(extensionTicks, nextMovementCheck);
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.25F);
                PlayerFinderCompassItem.flashTimeAdded(
                    player,
                    session.targetName(),
                    now + TIME_ADDED_FLASH_TICKS,
                    now
                );
            } else {
                session = session.withTeleportCheck(nextMovementCheck);
            }
            sessions.put(playerId, session);
            setDirty();
        }

        PlayerFinderCompassItem.synchronizeActiveUntil(player, session.targetName(), session.activeUntil(), now);

        if (now >= session.nextUpdateAt()) {
            player.sendSystemMessage(
                Component.translatable(
                    target != null
                        ? "message.antigrieflever.player_finder.time_left"
                        : "message.antigrieflever.player_finder.target_offline_during_tracking",
                    session.targetName(),
                    formatTicks(session.activeUntil() - now)
                )
            );
            player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F);

            long updateInterval = Config.playerFinderUpdateTicks();
            long nextUpdate = session.nextUpdateAt();
            while (nextUpdate <= now) {
                nextUpdate += updateInterval;
            }
            sessions.put(playerId, session.withNextUpdateAt(nextUpdate));
            setDirty();
        }
    }

    public static String formatTicks(long ticks) {
        long totalSeconds = Math.max(0L, (ticks + 19L) / 20L);
        long days = totalSeconds / 1200L;
        long seconds = totalSeconds % 60L;
        if (days > 0L) {
            long remainingSeconds = totalSeconds % 1200L;
            return "%dd %dm %ds".formatted(days, remainingSeconds / 60L, remainingSeconds % 60L);
        }
        if (totalSeconds >= 3600L) {
            return "%dh %dm %ds".formatted(totalSeconds / 3600L, totalSeconds % 3600L / 60L, seconds);
        }
        if (totalSeconds >= 60L) {
            return "%dm %ds".formatted(totalSeconds / 60L, seconds);
        }
        return "%ds".formatted(seconds);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            CompoundTag sessionTag = new CompoundTag();
            sessionTag.putUUID("Player", entry.getKey());
            sessionTag.putString("TargetName", entry.getValue().targetName());
            sessionTag.putLong("ActiveUntil", entry.getValue().activeUntil());
            sessionTag.putLong("CooldownUntil", entry.getValue().cooldownUntil());
            sessionTag.putLong("NextUpdateAt", entry.getValue().nextUpdateAt());
            sessionTag.putLong("NextMovementCheckAt", entry.getValue().nextMovementCheckAt());
            sessionTag.putDouble("PendingTeleportDistance", entry.getValue().pendingTeleportDistance());
            sessionTag.putBoolean("DeactivationNotified", entry.getValue().deactivationNotified());
            entries.add(sessionTag);
        }
        tag.put("Sessions", entries);
        return tag;
    }

    private static PlayerFinderSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerFinderSavedData data = new PlayerFinderSavedData();
        for (Tag rawEntry : tag.getList("Sessions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) rawEntry;
            if (!entry.hasUUID("Player")) {
                continue;
            }
            data.sessions.put(
                entry.getUUID("Player"),
                new Session(
                    entry.getString("TargetName"),
                    entry.getLong("ActiveUntil"),
                    entry.getLong("CooldownUntil"),
                    entry.getLong("NextUpdateAt"),
                    entry.getLong("NextMovementCheckAt"),
                    entry.getDouble("PendingTeleportDistance"),
                    entry.getBoolean("DeactivationNotified")
                )
            );
        }
        return data;
    }

    public record Session(
        String targetName,
        long activeUntil,
        long cooldownUntil,
        long nextUpdateAt,
        long nextMovementCheckAt,
        double pendingTeleportDistance,
        boolean deactivationNotified
    ) {
        private Session withNextUpdateAt(long nextUpdateAt) {
            return new Session(
                targetName,
                activeUntil,
                cooldownUntil,
                nextUpdateAt,
                nextMovementCheckAt,
                pendingTeleportDistance,
                deactivationNotified
            );
        }

        private Session withPendingTeleportDistance(double pendingTeleportDistance) {
            return new Session(
                targetName,
                activeUntil,
                cooldownUntil,
                nextUpdateAt,
                nextMovementCheckAt,
                pendingTeleportDistance,
                deactivationNotified
            );
        }

        private Session withTeleportCheck(long nextMovementCheckAt) {
            return new Session(
                targetName,
                activeUntil,
                cooldownUntil,
                nextUpdateAt,
                nextMovementCheckAt,
                0.0,
                deactivationNotified
            );
        }

        Session extend(long extensionTicks, long nextMovementCheckAt) {
            return new Session(
                targetName,
                activeUntil + extensionTicks,
                cooldownUntil + extensionTicks,
                nextUpdateAt,
                nextMovementCheckAt,
                0.0,
                deactivationNotified
            );
        }

        private Session withDeactivationNotified() {
            return new Session(
                targetName,
                activeUntil,
                cooldownUntil,
                nextUpdateAt,
                nextMovementCheckAt,
                pendingTeleportDistance,
                true
            );
        }
    }

    private static long nextMovementCheck(long previousCheck, long now) {
        if (previousCheck > now) {
            return previousCheck;
        }
        long skippedIntervals = (now - previousCheck) / PlayerFinderMovement.CHECK_INTERVAL_TICKS + 1L;
        return previousCheck + skippedIntervals * PlayerFinderMovement.CHECK_INTERVAL_TICKS;
    }

    private boolean isActivelyTracked(ServerPlayer target, long now) {
        String targetName = target.getGameProfile().getName();
        return sessions.values().stream().anyMatch(
            session -> now < session.activeUntil() && session.targetName().equalsIgnoreCase(targetName)
        );
    }

    private record PositionSnapshot(long tick, ResourceKey<Level> dimension, Vec3 position) {
        private static PositionSnapshot of(ServerPlayer player, long tick) {
            return new PositionSnapshot(tick, player.level().dimension(), player.position());
        }
    }

    private record TeleportSample(long tick, Vec3 destination) {
    }

    static final class TargetTeleportTracker {
        private final Deque<TeleportSample> samples = new ArrayDeque<>();

        boolean record(long tick, Vec3 destination) {
            prune(tick);
            boolean duplicate = samples.stream().anyMatch(
                sample ->
                    tick - sample.tick() <= PlayerFinderMovement.DEDUPLICATION_WINDOW_TICKS
                        && sample.destination().distanceToSqr(destination) < 0.01
            );
            if (duplicate) {
                return false;
            }
            samples.addLast(new TeleportSample(tick, destination));
            return true;
        }

        private void prune(long now) {
            long oldestAllowedTick = now - PlayerFinderMovement.DEDUPLICATION_WINDOW_TICKS;
            while (!samples.isEmpty() && samples.getFirst().tick() < oldestAllowedTick) {
                samples.removeFirst();
            }
        }
    }
}
