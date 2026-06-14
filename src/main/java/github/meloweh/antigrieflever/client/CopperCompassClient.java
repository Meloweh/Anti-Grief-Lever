package github.meloweh.antigrieflever.client;

import github.meloweh.antigrieflever.Antigrieflever;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

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
    }
}
