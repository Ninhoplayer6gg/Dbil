package dev.dbil.npc;

import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.combat.CombatService;
import dev.dbil.training.ProgressionService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Lightweight grounded sparring opponent; normal pathfinding goals are throttled by Minecraft. */
public final class TrainingEnemy extends PathfinderMob {
    public static final int EXPERIENCE_REWARD = NpcDefinitions.TRAINING_ENEMY.experienceReward();
    private boolean rewarded;

    public TrainingEnemy(EntityType<? extends TrainingEnemy> type, Level level) {
        super(type, level);
        xpReward = 0; // Character progression is deliberately separate from vanilla XP.
    }

    public static AttributeSupplier.Builder attributes() {
        NpcDefinition definition = NpcDefinitions.TRAINING_ENEMY;
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, definition.health())
                .add(Attributes.ATTACK_DAMAGE, definition.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, definition.movementSpeed())
                .add(Attributes.FOLLOW_RANGE, definition.detectionRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> target instanceof Player player && !player.isSpectator()
                        && player.getCapability(CharacterCapability.CAPABILITY)
                        .map(CharacterData::created).orElse(false)));
    }

    public long getPowerLevel() {
        return Math.round(getMaxHealth() * 12.0 + getAttributeValue(Attributes.ATTACK_DAMAGE) * 100
                + getAttributeValue(Attributes.MOVEMENT_SPEED) * 500);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        return entity instanceof LivingEntity living
                && CombatService.npcAttack(this, living, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !rewarded) {
            LivingEntity credit = getKillCredit();
            if (credit instanceof ServerPlayer player && player.isAlive() && !player.isRemoved()
                    && CharacterCapability.get(player).created()) {
                rewarded = true;
                CharacterCapability.get(player).recordTraining("training_defeats", 1);
                ProgressionService.award(player, EXPERIENCE_REWARD);
            }
        }
        super.die(source);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DbilRewarded", rewarded);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        rewarded = tag.getBoolean("DbilRewarded");
    }
}
