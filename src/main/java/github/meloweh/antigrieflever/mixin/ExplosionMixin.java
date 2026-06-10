package github.meloweh.antigrieflever.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import github.meloweh.antigrieflever.protection.DestructionContext;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @WrapMethod(method = "finalizeExplosion")
    private void antigrieflever$attributeExplosion(boolean showParticles, Operation<Void> original) {
        LivingEntity source = ((Explosion) (Object) this).getIndirectSourceEntity();
        UUID actor = source instanceof Player player ? player.getUUID() : null;
        DestructionContext.runWithActor(actor, () -> original.call(showParticles));
    }
}
