package dev.dbil.client.dev;

import dev.dbil.DBIL;
import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.client.ClientControls;
import dev.dbil.client.ClientState;
import dev.dbil.network.Network;
import dev.dbil.race.Races;
import dev.dbil.server.Action;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.Transformations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Development-only two-client test against a dedicated server ({@code -Ddbil.autotest=mp-a} / {@code mp-b}, see the
 * multiplayer CI job). Alpha powers up, transforms, fires a Kamehameha and flies; Beta stands nearby and records what a
 * <em>remote</em> player actually receives and renders: Alpha's appearance, Ki charge, transformation, beam and flight.
 * Both write {@code dbil-autotest-report.txt}; a line starting with {@code PASS} means every check held.
 */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class MultiplayerAutotest {
    private static final String MODE = System.getProperty("dbil.autotest", "");
    private static final boolean ALPHA = "mp-a".equals(MODE);
    private static final boolean BETA = "mp-b".equals(MODE);
    static final CharacterAppearance ALPHA_LOOK = CharacterAppearance.SAIYAN_DEFAULT
            .withHairstyle(AppearanceOptions.HAIR_SPIKY_TALL).withOutfit(AppearanceOptions.OUTFIT_BATTLE_ARMOR)
            .withOutfitPrimary(0xF2F2F2).withOutfitSecondary(0x2B4C9A).withOutfitAccent(0xE3C14B);
    static final CharacterAppearance BETA_LOOK = CharacterAppearance.HUMAN_DEFAULT
            .withHairstyle(AppearanceOptions.HAIR_MESSY).withHairColor(0x8A5A2B).withOutfit(AppearanceOptions.OUTFIT_TRAINING_GI)
            .withOutfitPrimary(0x3F8F4F).withOutfitSecondary(0x1D1D24).withOutfitAccent(0xE3C14B).withBodyType(1);
    private static final String[] BETA_CHECKS = {"alpha_look", "alpha_charging", "alpha_transforming", "alpha_super_saiyan",
            "alpha_beam", "alpha_flying"};
    private static final List<String> REPORT = new ArrayList<>();
    private static final Map<String, Integer> OBSERVED = new LinkedHashMap<>();
    private static final Map<Integer, String> PENDING_SHOTS = new HashMap<>();
    private static int frames, tick = -1, start = -1, finishAt = -1;
    private static Vec3 anchor = Vec3.ZERO;
    private static boolean finished;

    private MultiplayerAutotest() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!(ALPHA || BETA) || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (++frames > 20 * 60 * 9) {
            log("FAIL: timeout; observed=" + OBSERVED.keySet());
            finish(mc);
            return;
        }
        mc.getToasts().clear();
        if (mc.player == null || mc.level == null || !ClientState.received()) return;
        tick++;
        String due = PENDING_SHOTS.remove(tick);
        if (due != null) shot(mc, due);
        try {
            if (ALPHA) alpha(mc); else beta(mc);
        } catch (RuntimeException exception) {
            log("ERROR at tick " + tick + ": " + exception);
            DBIL.LOGGER.error("DBIL multiplayer autotest step failed", exception);
        }
    }

    private static void alpha(Minecraft mc) {
        if (!ClientState.data().created()) {
            if (tick == 20) {
                Network.sendCreate("Alpha", Races.SAIYAN, DBIL.id("earth_warrior"), "balanced", ALPHA_LOOK);
                log("user=" + mc.getUser().getName() + " create sent");
            }
            return;
        }
        Player beta = other(mc);
        if (start < 0) {
            // Start only once Beta is online, created and tracked, so every scene happens in front of it.
            if (beta == null || ClientState.appearance(beta.getId()) == null) return;
            start = tick;
            anchor = mc.player.position();
            log("beta visible: " + beta.getScoreboardName() + " id=" + beta.getId());
        }
        if (beta == null) {
            log("FAIL: beta left before the sequence ended");
            finish(mc);
            return;
        }
        int t = tick - start;
        double ax = Mth.floor(anchor.x) + 0.5, az = Mth.floor(anchor.z) + 0.5;
        int y = Mth.floor(anchor.y);
        switch (t) {
            // Command feedback would cover the scene in both clients' chat.
            case 0 -> command(mc, "gamerule sendCommandFeedback false");
            case 1 -> command(mc, "gamerule doDaylightCycle false");
            case 2 -> command(mc, "time set 6000");
            case 4 -> command(mc, "weather clear");
            case 6 -> command(mc, "dbil addxp 4000");
            case 8 -> command(mc, "dbil learnall");
            case 10 -> command(mc, "dbil unlockform dbil:super_saiyan");
            case 12 -> command(mc, String.format(Locale.ROOT, "tp @s %.2f %d %.2f 0 0", ax, y, az));
            case 14 -> {
                // Beta stands ahead and to Alpha's right, looking at Alpha.
                double bx = ax - 4, bz = az + 6;
                float yaw = (float) (Mth.atan2(az - bz, ax - bx) * (180.0 / Math.PI)) - 90F;
                command(mc, String.format(Locale.ROOT, "tp %s %.2f %d %.2f %.1f 0", beta.getScoreboardName(), bx, y, bz, yaw));
            }
            case 16 -> command(mc, "dbil heal");
            case 40 -> {
                shot(mc, "mp_a_sees_beta");
                var look = ClientState.appearance(beta.getId());
                log("alpha sees beta look: " + (look == null ? "none" : look.appearance().hairstyle() + " " + look.appearance().outfit()));
            }
            case 50 -> ClientControls.touchCharging = true;
            case 90 -> ClientControls.touchCharging = false;
            case 96, 170, 280 -> command(mc, "dbil heal");
            case 100 -> Network.sendTransform(Transformations.SUPER_SAIYAN);
            case 174 -> Network.sendSelectTechnique(Techniques.KAMEHAMEHA);
            case 178 -> Network.sendAction(Action.TECHNIQUE_HOLD);
            case 208 -> Network.sendAction(Action.TECHNIQUE_RELEASE);
            case 284 -> Network.sendAction(Action.FLIGHT_TOGGLE);
            case 290 -> ClientControls.ascend = true;
            case 300 -> ClientControls.ascend = false;
            case 330 -> log("alpha flying=" + ClientState.visual(mc.player.getId()).flying()
                    + " form=" + ClientState.visual(mc.player.getId()).transformation());
            case 340 -> Network.sendAction(Action.FLIGHT_TOGGLE);
            case 380 -> {
                var look = ClientState.appearance(beta.getId());
                boolean synced = look != null && look.appearance().hairstyle().equals(BETA_LOOK.hairstyle())
                        && look.appearance().outfit().equals(BETA_LOOK.outfit()) && look.appearance().bodyType() == BETA_LOOK.bodyType();
                log((synced ? "PASS" : "FAIL") + ": alpha sequence finished; beta appearance synced to alpha=" + synced);
                finish(mc);
            }
            default -> { }
        }
    }

    private static void beta(Minecraft mc) {
        if (!ClientState.data().created()) {
            if (tick == 20) {
                Network.sendCreate("Beta", Races.HUMAN, DBIL.id("earth_warrior"), "balanced", BETA_LOOK);
                log("user=" + mc.getUser().getName() + " create sent");
            }
            return;
        }
        Player alpha = other(mc);
        if (alpha == null) {
            // Beta stays until Alpha has finished and left, so Alpha's own checks never see Beta disappear.
            if (start >= 0) {
                log(missing().isEmpty() ? "PASS: beta observed every remote state of alpha " + OBSERVED
                        : "FAIL: alpha left; missing=" + missing());
                finish(mc);
            }
            return;
        }
        if (start < 0) {
            start = tick;
            log("alpha visible: " + alpha.getScoreboardName() + " id=" + alpha.getId());
        }
        ClientState.VisualState visual = ClientState.visual(alpha.getId());
        var look = ClientState.appearance(alpha.getId());
        boolean near = mc.player.distanceTo(alpha) < 12;
        observe("alpha_look", near && look != null && look.appearance().hairstyle().equals(ALPHA_LOOK.hairstyle())
                && look.appearance().outfit().equals(ALPHA_LOOK.outfit()), "mp_b_sees_alpha", 12);
        observe("alpha_charging", near && visual.charging(), "mp_b_alpha_charging", 20);
        observe("alpha_transforming", near && visual.transforming(), "mp_b_alpha_transforming", 14);
        observe("alpha_super_saiyan", near && visual.transformed() && !visual.transforming(), "mp_b_alpha_super_saiyan", 10);
        observe("alpha_beam", near && !mc.level.getEntitiesOfClass(KiBeamEntity.class, alpha.getBoundingBox().inflate(48)).isEmpty(),
                "mp_b_alpha_kamehameha", 8);
        observe("alpha_flying", visual.flying(), "mp_b_alpha_flying", 14);
        if (finishAt < 0 && missing().isEmpty()) finishAt = tick + 20 * 60;
        if (tick == finishAt) {
            log("PASS: beta observed every remote state of alpha " + OBSERVED + " (alpha still online)");
            finish(mc);
        } else if (finishAt < 0 && tick - start > 20 * 120) {
            log("FAIL: missing=" + missing() + " observed=" + OBSERVED);
            finish(mc);
        }
    }

    private static void observe(String key, boolean condition, String shotName, int delay) {
        if (!condition || OBSERVED.containsKey(key)) return;
        OBSERVED.put(key, tick);
        log("observed " + key + " at tick " + tick);
        int due = tick + delay;
        while (PENDING_SHOTS.containsKey(due)) due++;
        PENDING_SHOTS.put(due, shotName);
    }

    private static List<String> missing() {
        List<String> missing = new ArrayList<>();
        for (String check : BETA_CHECKS) if (!OBSERVED.containsKey(check)) missing.add(check);
        return missing;
    }

    private static Player other(Minecraft mc) {
        for (Player player : mc.level.players()) if (player != mc.player) return player;
        return null;
    }

    private static void command(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
        log("/" + command);
    }

    private static void shot(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "dbil_" + name + ".png", mc.getMainRenderTarget(),
                message -> log("screenshot " + name));
    }

    private static void log(String line) {
        REPORT.add(line);
        DBIL.LOGGER.info("[DBIL autotest] {}", line);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        try {
            Files.write(Path.of(mc.gameDirectory.getPath(), "dbil-autotest-report.txt"), REPORT, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DBIL.LOGGER.error("Could not write autotest report", exception);
        }
        mc.stop();
    }
}
