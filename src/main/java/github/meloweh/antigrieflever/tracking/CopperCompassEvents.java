package github.meloweh.antigrieflever.tracking;

import github.meloweh.antigrieflever.Antigrieflever;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class CopperCompassEvents {
    private CopperCompassEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        long now = player.getServer().overworld().getGameTime();
        if (now % 20L == 0L) {
            CopperCompassSavedData.get(player.getServer()).processPlayer(player, now);
        }
    }
}
