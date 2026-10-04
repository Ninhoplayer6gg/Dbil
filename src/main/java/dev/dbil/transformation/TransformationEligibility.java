package dev.dbil.transformation;

import dev.dbil.character.CharacterData;
import net.minecraft.resources.ResourceLocation;

/** Server-side eligibility query for a future activation service; it never edits character state. */
public final class TransformationEligibility {
    private TransformationEligibility() {}

    public enum Result {
        READY, UNKNOWN_FORM, NO_CHARACTER, WRONG_RACE, LOCKED, REQUIREMENTS,
        INSUFFICIENT_KI, INVALID_STATE, BUSY, COOLDOWN, ALREADY_ACTIVE
    }

    public static Result check(CharacterData data, ResourceLocation id) {
        var definition = Transformations.get(id);
        if (definition.isEmpty()) return Result.UNKNOWN_FORM;
        return check(data, definition.get());
    }

    public static Result check(CharacterData data, TransformationDefinition definition) {
        if (!data.created()) return Result.NO_CHARACTER;
        if (!definition.races().contains(data.raceId())) return Result.WRONG_RACE;
        if (!data.unlockedTransformations().contains(definition.id())) return Result.LOCKED;
        if (!definition.requirements().satisfiedBy(data)) return Result.REQUIREMENTS;
        if (!data.canSpendKi(TransformationService.activationCost(definition))) return Result.INSUFFICIENT_KI;
        return Result.READY;
    }
}
