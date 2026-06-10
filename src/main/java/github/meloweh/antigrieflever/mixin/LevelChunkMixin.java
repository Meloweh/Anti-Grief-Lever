package github.meloweh.antigrieflever.mixin;

import github.meloweh.antigrieflever.protection.DestructionContext;
import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void antigrieflever$guardDirectChunkMutation(
        BlockPos pos,
        BlockState newState,
        boolean moved,
        CallbackInfoReturnable<BlockState> callback
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState oldState = ((LevelChunk) (Object) this).getBlockState(pos);
        if (oldState.isAir() || oldState.is(newState.getBlock())) {
            return;
        }

        if (!ProtectionSavedData.get(serverLevel).canDestroy(pos, DestructionContext.currentActor())) {
            callback.setReturnValue(null);
        }
    }
}
