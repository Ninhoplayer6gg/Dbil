package dev.dbil.registry;

import dev.dbil.DBIL;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Named DBIL sound events. sounds.json maps them to tuned vanilla events, so resource packs can replace every
 * DBIL sound individually without code changes.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DBIL.MOD_ID);
    public static final RegistryObject<SoundEvent> KI_BLAST_FIRE = sound("ki.blast.fire");
    public static final RegistryObject<SoundEvent> KI_WAVE_FIRE = sound("ki.wave.fire");
    public static final RegistryObject<SoundEvent> KI_IMPACT = sound("ki.impact");
    public static final RegistryObject<SoundEvent> TECHNIQUE_CHARGE = sound("technique.charge");
    public static final RegistryObject<SoundEvent> BEAM_CHARGE = sound("beam.charge");
    public static final RegistryObject<SoundEvent> BEAM_FIRE = sound("beam.fire");
    public static final RegistryObject<SoundEvent> BEAM_HUM = sound("beam.hum");
    public static final RegistryObject<SoundEvent> EXPLOSION = sound("explosion");
    public static final RegistryObject<SoundEvent> EXPLOSION_BIG = sound("explosion.big");
    public static final RegistryObject<SoundEvent> AURA_CHARGE = sound("aura.charge");
    public static final RegistryObject<SoundEvent> AURA_LOOP = sound("aura.loop");
    public static final RegistryObject<SoundEvent> TRANSFORM_CHARGE = sound("transform.charge");
    public static final RegistryObject<SoundEvent> TRANSFORM_COMPLETE = sound("transform.complete");
    public static final RegistryObject<SoundEvent> TRANSFORM_REVERT = sound("transform.revert");
    public static final RegistryObject<SoundEvent> MELEE_SWING = sound("melee.swing");
    public static final RegistryObject<SoundEvent> MELEE_HIT = sound("melee.hit");
    public static final RegistryObject<SoundEvent> MELEE_HIT_HEAVY = sound("melee.hit_heavy");
    public static final RegistryObject<SoundEvent> GUARD_BLOCK = sound("guard.block");
    public static final RegistryObject<SoundEvent> GUARD_BREAK = sound("guard.break");
    public static final RegistryObject<SoundEvent> DASH = sound("dash");
    public static final RegistryObject<SoundEvent> VANISH = sound("vanish");
    public static final RegistryObject<SoundEvent> FLIGHT_WIND = sound("flight.wind");
    public static final RegistryObject<SoundEvent> FLIGHT_TAKEOFF = sound("flight.takeoff");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(DBIL.id(path)));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
