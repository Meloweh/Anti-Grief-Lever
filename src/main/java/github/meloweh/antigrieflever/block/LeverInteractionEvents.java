package github.meloweh.antigrieflever.block;

import github.meloweh.antigrieflever.Antigrieflever;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class LeverInteractionEvents {
    private LeverInteractionEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()
            && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AntiGriefLeverBlock) {
            event.setUseBlock(TriState.TRUE);
            event.setUseItem(TriState.FALSE);
        }
    }
}
