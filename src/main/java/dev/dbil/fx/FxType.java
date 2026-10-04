package dev.dbil.fx;

/** Discrete presentation events sent by the server. Values are wire constants: append, never reorder. */
public final class FxType {
    public static final int MELEE_SWING = 1;
    public static final int HIT = 2;
    public static final int DASH = 3;
    public static final int VANISH = 4;
    public static final int CHASE = 5;
    public static final int TECHNIQUE_FIRE = 6;
    public static final int KI_IMPACT = 7;
    public static final int EXPLOSION = 8;
    public static final int TRANSFORM_COMPLETE = 9;
    public static final int GUARD_BLOCK = 10;
    public static final int GUARD_BREAK = 11;
    public static final int TERRAIN_DEBRIS = 12;
    public static final int TRANSFORM_REVERT = 13;

    /** MELEE_SWING variants. */
    public static final int MELEE_JAB = 0, MELEE_CROSS = 1, MELEE_KICK = 2, MELEE_FINISHER = 3,
            MELEE_HEAVY = 4, MELEE_LAUNCHER = 5, MELEE_SMASH = 6;
    /** DASH variants. */
    public static final int DASH_FORWARD = 0, DASH_LEFT = 1, DASH_RIGHT = 2, DASH_BACK = 3;

    private FxType() {}
}
