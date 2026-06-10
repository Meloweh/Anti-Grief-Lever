package github.meloweh.antigrieflever.mixin;

import github.meloweh.antigrieflever.protection.DestructionContext;
import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void antigrieflever$guardDestructiveReplacement(
        BlockPos pos,
        BlockState newState,
        int flags,
        int recursionLeft,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Level self = (Level) (Object) this;
        if (!(self instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState oldState = self.getBlockState(pos);
        if (oldState.isAir() || oldState.is(newState.getBlock())) {
            return;
        }

        if (!ProtectionSavedData.get(serverLevel).canDestroy(pos, DestructionContext.currentActor())) {
            callback.setReturnValue(false);
        }
    }
}
