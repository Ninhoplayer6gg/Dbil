package dev.dbil.technique;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.combat.CombatService;
import dev.dbil.transformation.TransformationService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Server-traced energy projectile, independent of arrows, items and terrain destruction. */
public final class KiWaveEntity extends Projectile {
    private static final EntityDataAccessor<String> TECHNIQUE = SynchedEntityData.defineId(KiWaveEntity.class,
            EntityDataSerializers.STRING);
    private ResourceLocation techniqueId = Techniques.KI_WAVE;
    private float damage = 7.0F;
    private double remainingRange = 32;
    private Vec3 launchOrigin = Vec3.ZERO;
    private int lifetime;

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

    public ResourceLocation techniqueId() { return techniqueId; }
    public double remainingRange() { return remainingRange; }
    public Vec3 launchOrigin() { return launchOrigin; }

    @Override
    protected void defineSynchedData() { entityData.define(TECHNIQUE, Techniques.KI_WAVE.toString()); }

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
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
            remainingRange -= motion.length();
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        updateRotation();
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
        if (result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target
                && getOwner() instanceof ServerPlayer owner && definition != null) {
            boolean hit = CombatService.damageProjectile(owner, target, damage, getDeltaMovement(),
                    definition.knockback(), 0.18, this);
            if (hit && owner.isAlive() && !owner.isRemoved()) {
                CharacterCapability.get(owner).recordTraining("technique_hits", 1);
                TransformationService.recordCombat(owner);
            }
        }
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
                SoundSource.PLAYERS, 0.45F, 1.5F);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Technique", techniqueId.toString());
        tag.putFloat("Damage", damage);
        tag.putDouble("RemainingRange", remainingRange);
        tag.putInt("Lifetime", lifetime);
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
