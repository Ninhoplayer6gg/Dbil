package dev.dbil.gui;

import net.minecraft.client.Minecraft;

/** Presentation-only HUD memory fed by FX events (combo counter, chase prompt). Never sent anywhere. */
public final class HudState {
    private static int combo;
    private static long comboTick, chasePromptUntil;
    private static int lastSwing = -1;
    private static long lastSwingTick;

    private HudState() {}

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    public static void localSwing(int variant) {
        lastSwing = variant;
        lastSwingTick = now();
    }

    /** A confirmed hit by the local player. Launchers, smashes and finishers open the chase prompt. */
    public static void localHit() {
        long now = now();
        combo = now - comboTick <= 40 ? combo + 1 : 1;
        comboTick = now;
        if (now - lastSwingTick <= 6 && (lastSwing == dev.dbil.fx.FxType.MELEE_LAUNCHER
                || lastSwing == dev.dbil.fx.FxType.MELEE_SMASH || lastSwing == dev.dbil.fx.FxType.MELEE_FINISHER)) {
            chasePromptUntil = now + 28;
        }
    }

    public static int combo() { return now() - comboTick <= 40 ? combo : 0; }
    public static float comboFade() { return Math.max(0, 1 - (now() - comboTick) / 40F); }
    public static boolean chasePrompt() { return now() < chasePromptUntil; }
    public static void reset() { combo = 0; comboTick = chasePromptUntil = 0; lastSwing = -1; }
}
