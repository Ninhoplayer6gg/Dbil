package dev.dbil.training;

import dev.dbil.DBIL;
import dev.dbil.character.CharacterData;
import dev.dbil.network.Network;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.Transformations;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Server-evaluated personal quest chain. No packet can claim a reward or supply an objective value. */
public final class TrainingChallenges {
    public static final ResourceLocation FIRST_COMBAT = DBIL.id("first_combat");
    public static final ResourceLocation KI_CONTROL = DBIL.id("ki_control");
    public static final ResourceLocation AWAKENING = DBIL.id("awakening");
    public static final ResourceLocation BEAM_TRAINING = DBIL.id("beam_training");
    public static final ResourceLocation RAPID_KI = DBIL.id("rapid_ki");
    public static final ResourceLocation EXPLOSIVE_WAVE = DBIL.id("explosive_wave");
    public static final ResourceLocation UNLOCK_EVALUATOR = DBIL.id("training_challenge");
    private static final List<ChallengeDefinition> DEFINITIONS = List.of(
            new ChallengeDefinition(FIRST_COMBAT, Component.translatable("challenge.dbil.first_combat"), null,
                    List.of(new ChallengeDefinition.Objective("training_defeats", 1)),
                    40, DBIL.id("ki_blast"), false),
            new ChallengeDefinition(KI_CONTROL, Component.translatable("challenge.dbil.ki_control"), FIRST_COMBAT,
                    List.of(new ChallengeDefinition.Objective("training_defeats", 3),
                            new ChallengeDefinition.Objective("technique_hits", 5),
                            new ChallengeDefinition.Objective("flight_ticks", 1200)),
                    60, DBIL.id("ki_barrage"), false),
            new ChallengeDefinition(AWAKENING, Component.translatable("challenge.dbil.awakening"), KI_CONTROL,
                    List.of(new ChallengeDefinition.Objective("training_defeats", 6),
                            new ChallengeDefinition.Objective("level", 3),
                            new ChallengeDefinition.Objective("ki_charged", 250),
                            new ChallengeDefinition.Objective("technique_hits", 8)),
                    80, null, true),
            // 0.3 beam path. Appended after the 0.2 chain so existing progress and rewards keep their order.
            new ChallengeDefinition(BEAM_TRAINING, Component.translatable("challenge.dbil.beam_training"), KI_CONTROL,
                    List.of(new ChallengeDefinition.Objective("technique_hits", 12),
                            new ChallengeDefinition.Objective("ki_charged", 400),
                            new ChallengeDefinition.Objective("level", 2)),
                    70, DBIL.id("kamehameha"), false),
            new ChallengeDefinition(RAPID_KI, Component.translatable("challenge.dbil.rapid_ki"), BEAM_TRAINING,
                    List.of(new ChallengeDefinition.Objective("beam_hits", 4),
                            new ChallengeDefinition.Objective("technique_hits", 20),
                            new ChallengeDefinition.Objective("melee_hits", 40)),
                    80, DBIL.id("masenko"), false),
            new ChallengeDefinition(EXPLOSIVE_WAVE, Component.translatable("challenge.dbil.explosive_wave"), AWAKENING,
                    List.of(new ChallengeDefinition.Objective("training_defeats", 10),
                            new ChallengeDefinition.Objective("beam_hits", 8),
                            new ChallengeDefinition.Objective("level", 3)),
                    100, DBIL.id("galick_gun"), false));
    // Weak player keys release session timing when the actual player entity is replaced or disconnected.
    // The map is accessed only on the logical server thread; no timing is persisted or sent to clients.
    private static final Map<ServerPlayer, Long> LAST_EVALUATED = new WeakHashMap<>();
    private static boolean initialized;

    private TrainingChallenges() { }

    public static synchronized void bootstrap() {
        if (initialized) return;
        for (ChallengeDefinition definition : DEFINITIONS) {
            if (definition.prerequisite() != null && DEFINITIONS.stream()
                    .noneMatch(previous -> previous.id().equals(definition.prerequisite()))) {
                throw new IllegalStateException("Unknown training challenge prerequisite: " + definition.id());
            }
        }
        initialized = true;
    }

    /** Returns immutable definitions for the GUI; completed state and counters always come from its snapshot. */
    public static List<ChallengeDefinition> progress(CharacterData data) {
        Objects.requireNonNull(data);
        bootstrap();
        return data.compatibleSchema() ? DEFINITIONS : List.of();
    }

    public static boolean completed(CharacterData data, ResourceLocation id) {
        return data.storyFlags().contains("challenge:" + Objects.requireNonNull(id));
    }

    public static double counter(CharacterData data, String key) {
        Objects.requireNonNull(data);
        if ("level".equals(key)) return data.level();
        double value = data.trainingStats().getOrDefault(key, 0.0);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    public static boolean available(CharacterData data, ChallengeDefinition definition) {
        return data.created() && !completed(data, definition.id())
                && (definition.prerequisite() == null || completed(data, definition.prerequisite()));
    }

    public static boolean ready(CharacterData data, ChallengeDefinition definition) {
        return available(data, definition) && definition.objectives().stream()
                .allMatch(objective -> counter(data, objective.key()) >= objective.target());
    }

    /** At most one evaluation and one reward per player per second, including repeated calls in the same tick. */
    public static void tick(ServerPlayer player, CharacterData data) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || !data.created()
                || !data.compatibleSchema()) return;
        bootstrap();
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_EVALUATED.get(player);
        if (previous != null && now >= previous && now - previous < 20) return;
        LAST_EVALUATED.put(player, now);
        for (ChallengeDefinition definition : DEFINITIONS) {
            if (!ready(data, definition)) continue;
            RewardPlan reward = prepareReward(data, definition);
            if (reward == null) return;
            // The durable marker is written before external reward notifications/syncs. The preflight
            // guarantees every resource has capacity, so a full flag/unlock/equip collection grants no XP.
            data.setStoryFlag(definition.completionFlag());
            if (!completed(data, definition.id())) return;
            if (definition.rewardTechnique() != null) {
                data.learn(definition.rewardTechnique());
                data.equip(definition.rewardTechnique());
            }
            for (ResourceLocation form : reward.forms()) data.unlockTransformation(form);
            ProgressionService.award(player, definition.rewardXp());
            // XP multipliers can be zero; the unlock and completion still need an owner snapshot.
            Network.sync(player);
            player.displayClientMessage(Component.translatable("message.dbil.challenge_completed", definition.displayName()), false);
            return;
        }
    }

    private record RewardPlan(List<ResourceLocation> forms) { }

    /** Rare, completion-only preflight keeps the normal 1 Hz objective checks small and allocation-light. */
    private static RewardPlan prepareReward(CharacterData data, ChallengeDefinition definition) {
        List<ResourceLocation> forms = definition.unlockRaceTransformation() ? Transformations.values().stream()
                .filter(form -> form.races().contains(data.raceId()))
                .filter(form -> form.unlockCondition().evaluator().equals(UNLOCK_EVALUATOR))
                .filter(form -> definition.id().toString().equals(form.unlockCondition().parameters().get("challenge")))
                .map(form -> form.id()).toList() : List.of();
        if (definition.unlockRaceTransformation() && forms.isEmpty()) return null;
        if (definition.rewardTechnique() != null && Techniques.get(definition.rewardTechnique()) == null) return null;
        CharacterData planned = new CharacterData();
        planned.copyFrom(data);
        planned.setStoryFlag(definition.completionFlag());
        if (!completed(planned, definition.id())) return null;
        if (definition.rewardTechnique() != null) {
            planned.learn(definition.rewardTechnique());
            planned.equip(definition.rewardTechnique());
            if (!planned.unlockedTechniques().contains(definition.rewardTechnique())
                    || !planned.equippedTechniques().contains(definition.rewardTechnique())) return null;
        }
        for (ResourceLocation form : forms) {
            planned.unlockTransformation(form);
            if (!planned.unlockedTransformations().contains(form)) return null;
        }
        return new RewardPlan(forms);
    }
}
