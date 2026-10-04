package dev.dbil.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dbil.DBIL;
import dev.dbil.client.ClientFlightController;
import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.registry.ModParticles;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.Transformations;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * World-space presentation: layered auras, technique charge orbs, fast-flight speed lines and the lock-on
 * reticle. Additive vanilla render types only (no custom shaders), bounded quad counts per character, and
 * everything scales with the aura quality / distance options.
 */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class WorldEffectsRenderer {
    public static final ResourceLocation FLAME = DBIL.id("textures/entity/effect/aura_flame.png");
    public static final ResourceLocation GLOW = DBIL.id("textures/entity/effect/glow.png");
    public static final ResourceLocation CORE = DBIL.id("textures/entity/effect/core.png");
    public static final ResourceLocation RING = DBIL.id("textures/entity/effect/ring.png");
    public static final ResourceLocation STREAK = DBIL.id("textures/entity/effect/streak.png");
    private static final Map<Integer, Long> CHARGE_STARTED = new HashMap<>();
    private static final float[][] SPEED_LINES = new float[24][4];
    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastFrame;

    private WorldEffectsRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        ClientConfig.Quality quality = ClientConfig.aura();
        double limit = ClientConfig.distance();
        for (Player player : minecraft.level.players()) {
            if (player.isInvisible() || player.isSpectator()) continue;
            double distance = player.position().distanceTo(cam);
            if (distance > limit) continue;
            ClientState.VisualState visual = ClientState.visual(player.getId());
            boolean firstPersonSelf = player == minecraft.player && camera.getEntity() == player && !camera.isDetached();
            if (quality != ClientConfig.Quality.OFF) renderAura(pose, buffers, camera, player, visual, partial, quality, firstPersonSelf);
            if (visual.chargingTechnique() && !(firstPersonSelf && visual.techniqueTicks() <= 0 && !visual.techniqueHolding())) {
                renderChargeOrb(pose, buffers, camera, player, visual, partial, firstPersonSelf);
            }
        }
        renderSpeedLines(pose, buffers, camera, minecraft, partial);
        renderReticle(pose, buffers, camera, minecraft, partial);
        buffers.endBatch(RenderType.eyes(FLAME));
        buffers.endBatch(RenderType.eyes(GLOW));
        buffers.endBatch(RenderType.eyes(CORE));
        buffers.endBatch(RenderType.eyes(RING));
        buffers.endBatch(RenderType.eyes(STREAK));
        buffers.endBatch(RenderType.lightning());
    }

    // ------------------------------------------------------------------------------------------------ aura

    private static void renderAura(PoseStack pose, MultiBufferSource buffers, Camera camera, Player player,
                                   ClientState.VisualState visual, float partial, ClientConfig.Quality quality, boolean firstPerson) {
        float intensity = 0;
        AuraStyles.AuraStyle style = AuraStyles.BASE;
        long now = player.level().getGameTime();
        if (visual.charging()) CHARGE_STARTED.putIfAbsent(player.getId(), now); else CHARGE_STARTED.remove(player.getId());
        float stability = 1;
        if (visual.transformed()) {
            style = AuraStyles.get(visual.transformation());
            intensity = visual.charging() ? 1.05F : 0.55F;
            stability = Mth.clamp(visual.formMastery() / 100F, 0, 1);
        }
        if (visual.charging() && !visual.transformed()) {
            long started = CHARGE_STARTED.getOrDefault(player.getId(), now);
            style = AuraStyles.CHARGING;
            intensity = 0.6F + Math.min(0.45F, (now - started) / 80F);
        }
        if (visual.transforming()) {
            float progress = visual.transformationProgress();
            boolean saiyan = ClientState.appearance(player.getId()) != null
                    && dev.dbil.race.Races.SAIYAN.equals(ClientState.appearance(player.getId()).race());
            boolean flick = progress > 0.4F && (player.tickCount / 2) % 2 == 0;
            style = saiyan && (progress > 0.85F || flick) ? AuraStyles.SUPER_SAIYAN
                    : saiyan ? AuraStyles.CHARGING : AuraStyles.POTENTIAL;
            intensity = 0.5F + progress * 0.8F;
            stability = 0.3F;
        }
        if (intensity <= 0 && visual.chargingTechnique()) {
            intensity = 0.25F + visual.techniqueChargeNow(partial) * 0.35F;
        }
        if (intensity <= 0 && visual.fastFlight() && visual.flying()) intensity = 0.25F;
        if (intensity <= 0) return;
        intensity *= ClientConfig.SPEC.isLoaded() ? (0.4F + 0.6F * ClientConfig.auraIntensity.get().floatValue()) : 1;
        // Unstable (low-mastery) forms drop out and surge.
        float time = player.tickCount + partial;
        float surge = 1 + (1 - stability) * 0.35F * Mth.sin(time * 0.9F + player.getId());
        if (stability < 0.5F && Mth.sin(time * 0.37F + player.getId() * 3) > 0.92F) surge *= 0.45F;
        intensity *= surge;
        Vec3 feet = lerp(player, partial).subtract(camera.getPosition());
        int tongues = switch (quality) { case LOW -> 6; case MEDIUM -> 10; case HIGH -> 14; default -> 0; };
        float radius = style.radius() * (0.85F + 0.25F * intensity);
        float height = style.height() * (0.55F + 0.45F * intensity);
        float[] rgb = rgb(style.color());
        float[] core = rgb(style.coreColor());
        float brightness = Math.min(1, 0.55F * intensity);
        if (!firstPerson) {
            VertexConsumer flames = buffers.getBuffer(RenderType.eyes(FLAME));
            Vec3 camPos = Vec3.ZERO;
            for (int i = 0; i < tongues; i++) {
                float angle = (i / (float) tongues) * Mth.TWO_PI + time * 0.03F;
                float flicker = 0.75F + 0.25F * Mth.sin(time * (0.5F + style.turbulence() * 0.4F) + i * 2.1F);
                float h = height * flicker * (0.8F + 0.2F * Mth.sin(i * 1.7F));
                float w = 0.35F + 0.25F * intensity;
                Vec3 base = feet.add(Mth.cos(angle) * radius, -0.05, Mth.sin(angle) * radius);
                cylindricalQuad(pose, flames, base, camPos, w, h, 0.18F, rgb, brightness * 0.75F);
            }
            // Inner hot core shell, narrower and whiter.
            for (int i = 0; i < tongues / 2; i++) {
                float angle = (i / (float) Math.max(1, tongues / 2)) * Mth.TWO_PI - time * 0.05F;
                float h = height * 0.7F * (0.8F + 0.2F * Mth.sin(time * 0.7F + i));
                Vec3 base = feet.add(Mth.cos(angle) * radius * 0.55F, 0, Mth.sin(angle) * radius * 0.55F);
                cylindricalQuad(pose, flames, base, camPos, 0.3F, h, 0.08F, core, brightness * 0.45F);
            }
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
            billboard(pose, glow, camera, feet.add(0, 1.0, 0), 1.3F + intensity * 0.6F, rgb, brightness * 0.35F, 0);
        }
        // Rising streaks of energy.
        int streaks = quality == ClientConfig.Quality.HIGH ? 8 : quality == ClientConfig.Quality.MEDIUM ? 5 : 2;
        VertexConsumer streakBuffer = buffers.getBuffer(RenderType.eyes(STREAK));
        for (int i = 0; i < streaks; i++) {
            float phase = ((time * 0.06F + i * 0.37F) % 1F);
            float angle = i * 2.39F + player.getId();
            float r = radius * (firstPerson ? 1.3F : 0.9F);
            Vec3 base = feet.add(Mth.cos(angle) * r, phase * height * 1.1F, Mth.sin(angle) * r);
            float fade = Mth.sin(phase * Mth.PI);
            verticalStreak(pose, streakBuffer, base, 0.06F, 0.55F, core, brightness * fade * 0.9F);
        }
        // Electric discharges: Super Saiyan always crackles; unstable forms crackle more.
        if (style.lightning() && quality != ClientConfig.Quality.LOW) {
            float chance = 0.18F + (1 - stability) * 0.45F;
            long slot = (long) (time / 3);
            RANDOM.setSeed(slot * 31 + player.getId());
            int bolts = quality == ClientConfig.Quality.HIGH ? 2 : 1;
            VertexConsumer lightning = buffers.getBuffer(RenderType.lightning());
            for (int b = 0; b < bolts; b++) {
                if (RANDOM.nextFloat() > chance) continue;
                Vec3 start = feet.add((RANDOM.nextFloat() - 0.5) * radius * 2, 0.3 + RANDOM.nextFloat() * 1.4, (RANDOM.nextFloat() - 0.5) * radius * 2);
                bolt(pose, lightning, start, 5, 0.22F, 0.75F, 0.95F, 1.0F, 0.85F);
            }
        }
        // Ground ring while powering up on the ground.
        if (player.onGround() && (visual.charging() || visual.transforming()) && !firstPerson) {
            VertexConsumer ring = buffers.getBuffer(RenderType.eyes(RING));
            float size = 0.9F + 0.35F * Mth.sin(time * 0.25F) + intensity * 0.4F;
            groundQuad(pose, ring, feet.add(0, 0.03, 0), size, time * 0.05F, rgb, brightness * 0.5F);
        }
    }

    /** Aura motes and dust, emitted from the client tick so frame rate does not change particle counts. */
    public static void tickParticles(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || ClientConfig.aura() == ClientConfig.Quality.OFF) return;
        for (Player player : minecraft.level.players()) {
            ClientState.VisualState visual = ClientState.visual(player.getId());
            boolean aura = visual.charging() || visual.transforming() || visual.transformed();
            if (!aura && !visual.chargingTechnique()) continue;
            Vec3 center = player.position().add(0, 1, 0);
            if (aura) {
                AuraStyles.AuraStyle style = visual.transformed() ? AuraStyles.get(visual.transformation())
                        : visual.transforming() ? AuraStyles.SUPER_SAIYAN : AuraStyles.CHARGING;
                int base = visual.transforming() ? 4 : visual.charging() ? 3 : 1;
                int n = FxParticles.count(base, center);
                for (int i = 0; i < n; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2;
                    FxParticles.spawn(ModParticles.AURA_MOTE.get(), center.x + Math.cos(angle) * 0.55, player.getY() + RANDOM.nextDouble() * 1.8,
                            center.z + Math.sin(angle) * 0.55, style.moteColor(), 0.12F + RANDOM.nextFloat() * 0.08F, 0.05F + RANDOM.nextFloat() * 0.05F);
                }
                if (player.onGround() && (visual.charging() || visual.transforming()) && player.tickCount % 5 == 0) {
                    FxParticles.dust(player.position(), visual.transforming() ? 3 : 1, 0.4F, 1.6F);
                }
            }
            if (visual.chargingTechnique()) {
                float charge = visual.techniqueChargeNow(0);
                if (charge >= 0.6F) {
                    Vec3 orb = chargePosition(player, visual, 0);
                    int n = FxParticles.count(charge >= 0.9F ? 3 : 2, orb);
                    TechniqueProfile profile = Techniques.profile(visual.technique());
                    for (int i = 0; i < n; i++) {
                        Vec3 offset = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(0.9);
                        Vec3 p = orb.add(offset);
                        FxParticles.spawn(ModParticles.KI_TRAIL.get(), p.x, p.y, p.z, profile.color(), 0.12F, 0);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ technique charge

    private static void renderChargeOrb(PoseStack pose, MultiBufferSource buffers, Camera camera, Player player,
                                        ClientState.VisualState visual, float partial, boolean firstPerson) {
        if (KiBeamEntity.clientBeamActive(player.getId(), player.level().getGameTime())) return;
        TechniqueProfile profile = Techniques.profile(visual.technique());
        float charge = visual.techniqueChargeNow(partial);
        Vec3 position = chargePosition(player, visual, partial).subtract(camera.getPosition());
        if (firstPerson) position = position.add(0, -0.15, 0);
        float time = player.tickCount + partial;
        float size = profile.size() * (0.45F + charge * 1.1F) * (0.92F + 0.08F * Mth.sin(time * 1.3F));
        float[] outer = rgb(profile.color());
        float[] inner = rgb(profile.coreColor());
        float tierGlow = charge < 0.3F ? 0.55F : charge < 0.6F ? 0.8F : 1.0F;
        billboard(pose, buffers.getBuffer(RenderType.eyes(GLOW)), camera, position, size * 2.6F, outer, 0.6F * tierGlow, 0);
        billboard(pose, buffers.getBuffer(RenderType.eyes(CORE)), camera, position, size, inner, tierGlow, 0);
        if (charge >= 0.3F) {
            billboard(pose, buffers.getBuffer(RenderType.eyes(RING)), camera, position, size * 1.9F, outer, 0.5F * tierGlow, time * 0.15F);
        }
        if (charge >= 0.9F) {
            billboard(pose, buffers.getBuffer(RenderType.eyes(RING)), camera, position, size * 2.6F, inner, 0.35F, -time * 0.22F);
            RANDOM.setSeed((long) (time / 2) * 17 + player.getId());
            VertexConsumer lightning = buffers.getBuffer(RenderType.lightning());
            for (int i = 0; i < 2; i++) {
                bolt(pose, lightning, position, 3, size * 0.9F, outer[0], outer[1], outer[2], 0.8F);
            }
        }
    }

    /** Hands position per pose family: hip for Kamehameha, side for Galick Gun, overhead for Masenko. */
    public static Vec3 chargePosition(LivingEntity entity, ClientState.VisualState visual, float partial) {
        TechniqueProfile profile = Techniques.profile(visual.technique());
        float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 base = lerp(entity, partial);
        return switch (profile.pose()) {
            case KAMEHAMEHA -> base.add(0, 0.85, 0).add(right.scale(-0.55)).add(forward.scale(-0.1));
            case GALICK_GUN -> base.add(0, 1.25, 0).add(right.scale(0.6)).add(forward.scale(0.35));
            case MASENKO -> base.add(0, 2.35, 0).add(forward.scale(0.25));
            default -> base.add(0, 1.3, 0).add(forward.scale(0.75));
        };
    }

    // ------------------------------------------------------------------------------------------------ speed lines

    private static void renderSpeedLines(PoseStack pose, MultiBufferSource buffers, Camera camera, Minecraft minecraft, float partial) {
        long nowNanos = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016F : Math.min(0.1F, (nowNanos - lastFrame) / 1.0e9F);
        lastFrame = nowNanos;
        if (ClientConfig.SPEC.isLoaded() && !ClientConfig.speedLines.get()) return;
        if (!ClientFlightController.active() || !ClientState.visual(minecraft.player.getId()).fastFlight()) return;
        Vec3 velocity = ClientFlightController.velocity();
        double speed = velocity.length();
        if (speed < ClientFlightController.cruiseSpeed() * 1.05) return;
        Vec3 dir = velocity.normalize();
        Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(dir).normalize();
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(STREAK));
        float alpha = (float) Math.min(1, (speed - ClientFlightController.cruiseSpeed()) / 0.3) * 0.55F;
        float[] white = {1, 1, 1};
        for (float[] line : SPEED_LINES) {
            // line = {forward distance, angle, radius, length}
            if (line[3] == 0 || line[0] < -2) {
                line[0] = 6 + RANDOM.nextFloat() * 8;
                line[1] = RANDOM.nextFloat() * Mth.TWO_PI;
                line[2] = 1.2F + RANDOM.nextFloat() * 2.5F;
                line[3] = 1.5F + RANDOM.nextFloat() * 2.5F;
            }
            line[0] -= (float) (speed * 20 * dt * 2.2);
            Vec3 center = dir.scale(line[0]).add(side.scale(Mth.cos(line[1]) * line[2])).add(up.scale(Mth.sin(line[1]) * line[2]));
            Vec3 a = center.subtract(dir.scale(line[3] * 0.5)), b = center.add(dir.scale(line[3] * 0.5));
            ribbon(pose, consumer, a, b, 0.025F, Vec3.ZERO, white, alpha);
        }
    }

    // ------------------------------------------------------------------------------------------------ lock-on

    private static void renderReticle(PoseStack pose, MultiBufferSource buffers, Camera camera, Minecraft minecraft, float partial) {
        if (ClientConfig.SPEC.isLoaded() && !ClientConfig.targetReticle.get()) return;
        int targetId = ClientState.visual(minecraft.player.getId()).targetId();
        if (targetId < 0 || !(minecraft.level.getEntity(targetId) instanceof LivingEntity target) || !target.isAlive()) return;
        Vec3 center = lerp(target, partial).add(0, target.getBbHeight() * 0.55, 0).subtract(camera.getPosition());
        float time = minecraft.player.tickCount + partial;
        float size = Math.max(0.6F, target.getBbHeight() * 0.55F) * (1 + 0.06F * Mth.sin(time * 0.3F));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
        float spin = time * 0.04F;
        for (int k = 0; k < 4; k++) {
            float angle = spin + k * Mth.HALF_PI;
            float cos = Mth.cos(angle), sin = Mth.sin(angle);
            Vector3f axisA = new Vector3f(left).mul(cos).add(new Vector3f(up).mul(sin));
            Vector3f axisB = new Vector3f(left).mul(-sin).add(new Vector3f(up).mul(cos));
            Vec3 corner = center.add(axisA.x() * size + axisB.x() * size, axisA.y() * size + axisB.y() * size, axisA.z() * size + axisB.z() * size);
            Vec3 armA = corner.subtract(axisA.x() * size * 0.45, axisA.y() * size * 0.45, axisA.z() * size * 0.45);
            Vec3 armB = corner.subtract(axisB.x() * size * 0.45, axisB.y() * size * 0.45, axisB.z() * size * 0.45);
            colorRibbon(pose, consumer, corner, armA, 0.035F, camera, 1.0F, 0.78F, 0.3F, 0.85F);
            colorRibbon(pose, consumer, corner, armB, 0.035F, camera, 1.0F, 0.78F, 0.3F, 0.85F);
        }
        Vec3 top = center.add(0, target.getBbHeight() * 0.55 + 0.35, 0);
        Vec3 tip = top.add(0, -0.18, 0);
        colorRibbon(pose, consumer, top.add(-0.12, 0, 0), tip, 0.03F, camera, 1.0F, 0.85F, 0.35F, 0.9F);
        colorRibbon(pose, consumer, top.add(0.12, 0, 0), tip, 0.03F, camera, 1.0F, 0.85F, 0.35F, 0.9F);
    }

    // ------------------------------------------------------------------------------------------------ geometry

    public static Vec3 lerp(net.minecraft.world.entity.Entity entity, float partial) {
        return new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));
    }

    public static float[] rgb(int color) {
        return new float[] {((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F};
    }

    /** Vertical flame quad that turns around Y to face the camera; the top leans outward slightly. */
    private static void cylindricalQuad(PoseStack pose, VertexConsumer vc, Vec3 base, Vec3 camera, float width, float height,
                                        float lean, float[] rgb, float brightness) {
        Vec3 toCam = camera.subtract(base);
        double length = Math.sqrt(toCam.x * toCam.x + toCam.z * toCam.z);
        if (length < 1.0e-4) return;
        float rx = (float) (-toCam.z / length) * width * 0.5F, rz = (float) (toCam.x / length) * width * 0.5F;
        double outward = Math.sqrt(base.x * base.x + base.z * base.z);
        float lx = 0, lz = 0;
        if (outward > 1.0e-3) { lx = (float) (base.x / outward * lean); lz = (float) (base.z / outward * lean); }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = rgb[0] * brightness, g = rgb[1] * brightness, b = rgb[2] * brightness;
        float x = (float) base.x, y = (float) base.y, z = (float) base.z;
        quad(vc, m, n, x - rx, y, z - rz, x + rx, y, z + rz, x + rx + lx, y + height, z + rz + lz, x - rx + lx, y + height, z - rz + lz,
                0, 1, 1, 0, r, g, b);
    }

    private static void verticalStreak(PoseStack pose, VertexConsumer vc, Vec3 base, float width, float height, float[] rgb, float brightness) {
        Vec3 a = base, b = base.add(0, height, 0);
        ribbon(pose, vc, a, b, width, Vec3.ZERO, rgb, brightness);
    }

    /** A camera-facing ribbon from a to b (positions relative to the camera). Uses the streak texture's long axis. */
    private static void ribbon(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float width, Vec3 camera, float[] rgb, float brightness) {
        Vec3 along = b.subtract(a);
        Vec3 view = a.add(b).scale(0.5).subtract(camera);
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0e-8) return;
        side = side.normalize().scale(width);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = rgb[0] * brightness, g = rgb[1] * brightness, bl = rgb[2] * brightness;
        quad(vc, m, n, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z),
                (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z),
                (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z),
                (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), 0, 0, 1, 1, r, g, bl);
    }

    public static void billboard(PoseStack pose, VertexConsumer vc, Camera camera, Vec3 center, float size, float[] rgb,
                                 float brightness, float rotation) {
        Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
        if (rotation != 0) {
            float c = Mth.cos(rotation), s = Mth.sin(rotation);
            Vector3f l2 = new Vector3f(left).mul(c).add(new Vector3f(up).mul(s));
            Vector3f u2 = new Vector3f(left).mul(-s).add(new Vector3f(up).mul(c));
            left = l2; up = u2;
        }
        float h = size * 0.5F;
        left.mul(h); up.mul(h);
        float x = (float) center.x, y = (float) center.y, z = (float) center.z;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = rgb[0] * brightness, g = rgb[1] * brightness, b = rgb[2] * brightness;
        quad(vc, m, n, x + left.x() - up.x(), y + left.y() - up.y(), z + left.z() - up.z(),
                x - left.x() - up.x(), y - left.y() - up.y(), z - left.z() - up.z(),
                x - left.x() + up.x(), y - left.y() + up.y(), z - left.z() + up.z(),
                x + left.x() + up.x(), y + left.y() + up.y(), z + left.z() + up.z(), 0, 1, 1, 0, r, g, b);
    }

    private static void groundQuad(PoseStack pose, VertexConsumer vc, Vec3 center, float size, float rotation, float[] rgb, float brightness) {
        float c = Mth.cos(rotation) * size, s = Mth.sin(rotation) * size;
        float x = (float) center.x, y = (float) center.y, z = (float) center.z;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = rgb[0] * brightness, g = rgb[1] * brightness, b = rgb[2] * brightness;
        quad(vc, m, n, x - c + s, y, z - s - c, x + c + s, y, z + s - c, x + c - s, y, z + s + c, x - c - s, y, z - s + c, 0, 0, 1, 1, r, g, b);
    }

    /** Both windings, so back-face culling of the eyes render type never hides a quad. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1,
                             float r, float g, float b) {
        vertex(vc, m, n, x0, y0, z0, u0, v0, r, g, b);
        vertex(vc, m, n, x1, y1, z1, u1, v0, r, g, b);
        vertex(vc, m, n, x2, y2, z2, u1, v1, r, g, b);
        vertex(vc, m, n, x3, y3, z3, u0, v1, r, g, b);
        vertex(vc, m, n, x3, y3, z3, u0, v1, r, g, b);
        vertex(vc, m, n, x2, y2, z2, u1, v1, r, g, b);
        vertex(vc, m, n, x1, y1, z1, u1, v0, r, g, b);
        vertex(vc, m, n, x0, y0, z0, u0, v0, r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float r, float g, float b) {
        vc.vertex(m, x, y, z).color(r, g, b, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    /** Untextured additive ribbon for the lightning render type (position + color). */
    private static void colorRibbon(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float width, Camera camera,
                                    float r, float g, float bl, float alpha) {
        Vec3 along = b.subtract(a);
        Vec3 view = a.add(b).scale(0.5);
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0e-8) return;
        side = side.normalize().scale(width);
        Matrix4f m = pose.last().pose();
        colorVertex(vc, m, a.subtract(side), r, g, bl, alpha);
        colorVertex(vc, m, a.add(side), r, g, bl, alpha);
        colorVertex(vc, m, b.add(side), r, g, bl, alpha);
        colorVertex(vc, m, b.subtract(side), r, g, bl, alpha);
        colorVertex(vc, m, b.subtract(side), r, g, bl, alpha);
        colorVertex(vc, m, b.add(side), r, g, bl, alpha);
        colorVertex(vc, m, a.add(side), r, g, bl, alpha);
        colorVertex(vc, m, a.subtract(side), r, g, bl, alpha);
    }

    private static void colorVertex(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    /** Jagged electric arc from a start point, built from short random segments (relative to the camera). */
    public static void bolt(PoseStack pose, VertexConsumer vc, Vec3 start, int segments, float length, float r, float g, float b, float a) {
        Vec3 point = start;
        Vec3 direction = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
        for (int i = 0; i < segments; i++) {
            Vec3 jitter = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).scale(0.5);
            Vec3 next = point.add(direction.add(jitter).normalize().scale(length));
            colorRibbon(pose, vc, point, next, 0.018F, null, r, g, b, a);
            point = next;
        }
    }
}
