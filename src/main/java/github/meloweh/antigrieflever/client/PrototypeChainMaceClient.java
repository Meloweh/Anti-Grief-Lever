package github.meloweh.antigrieflever.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.weapon.PrototypeChainMace;
import github.meloweh.antigrieflever.weapon.PrototypeChainMace.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Antigrieflever.MODID, value = Dist.CLIENT)
public final class PrototypeChainMaceClient {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        Antigrieflever.MODID,
        "textures/entity/prototype_chain_mace.png"
    );
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, OrientationState> ORIENTATIONS = new HashMap<>();

    private PrototypeChainMaceClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            STATES.clear();
            ORIENTATIONS.clear();
            return;
        }

        Set<UUID> activePlayers = new HashSet<>();
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (!PrototypeChainMace.canRunFor(player)) {
                continue;
            }

            activePlayers.add(player.getUUID());
            STATES.computeIfAbsent(player.getUUID(), id -> new State()).tick(player);
        }
        STATES.keySet().removeIf(id -> !activePlayers.contains(id));
        ORIENTATIONS.keySet().removeIf(id -> !activePlayers.contains(id));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || STATES.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        List<RenderPose> renderPoses = new ArrayList<>(STATES.size());
        for (Map.Entry<UUID, State> entry : STATES.entrySet()) {
            State state = entry.getValue();
            Vec3 handle = state.interpolatedHandle(partialTick);
            Vec3 tip = state.interpolatedTip(partialTick);
            Vec3 direction = safeDirection(tip.subtract(handle), new Vec3(0.0D, -1.0D, 0.0D));
            Vec3 sideX = ORIENTATIONS
                .computeIfAbsent(entry.getKey(), id -> new OrientationState())
                .sideAxis(direction);
            renderPoses.add(new RenderPose(state, handle, tip, sideX));
        }

        RenderType baseType = RenderType.entityCutoutNoCull(TEXTURE);
        VertexConsumer base = bufferSource.getBuffer(baseType);

        for (RenderPose renderPose : renderPoses) {
            State state = renderPose.state();
            renderMace(
                poseStack,
                base,
                camera,
                renderPose.handle(),
                renderPose.tip(),
                renderPose.sideX(),
                state.swingSpeed(),
                state.fireHeat(),
                0.0F,
                false
            );
        }
        bufferSource.endBatch(baseType);

        float gameTime = minecraft.level.getGameTime() + partialTick;
        RenderType fireType = RenderType.energySwirl(TEXTURE, gameTime * 0.012F, gameTime * 0.018F);
        VertexConsumer fire = bufferSource.getBuffer(fireType);
        for (RenderPose renderPose : renderPoses) {
            State state = renderPose.state();
            renderMace(
                poseStack,
                fire,
                camera,
                renderPose.handle(),
                renderPose.tip(),
                renderPose.sideX(),
                state.swingSpeed(),
                state.fireHeat(),
                gameTime,
                true
            );
        }
        bufferSource.endBatch(fireType);
    }

    private static void renderMace(
        PoseStack poseStack,
        VertexConsumer consumer,
        Vec3 camera,
        Vec3 handle,
        Vec3 tip,
        Vec3 sideX,
        double speed,
        double fireHeat,
        float gameTime,
        boolean fireOverlay
    ) {
        Vec3 segment = tip.subtract(handle);
        double length = segment.length();
        if (length < 1.0E-5D || !Double.isFinite(length)) {
            return;
        }

        double heat = Mth.clamp(fireHeat, 0.0D, 1.0D);
        if (fireOverlay && heat < PrototypeChainMace.minVisibleFireHeat()) {
            return;
        }

        Vec3 direction = segment.scale(1.0D / length);
        Vec3 center = handle.add(tip).scale(0.5D).subtract(camera);
        double visualHeat = fireOverlay ? Math.pow(heat, 1.65D) : 0.0D;
        double halfWidth = PrototypeChainMace.WIDTH * (fireOverlay ? 0.53D + visualHeat * 0.25D : 0.5D);
        double halfLength = length * 0.5D;
        float flicker = fireOverlay ? 0.78F + 0.22F * (float) Math.sin(gameTime * 0.9F + speed * 11.0D) : 1.0F;
        int red = fireOverlay ? 255 : 238;
        int green = fireOverlay ? 82 + (int) (96.0D * visualHeat * flicker) : 238;
        int blue = fireOverlay ? 12 : 238;
        int alpha = fireOverlay ? 6 + (int) (166.0D * visualHeat * flicker) : 255;

        Vec3 sideZ = safeDirection(direction.cross(sideX), new Vec3(0.0D, 0.0D, 1.0D));
        addOrientedTexturedBox(
            poseStack.last(),
            consumer,
            center,
            sideX,
            direction,
            sideZ,
            -halfWidth,
            -halfLength,
            -halfWidth,
            halfWidth,
            halfLength,
            halfWidth,
            red,
            green,
            blue,
            alpha
        );
    }

    private static Vec3 safeDirection(Vec3 vector, Vec3 fallback) {
        double lengthSqr = vector.lengthSqr();
        if (!Double.isFinite(lengthSqr) || lengthSqr < 1.0E-8D) {
            return fallback;
        }
        return vector.scale(1.0D / Math.sqrt(lengthSqr));
    }

    private static void addOrientedTexturedBox(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        Vec3 center,
        Vec3 axisX,
        Vec3 axisY,
        Vec3 axisZ,
        double x0,
        double y0,
        double z0,
        double x1,
        double y1,
        double z1,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        Vec3 p000 = transform(center, axisX, axisY, axisZ, x0, y0, z0);
        Vec3 p001 = transform(center, axisX, axisY, axisZ, x0, y0, z1);
        Vec3 p010 = transform(center, axisX, axisY, axisZ, x0, y1, z0);
        Vec3 p011 = transform(center, axisX, axisY, axisZ, x0, y1, z1);
        Vec3 p100 = transform(center, axisX, axisY, axisZ, x1, y0, z0);
        Vec3 p101 = transform(center, axisX, axisY, axisZ, x1, y0, z1);
        Vec3 p110 = transform(center, axisX, axisY, axisZ, x1, y1, z0);
        Vec3 p111 = transform(center, axisX, axisY, axisZ, x1, y1, z1);

        orientedQuad(pose, consumer, p001, p101, p111, p011, red, green, blue, alpha, axisZ);
        orientedQuad(pose, consumer, p100, p000, p010, p110, red, green, blue, alpha, axisZ.scale(-1.0D));
        orientedQuad(pose, consumer, p101, p100, p110, p111, red, green, blue, alpha, axisX);
        orientedQuad(pose, consumer, p000, p001, p011, p010, red, green, blue, alpha, axisX.scale(-1.0D));
        orientedQuad(pose, consumer, p011, p111, p110, p010, red, green, blue, alpha, axisY);
        orientedQuad(pose, consumer, p000, p100, p101, p001, red, green, blue, alpha, axisY.scale(-1.0D));
    }

    private static Vec3 transform(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ, double x, double y, double z) {
        return center
            .add(axisX.scale(x))
            .add(axisY.scale(y))
            .add(axisZ.scale(z));
    }

    private static void orientedQuad(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        Vec3 p0,
        Vec3 p1,
        Vec3 p2,
        Vec3 p3,
        int red,
        int green,
        int blue,
        int alpha,
        Vec3 normal
    ) {
        quad(
            pose,
            consumer,
            (float) p0.x,
            (float) p0.y,
            (float) p0.z,
            (float) p1.x,
            (float) p1.y,
            (float) p1.z,
            (float) p2.x,
            (float) p2.y,
            (float) p2.z,
            (float) p3.x,
            (float) p3.y,
            (float) p3.z,
            red,
            green,
            blue,
            alpha,
            (float) normal.x,
            (float) normal.y,
            (float) normal.z
        );
    }

    private static void quad(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        int red,
        int green,
        int blue,
        int alpha,
        float normalX,
        float normalY,
        float normalZ
    ) {
        vertex(pose, consumer, x0, y0, z0, red, green, blue, alpha, 0.0F, 1.0F, normalX, normalY, normalZ);
        vertex(pose, consumer, x1, y1, z1, red, green, blue, alpha, 1.0F, 1.0F, normalX, normalY, normalZ);
        vertex(pose, consumer, x2, y2, z2, red, green, blue, alpha, 1.0F, 0.0F, normalX, normalY, normalZ);
        vertex(pose, consumer, x3, y3, z3, red, green, blue, alpha, 0.0F, 0.0F, normalX, normalY, normalZ);
    }

    private static void vertex(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        float x,
        float y,
        float z,
        int red,
        int green,
        int blue,
        int alpha,
        float u,
        float v,
        float normalX,
        float normalY,
        float normalZ
    ) {
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static final class OrientationState {
        private Vec3 sideX = new Vec3(1.0D, 0.0D, 0.0D);

        private Vec3 sideAxis(Vec3 direction) {
            Vec3 projected = sideX.subtract(direction.scale(sideX.dot(direction)));
            if (projected.lengthSqr() < 1.0E-8D) {
                Vec3 fallback = Math.abs(direction.x) < 0.85D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, 1.0D);
                projected = fallback.subtract(direction.scale(fallback.dot(direction)));
            }
            sideX = safeDirection(projected, sideX);
            return sideX;
        }
    }

    private record RenderPose(State state, Vec3 handle, Vec3 tip, Vec3 sideX) {
    }
}
