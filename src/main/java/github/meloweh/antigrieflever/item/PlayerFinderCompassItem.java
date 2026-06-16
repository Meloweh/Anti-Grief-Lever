package github.meloweh.antigrieflever.item;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.tracking.PlayerFinderSavedData;
import java.util.Optional;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PlayerFinderCompassItem extends CompassItem {
    private static final String TARGET_NAME = "PlayerFinderTarget";
    private static final String ACTIVE_UNTIL = "PlayerFinderActiveUntil";
    private static final String TIME_ADDED_FLASH_UNTIL = "PlayerFinderTimeAddedFlashUntil";
    private static final long TARGET_REFRESH_INTERVAL_TICKS = 10L;

    public PlayerFinderCompassItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(String targetName) {
        ItemStack stack = new ItemStack(Antigrieflever.PLAYER_FINDER_COMPASS.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TARGET_NAME, targetName));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(targetName + " finder"));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        String targetName = targetName(stack).orElse("");
        if (targetName.isBlank()) {
            serverPlayer.sendSystemMessage(
                Component.translatable("message.antigrieflever.player_finder.missing_target")
            );
            return InteractionResultHolder.fail(stack);
        }

        long now = serverPlayer.getServer().overworld().getGameTime();
        long stackActiveUntil = activeUntil(stack);
        if (stackActiveUntil > now) {
            serverPlayer.sendSystemMessage(
                Component.translatable(
                    "message.antigrieflever.player_finder.compass_already_active",
                    PlayerFinderSavedData.formatTicks(stackActiveUntil - now)
                )
            );
            return InteractionResultHolder.fail(stack);
        }
        if (stackActiveUntil > 0L) {
            clearActiveState(stack);
        }

        PlayerFinderSavedData savedData = PlayerFinderSavedData.get(serverPlayer.getServer());
        savedData.processPlayer(serverPlayer, now);
        PlayerFinderSavedData.Session session = savedData.session(serverPlayer.getUUID());
        if (session != null && now < session.activeUntil()) {
            serverPlayer.sendSystemMessage(
                Component.translatable(
                    "message.antigrieflever.player_finder.player_already_active",
                    PlayerFinderSavedData.formatTicks(session.activeUntil() - now)
                )
            );
            return InteractionResultHolder.fail(stack);
        }
        if (session != null && now < session.cooldownUntil()) {
            serverPlayer.sendSystemMessage(
                Component.translatable(
                    "message.antigrieflever.player_finder.cooldown",
                    PlayerFinderSavedData.formatTicks(session.cooldownUntil() - now)
                )
            );
            return InteractionResultHolder.fail(stack);
        }

        ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            serverPlayer.sendSystemMessage(
                Component.translatable("message.antigrieflever.player_finder.target_offline", targetName)
            );
            return InteractionResultHolder.fail(stack);
        }

        long activeUntil = now + Config.playerFinderActiveTicks();
        setActiveState(stack, activeUntil);
        updateTracker(stack, target);
        savedData.activate(serverPlayer.getUUID(), targetName, now);
        savedData.observePosition(target, now);
        serverPlayer.playNotifySound(SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.9F, 1.1F);
        serverPlayer.sendSystemMessage(
            Component.translatable(
                "message.antigrieflever.player_finder.activated",
                target.getDisplayName(),
                PlayerFinderSavedData.formatTicks(Config.playerFinderActiveTicks())
            )
        );
        target.sendSystemMessage(Component.translatable("message.antigrieflever.player_finder.tracked_warning"));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel)) {
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

        String targetName = targetName(stack).orElse("");
        ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        } else {
            updateTracker(stack, target);
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

    public static Optional<String> targetName(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String targetName = tag.getString(TARGET_NAME).trim();
        return targetName.isEmpty() ? Optional.empty() : Optional.of(targetName);
    }

    public static long activeUntil(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(ACTIVE_UNTIL);
    }

    public static long timeAddedFlashUntil(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag()
            .getLong(TIME_ADDED_FLASH_UNTIL);
    }

    public static void synchronizeActiveUntil(ServerPlayer player, String targetName, long activeUntil, long now) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (
                stack.is(Antigrieflever.PLAYER_FINDER_COMPASS.get())
                    && activeUntil(stack) > now
                    && targetName(stack).filter(targetName::equalsIgnoreCase).isPresent()
            ) {
                setActiveState(stack, activeUntil);
            }
        }
    }

    public static void flashTimeAdded(ServerPlayer player, String targetName, long flashUntil, long now) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (
                stack.is(Antigrieflever.PLAYER_FINDER_COMPASS.get())
                    && activeUntil(stack) > now
                    && targetName(stack).filter(targetName::equalsIgnoreCase).isPresent()
            ) {
                CustomData.update(
                    DataComponents.CUSTOM_DATA,
                    stack,
                    tag -> tag.putLong(TIME_ADDED_FLASH_UNTIL, flashUntil)
                );
            }
        }
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void setActiveState(ItemStack stack, long activeUntil) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(ACTIVE_UNTIL, activeUntil));
    }

    private static void clearActiveState(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(ACTIVE_UNTIL));
        stack.remove(DataComponents.LODESTONE_TRACKER);
    }

    private static void updateTracker(ItemStack stack, ServerPlayer target) {
        GlobalPos targetPosition = GlobalPos.of(target.serverLevel().dimension(), target.blockPosition());
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (current == null || current.tracked() || !current.target().equals(Optional.of(targetPosition))) {
            stack.set(
                DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(targetPosition), false)
            );
        }
    }
}
