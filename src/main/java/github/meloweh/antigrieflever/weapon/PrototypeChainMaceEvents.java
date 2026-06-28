package github.meloweh.antigrieflever.weapon;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.weapon.PrototypeChainMace.State;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID)
public final class PrototypeChainMaceEvents {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private PrototypeChainMaceEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!PrototypeChainMace.canRunFor(player)) {
            STATES.remove(player.getUUID());
            return;
        }

        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        state.tick(player);
        playFastSwingCue(player, state);
        damageTargets(player, state);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private static void damageTargets(ServerPlayer owner, State state) {
        float damage = PrototypeChainMace.damageForSpeed(state.swingSpeed(), state.fireHeat());
        if (damage <= 0.0F) {
            return;
        }

        ServerLevel level = owner.serverLevel();
        long gameTime = level.getGameTime();
        AABB hitBox = PrototypeChainMace.sweptHitBox(state);
        for (LivingEntity target : level.getEntitiesOfClass(
            LivingEntity.class,
            hitBox,
            target -> canDamage(owner, target)
        )) {
            UUID targetId = target.getUUID();
            if (!state.canHit(targetId, gameTime) || !PrototypeChainMace.intersectsSweep(state, target)) {
                continue;
            }

            if (target.hurt(owner.damageSources().playerAttack(owner), damage)) {
                state.markHit(targetId, gameTime);
                pushTarget(target, state.swingVector(), damage);
            }
        }
        state.pruneHitMemory(gameTime);
    }

    private static void playFastSwingCue(ServerPlayer owner, State state) {
        ServerLevel level = owner.serverLevel();
        long gameTime = level.getGameTime();
        if (!state.shouldEmitFastSwingEffect(gameTime)) {
            return;
        }

        double heat = state.fireHeat();
        level.playSound(
            null,
            owner.blockPosition(),
            SoundEvents.FIRECHARGE_USE,
            SoundSource.PLAYERS,
            (float) Mth.clamp(0.35D + heat * 0.65D, 0.35D, 1.0D),
            (float) Mth.clamp(0.7D + heat * 0.45D, 0.7D, 1.25D)
        );
    }

    private static boolean canDamage(ServerPlayer owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || target.isSpectator() || !target.attackable()) {
            return false;
        }
        if (target instanceof Player targetPlayer && !owner.canHarmPlayer(targetPlayer)) {
            return false;
        }
        return !target.isAlliedTo(owner) && !target.skipAttackInteraction(owner);
    }

    private static void pushTarget(LivingEntity target, Vec3 swingVector, float damage) {
        double length = swingVector.length();
        if (length < 1.0E-5D || !Double.isFinite(length)) {
            return;
        }

        Vec3 direction = swingVector.scale(1.0D / length);
        double strength = Mth.clamp(damage * 0.035D + length * 0.25D, 0.12D, 0.85D);
        target.push(
            direction.x * strength,
            Math.max(0.04D, direction.y * strength * 0.35D),
            direction.z * strength
        );
        target.hurtMarked = true;
    }
}
