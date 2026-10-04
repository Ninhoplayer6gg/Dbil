package dev.dbil.client.dev;

import dev.dbil.DBIL;
import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.capability.CharacterCapability;
import dev.dbil.character.CharacterData;
import dev.dbil.client.ClientControls;
import dev.dbil.client.ClientState;
import dev.dbil.gui.CharacterCreationScreen;
import dev.dbil.gui.DBILMenuScreen;
import dev.dbil.network.Network;
import dev.dbil.npc.TrainingEnemy;
import dev.dbil.race.Races;
import dev.dbil.registry.ModEntities;
import dev.dbil.server.Action;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.Transformations;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Development-only scripted client run, enabled with {@code -Ddbil.autotest=true} (see build.gradle). It drives
 * the real client and integrated server paths in a singleplayer world, captures screenshots of every visual
 * system and writes a short report, then quits. Never active in normal play.
 */
@Mod.EventBusSubscriber(modid = DBIL.MOD_ID, value = Dist.CLIENT)
public final class ClientAutotest {
    private static final boolean ENABLED = Boolean.getBoolean("dbil.autotest");
    private static final List<String> REPORT = new ArrayList<>();
    private static int tick = -1;
    private static int start = -1;
    private static int beamsSeen, maxCharge;
    private static boolean finished;

    private ClientAutotest() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (mc.player == null || mc.level == null || !ClientState.received()) return;
        tick++;
        try {
            run(mc);
        } catch (RuntimeException exception) {
            log("ERROR at tick " + tick + ": " + exception);
            DBIL.LOGGER.error("DBIL autotest step failed", exception);
        }
        if (mc.level.getEntitiesOfClass(KiBeamEntity.class, mc.player.getBoundingBox().inflate(48)).size() > 0) beamsSeen++;
    }

    private static void run(Minecraft mc) {
        CharacterData data = ClientState.data();
        if (!data.created()) {
            if (tick == 20) {
                CharacterAppearance look = CharacterAppearance.SAIYAN_DEFAULT;
                Network.sendCreate("Kairo", Races.SAIYAN, DBIL.id("earth_warrior"), "balanced", look);
                log("create sent");
            }
            if (tick == 15) shot(mc, "00_creation_screen");
            if (tick > 400) { log("FAIL: character was never created"); finish(mc); }
            return;
        }
        if (start < 0) {
            start = tick;
            log("character created at tick " + tick);
            server(mc, (server, player, level) -> {
                CharacterData serverData = CharacterCapability.get(player);
                serverData.addExperience(4000);
                for (var technique : Techniques.values()) { serverData.learn(technique.id()); serverData.equip(technique.id()); }
                serverData.unlockTransformation(Transformations.SUPER_SAIYAN);
                serverData.setKi(serverData.maxKi());
                level.setDayTime(6000);
                level.setWeatherParameters(12000, 0, false, false);
                Vec3 look = player.getLookAngle();
                Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
                Vec3 right = new Vec3(-forward.z, 0, forward.x);
                Vec3 spot = player.position().add(forward.scale(3.0)).add(right.scale(1.6));
                TrainingEnemy enemy = ModEntities.TRAINING_ENEMY.get().create(level);
                if (enemy != null) {
                    enemy.moveTo(spot.x, player.getY(), spot.z, player.getYRot() + 180, 0);
                    enemy.setNoAi(true);
                    enemy.setPersistenceRequired();
                    level.addFreshEntity(enemy);
                }
                dev.dbil.network.Network.sync(player);
            });
        }
        int t = tick - start;
        CharacterData d = data;
        switch (t) {
            case 30 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); log("level=" + d.level() + " techniques=" + d.unlockedTechniques().size()); }
            case 45 -> shot(mc, "01_character_front");
            case 50 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 52 -> Network.sendAction(Action.LOCK_ON);
            case 70 -> shot(mc, "02_lockon_hud");
            case 75 -> ClientControls.touchCharging = true;
            case 120 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
            case 125 -> shot(mc, "03_ki_charge_aura");
            case 130 -> ClientControls.touchCharging = false;
            case 140 -> Network.sendTransform(Transformations.SUPER_SAIYAN);
            case 152 -> shot(mc, "04_transforming");
            case 185 -> shot(mc, "05_super_saiyan_front");
            case 190 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 196 -> shot(mc, "06_super_saiyan_back");
            case 200 -> Network.sendSelectTechnique(Techniques.KAMEHAMEHA);
            case 205 -> Network.sendAction(Action.TECHNIQUE_HOLD);
            case 235 -> { maxCharge = Math.round(ClientState.visual(mc.player.getId()).techniqueChargeNow(0) * 100); shot(mc, "07_kamehameha_charge"); }
            case 246 -> Network.sendAction(Action.TECHNIQUE_RELEASE);
            case 254 -> shot(mc, "08_kamehameha_beam");
            case 262 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
            case 264 -> shot(mc, "09_beam_front");
            case 300 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); Network.sendAction(Action.LIGHT); }
            case 309 -> Network.sendAction(Action.LIGHT);
            case 318 -> Network.sendAction(Action.LIGHT);
            case 320 -> shot(mc, "10_combo_kick");
            case 327 -> Network.sendAction(Action.LIGHT);
            case 345 -> Network.sendAction(Action.FLIGHT_TOGGLE);
            case 350 -> { Network.sendAction(Action.LOCK_ON); ClientControls.forward = true; ClientControls.touchFast = true; }
            case 385 -> shot(mc, "11_fast_flight");
            case 395 -> { ClientControls.forward = false; ClientControls.touchFast = false; }
            case 405 -> Network.sendAction(Action.FLIGHT_TOGGLE);
            case 420 -> mc.setScreen(new CharacterCreationScreen(true));
            case 432 -> shot(mc, "12_appearance_editor");
            case 436 -> mc.setScreen(new DBILMenuScreen());
            case 446 -> shot(mc, "13_menu");
            case 450 -> { mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 460 -> shot(mc, "14_first_person");
            case 465 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                Network.sendAppearance(CharacterAppearance.SAIYAN_DEFAULT.withBodyType(1).withHairstyle(AppearanceOptions.HAIR_STRAIGHT)
                        .withHairColor(0x6D3E91).withOutfit(AppearanceOptions.OUTFIT_BATTLE_ARMOR).withOutfitPrimary(0xF2F2F2)
                        .withOutfitSecondary(0x2B4C9A).withOutfitAccent(0xE3C14B).withEyeStyle(4).withEyebrowStyle(3)
                        .withAccessories(AppearanceOptions.ACCESSORY_TAIL | AppearanceOptions.ACCESSORY_WRISTBANDS));
            }
            case 480 -> shot(mc, "15_alt_super_saiyan");
            case 485 -> Network.sendTransform(CharacterData.BASE_FORM);
            case 505 -> shot(mc, "16_alt_base");
            case 510 -> {
                Network.sendAppearance(CharacterAppearance.HUMAN_DEFAULT.withHairstyle(AppearanceOptions.HAIR_SPIKY_TALL)
                        .withOutfit(AppearanceOptions.OUTFIT_FIGHTER_VEST).withAccessories(AppearanceOptions.ACCESSORY_HEADBAND));
            }
            case 525 -> shot(mc, "17_vest_headband");
            case 530 -> {
                log("beam ticks observed=" + beamsSeen + " kamehameha charge at shot=" + maxCharge + "%");
                log("final form=" + ClientState.visual(mc.player.getId()).transformation() + " appearance=" + ClientState.data().appearance());
                log("PASS: sequence completed without client exceptions");
                finish(mc);
            }
            default -> { }
        }
    }

    @FunctionalInterface
    private interface ServerTask { void run(IntegratedServer server, ServerPlayer player, ServerLevel level); }

    private static void server(Minecraft mc, ServerTask task) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) { log("no integrated server"); return; }
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) task.run(server, player, player.serverLevel());
        });
    }

    private static void shot(Minecraft mc, String name) {
        Consumer<net.minecraft.network.chat.Component> sink = message -> log("screenshot " + name + ": " + message.getString());
        Screenshot.grab(mc.gameDirectory, "dbil_" + name + ".png", mc.getMainRenderTarget(), sink);
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
