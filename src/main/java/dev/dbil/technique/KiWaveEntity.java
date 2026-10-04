package dev.dbil.technique;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.combat.CombatService;
import dev.dbil.combat.TerrainDamageService;
import dev.dbil.fx.FxService;
import dev.dbil.fx.FxType;
import dev.dbil.registry.ModSounds;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Server-traced energy projectile, independent of arrows and items. Charge scales damage, size and push. */
public final class KiWaveEntity extends Projectile {
    private static final EntityDataAccessor<String> TECHNIQUE = SynchedEntityData.defineId(KiWaveEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(KiWaveEntity.class,
            EntityDataSerializers.FLOAT);
    public static final int TRAIL_LENGTH = 8;
    private ResourceLocation techniqueId = Techniques.KI_WAVE;
    private float damage = 7.0F;
    private double remainingRange = 32;
    private Vec3 launchOrigin = Vec3.ZERO;
    private int lifetime;
    // Client-only trail history: never serialized or synchronized.
    public final Vec3[] trail = new Vec3[TRAIL_LENGTH];
    public int trailCount, clientAge;

    public KiWaveEntity(EntityType<? extends KiWaveEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void initialize(ServerPlayer owner, TechniqueDefinition definition, Vec3 direction, float damage) {
        setOwner(owner);
        techniqueId = definition.id();
        entityData.set(TECHNIQUE, techniqueId.toString());
        this.damage = damage;
        remainingRange = definition.range();
        // Start at the eye rather than beyond it: adjacent thin walls must still intercept the shot.
        Vec3 start = owner.getEyePosition();
        launchOrigin = start;
        setPos(start.x, start.y, start.z);
        setDeltaMovement(direction.normalize().scale(definition.projectileSpeed()));
    }

    public void setCharge(float charge) {
        entityData.set(CHARGE, Float.isFinite(charge) ? Math.max(0, Math.min(1, charge)) : 0);
    }

    public ResourceLocation techniqueId() { return techniqueId; }
    public double remainingRange() { return remainingRange; }
    public Vec3 launchOrigin() { return launchOrigin; }
    public float charge() { return entityData.get(CHARGE); }
    public float damage() { return damage; }
    /** Visual and collision radius in blocks. */
    public double radius() {
        TechniqueProfile profile = Techniques.profile(techniqueId);
        return profile.size() * profile.sizeFactor(charge());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TECHNIQUE, Techniques.KI_WAVE.toString());
        entityData.define(CHARGE, 0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TECHNIQUE.equals(key)) {
            ResourceLocation synced = ResourceLocation.tryParse(entityData.get(TECHNIQUE));
            techniqueId = synced == null ? Techniques.KI_WAVE : synced;
        }
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide) {
            if (++lifetime > 100 || remainingRange <= 0 || !(getOwner() instanceof ServerPlayer owner)
                    || !owner.isAlive() || owner.isRemoved() || owner.level() != level()) {
                discard();
                return;
            }
            if (motion.length() > remainingRange) {
                motion = motion.normalize().scale(remainingRange);
                setDeltaMovement(motion);
            }
            HitResult hit = trace(motion);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            remainingRange -= motion.length();
        } else {
            clientAge++;
            System.arraycopy(trail, 0, trail, 1, TRAIL_LENGTH - 1);
            trail[0] = position();
            trailCount = Math.min(TRAIL_LENGTH, trailCount + 1);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        updateRotation();
    }

    /** Blocks stop the shot first; charged shots use their larger radius for entity contact. */
    private HitResult trace(Vec3 motion) {
        Vec3 start = position();
        Vec3 end = start.add(motion);
        BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        float inflate = (float) Math.max(0, radius() - getBbWidth() * 0.5);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level(), this, start, end,
                getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0 + inflate), this::canHitEntity, inflate);
        return entity != null ? entity : block;
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        return super.canHitEntity(candidate) && candidate instanceof LivingEntity living
                && living.isAlive() && !living.isSpectator();
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide) return;
        TechniqueDefinition definition = Techniques.get(techniqueId);
        TechniqueProfile profile = Techniques.profile(techniqueId);
        float charge = charge();
        Vec3 impact = result.getLocation();
        if (result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target
                && getOwner() instanceof ServerPlayer owner && definition != null) {
            impact = target.getBoundingBox().getCenter();
            boolean hit = CombatService.damageProjectile(owner, target, damage, getDeltaMovement(),
                    definition.knockback() * profile.knockbackFactor(charge), 0.18 + charge * 0.2, this);
            if (hit && owner.isAlive() && !owner.isRemoved()) {
                CharacterCapability.get(owner).recordTraining("technique_hits", 1);
                TransformationService.recordCombat(owner);
            }
        }
        ServerLevel server = (ServerLevel) level();
        double radius = radius();
        FxService.at(server, FxType.KI_IMPACT, result.getType() == HitResult.Type.BLOCK ? 2 : 1, impact,
                (float) (radius * 2.2), profile.color());
        level().playSound(null, impact.x, impact.y, impact.z, ModSounds.KI_IMPACT.get(), SoundSource.PLAYERS,
                0.5F + charge * 0.5F, 1.25F - charge * 0.35F);
        // A well-charged Ki Wave bursts into a small blast; terrain damage stays an opt-in server rule.
        if (charge >= 0.6F && getOwner() instanceof ServerPlayer owner && definition != null) {
            double blast = 1.2 + charge * 1.3;
            FxService.at(server, FxType.EXPLOSION, 0, impact, (float) blast, profile.color());
            CombatService.explosion(owner, this, impact, blast, damage * 0.4F, definition.knockback());
            if (result.getType() == HitResult.Type.BLOCK) TerrainDamageService.crater(owner, server, impact, 1.0 + charge, charge);
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Technique", techniqueId.toString());
        tag.putFloat("Damage", damage);
        tag.putDouble("RemainingRange", remainingRange);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("Charge", charge());
        tag.putDouble("OriginX", launchOrigin.x);
        tag.putDouble("OriginY", launchOrigin.y);
        tag.putDouble("OriginZ", launchOrigin.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation saved = ResourceLocation.tryParse(tag.getString("Technique"));
        techniqueId = saved == null ? Techniques.KI_WAVE : saved;
        entityData.set(TECHNIQUE, techniqueId.toString());
        float readDamage = tag.getFloat("Damage");
        damage = Float.isFinite(readDamage) ? Math.max(0.25F, Math.min(100_000F, readDamage)) : 7.0F;
        double readRange = tag.getDouble("RemainingRange");
        remainingRange = Double.isFinite(readRange) ? Math.max(0, Math.min(128, readRange)) : 0;
        lifetime = Math.max(0, tag.getInt("Lifetime"));
        setCharge(tag.getFloat("Charge"));
        Vec3 origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
        launchOrigin = tag.contains("OriginX") && Double.isFinite(origin.x) && Double.isFinite(origin.y)
                && Double.isFinite(origin.z) && Math.abs(origin.x) <= 30_000_000 && Math.abs(origin.z) <= 30_000_000
                && Math.abs(origin.y) <= 4096 ? origin : position();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
