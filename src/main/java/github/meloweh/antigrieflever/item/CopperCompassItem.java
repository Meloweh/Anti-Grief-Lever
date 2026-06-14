package github.meloweh.antigrieflever.item;

import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class CopperCompassItem extends CompassItem {
    private static final long TARGET_REFRESH_INTERVAL_TICKS = 20L;

    public CopperCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }
        if ((serverLevel.getGameTime() + slot) % TARGET_REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }

        Optional<GlobalPos> target = ProtectionSavedData.get(serverLevel)
            .nearestActiveSourceNotOwnedBy(player.blockPosition(), player.getUUID())
            .map(pos -> GlobalPos.of(serverLevel.dimension(), pos));
        LodestoneTracker currentTracker = stack.get(DataComponents.LODESTONE_TRACKER);
        Optional<GlobalPos> currentTarget = currentTracker == null ? Optional.empty() : currentTracker.target();
        if (currentTracker != null && !currentTracker.tracked() && currentTarget.equals(target)) {
            return;
        }

        if (target.isPresent()) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(target, false));
        } else {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.isEnchanted();
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
    }
}
