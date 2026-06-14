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
public final class TradeCommands {
    private static final int MAX_TRADE_AMOUNT = 6400;
    private static final long REQUEST_LIFETIME_MILLIS = 60_000L;
    private static final Map<RequestKey, TradeRequest> REQUESTS = new HashMap<>();

    private TradeCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandBuildContext buildContext = event.getBuildContext();
        event.getDispatcher().register(
            Commands.literal("trade")
                .requires(source -> source.getPlayer() != null)
                .executes(context -> sendUsage(context.getSource()))
                .then(
                    Commands.argument("player", EntityArgument.player())
                        .executes(context -> sendUsage(context.getSource()))
                        .then(Commands.literal("accept").executes(context -> respond(context, true)))
                        .then(Commands.literal("reject").executes(context -> respond(context, false)))
                        .then(
                            Commands.argument("incoming_item", ItemArgument.item(buildContext))
                                .then(
                                    Commands.argument("incoming_amount", IntegerArgumentType.integer(1, MAX_TRADE_AMOUNT))
                                        .then(
                                            Commands.argument("outgoing_item", ItemArgument.item(buildContext))
                                                .then(
                                                    Commands.argument(
                                                        "outgoing_amount",
                                                        IntegerArgumentType.integer(1, MAX_TRADE_AMOUNT)
                                                    ).executes(TradeCommands::sendRequest)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int sendUsage(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.antigrieflever.trade.usage"));
        return 0;
    }

    private static int sendRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer requester = source.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (requester.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("commands.antigrieflever.trade.self"));
            return 0;
        }

        ItemStack incoming = createPrototype(ItemArgument.getItem(context, "incoming_item"));
        ItemStack outgoing = createPrototype(ItemArgument.getItem(context, "outgoing_item"));
        int incomingAmount = IntegerArgumentType.getInteger(context, "incoming_amount");
        int outgoingAmount = IntegerArgumentType.getInteger(context, "outgoing_amount");
        if (incoming.isEmpty() || outgoing.isEmpty()) {
            source.sendFailure(Component.translatable("commands.antigrieflever.trade.invalid_item"));
            return 0;
        }
        if (!hasAtLeast(requester.getInventory(), outgoing, outgoingAmount)) {
            source.sendFailure(
                Component.translatable(
                    "commands.antigrieflever.trade.sender_missing",
                    outgoingAmount,
                    outgoing.getDisplayName()
                )
            );
            return 0;
        }

        long now = Util.getMillis();
        cleanupExpired(now);
        TradeRequest request = new TradeRequest(
            incoming,
            incomingAmount,
            outgoing,
            outgoingAmount,
            now + REQUEST_LIFETIME_MILLIS
        );
        REQUESTS.put(new RequestKey(requester.getUUID(), target.getUUID()), request);

        source.sendSuccess(
            () -> Component.translatable(
                "commands.antigrieflever.trade.sent",
                target.getDisplayName(),
                incomingAmount,
                incoming.getDisplayName(),
                outgoingAmount,
                outgoing.getDisplayName()
            ),
            false
        );
        target.sendSystemMessage(
            Component.translatable(
                "commands.antigrieflever.trade.received",
                requester.getDisplayName(),
                incomingAmount,
                incoming.getDisplayName(),
                outgoingAmount,
                outgoing.getDisplayName(),
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
            source.sendFailure(Component.translatable("commands.antigrieflever.trade.self"));
            return 0;
        }

        long now = Util.getMillis();
        cleanupExpired(now);
        RequestKey key = new RequestKey(requester.getUUID(), responder.getUUID());
        TradeRequest request = REQUESTS.get(key);
        if (request == null) {
            source.sendFailure(
                Component.translatable("commands.antigrieflever.trade.no_request", requester.getDisplayName())
            );
            return 0;
        }
        if (request.isExpired(now)) {
            REQUESTS.remove(key);
            source.sendFailure(
                Component.translatable("commands.antigrieflever.trade.expired", requester.getDisplayName())
            );
            return 0;
        }

        if (!accept) {
            REQUESTS.remove(key);
            source.sendSuccess(
                () -> Component.translatable("commands.antigrieflever.trade.rejected", requester.getDisplayName()),
                false
            );
            requester.sendSystemMessage(
                Component.translatable("commands.antigrieflever.trade.rejected_notice", responder.getDisplayName())
            );
            return 1;
        }

        if (!hasAtLeast(requester.getInventory(), request.outgoing(), request.outgoingAmount())) {
            source.sendFailure(
                Component.translatable(
                    "commands.antigrieflever.trade.requester_missing",
                    requester.getDisplayName(),
                    request.outgoingAmount(),
                    request.outgoing().getDisplayName()
                )
            );
            return 0;
        }
        if (!hasAtLeast(responder.getInventory(), request.incoming(), request.incomingAmount())) {
            source.sendFailure(
                Component.translatable(
                    "commands.antigrieflever.trade.responder_missing",
                    request.incomingAmount(),
                    request.incoming().getDisplayName()
                )
            );
            return 0;
        }
        if (!canReceiveAfterRemoval(
            requester.getInventory(),
            request.outgoing(),
            request.outgoingAmount(),
            request.incoming(),
            request.incomingAmount()
        )) {
            source.sendFailure(
                Component.translatable("commands.antigrieflever.trade.requester_no_space", requester.getDisplayName())
            );
            return 0;
        }
        if (!canReceiveAfterRemoval(
            responder.getInventory(),
            request.incoming(),
            request.incomingAmount(),
            request.outgoing(),
            request.outgoingAmount()
        )) {
            source.sendFailure(Component.translatable("commands.antigrieflever.trade.responder_no_space"));
            return 0;
        }

        removeItems(requester.getInventory(), request.outgoing(), request.outgoingAmount());
        removeItems(responder.getInventory(), request.incoming(), request.incomingAmount());
        insertItems(requester.getInventory(), request.incoming(), request.incomingAmount());
        insertItems(responder.getInventory(), request.outgoing(), request.outgoingAmount());
        requester.containerMenu.broadcastChanges();
        responder.containerMenu.broadcastChanges();
        REQUESTS.remove(key);

        source.sendSuccess(
            () -> Component.translatable("commands.antigrieflever.trade.accepted", requester.getDisplayName()),
            false
        );
        requester.sendSystemMessage(
            Component.translatable("commands.antigrieflever.trade.accepted_notice", responder.getDisplayName())
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

    private static boolean canReceiveAfterRemoval(
        Inventory inventory,
        ItemStack removalPrototype,
        int removalAmount,
        ItemStack receivedPrototype,
        int receivedAmount
    ) {
        ItemStack[] simulated = copyInventory(inventory);
        return simulateRemove(simulated, removalPrototype, removalAmount)
            && simulateInsert(simulated, inventory.selected, receivedPrototype, receivedAmount);
    }

    private static ItemStack[] copyInventory(Inventory inventory) {
        ItemStack[] stacks = new ItemStack[inventory.getContainerSize()];
        for (int slot = 0; slot < stacks.length; slot++) {
            stacks[slot] = inventory.getItem(slot).copy();
        }
        return stacks;
    }

    private static boolean simulateRemove(ItemStack[] stacks, ItemStack prototype, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < stacks.length && remaining > 0; slot++) {
            ItemStack stack = stacks[slot];
            if (!ItemStack.isSameItemSameComponents(stack, prototype)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) {
                stacks[slot] = ItemStack.EMPTY;
            }
            remaining -= removed;
        }
        return remaining == 0;
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

    private record TradeRequest(
        ItemStack incoming,
        int incomingAmount,
        ItemStack outgoing,
        int outgoingAmount,
        long expiresAt
    ) {
        private TradeRequest {
            incoming = incoming.copyWithCount(1);
            outgoing = outgoing.copyWithCount(1);
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
