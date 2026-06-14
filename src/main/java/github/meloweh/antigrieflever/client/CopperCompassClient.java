package github.meloweh.antigrieflever.client;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.item.PlayerFinderCompassItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID, value = Dist.CLIENT)
public final class CopperCompassClient {
    private CopperCompassClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
            Antigrieflever.COPPER_COMPASS.get(),
            ResourceLocation.withDefaultNamespace("angle"),
            new CompassItemPropertyFunction((level, stack, entity) -> {
                LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                // A null target makes the vanilla compass renderer spin randomly, matching Nether behavior.
                return tracker == null ? null : tracker.target().orElse(null);
            })
        ));
        event.enqueueWork(() -> ItemProperties.register(
            Antigrieflever.PLAYER_FINDER_COMPASS.get(),
            ResourceLocation.withDefaultNamespace("angle"),
            new CompassItemPropertyFunction((level, stack, entity) -> {
                LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                return tracker == null ? null : tracker.target().orElse(null);
            })
        ));
    }

    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(Antigrieflever.PLAYER_FINDER_COMPASS, (graphics, font, stack, x, y) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return false;
            }

            long remaining = PlayerFinderCompassItem.timeAddedFlashUntil(stack) - minecraft.level.getGameTime();
            if (remaining <= 0L) {
                return false;
            }

            double pulse = 0.5 + 0.5 * Math.sin((20L - Math.min(20L, remaining)) * Math.PI / 4.0);
            int alpha = 48 + (int) (96.0 * pulse);
            graphics.fill(x, y, x + 16, y + 16, alpha << 24 | 0xFFD36A);
            graphics.renderOutline(x, y, 16, 16, 0xD0FFE49A);
            return false;
        });
    }
}
