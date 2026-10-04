package dev.dbil.client.anim;

import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.fx.FxType;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.Techniques;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Procedural animation layered on top of vanilla {@code HumanoidModel.setupAnim}. Vanilla keeps item, bow,
 * riding, swimming and crouch poses; DBIL replaces the rigid defaults with blended stances (combat, flight,
 * power-up, guard, technique poses) and one-shot actions (combo strikes, dashes, hit reactions, landings).
 * Everything is presentation: the server never reads these values.
 */
public final class CharacterAnimator {
    private static final Map<Integer, AnimState> STATES = new HashMap<>();
    private static final Root ROOT = new Root();
    private static final float PI = Mth.PI;

    /** Whole-body transform applied by the renderer before the model (lean, spin, bob). */
    public static final class Root {
        public float pitch, roll, yaw, y;
    }

    private CharacterAnimator() {}

    public static AnimState state(LivingEntity entity) {
        AnimState state = STATES.computeIfAbsent(entity.getId(), id -> new AnimState());
        state.lastSeen = entity.level().getGameTime();
        return state;
    }

    public static void trigger(LivingEntity entity, AnimState.Action action, int variant, float magnitude, float length) {
        state(entity).play(action, variant, magnitude, entity.tickCount, length);
    }

    public static void hit(LivingEntity entity, int strength) { state(entity).hit(strength, entity.tickCount); }

    public static void prune(long gameTime) { STATES.values().removeIf(state -> gameTime - state.lastSeen > 200); }

    public static void clear() { STATES.clear(); }

    // ------------------------------------------------------------------------------------------------ root

    public static Root root(LivingEntity entity, float partialTick) {
        float age = entity.tickCount + partialTick;
        AnimState s = state(entity);
        update(s, entity, age);
        Root root = ROOT;
        root.pitch = s.leanPitch;
        root.roll = s.leanRoll;
        root.yaw = 0;
        root.y = s.bob;
        float t = s.progress(age);
        if (t >= 0) {
            switch (s.action) {
                case MELEE -> {
                    if (s.variant == FxType.MELEE_FINISHER) {
                        root.yaw = -360F * ease(range(t, 0.1F, 0.75F));
                        root.y += 0.12F * Mth.sin(PI * range(t, 0.05F, 0.85F));
                    } else if (s.variant == FxType.MELEE_HEAVY) {
                        root.pitch -= 12F * bump(t, 0.35F, 0.6F, 0.95F);
                    } else if (s.variant == FxType.MELEE_LAUNCHER) {
                        root.y += -0.1F * bump(t, 0.0F, 0.2F, 0.35F) + 0.18F * bump(t, 0.3F, 0.5F, 0.95F);
                    } else if (s.variant == FxType.MELEE_SMASH) {
                        root.pitch -= 16F * bump(t, 0.4F, 0.55F, 0.95F);
                    }
                }
                case DASH -> {
                    float w = bump(t, 0, 0.2F, 1F);
                    switch (s.variant) {
                        case FxType.DASH_LEFT -> root.roll += 24F * w;
                        case FxType.DASH_RIGHT -> root.roll -= 24F * w;
                        case FxType.DASH_BACK -> root.pitch += 18F * w;
                        default -> root.pitch -= 26F * w;
                    }
                }
                case LAND -> root.y -= 0.14F * Math.min(1, s.magnitude) * bump(t, 0, 0.15F, 1F);
                default -> { }
            }
        }
        float hit = age - s.hitStart;
        if (hit >= 0 && hit < 14) {
            float w = s.hitStrength >= 3 ? 1 : s.hitStrength == 2 ? 0.6F : 0.3F;
            root.pitch += 22F * w * decay(hit, 14);
            if (s.hitStrength >= 3 && !entity.onGround()) root.pitch += 360F * Math.min(1, hit / 12F) * 0.5F;
        }
        return root;
    }

    // ------------------------------------------------------------------------------------------------ smoothing

    private static void update(AnimState s, LivingEntity entity, float age) {
        float dt = Float.isNaN(s.lastAge) ? 1 : Mth.clamp(age - s.lastAge, 0, 4);
        if (dt == 0) return;
        s.lastAge = age;
        ClientState.VisualState v = ClientState.visual(entity.getId());
        double vx = entity.getX() - entity.xo, vy = entity.getY() - entity.yo, vz = entity.getZ() - entity.zo;
        float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
        float forward = (float) (-Mth.sin(yaw) * vx + Mth.cos(yaw) * vz);
        float side = (float) (Mth.cos(yaw) * vx + Mth.sin(yaw) * vz);
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        boolean onGround = entity.onGround();
        if (!onGround && !v.flying()) s.airTime += dt; else {
            if (!s.wasOnGround && s.airTime > 8 && s.fallSpeed > 0.45F && s.action == AnimState.Action.NONE) {
                s.play(AnimState.Action.LAND, 0, Math.min(1.5F, s.fallSpeed), age, 7);
            }
            s.airTime = 0;
        }
        if (vy < 0) s.fallSpeed = (float) -vy; else if (onGround) s.fallSpeed = 0;
        s.wasOnGround = onGround || v.flying();
        boolean beam = KiBeamEntity.clientBeamActive(entity.getId(), entity.level().getGameTime());
        boolean recent = s.action == AnimState.Action.MELEE || age - s.hitStart < 60;
        float rate = 1 - (float) Math.exp(-0.32F * dt);
        boolean aggressive = entity instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive();
        s.combat = approach(s.combat, (v.inCombat() || v.targetId() >= 0 || recent || aggressive) && !v.fastFlight() ? 1 : 0, rate);
        s.flight = approach(s.flight, v.flying() ? 1 : 0, rate);
        s.fast = approach(s.fast, v.flying() && v.fastFlight() && speed > 0.3F ? 1 : 0, rate * 0.8F);
        s.charge = approach(s.charge, v.charging() ? 1 : 0, rate);
        s.guard = approach(s.guard, v.guarding() ? 1 : 0, rate * 1.4F);
        s.transform = approach(s.transform, v.transforming() ? 1 : 0, rate);
        s.technique = approach(s.technique, v.chargingTechnique() || beam ? 1 : 0, rate * 1.3F);
        s.fall = approach(s.fall, !onGround && !v.flying() && s.airTime > 6 && vy < -0.2 ? 1 : 0, rate);
        if (v.technique() != null) s.techniquePose = Techniques.profile(v.technique()).pose().ordinal();
        else if (beam) s.techniquePose = Techniques.profile(KiBeamEntity.clientBeamTechnique(entity.getId())).pose().ordinal();
        float pitchTarget;
        if (s.fast > 0.05F) {
            pitchTarget = Mth.clamp(-70F - entity.getXRot() * 0.6F, -140F, -25F) * s.fast
                    + flightLean(forward, v) * (1 - s.fast);
        } else if (v.flying()) {
            pitchTarget = flightLean(forward, v);
        } else {
            pitchTarget = entity.isSprinting() ? -9F : -3F * Mth.clamp(forward / 0.2F, -1, 1);
        }
        if (s.technique > 0.5F || s.charge > 0.5F || s.guard > 0.5F) pitchTarget *= 0.3F;
        float rollTarget = v.flying() ? Mth.clamp(-side * 70F, -20F, 20F) : 0;
        s.leanPitch = approach(s.leanPitch, pitchTarget, rate * 0.7F);
        s.leanRoll = approach(s.leanRoll, rollTarget, rate * 0.7F);
        float bob = v.flying() && s.fast < 0.5F ? Mth.sin(age * 0.1F) * 0.035F : 0;
        if (onGround && s.combat > 0.1F && speed < 0.05F) bob += Mth.sin(age * 0.28F) * 0.012F * s.combat;
        s.bob = bob;
    }

    private static float flightLean(float forward, ClientState.VisualState v) {
        return forward > 0 ? -26F * Mth.clamp(forward / 0.5F, 0, 1) : 12F * Mth.clamp(-forward / 0.3F, 0, 1);
    }

    // ------------------------------------------------------------------------------------------------ pose

    public static void apply(PlayerModel<?> model, LivingEntity entity, float limbSwing, float limbAmount,
                             float age, float headYaw, float headPitch) {
        if (model.riding || entity.isSleeping() || model.swimAmount > 0 || entity.isFallFlying()
                || entity.isAutoSpinAttack() || entity.isDeadOrDying()) return;
        AnimState s = state(entity);
        update(s, entity, age);
        boolean extras = !ClientConfig.SPEC.isLoaded() || ClientConfig.animationExtras.get();
        boolean arms = model.rightArmPose == HumanoidModel.ArmPose.EMPTY && model.leftArmPose == HumanoidModel.ArmPose.EMPTY;
        ClientState.VisualState v = ClientState.visual(entity.getId());

        // Ground idle and locomotion polish.
        if (entity.onGround() && s.flight < 0.5F) {
            if (extras && limbAmount < 0.1F) {
                float breathe = Mth.sin(age * 0.07F);
                add(model.rightArm, 0.02F * breathe, 0, 0.07F + 0.02F * breathe);
                add(model.leftArm, 0.02F * breathe, 0, -0.07F - 0.02F * breathe);
                add(model.rightLeg, 0, 0, 0.03F);
                add(model.leftLeg, 0, 0, -0.03F);
            }
            float twist = Mth.sin(limbSwing * 0.6662F) * 0.1F * limbAmount;
            model.body.yRot += twist;
            model.head.yRot -= twist * 0.5F;
            if (entity.isSprinting() && arms) {
                float pump = Mth.cos(limbSwing * 0.6662F) * 1.25F * limbAmount;
                model.rightArm.xRot = pump - 0.3F;
                model.leftArm.xRot = -pump - 0.3F;
            }
        }

        // Combat stance: guard up, staggered feet, small fighting bounce.
        float combat = s.combat * (1 - s.flight * 0.3F) * (1 - s.charge) * (1 - s.guard) * (1 - s.technique) * (1 - s.transform);
        if (combat > 0.01F) {
            float fidget = extras ? Mth.sin(age * 0.2F) * 0.06F : 0;
            if (arms) {
                blend(model.rightArm, combat, -0.95F + fidget, -0.35F, 0.12F);
                blend(model.leftArm, combat, -1.2F - fidget, 0.45F, -0.08F);
            }
            model.body.yRot += -0.22F * combat;
            if (limbAmount < 0.15F && entity.onGround()) {
                blend(model.rightLeg, combat, 0.28F, 0.12F, 0.06F);
                blend(model.leftLeg, combat, -0.22F, -0.1F, -0.06F);
            }
        }

        // Flight: hover, cruise and the aerodynamic fast-flight pose.
        if (s.flight > 0.01F) {
            float f = s.flight * (1 - s.fast);
            float sway = extras ? Mth.sin(age * 0.1F) * 0.06F : 0;
            blend(model.rightLeg, f, -0.28F + sway, 0.05F, 0.06F);
            blend(model.leftLeg, f, 0.12F - sway, -0.05F, -0.06F);
            if (arms && combat < 0.5F) {
                blend(model.rightArm, f * (1 - combat), 0.15F, 0, 0.28F + sway * 0.5F);
                blend(model.leftArm, f * (1 - combat), 0.15F, 0, -0.28F - sway * 0.5F);
            }
            if (s.fast > 0.01F) {
                float w = s.fast;
                float headComp = Mth.clamp(-s.leanPitch * Mth.DEG_TO_RAD, 0, 1.5F);
                model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - headComp * 0.85F);
                blend(model.rightLeg, w, 0.12F, 0.02F, 0.03F);
                blend(model.leftLeg, w, 0.18F, -0.02F, -0.03F);
                if (arms) {
                    blend(model.rightArm, w, -2.95F, 0.05F, 0.1F);
                    blend(model.leftArm, w, 0.35F, 0, -0.12F);
                }
            }
        }

        // Falling: arms lift and legs spread.
        if (s.fall > 0.01F) {
            float flail = Mth.sin(age * 0.6F) * 0.15F;
            if (arms) {
                blend(model.rightArm, s.fall, -0.4F + flail, 0, 1.1F);
                blend(model.leftArm, s.fall, -0.4F - flail, 0, -1.1F);
            }
            blend(model.rightLeg, s.fall, -0.2F, 0, 0.18F);
            blend(model.leftLeg, s.fall, 0.15F, 0, -0.18F);
        }

        // Power-up: fists at the hips, wide stance, shaking with intensity.
        float power = Math.max(s.charge, s.transform);
        if (power > 0.01F) {
            float progress = v.transformationProgress();
            float shake = (extras ? Mth.sin(age * 2.7F) * 0.025F : 0) * (1 + s.transform * 2);
            float scream = s.transform * smooth(range(progress, 0.65F, 0.95F));
            if (arms) {
                blend(model.rightArm, power, 0.25F + shake - scream * 0.4F, 0.35F, 0.55F + scream * 0.55F);
                blend(model.leftArm, power, 0.25F - shake - scream * 0.4F, -0.35F, -0.55F - scream * 0.55F);
            }
            model.body.xRot = Mth.lerp(power, model.body.xRot, 0.18F - scream * 0.35F + shake);
            model.head.xRot = Mth.lerp(power, model.head.xRot, -0.15F - scream * 0.45F);
            blend(model.rightLeg, power, 0.05F, 0, 0.22F);
            blend(model.leftLeg, power, -0.05F, 0, -0.22F);
        }

        // Guard: crossed forearms in front of the face.
        if (s.guard > 0.01F && arms) {
            blend(model.rightArm, s.guard, -1.55F, -0.7F, 0.25F);
            blend(model.leftArm, s.guard, -1.45F, 0.7F, -0.25F);
            model.body.xRot = Mth.lerp(s.guard, model.body.xRot, 0.12F);
        }

        // Technique charge and beam poses.
        if (s.technique > 0.01F && arms && s.techniquePose >= 0) {
            boolean firing = KiBeamEntity.clientBeamActive(entity.getId(), entity.level().getGameTime());
            techniquePose(model, s.technique, s.techniquePose, firing, age);
        }

        // One-shot actions.
        float t = s.progress(age);
        if (t >= 0) {
            switch (s.action) {
                case MELEE -> melee(model, s.variant, t, arms);
                case TECHNIQUE_FIRE -> fire(model, s.variant, Mth.floor(s.magnitude), t, arms);
                case POWER_BURST -> {
                    float w = bump(t, 0, 0.15F, 1F);
                    if (arms) {
                        blend(model.rightArm, w, -0.3F, 0.2F, 1.3F);
                        blend(model.leftArm, w, -0.3F, -0.2F, -1.3F);
                    }
                    model.head.xRot = Mth.lerp(w, model.head.xRot, -0.5F);
                    model.body.xRot = Mth.lerp(w, model.body.xRot, -0.15F);
                }
                case LAND -> {
                    float w = bump(t, 0, 0.12F, 1F) * Math.min(1, s.magnitude);
                    model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45F);
                    if (arms) {
                        blend(model.rightArm, w, -0.6F, 0, 0.35F);
                        blend(model.leftArm, w, -0.6F, 0, -0.35F);
                    }
                    blend(model.rightLeg, w, -0.35F, 0, 0.12F);
                    blend(model.leftLeg, w, 0.25F, 0, -0.12F);
                }
                case DASH -> {
                    float w = bump(t, 0, 0.2F, 1F);
                    if (arms) {
                        blend(model.rightArm, w, 0.6F, 0, 0.2F);
                        blend(model.leftArm, w, 0.6F, 0, -0.2F);
                    }
                }
                case VANISH -> {
                    float w = bump(t, 0, 0.1F, 1F);
                    if (arms) {
                        blend(model.rightArm, w, -1.0F, -0.4F, 0.1F);
                        blend(model.leftArm, w, -1.25F, 0.45F, -0.1F);
                    }
                }
                default -> { }
            }
        }

        // Hit reaction stacks on top of everything.
        float hit = age - s.hitStart;
        if (hit >= 0 && hit < 12) {
            float w = decay(hit, s.hitStrength >= 2 ? 12 : 7) * (s.hitStrength >= 3 ? 1.0F : s.hitStrength == 2 ? 0.75F : 0.45F);
            model.body.xRot -= 0.3F * w;
            model.head.xRot -= 0.45F * w;
            if (arms) {
                add(model.rightArm, -0.5F * w, 0, 0.7F * w);
                add(model.leftArm, -0.5F * w, 0, -0.7F * w);
            }
            add(model.rightLeg, -0.25F * w, 0, 0.1F * w);
            add(model.leftLeg, 0.2F * w, 0, -0.1F * w);
        }
    }

    private static void techniquePose(PlayerModel<?> m, float w, int pose, boolean firing, float age) {
        float pulse = Mth.sin(age * 0.9F) * 0.03F;
        TechniqueProfile.Pose[] poses = TechniqueProfile.Pose.values();
        TechniqueProfile.Pose p = poses[Mth.clamp(pose, 0, poses.length - 1)];
        switch (p) {
            case KAMEHAMEHA -> {
                if (firing) {
                    blend(m.rightArm, w, -1.57F, 0.18F, 0);
                    blend(m.leftArm, w, -1.57F, -0.18F, 0);
                    m.body.xRot = Mth.lerp(w, m.body.xRot, 0.1F);
                } else {
                    blend(m.rightArm, w, 0.55F + pulse, -0.35F, 0.25F);
                    blend(m.leftArm, w, -0.2F - pulse, 0.95F, 0);
                    m.body.yRot = Mth.lerp(w, m.body.yRot, 0.55F);
                    blend(m.rightLeg, w, 0.3F, 0, 0.15F);
                    blend(m.leftLeg, w, -0.25F, 0, -0.15F);
                }
            }
            case GALICK_GUN -> {
                if (firing) {
                    blend(m.rightArm, w, -1.6F, 0.08F, 0.05F);
                    blend(m.leftArm, w, -1.6F, -0.08F, -0.05F);
                    m.body.yRot = Mth.lerp(w, m.body.yRot, -0.25F);
                } else {
                    blend(m.rightArm, w, -1.25F + pulse, 0.95F, 0);
                    blend(m.leftArm, w, -0.35F - pulse, 0.6F, -0.55F);
                    m.body.yRot = Mth.lerp(w, m.body.yRot, -0.8F);
                    blend(m.rightLeg, w, -0.2F, 0, 0.18F);
                    blend(m.leftLeg, w, 0.3F, 0, -0.18F);
                }
            }
            case MASENKO -> {
                if (firing) {
                    blend(m.rightArm, w, -1.5F, 0.1F, 0);
                    blend(m.leftArm, w, -1.5F, -0.1F, 0);
                } else {
                    blend(m.rightArm, w, -3.0F + pulse, -0.15F, 0.12F);
                    blend(m.leftArm, w, -3.0F - pulse, 0.15F, -0.12F);
                    m.head.xRot = Mth.lerp(w, m.head.xRot, -0.2F);
                }
            }
            case WAVE -> {
                blend(m.rightArm, w, -1.4F + pulse, -0.1F, 0);
                blend(m.leftArm, w, -1.2F, 0.5F, 0);
            }
            case BARRAGE, BLAST -> {
                blend(m.rightArm, w, -1.1F + pulse, -0.2F, 0);
                blend(m.leftArm, w, -0.8F, 0.3F, 0);
            }
        }
    }

    private static void fire(PlayerModel<?> m, int pose, int index, float t, boolean arms) {
        if (!arms) return;
        TechniqueProfile.Pose[] poses = TechniqueProfile.Pose.values();
        TechniqueProfile.Pose p = poses[Mth.clamp(pose, 0, poses.length - 1)];
        float w = bump(t, 0, 0.15F, 1F);
        switch (p) {
            case BLAST, BARRAGE -> {
                ModelPart arm = index % 2 == 0 ? m.rightArm : m.leftArm;
                blend(arm, w, -1.62F, index % 2 == 0 ? 0.1F : -0.1F, 0);
                m.body.yRot += (index % 2 == 0 ? -0.25F : 0.25F) * w;
            }
            case WAVE -> {
                blend(m.rightArm, w, -1.6F, 0.05F, 0);
                blend(m.leftArm, w, -1.4F, 0.4F, 0);
                m.body.yRot -= 0.2F * w;
            }
            default -> {
                blend(m.rightArm, w, -1.57F, 0.15F, 0);
                blend(m.leftArm, w, -1.57F, -0.15F, 0);
            }
        }
    }

    private static void melee(PlayerModel<?> m, int variant, float t, boolean arms) {
        switch (variant) {
            case FxType.MELEE_JAB -> strike(m, m.rightArm, t, -0.25F, 1, arms);
            case FxType.MELEE_CROSS -> strike(m, m.leftArm, t, 0.3F, -1, arms);
            case FxType.MELEE_KICK -> {
                float wind = bump(t, 0, 0.18F, 0.3F), hit = bump(t, 0.2F, 0.38F, 0.85F);
                blend(m.rightLeg, Math.max(wind, hit), -0.4F * wind - 1.55F * hit, 0, 0.15F * hit);
                m.body.xRot -= 0.25F * hit;
                if (arms) {
                    blend(m.rightArm, hit, -0.3F, 0, 0.6F);
                    blend(m.leftArm, hit, -0.9F, 0.3F, -0.3F);
                }
            }
            case FxType.MELEE_FINISHER -> {
                float w = bump(t, 0.05F, 0.2F, 0.85F);
                blend(m.rightLeg, w, -0.4F, 0, 1.35F);
                blend(m.leftLeg, w, 0.1F, 0, -0.1F);
                if (arms) {
                    blend(m.rightArm, w, -0.2F, 0, 1.2F);
                    blend(m.leftArm, w, -0.2F, 0, -1.2F);
                }
            }
            case FxType.MELEE_HEAVY -> {
                float wind = bump(t, 0, 0.3F, 0.42F), hit = bump(t, 0.38F, 0.55F, 0.95F);
                if (arms) {
                    blend(m.rightArm, Math.max(wind, hit), 0.7F * wind - 1.65F * hit, 0.4F * wind - 0.55F * hit, 0.1F);
                    blend(m.leftArm, Math.max(wind, hit), -1.1F, 0.4F, -0.1F);
                }
                m.body.yRot += 0.55F * wind - 0.6F * hit;
            }
            case FxType.MELEE_LAUNCHER -> {
                float wind = bump(t, 0, 0.22F, 0.35F), hit = bump(t, 0.3F, 0.5F, 0.95F);
                if (arms) blend(m.rightArm, Math.max(wind, hit), 0.5F * wind - 2.9F * hit, -0.15F, 0.1F);
                m.body.xRot += 0.35F * wind - 0.2F * hit;
            }
            case FxType.MELEE_SMASH -> {
                float raise = bump(t, 0, 0.3F, 0.45F), slam = bump(t, 0.4F, 0.55F, 0.95F);
                if (arms) {
                    float w = Math.max(raise, slam);
                    blend(m.rightArm, w, -2.85F * raise - 0.9F * slam, -0.25F, 0);
                    blend(m.leftArm, w, -2.85F * raise - 0.9F * slam, 0.25F, 0);
                }
                m.body.xRot += 0.45F * slam;
            }
            default -> { }
        }
    }

    private static void strike(PlayerModel<?> m, ModelPart arm, float t, float twist, int side, boolean arms) {
        float wind = bump(t, 0, 0.15F, 0.25F), hit = bump(t, 0.18F, 0.35F, 0.8F);
        if (arms) {
            blend(arm, Math.max(wind, hit), -0.5F * wind - 1.65F * hit, side * (0.35F * wind - 0.12F * hit), 0);
            ModelPart other = arm == m.rightArm ? m.leftArm : m.rightArm;
            blend(other, hit, -1.0F, side * -0.45F, 0);
        }
        m.body.yRot += twist * hit * 1.4F;
    }

    // ------------------------------------------------------------------------------------------------ math

    private static void blend(ModelPart part, float w, float x, float y, float z) {
        if (w <= 0) return;
        if (w >= 1) { part.xRot = x; part.yRot = y; part.zRot = z; return; }
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    private static void add(ModelPart part, float x, float y, float z) {
        part.xRot += x;
        part.yRot += y;
        part.zRot += z;
    }

    private static float approach(float current, float target, float rate) {
        return current + (target - current) * Mth.clamp(rate, 0, 1);
    }

    /** 0 before a, rises to 1 at b, holds, falls back to 0 at c. */
    private static float bump(float t, float a, float b, float c) {
        if (t <= a || t >= c) return 0;
        if (t < b) return smooth((t - a) / Math.max(1.0e-4F, b - a));
        float tail = Math.min(1, b + (c - b) * 0.4F);
        if (t < tail) return 1;
        return smooth(1 - (t - tail) / Math.max(1.0e-4F, c - tail));
    }

    private static float range(float t, float a, float b) { return Mth.clamp((t - a) / (b - a), 0, 1); }
    private static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float ease(float x) { return 1 - (1 - x) * (1 - x); }
    private static float decay(float elapsed, float length) { return Mth.clamp(1 - elapsed / length, 0, 1); }
}
