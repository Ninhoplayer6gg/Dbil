package dev.dbil.technique;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.combat.CombatService;
import dev.dbil.combat.TerrainDamageService;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.registry.ModSounds;
import dev.dbil.server.PlayerState;
import dev.dbil.server.ServerRuntime;
import dev.dbil.targeting.TargetingService;
import dev.dbil.transformation.TransformationService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative continuous beam. The origin follows the caster every tick, the head grows until it meets a
 * block or its range, and every living entity inside the beam volume takes an impact hit followed by bounded
 * interval hits. Clients only receive owner, direction, length and charge for rendering.
 */
public final class KiBeamEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TECHNIQUE = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(KiBeamEntity.class, EntityDataSerializers.BYTE);
    public static final byte PHASE_FIRING = 0, PHASE_FADING = 1;
    private static final int HIT_INTERVAL = 6;
    private static final int FADE_TICKS = 8;
    private static final int MAX_LIFETIME = 400;

    /** Client-side registry of beams currently fired by each owner entity id (presentation only). */
    private static final Map<Integer, ClientBeam> CLIENT_BEAMS = new java.util.concurrent.ConcurrentHashMap<>();
    private record ClientBeam(long tick, ResourceLocation technique) {}

    public static boolean clientBeamActive(int ownerId, long gameTime) {
        ClientBeam beam = CLIENT_BEAMS.get(ownerId);
        return beam != null && gameTime - beam.tick() <= 2;
    }

    public static ResourceLocation clientBeamTechnique(int ownerId) {
        ClientBeam beam = CLIENT_BEAMS.get(ownerId);
        return beam == null ? Techniques.KAMEHAMEHA : beam.technique();
    }

    private UUID ownerUuid;
    private float damage;
    private double width = 0.8;
    private double range = 32;
    private double speed = 1.5;
    private double knockback = 1.0;
    private int sustain;
    private int fade;
    private int lifetime;
    private boolean reachedEnd;
    private boolean blockImpact;
    private final Map<Integer, Long> lastHits = new HashMap<>();
    // Client interpolation only.
    public float clientLength, clientPrevLength, clientYaw, clientPrevYaw, clientPitch, clientPrevPitch;
    public int clientAge;

    public KiBeamEntity(EntityType<? extends KiBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void initialize(ServerPlayer owner, TechniqueDefinition definition, TechniqueProfile profile, Vec3 direction,
                           float damage, float charge) {
        ownerUuid = owner.getUUID();
        entityData.set(OWNER, owner.getId());
        entityData.set(TECHNIQUE, definition.id().toString());
        entityData.set(CHARGE, charge);
        this.damage = damage;
        TechniqueDefinition.BeamProperties beam = definition.beam();
        width = (beam == null ? 0.8 : beam.width()) * profile.sizeFactor(charge);
        sustain = beam == null ? 20 : beam.durationTicks();
        range = definition.range();
        speed = definition.projectileSpeed();
        knockback = definition.knockback() * profile.knockbackFactor(charge);
        setAim(direction);
        Vec3 origin = origin(owner, direction);
        setPos(origin.x, origin.y, origin.z);
    }

    public ResourceLocation techniqueId() {
        ResourceLocation id = ResourceLocation.tryParse(entityData.get(TECHNIQUE));
        return id == null ? Techniques.KAMEHAMEHA : id;
    }
    public int ownerId() { return entityData.get(OWNER); }
    public float length() { return entityData.get(LENGTH); }
    public float charge() { return entityData.get(CHARGE); }
    public float aimYaw() { return entityData.get(AIM_YAW); }
    public float aimPitch() { return entityData.get(AIM_PITCH); }
    public byte phase() { return entityData.get(PHASE); }
    public double beamWidth() {
        TechniqueDefinition definition = Techniques.get(techniqueId());
        double base = definition == null || definition.beam() == null ? 0.8 : definition.beam().width();
        return base * Techniques.profile(techniqueId()).sizeFactor(charge());
    }

    public static Vec3 direction(float yaw, float pitch) {
        float yawRad = yaw * Mth.DEG_TO_RAD, pitchRad = pitch * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yawRad) * Mth.cos(pitchRad), -Mth.sin(pitchRad), Mth.cos(yawRad) * Mth.cos(pitchRad));
    }

    /** Hands position: slightly below the eyes and in front of the body, shared by server and renderer. */
    public static Vec3 origin(Entity owner, Vec3 direction) {
        return owner.getEyePosition().add(0, -0.38, 0).add(direction.scale(0.75));
    }

    private void setAim(Vec3 direction) {
        Vec3 d = direction.normalize();
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        entityData.set(AIM_YAW, yaw);
        entityData.set(AIM_PITCH, pitch);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(TECHNIQUE, Techniques.KAMEHAMEHA.toString());
        entityData.define(LENGTH, 0F);
        entityData.define(CHARGE, 0F);
        entityData.define(AIM_YAW, 0F);
        entityData.define(AIM_PITCH, 0F);
        entityData.define(PHASE, PHASE_FIRING);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientAge++;
            clientPrevLength = clientAge <= 1 ? length() : clientLength;
            clientLength = length();
            clientPrevYaw = clientAge <= 1 ? aimYaw() : clientYaw;
            clientYaw = aimYaw();
            clientPrevPitch = clientAge <= 1 ? aimPitch() : clientPitch;
            clientPitch = aimPitch();
            if (phase() == PHASE_FIRING) CLIENT_BEAMS.put(ownerId(), new ClientBeam(level().getGameTime(), techniqueId()));
            if (CLIENT_BEAMS.size() > 64) CLIENT_BEAMS.values().removeIf(beam -> level().getGameTime() - beam.tick() > 40);
            return;
        }
        if (++lifetime > MAX_LIFETIME) { discard(); return; }
        ServerPlayer owner = owner();
        if (phase() == PHASE_FADING) {
            if (--fade <= 0) discard();
            return;
        }
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level()
                || !CharacterCapability.get(owner).created()) {
            beginFade(false, null);
            return;
        }
        PlayerState state = ServerRuntime.state(owner);
        if (state.activeBeamId != getId()) { beginFade(false, null); return; }
        Vec3 direction = steer(owner);
        Vec3 origin = origin(owner, direction);
        setPos(origin.x, origin.y, origin.z);
        double length = length();
        if (!reachedEnd) length = Math.min(range, length + speed);
        Vec3 end = origin.add(direction.scale(length));
        BlockHitResult block = level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        blockImpact = block.getType() != HitResult.Type.MISS;
        if (blockImpact) {
            length = origin.distanceTo(block.getLocation());
            end = block.getLocation();
            reachedEnd = true;
        } else if (length >= range - 1.0e-3) {
            reachedEnd = true;
        }
        entityData.set(LENGTH, (float) length);
        hitEntities(owner, origin, end, direction);
        if (reachedEnd && --sustain <= 0) {
            beginFade(true, end);
        }
    }

    private Vec3 steer(ServerPlayer owner) {
        Vec3 current = direction(aimYaw(), aimPitch());
        LivingEntity lock = TargetingService.target(owner);
        Vec3 desired = lock != null ? lock.getBoundingBox().getCenter().subtract(origin(owner, current)).normalize()
                : owner.getLookAngle();
        float turn = Techniques.profile(techniqueId()).turnRate() * Mth.DEG_TO_RAD;
        double angle = Math.acos(Mth.clamp(current.dot(desired), -1, 1));
        Vec3 result = angle <= turn || angle < 1.0e-4 ? desired
                : current.scale(Math.sin(angle - turn)).add(desired.scale(Math.sin(turn))).scale(1 / Math.sin(angle)).normalize();
        setAim(result);
        return result;
    }

    private void hitEntities(ServerPlayer owner, Vec3 origin, Vec3 end, Vec3 direction) {
        double radius = width * 0.5;
        AABB bounds = new AABB(origin, end).inflate(radius + 0.5);
        long now = level().getGameTime();
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != owner && entity.isAlive() && TargetingService.eligible(owner, entity))) {
            Vec3 center = target.getBoundingBox().getCenter();
            double along = Mth.clamp(center.subtract(origin).dot(direction), 0, origin.distanceTo(end));
            Vec3 closest = origin.add(direction.scale(along));
            double reach = radius + target.getBbWidth() * 0.5 + 0.1;
            if (closest.distanceToSqr(center) > reach * reach + target.getBbHeight() * target.getBbHeight() * 0.25) continue;
            Long previous = lastHits.get(target.getId());
            if (previous != null && now - previous < HIT_INTERVAL) continue;
            boolean first = previous == null;
            lastHits.put(target.getId(), now);
            float amount = first ? damage * 0.55F : damage * 0.45F / Math.max(1, beamTicks() / HIT_INTERVAL);
            double push = first ? knockback : knockback * 0.25;
            if (CombatService.damageBeam(owner, target, amount, direction, push, first ? 0.25 : 0.05, this)) {
                CharacterCapability.get(owner).recordTraining(first ? "technique_hits" : "beam_ticks", 1);
                if (first) CharacterCapability.get(owner).recordTraining("beam_hits", 1);
                TransformationService.recordCombat(owner);
                FxService.at((ServerLevel) level(), FxType.KI_IMPACT, first ? 1 : 0, closest,
                        (float) (width * (first ? 1.2 : 0.6)), Techniques.profile(techniqueId()).color());
            }
        }
    }

    private int beamTicks() {
        TechniqueDefinition definition = Techniques.get(techniqueId());
        return definition == null || definition.beam() == null ? 20 : definition.beam().durationTicks();
    }

    private void beginFade(boolean explode, Vec3 head) {
        if (phase() == PHASE_FADING) return;
        entityData.set(PHASE, PHASE_FADING);
        fade = FADE_TICKS;
        ServerPlayer owner = owner();
        if (owner != null) {
            PlayerState state = ServerRuntime.state(owner);
            if (state.activeBeamId == getId()) state.activeBeamId = -1;
        }
        if (!explode || head == null || owner == null) return;
        float charge = charge();
        double radius = 1.4 + width * 1.2 + charge * 1.2;
        ServerLevel server = (ServerLevel) level();
        int color = Techniques.profile(techniqueId()).color();
        FxService.at(server, FxType.EXPLOSION, blockImpact ? 1 : 0, head, (float) radius, color);
        server.playSound(null, head.x, head.y, head.z, charge >= 0.6F ? ModSounds.EXPLOSION_BIG.get() : ModSounds.EXPLOSION.get(),
                SoundSource.PLAYERS, 1.0F + charge * 0.6F, 1.0F);
        CombatService.explosion(owner, this, head, radius, damage * 0.3F, knockback * 0.8);
        if (blockImpact) TerrainDamageService.crater(owner, server, head, Math.min(3.0, 0.8 + charge * 2.2), charge);
    }

    private ServerPlayer owner() {
        if (ownerUuid == null || !(level() instanceof ServerLevel server)) return null;
        return server.getEntity(ownerUuid) instanceof ServerPlayer player ? player : null;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 origin = position();
        Vec3 end = origin.add(direction(aimYaw(), aimPitch()).scale(Math.max(1, length())));
        return new AABB(origin, end).inflate(2.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 160 * 160; }
    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean shouldBeSaved() { return false; }
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
