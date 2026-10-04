package dev.dbil.server;

/** Client intents. New 0.3 values are appended so older ordinals keep their meaning inside protocol 3. */
public enum Action {
    CHARGE_START, CHARGE_STOP, FLIGHT_TOGGLE, DASH, LIGHT, HEAVY, TECHNIQUE, LOCK_ON, STOP_ALL, GUARD_START, GUARD_STOP,
    TRANSFORM_REVERT, SPAR_START,
    TECHNIQUE_HOLD, TECHNIQUE_RELEASE, LAUNCHER, SMASH, VANISH, DASH_LEFT, DASH_RIGHT, DASH_BACK, TARGET_NEXT
}
