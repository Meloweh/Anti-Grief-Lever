package github.meloweh.antigrieflever.tracking;

import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

public final class CopperCompassSavedData extends SavedData {
    private static final String DATA_NAME = "antigrieflever_copper_compass";
    private static final Factory<CopperCompassSavedData> FACTORY =
        new Factory<>(CopperCompassSavedData::new, CopperCompassSavedData::load);

    private final Map<UUID, Session> sessions = new HashMap<>();

    public static CopperCompassSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Nullable
    public Session session(UUID player) {
        return sessions.get(player);
    }

    public void activate(UUID player, long now) {
        long activeUntil = now + Config.copperCompassActiveTicks();
        sessions.put(
            player,
            new Session(
                activeUntil,
                activeUntil + Config.copperCompassCooldownTicks(),
                now + Config.copperCompassUpdateTicks(),
                false
            )
        );
        setDirty();
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
                        "message.antigrieflever.copper_compass.deactivated",
                        PlayerFinderSavedData.formatTicks(Math.max(0L, session.cooldownUntil() - now))
                    )
                );
                player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
                session = session.withDeactivationNotified();
                sessions.put(playerId, session);
                setDirty();
            }

            if (now >= session.cooldownUntil()) {
                player.sendSystemMessage(
                    Component.translatable("message.antigrieflever.copper_compass.cooldown_finished")
                );
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
                sessions.remove(playerId);
                setDirty();
            }
            return;
        }

        if (now >= session.nextUpdateAt()) {
            boolean hasTarget = ProtectionSavedData.get(player.serverLevel())
                .nearestActiveSourceNotOwnedBy(player.blockPosition(), player.getUUID())
                .isPresent();
            player.sendSystemMessage(
                Component.translatable(
                    hasTarget
                        ? "message.antigrieflever.copper_compass.time_left"
                        : "message.antigrieflever.copper_compass.target_missing_during_tracking",
                    PlayerFinderSavedData.formatTicks(session.activeUntil() - now)
                )
            );
            player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F);

            long updateInterval = Config.copperCompassUpdateTicks();
            long nextUpdate = session.nextUpdateAt();
            while (nextUpdate <= now) {
                nextUpdate += updateInterval;
            }
            sessions.put(playerId, session.withNextUpdateAt(nextUpdate));
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Session> entry : sessions.entrySet()) {
            CompoundTag sessionTag = new CompoundTag();
            sessionTag.putUUID("Player", entry.getKey());
            sessionTag.putLong("ActiveUntil", entry.getValue().activeUntil());
            sessionTag.putLong("CooldownUntil", entry.getValue().cooldownUntil());
            sessionTag.putLong("NextUpdateAt", entry.getValue().nextUpdateAt());
            sessionTag.putBoolean("DeactivationNotified", entry.getValue().deactivationNotified());
            entries.add(sessionTag);
        }
        tag.put("Sessions", entries);
        return tag;
    }

    private static CopperCompassSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CopperCompassSavedData data = new CopperCompassSavedData();
        for (Tag rawEntry : tag.getList("Sessions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) rawEntry;
            if (!entry.hasUUID("Player")) {
                continue;
            }
            data.sessions.put(
                entry.getUUID("Player"),
                new Session(
                    entry.getLong("ActiveUntil"),
                    entry.getLong("CooldownUntil"),
                    entry.getLong("NextUpdateAt"),
                    entry.getBoolean("DeactivationNotified")
                )
            );
        }
        return data;
    }

    public record Session(long activeUntil, long cooldownUntil, long nextUpdateAt, boolean deactivationNotified) {
        private Session withNextUpdateAt(long nextUpdateAt) {
            return new Session(activeUntil, cooldownUntil, nextUpdateAt, deactivationNotified);
        }

        private Session withDeactivationNotified() {
            return new Session(activeUntil, cooldownUntil, nextUpdateAt, true);
        }
    }
}
