package github.meloweh.antigrieflever.weapon;

import com.mojang.brigadier.context.CommandContext;
import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class PrototypeChainMaceCommands {
    private PrototypeChainMaceCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("eastereggmace")
                .requires(source -> source.hasPermission(2))
                .executes(context -> sendUsage(context.getSource()))
                .then(Commands.literal("on").executes(context -> setEnabled(context, true)))
                .then(Commands.literal("off").executes(context -> setEnabled(context, false)))
        );
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        Config.setPrototypeChainMaceEnabled(enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Easter egg mace " + (enabled ? "enabled" : "disabled") + "."),
            true
        );
        return 1;
    }

    private static int sendUsage(CommandSourceStack source) {
        source.sendFailure(Component.literal("Usage: /eastereggmace <on|off>"));
        return 0;
    }
}
