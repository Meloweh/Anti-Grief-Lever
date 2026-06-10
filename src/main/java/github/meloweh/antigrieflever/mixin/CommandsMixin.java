package github.meloweh.antigrieflever.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.ParseResults;
import github.meloweh.antigrieflever.protection.DestructionContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Commands.class)
public abstract class CommandsMixin {
    @WrapMethod(method = "performCommand")
    private void antigrieflever$attributePlayerCommand(
        ParseResults<CommandSourceStack> parseResults,
        String command,
        Operation<Void> original
    ) {
        ServerPlayer player = parseResults.getContext().getSource().getPlayer();
        DestructionContext.runWithActor(
            player == null ? null : player.getUUID(),
            () -> original.call(parseResults, command)
        );
    }
}
