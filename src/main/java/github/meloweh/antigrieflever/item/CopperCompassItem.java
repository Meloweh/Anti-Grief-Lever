package github.meloweh.antigrieflever.item;

import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import github.meloweh.antigrieflever.tracking.CopperCompassSavedData;
import github.meloweh.antigrieflever.tracking.PlayerFinderSavedData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class CopperCompassItem extends CompassItem {
    private static final String ACTIVE_UNTIL = "CopperCompassActiveUntil";
    private static final long TARGET_REFRESH_INTERVAL_TICKS = 10L;

    public CopperCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        long now = serverPlayer.getServer().overworld().getGameTime();
        long stackActiveUntil = activeUntil(stack);
        if (stackActiveUntil > now) {
            serverPlayer.sendSystemMessage(
                Component.translatable(
                    "message.antigrieflever.copper_compass.compass_already_active",
                    PlayerFinderSavedData.formatTicks(stackActiveUntil - now)
                )
            );
            return InteractionResultHolder.fail(stack);
        }
        if (stackActiveUntil > 0L) {
            clearActiveState(stack);
        }

        CopperCompassSavedData savedData = CopperCompassSavedData.get(serverPlayer.getServer());
        savedData.processPlayer(serverPlayer, now);
        CopperCompassSavedData.Session session = savedData.session(serverPlayer.getUUID());
        if (session != null && now < session.activeUntil()) {
            serverPlayer.sendSystemMessage(
                Component.translatable(
                    "message.antigrieflever.copper_compass.player_already_active",
                    PlayerFinderSavedData.formatTicks(session.activeUntil() - now)
                )
            );
            return InteractionResultHolder.fail(stack);
        }
        if (session != null && now < session.cooldownUntil()) {
            serverPlayer.sendSystemMessage(
                Component.translatable(
                    "message.antigrieflever.copper_compass.cooldown",
                    PlayerFinderSavedData.formatTicks(session.cooldownUntil() - now)
                )
            );
            return InteractionResultHolder.fail(stack);
        }

        Optional<BlockPos> target = ProtectionSavedData.get(serverLevel)
            .nearestActiveSourceNotOwnedBy(serverPlayer.blockPosition(), serverPlayer.getUUID());
        if (target.isEmpty()) {
            serverPlayer.sendSystemMessage(
                Component.translatable("message.antigrieflever.copper_compass.missing_target")
            );
            return InteractionResultHolder.fail(stack);
        }

        long activeUntil = now + Config.copperCompassActiveTicks();
        setActiveState(stack, activeUntil);
        updateTracker(stack, serverLevel, target.get());
        savedData.activate(serverPlayer.getUUID(), now);
        serverPlayer.playNotifySound(SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.9F, 1.1F);
        serverPlayer.sendSystemMessage(
            Component.translatable(
                "message.antigrieflever.copper_compass.activated",
                PlayerFinderSavedData.formatTicks(Config.copperCompassActiveTicks())
            )
        );
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }

        long activeUntil = activeUntil(stack);
        if (activeUntil <= 0L) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
            return;
        }

        long now = serverLevel.getServer().overworld().getGameTime();
        if (now >= activeUntil) {
            clearActiveState(stack);
            return;
        }
        if ((now + slot) % TARGET_REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }

        Optional<GlobalPos> target = ProtectionSavedData.get(serverLevel)
            .nearestActiveSourceNotOwnedBy(player.blockPosition(), player.getUUID())
            .map(pos -> GlobalPos.of(serverLevel.dimension(), pos));
        if (target.isPresent()) {
            updateTracker(stack, target.get());
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

    public static long activeUntil(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getLong(ACTIVE_UNTIL);
    }

    private static void setActiveState(ItemStack stack, long activeUntil) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(ACTIVE_UNTIL, activeUntil));
    }

    private static void clearActiveState(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ACTIVE_UNTIL));
        stack.remove(DataComponents.LODESTONE_TRACKER);
    }

    private static void updateTracker(ItemStack stack, ServerLevel level, BlockPos target) {
        updateTracker(stack, GlobalPos.of(level.dimension(), target));
    }

    private static void updateTracker(ItemStack stack, GlobalPos target) {
        LodestoneTracker currentTracker = stack.get(DataComponents.LODESTONE_TRACKER);
        Optional<GlobalPos> currentTarget = currentTracker == null ? Optional.empty() : currentTracker.target();
        if (currentTracker != null && !currentTracker.tracked() && currentTarget.equals(Optional.of(target))) {
            return;
        }

        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
    }
}
