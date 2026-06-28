package github.meloweh.antigrieflever.weapon;

import github.meloweh.antigrieflever.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PrototypeChainMace {
    public static final double LENGTH = 5.4D;
    public static final double WIDTH = 0.4D;
    public static final double HIT_RADIUS = WIDTH * 0.5D + 0.25D;
    public static final double MIN_DAMAGE_SPEED = 0.22D;
    public static final double FAST_SWING_EFFECT_SPEED = 0.62D;
    public static final double MAX_DAMAGE = 18.0D;
    public static final int HIT_COOLDOWN_TICKS = 8;
    public static final int FAST_SWING_EFFECT_COOLDOWN_TICKS = 12;

    private static final double HANDLE_HAND_TIP_HEIGHT_SCALE = 0.4D;
    private static final double HANDLE_HEIGHT_PIXELS_ABOVE_HAND_TIP = 2.0D / 16.0D;
    private static final double HANDLE_DIAMETER_DEPTH_INSIDE_PLAYER = 2.0D / 3.0D;
    private static final double HANDLE_DIAMETER_OUTWARD_OFFSET = 0.2D;
    private static final double TELEPORT_RESET_DISTANCE = 8.0D;
    private static final double RESTORING_FORCE = 0.038D;
    private static final double INERTIA_FORCE = -1.05D;
    private static final double DAMPING = 0.972D;
    private static final double MOVING_RESTING_FORCE_SCALE = 0.38D;
    private static final double MOVING_GRAVITY_SCALE = 0.45D;
    private static final double FIRE_EFFECT_DELAY_TICKS = 30.0D;
    private static final double FIRE_EFFECT_BUILDUP_TICKS = 120.0D;
    private static final double FIRE_EFFECT_DECAY_PER_TICK = 0.028D;
    private static final double MIN_VISIBLE_FIRE_HEAT = 0.012D;
    private static final double FIRE_DAMAGE_BONUS_MULTIPLIER = 0.7D;
    private static final Vec3 GRAVITY = new Vec3(0.0D, -0.012D, 0.0D);
    private static final Vec3 REST_DIRECTION = new Vec3(0.0D, -1.0D, 0.0D);

    private PrototypeChainMace() {
    }

    public static boolean isAssignedTo(Player player) {
        if (!Config.prototypeChainMaceEnabled()) {
            return false;
        }

        String configuredName = Config.prototypeChainMacePlayerName().trim();
        return !configuredName.isEmpty()
            && configuredName.equalsIgnoreCase(player.getGameProfile().getName());
    }

    public static boolean canRunFor(Player player) {
        return isAssignedTo(player) && player.isAlive() && !player.isSpectator();
    }

    public static Vec3 handlePosition(Player player) {
        return handFixationPosition(player).add(bodyForward(player).scale(handleForwardOffset(player)));
    }

    public static float damageForSpeed(double speed) {
        if (speed < MIN_DAMAGE_SPEED) {
            return 0.0F;
        }

        double damage = 2.0D + (speed - MIN_DAMAGE_SPEED) * 18.0D;
        return (float) Mth.clamp(damage, 1.0D, MAX_DAMAGE);
    }

    public static float damageForSpeed(double speed, double fireHeat) {
        float damage = damageForSpeed(speed);
        if (damage <= 0.0F || fireHeat < MIN_VISIBLE_FIRE_HEAT) {
            return damage;
        }

        double multiplier = 1.0D + Mth.clamp(fireHeat, 0.0D, 1.0D) * FIRE_DAMAGE_BONUS_MULTIPLIER;
        return (float) (damage * multiplier);
    }

    public static double normalizedDamageSpeed(double speed) {
        return Mth.clamp((speed - MIN_DAMAGE_SPEED) / (1.0D - MIN_DAMAGE_SPEED), 0.0D, 1.0D);
    }

    public static double fastSwingHeat(double speed) {
        return Mth.clamp((speed - FAST_SWING_EFFECT_SPEED) / (1.25D - FAST_SWING_EFFECT_SPEED), 0.0D, 1.0D);
    }

    public static double minVisibleFireHeat() {
        return MIN_VISIBLE_FIRE_HEAT;
    }

    public static AABB sweptHitBox(State state) {
        AABB box = pointBox(state.previousHandle)
            .minmax(pointBox(state.handle))
            .minmax(pointBox(state.previousTip))
            .minmax(pointBox(state.tip));
        return box.inflate(HIT_RADIUS);
    }

    public static boolean intersectsSweep(State state, LivingEntity target) {
        AABB targetBox = target.getBoundingBox().inflate(HIT_RADIUS);
        if (intersectsSegment(targetBox, state.previousHandle, state.previousTip)
            || intersectsSegment(targetBox, state.handle, state.tip)) {
            return true;
        }

        for (int index = 0; index <= 5; index++) {
            double t = index / 5.0D;
            Vec3 previousPoint = state.previousHandle.lerp(state.previousTip, t);
            Vec3 point = state.handle.lerp(state.tip, t);
            if (intersectsSegment(targetBox, previousPoint, point)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsSegment(AABB box, Vec3 start, Vec3 end) {
        return box.contains(start) || box.contains(end) || box.clip(start, end).isPresent();
    }

    private static AABB pointBox(Vec3 point) {
        return new AABB(point.x, point.y, point.z, point.x, point.y, point.z);
    }

    private static Vec3 safeDirection(Vec3 vector, Vec3 fallback) {
        double lengthSqr = vector.lengthSqr();
        if (!Double.isFinite(lengthSqr) || lengthSqr < 1.0E-8D) {
            return fallback;
        }
        return vector.scale(1.0D / Math.sqrt(lengthSqr));
    }

    private static Vec3 handFixationPosition(Player player) {
        return player.position()
            .add(0.0D, player.getBbHeight() * HANDLE_HAND_TIP_HEIGHT_SCALE + HANDLE_HEIGHT_PIXELS_ABOVE_HAND_TIP, 0.0D);
    }

    private static double handleForwardOffset(Player player) {
        return player.getBbWidth() * 0.5D - WIDTH * (HANDLE_DIAMETER_DEPTH_INSIDE_PLAYER - HANDLE_DIAMETER_OUTWARD_OFFSET);
    }

    private static Vec3 bodyForward(Player player) {
        return bodyForward(bodyYawRadians(player));
    }

    private static Vec3 bodyForward(double yawRadians) {
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private static double bodyYawRadians(Player player) {
        return player.yBodyRot * Mth.DEG_TO_RAD;
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public static final class State {
        private boolean initialized;
        private Vec3 handle = Vec3.ZERO;
        private Vec3 previousHandle = Vec3.ZERO;
        private Vec3 tip = Vec3.ZERO;
        private Vec3 previousTip = Vec3.ZERO;
        private Vec3 velocity = Vec3.ZERO;
        private Vec3 previousHandleDelta = Vec3.ZERO;
        private double swingSpeed;
        private double fastSwingTicks;
        private double fireHeat;
        private long lastFastSwingEffectTick = Long.MIN_VALUE;
        private final Map<UUID, Long> lastHitTicksByTarget = new HashMap<>();

        public void tick(Player player) {
            Vec3 nextHandle = handlePosition(player);
            if (!initialized
                || !isFinite(nextHandle)
                || nextHandle.distanceToSqr(handle) > TELEPORT_RESET_DISTANCE * TELEPORT_RESET_DISTANCE) {
                reset(nextHandle);
                return;
            }

            previousHandle = handle;
            previousTip = tip;

            Vec3 handleDelta = nextHandle.subtract(handle);
            Vec3 handleAcceleration = handleDelta.subtract(previousHandleDelta);
            handle = nextHandle;

            Vec3 restTip = handle.add(REST_DIRECTION.scale(LENGTH));
            double motionRelease = Mth.clamp((velocity.length() + handleDelta.length() * 1.5D) / 0.85D, 0.0D, 1.0D);
            double restoringForce = lerp(RESTORING_FORCE, RESTORING_FORCE * MOVING_RESTING_FORCE_SCALE, motionRelease);
            double gravityScale = lerp(1.0D, MOVING_GRAVITY_SCALE, motionRelease);
            velocity = velocity
                .add(restTip.subtract(tip).scale(restoringForce))
                .add(GRAVITY.scale(gravityScale))
                .add(handleAcceleration.scale(INERTIA_FORCE));

            Vec3 projectedTip = constrainToRod(tip.add(velocity), handle, REST_DIRECTION);
            Vec3 projectedVelocity = projectedTip.subtract(tip);
            Vec3 rodDirection = safeDirection(projectedTip.subtract(handle), REST_DIRECTION);
            Vec3 radialVelocity = rodDirection.scale(projectedVelocity.dot(rodDirection));

            velocity = projectedVelocity.subtract(radialVelocity).scale(DAMPING);
            tip = projectedTip;
            swingSpeed = tip.subtract(previousTip).length();
            previousHandleDelta = handleDelta;
            updateFireHeat();
        }

        public Vec3 interpolatedHandle(float partialTick) {
            return previousHandle.lerp(handle, Mth.clamp(partialTick, 0.0F, 1.0F));
        }

        public Vec3 interpolatedTip(float partialTick) {
            return previousTip.lerp(tip, Mth.clamp(partialTick, 0.0F, 1.0F));
        }

        public Vec3 swingVector() {
            return tip.subtract(previousTip);
        }

        public double swingSpeed() {
            return swingSpeed;
        }

        public double fireHeat() {
            return fireHeat;
        }

        public boolean shouldEmitFastSwingEffect(long gameTime) {
            if (fireHeat < MIN_VISIBLE_FIRE_HEAT
                || gameTime - lastFastSwingEffectTick < FAST_SWING_EFFECT_COOLDOWN_TICKS) {
                return false;
            }

            lastFastSwingEffectTick = gameTime;
            return true;
        }

        public boolean canHit(UUID targetId, long gameTime) {
            Long lastHit = lastHitTicksByTarget.get(targetId);
            return lastHit == null || gameTime - lastHit >= HIT_COOLDOWN_TICKS;
        }

        public void markHit(UUID targetId, long gameTime) {
            lastHitTicksByTarget.put(targetId, gameTime);
        }

        public void pruneHitMemory(long gameTime) {
            lastHitTicksByTarget.entrySet().removeIf(entry -> gameTime - entry.getValue() > 200L);
        }

        private void reset(Vec3 nextHandle) {
            initialized = true;
            handle = nextHandle;
            previousHandle = nextHandle;
            tip = nextHandle.add(REST_DIRECTION.scale(LENGTH));
            previousTip = tip;
            velocity = Vec3.ZERO;
            previousHandleDelta = Vec3.ZERO;
            swingSpeed = 0.0D;
            fastSwingTicks = 0.0D;
            fireHeat = 0.0D;
            lastFastSwingEffectTick = Long.MIN_VALUE;
        }

        private static Vec3 constrainToRod(Vec3 rawTip, Vec3 handle, Vec3 fallbackDirection) {
            Vec3 offset = rawTip.subtract(handle);
            Vec3 direction = safeDirection(offset, fallbackDirection);
            return handle.add(direction.scale(LENGTH));
        }

        private void updateFireHeat() {
            double speedHeat = fastSwingHeat(swingSpeed);
            if (speedHeat <= 0.0D) {
                fastSwingTicks = 0.0D;
                fireHeat = Math.max(0.0D, fireHeat - FIRE_EFFECT_DECAY_PER_TICK);
                return;
            }

            fastSwingTicks += 1.0D;
            double buildup = Mth.clamp(
                (fastSwingTicks - FIRE_EFFECT_DELAY_TICKS) / FIRE_EFFECT_BUILDUP_TICKS,
                0.0D,
                1.0D
            );
            buildup = buildup * buildup * buildup;
            fireHeat = speedHeat * buildup;
        }

        private static double lerp(double from, double to, double amount) {
            return from + (to - from) * amount;
        }
    }
}
