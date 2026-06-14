package github.meloweh.antigrieflever.tracking;

import github.meloweh.antigrieflever.Antigrieflever;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class PlayerFinderEvents {
    private PlayerFinderEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long now = player.getServer().overworld().getGameTime();
        PlayerFinderSavedData savedData = PlayerFinderSavedData.get(player.getServer());
        savedData.observePosition(player, now);
        if (now % 20L == 0L) {
            savedData.processPlayer(player, now);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTeleport(EntityTeleportEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            long now = player.getServer().overworld().getGameTime();
            double distance = event.getPrev().distanceTo(event.getTarget());
            PlayerFinderSavedData.get(player.getServer()).recordTeleport(player, now, distance, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long now = player.getServer().overworld().getGameTime();
            PlayerFinderSavedData.get(player.getServer()).recordDimensionChange(player, now);
        }
    }
}
