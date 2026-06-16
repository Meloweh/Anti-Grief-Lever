package github.meloweh.antigrieflever.client;

import github.meloweh.antigrieflever.network.ModNetwork.OpenConfigPayload;
import github.meloweh.antigrieflever.network.ModNetwork.OpenWarpStonePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void open(OpenConfigPayload payload) {
        Minecraft.getInstance().setScreen(
            new LeverConfigScreen(
                payload.pos(),
                payload.definition(),
                payload.maxRadius(),
                payload.titleKey(),
                payload.regionLabelKey()
            )
        );
    }

    public static void openWarpStone(OpenWarpStonePayload payload) {
        Minecraft.getInstance().setScreen(new WarpStoneConfigScreen(payload.pos(), payload.name(), payload.options()));
    }
}
