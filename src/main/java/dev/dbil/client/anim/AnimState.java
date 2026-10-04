package dev.dbil.client.anim;

/** Per-entity presentation state for the procedural animator. Client-only, never synchronized. */
public final class AnimState {
    public enum Action { NONE, MELEE, TECHNIQUE_FIRE, DASH, VANISH, POWER_BURST, LAND }

    // One-shot action.
    Action action = Action.NONE;
    int variant;
    float magnitude;
    float actionStart;
    float actionLength;
    // Hit reaction (stacks with actions).
    float hitStart = -1000;
    int hitStrength;
    // Smoothed blends (0..1).
    float combat, flight, fast, charge, guard, transform, fall, technique;
    float leanPitch, leanRoll, bob;
    float lastAge = Float.NaN;
    // Ground/fall tracking.
    boolean wasOnGround = true;
    float airTime;
    float fallSpeed;
    // Last technique pose seen while charging, kept for the fire pose.
    int techniquePose = -1;
    long lastSeen;

    public void play(Action next, int variant, float magnitude, float age, float length) {
        this.action = next;
        this.variant = variant;
        this.magnitude = magnitude;
        this.actionStart = age;
        this.actionLength = Math.max(1, length);
    }

    public void hit(int strength, float age) {
        hitStrength = strength;
        hitStart = age;
    }

    /** 0..1 progress of the current action or -1 when finished. */
    float progress(float age) {
        if (action == Action.NONE) return -1;
        float t = (age - actionStart) / actionLength;
        if (t < 0 || t > 1) {
            if (t > 1) action = Action.NONE;
            return -1;
        }
        return t;
    }
}
