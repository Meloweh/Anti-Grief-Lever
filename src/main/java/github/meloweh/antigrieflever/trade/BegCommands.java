package github.meloweh.antigrieflever.trade;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import github.meloweh.antigrieflever.Antigrieflever;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class BegCommands {
    private static final int MAX_BEG_AMOUNT = 6400;
    private static final long REQUEST_LIFETIME_MILLIS = 60_000L;
    private static final Map<RequestKey, BegRequest> REQUESTS = new HashMap<>();

    private BegCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandBuildContext buildContext = event.getBuildContext();
        event.getDispatcher().register(
            Commands.literal("beg")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> sendUsage(context.getSource()))
                .then(
                    Commands.argument("player", EntityArgument.player())
                        .executes(context -> sendUsage(context.getSource()))
                        .then(Commands.literal("accept").executes(context -> respond(context, true)))
                        .then(Commands.literal("reject").executes(context -> respond(context, false)))
                        .then(
                            Commands.argument("item", ItemArgument.item(buildContext))
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer(1, MAX_BEG_AMOUNT))
                                        .executes(BegCommands::sendRequest)
                                )
                        )
                )
        );
    }

    private static int sendUsage(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.antigrieflever.beg.usage"));
        return 0;
    }

    private static int sendRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (requester.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("commands.antigrieflever.beg.self"));
            return 0;
        }

        ItemStack item = createPrototype(ItemArgument.getItem(context, "item"));
        int amount = IntegerArgumentType.getInteger(context, "amount");
        if (item.isEmpty()) {
            source.sendFailure(Component.translatable("commands.antigrieflever.beg.invalid_item"));
            return 0;
        }

        long now = Util.getMillis();
        cleanupExpired(now);
        BegRequest request = new BegRequest(item, amount, now + REQUEST_LIFETIME_MILLIS);
        REQUESTS.put(new RequestKey(requester.getUUID(), target.getUUID()), request);

        source.sendSuccess(
            () -> Component.translatable(
                "commands.antigrieflever.beg.sent",
                target.getDisplayName(),
                amount,
                item.getDisplayName()
            ),
            false
        );
        target.sendSystemMessage(
            Component.translatable(
                "commands.antigrieflever.beg.received",
                requester.getDisplayName(),
                amount,
                item.getDisplayName(),
                requester.getGameProfile().getName(),
                requester.getGameProfile().getName()
            )
        );
        return 1;
    }

    private static int respond(CommandContext<CommandSourceStack> context, boolean accept) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer responder = source.getPlayerOrException();
        ServerPlayer requester = EntityArgument.getPlayer(context, "player");
        if (responder.getUUID().equals(requester.getUUID())) {
            source.sendFailure(Component.translatable("commands.antigrieflever.beg.self"));
            return 0;
        }

        long now = Util.getMillis();
        cleanupExpired(now);
        RequestKey key = new RequestKey(requester.getUUID(), responder.getUUID());
        BegRequest request = REQUESTS.get(key);
        if (request == null) {
            source.sendFailure(
                Component.translatable("commands.antigrieflever.beg.no_request", requester.getDisplayName())
            );
            return 0;
        }
        if (request.isExpired(now)) {
            REQUESTS.remove(key);
            source.sendFailure(
                Component.translatable("commands.antigrieflever.beg.expired", requester.getDisplayName())
            );
            return 0;
        }

        if (!accept) {
            REQUESTS.remove(key);
            source.sendSuccess(
                () -> Component.translatable("commands.antigrieflever.beg.rejected", requester.getDisplayName()),
                false
            );
            requester.sendSystemMessage(
                Component.translatable("commands.antigrieflever.beg.rejected_notice", responder.getDisplayName())
            );
            return 1;
        }

        if (!hasAtLeast(responder.getInventory(), request.item(), request.amount())) {
            source.sendFailure(
                Component.translatable(
                    "commands.antigrieflever.beg.responder_missing",
                    request.amount(),
                    request.item().getDisplayName()
                )
            );
            return 0;
        }
        if (!canReceive(requester.getInventory(), request.item(), request.amount())) {
            source.sendFailure(
                Component.translatable("commands.antigrieflever.beg.requester_no_space", requester.getDisplayName())
            );
            return 0;
        }

        removeItems(responder.getInventory(), request.item(), request.amount());
        insertItems(requester.getInventory(), request.item(), request.amount());
        responder.containerMenu.broadcastChanges();
        requester.containerMenu.broadcastChanges();
        REQUESTS.remove(key);

        source.sendSuccess(
            () -> Component.translatable(
                "commands.antigrieflever.beg.accepted",
                requester.getDisplayName(),
                request.amount(),
                request.item().getDisplayName()
            ),
            false
        );
        requester.sendSystemMessage(
            Component.translatable(
                "commands.antigrieflever.beg.accepted_notice",
                responder.getDisplayName(),
                request.amount(),
                request.item().getDisplayName()
            )
        );
        return 1;
    }

    private static ItemStack createPrototype(ItemInput input) throws CommandSyntaxException {
        return input.createItemStack(1, false).copyWithCount(1);
    }

    private static void cleanupExpired(long now) {
        REQUESTS.values().removeIf(request -> request.isExpired(now));
    }

    private static boolean hasAtLeast(Inventory inventory, ItemStack prototype, int amount) {
        int found = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                found += stack.getCount();
                if (found >= amount) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canReceive(Inventory inventory, ItemStack receivedPrototype, int receivedAmount) {
        ItemStack[] simulated = copyInventory(inventory);
        return simulateInsert(simulated, inventory.selected, receivedPrototype, receivedAmount);
    }

    private static ItemStack[] copyInventory(Inventory inventory) {
        ItemStack[] stacks = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < stacks.length; slot++) {
            stacks[slot] = inventory.getItem(slot).copy();
        }
        return stacks;
    }

    private static boolean simulateInsert(ItemStack[] stacks, int selectedSlot, ItemStack prototype, int amount) {
        int remaining = amount;
        remaining = simulateFillSlot(stacks, selectedSlot, prototype, remaining);
        remaining = simulateFillSlot(stacks, Inventory.SLOT_OFFHAND, prototype, remaining);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
            remaining = simulateFillSlot(stacks, slot, prototype, remaining);
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
            if (stacks[slot].isEmpty()) {
                int inserted = Math.min(remaining, prototype.getMaxStackSize());
                stacks[slot] = prototype.copyWithCount(inserted);
                remaining -= inserted;
            }
        }
        return remaining == 0;
    }

    private static int simulateFillSlot(ItemStack[] stacks, int slot, ItemStack prototype, int amount) {
        if (amount <= 0 || slot < 0 || slot >= stacks.length) {
            return amount;
        }

        ItemStack stack = stacks[slot];
        if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
            return amount;
        }

        int inserted = Math.min(amount, stack.getMaxStackSize() - stack.getCount());
        if (inserted > 0) {
            stack.grow(inserted);
        }
        return amount - inserted;
    }

    private static void removeItems(Inventory inventory, ItemStack prototype, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, prototype)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= removed;
        }
        inventory.setChanged();
    }

    private static void insertItems(Inventory inventory, ItemStack prototype, int amount) {
        int remaining = amount;
        remaining = fillSlot(inventory, inventory.selected, prototype, remaining);
        remaining = fillSlot(inventory, Inventory.SLOT_OFFHAND, prototype, remaining);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
            remaining = fillSlot(inventory, slot, prototype, remaining);
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                int inserted = Math.min(remaining, prototype.getMaxStackSize());
                inventory.setItem(slot, prototype.copyWithCount(inserted));
                remaining -= inserted;
            }
        }
        inventory.setChanged();
    }

    private static int fillSlot(Inventory inventory, int slot, ItemStack prototype, int amount) {
        if (amount <= 0 || slot < 0 || slot >= inventory.getContainerSize()) {
            return amount;
        }

        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
            return amount;
        }

        int inserted = Math.min(amount, stack.getMaxStackSize() - stack.getCount());
        if (inserted > 0) {
            stack.grow(inserted);
        }
        return amount - inserted;
    }

    private record RequestKey(UUID requester, UUID target) {
    }

    private record BegRequest(ItemStack item, int amount, long expiresAt) {
        private BegRequest {
            item = item.copyWithCount(1);
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
