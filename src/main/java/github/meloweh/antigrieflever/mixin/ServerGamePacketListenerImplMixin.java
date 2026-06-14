package github.meloweh.antigrieflever.mixin;

import github.meloweh.antigrieflever.tracking.PlayerFinderSavedData;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("HEAD"))
    private void antigrieflever$recordPlayerTeleport(
        double x,
        double y,
        double z,
        float yRotation,
        float xRotation,
        Set<RelativeMovement> relativeMovements,
        CallbackInfo callback
    ) {
        Vec3 destination = new Vec3(
            relativeMovements.contains(RelativeMovement.X) ? player.getX() + x : x,
            relativeMovements.contains(RelativeMovement.Y) ? player.getY() + y : y,
            relativeMovements.contains(RelativeMovement.Z) ? player.getZ() + z : z
        );
        long now = player.getServer().overworld().getGameTime();
        PlayerFinderSavedData.get(player.getServer())
            .recordTeleport(player, now, player.position().distanceTo(destination), destination);
    }
}
