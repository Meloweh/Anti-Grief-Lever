package github.meloweh.antigrieflever.protection;

import github.meloweh.antigrieflever.Antigrieflever;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class ProtectionEvents {
    private ProtectionEvents() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && !canDestroy(level, event.getPos(), event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProtectedLeverPlacement(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || (!event.getPlacedBlock().is(Antigrieflever.ANTI_GRIEF_LEVER.get())
                && !event.getPlacedBlock().is(Antigrieflever.RESTORATOR_LEVER.get()))
            || !ProtectionSavedData.get(level).isProtected(event.getPos())) {
            return;
        }

        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.lever.place_in_protected"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerPlacement(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()
            || !(event.getLevel() instanceof ServerLevel level)
            || !(event.getEntity() instanceof Player player)) {
            return;
        }

        ProtectionSavedData data = ProtectionSavedData.get(level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlaceEvent) {
            for (BlockSnapshot snapshot : multiPlaceEvent.getReplacedBlockSnapshots()) {
                data.recordPlayerPlacement(snapshot.getPos(), player.getUUID());
            }
        } else {
            data.recordPlayerPlacement(event.getPos(), player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
            || !(event.getLevel() instanceof ServerLevel level)
            || !hasBlockItemStorage(level, event.getPos())
            || ProtectionSavedData.get(level).canAccessContainer(event.getPos(), event.getEntity().getUUID())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.getEntity().displayClientMessage(
            Component.translatable("message.antigrieflever.container_protected"),
            true
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerEntityOpen(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!shouldPreventEntityContainerAccess(event.isCanceled(), event.getEntity(), target)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().displayClientMessage(
            Component.translatable("message.antigrieflever.container_protected"),
            true
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSpecificContainerEntityOpen(PlayerInteractEvent.EntityInteractSpecific event) {
        Entity target = event.getTarget();
        if (!shouldPreventEntityContainerAccess(event.isCanceled(), event.getEntity(), target)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().displayClientMessage(
            Component.translatable("message.antigrieflever.container_protected"),
            true
        );
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID sourceActor = actorOf(event.getExplosion().getIndirectSourceEntity());
        UUID actor = sourceActor != null ? sourceActor : DestructionContext.currentActor();
        event.getAffectedBlocks().removeIf(pos -> !canDestroy(level, pos, actor));
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }

        UUID actor = DestructionContext.currentActor();
        boolean protectedMovement = resolver.getToDestroy().stream().anyMatch(pos -> !canDestroy(level, pos, actor));
        if (!protectedMovement) {
            protectedMovement = resolver.getToPush().stream().anyMatch(pos ->
                !canDestroy(level, pos, actor)
                    || !canDestroy(level, pos.relative(resolver.getPushDirection()), actor)
            );
        }
        if (protectedMovement) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDestroy(LivingDestroyBlockEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level
            && !canDestroy(level, event.getPos(), actorOf(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFluidReplace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && !canDestroy(level, event.getPos(), DestructionContext.currentActor())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && !canDestroy(level, event.getPos(), actorOf(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && !canDestroy(level, event.getPos(), actorOf(event.getPlayer()))) {
            event.setCanceled(true);
        }
    }

    private static boolean canDestroy(ServerLevel level, BlockPos pos, @Nullable UUID actor) {
        return ProtectionSavedData.get(level).canDestroy(pos, actor);
    }

    private static boolean hasBlockItemStorage(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof Container) {
            return true;
        }
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEntityItemStorage(Entity entity) {
        return entity instanceof Container || entity.getCapability(Capabilities.ItemHandler.ENTITY) != null;
    }

    private static boolean shouldPreventEntityContainerAccess(boolean eventCanceled, Player player, Entity target) {
        return !eventCanceled
            && target.level() instanceof ServerLevel level
            && hasEntityItemStorage(target)
            && !ProtectionSavedData.get(level).canAccessContainer(target.blockPosition(), player.getUUID());
    }

    @Nullable
    private static UUID actorOf(@Nullable Entity entity) {
        return entity instanceof Player player ? player.getUUID() : null;
    }
}
