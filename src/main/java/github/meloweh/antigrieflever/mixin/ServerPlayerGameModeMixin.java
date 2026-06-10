package github.meloweh.antigrieflever.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import github.meloweh.antigrieflever.protection.DestructionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean antigrieflever$attributeBlockBreak(BlockPos pos, Operation<Boolean> original) {
        return DestructionContext.callWithActor(player.getUUID(), () -> original.call(pos));
    }

    @WrapMethod(method = "useItemOn")
    private InteractionResult antigrieflever$attributeItemUseOn(
        ServerPlayer player,
        Level level,
        ItemStack stack,
        InteractionHand hand,
        BlockHitResult hitResult,
        Operation<InteractionResult> original
    ) {
        return DestructionContext.callWithActor(
            player.getUUID(),
            () -> original.call(player, level, stack, hand, hitResult)
        );
    }

    @WrapMethod(method = "useItem")
    private InteractionResult antigrieflever$attributeItemUse(
        ServerPlayer player,
        Level level,
        ItemStack stack,
        InteractionHand hand,
        Operation<InteractionResult> original
    ) {
        return DestructionContext.callWithActor(
            player.getUUID(),
            () -> original.call(player, level, stack, hand)
        );
    }
}
