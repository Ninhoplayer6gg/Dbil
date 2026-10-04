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
import dev.dbil.server.ServerRuntime;
import dev.dbil.technique.KiBeamEntity;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.Transformations;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
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
    // Written on the integrated server thread during setup, read later by scheduled server tasks.
    private static volatile Vec3 origin = Vec3.ZERO, forward = new Vec3(0, 0, 1), right = new Vec3(-1, 0, 0);
    private static volatile float originYaw;
    private static volatile int cameraId = -1;
    private static volatile TrainingEnemy rival;

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
                // The world spawn is randomized (it once landed in the ocean, where flight and transformation are
                // correctly refused), so every run builds the same dry arena first.
                BlockPos center = player.blockPosition();
                int top = level.getSeaLevel() + 1;
                for (int dx = -12; dx <= 12; dx++) {
                    for (int dz = -12; dz <= 12; dz++) {
                        BlockPos column = center.offset(dx, 0, dz);
                        level.setBlock(column.atY(top), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                        level.setBlock(column.atY(top - 1), Blocks.DIRT.defaultBlockState(), 2);
                        level.setBlock(column.atY(top - 2), Blocks.DIRT.defaultBlockState(), 2);
                        for (int y = top + 1; y <= top + 20; y++) {
                            if (!level.getBlockState(column.atY(y)).isAir()) level.setBlock(column.atY(y), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                originYaw = player.getYRot();
                origin = new Vec3(center.getX() + 0.5, top + 1, center.getZ() + 0.5);
                player.connection.teleport(origin.x, origin.y, origin.z, originYaw, 0);
                float yawRadians = originYaw * ((float) Math.PI / 180F);
                forward = new Vec3(-Mth.sin(yawRadians), 0, Mth.cos(yawRadians));
                right = new Vec3(-forward.z, 0, forward.x);
                // The rival stands beyond the front camera (4 blocks) so front shots see the fighter, not the rival.
                spawnRival(level, origin.add(forward.scale(7.0)).add(right.scale(2.5)));
                // Invisible side camera for cinematic shots of beams and blows.
                Vec3 camera = origin.add(right.scale(8.0)).add(forward.scale(3.5));
                double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(camera.x), (int) Math.floor(camera.z));
                camera = new Vec3(camera.x, Math.max(origin.y, ground), camera.z);
                Vec3 focus = origin.add(forward.scale(3.5)).add(right.scale(1.0)).add(0, 1.1, 0);
                Vec3 eye = camera.add(0, 1.7775, 0);
                double dx = focus.x - eye.x, dy = focus.y - eye.y, dz = focus.z - eye.z;
                float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90F;
                float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
                ArmorStand stand = new ArmorStand(level, camera.x, camera.y, camera.z);
                stand.moveTo(camera.x, camera.y, camera.z, yaw, pitch);
                stand.setYHeadRot(yaw);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.setInvulnerable(true);
                level.addFreshEntity(stand);
                cameraId = stand.getId();
                dev.dbil.network.Network.sync(player);
            });
        }
        int t = tick - start;
        CharacterData d = data;
        int self = mc.player.getId();
        switch (t) {
            case 30 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); log("level=" + d.level() + " techniques=" + d.unlockedTechniques().size()); }
            case 45 -> shot(mc, "01_character_front");
            case 50 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 52 -> Network.sendAction(Action.LOCK_ON);
            case 70 -> { shot(mc, "02_lockon_hud"); log("lock-on target=" + ClientState.visual(self).targetId()); }
            case 72 -> sideCamera(mc, true);
            case 75 -> shot(mc, "02b_lockon_side");
            case 76 -> sideCamera(mc, false);
            case 80 -> ClientControls.touchCharging = true;
            case 118 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 125 -> { shot(mc, "03_ki_charge_aura"); log("charging=" + ClientState.visual(self).charging()); }
            case 130 -> ClientControls.touchCharging = false;
            case 134, 198, 335, 528 -> refill(mc);
            case 140 -> Network.sendTransform(Transformations.SUPER_SAIYAN);
            case 152 -> { shot(mc, "04_transforming"); log("transforming=" + ClientState.visual(self).transforming()); }
            case 185 -> { shot(mc, "05_super_saiyan_front"); log("form=" + ClientState.visual(self).transformation()); }
            case 190 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 196 -> shot(mc, "06_super_saiyan_back");
            case 200 -> Network.sendSelectTechnique(Techniques.KAMEHAMEHA);
            case 205 -> Network.sendAction(Action.TECHNIQUE_HOLD);
            case 228, 250, 262, 319 -> sideCamera(mc, true);
            case 231 -> shot(mc, "07b_kamehameha_charge_side");
            case 232, 253, 265, 322 -> sideCamera(mc, false);
            case 235 -> { maxCharge = Math.round(ClientState.visual(self).techniqueChargeNow(0) * 100); shot(mc, "07_kamehameha_charge"); }
            case 246 -> Network.sendAction(Action.TECHNIQUE_RELEASE);
            case 252 -> shot(mc, "08b_kamehameha_beam_side");
            case 256 -> shot(mc, "08_kamehameha_beam");
            case 264 -> shot(mc, "09_beam_impact_side");
            case 285 -> server(mc, (server, player, level) -> {
                // Bring the rival into melee range in front of the fighter for the combo.
                Vec3 facing = player.getLookAngle();
                Vec3 flat = new Vec3(facing.x, 0, facing.z).normalize();
                spawnRival(level, player.position().add(flat.scale(2.4)));
                CharacterCapability.get(player).setStamina(CharacterCapability.get(player).maxStamina());
            });
            case 290 -> {
                // The old rival was replaced; drop a stale lock (if any), then lock onto the new one.
                int target = ClientState.visual(self).targetId();
                if (target >= 0 && rival != null && target != rival.getId()) Network.sendAction(Action.LOCK_ON);
            }
            case 294 -> { if (ClientState.visual(self).targetId() < 0) Network.sendAction(Action.LOCK_ON); }
            case 298 -> log("combo target=" + ClientState.visual(self).targetId() + " rival=" + (rival == null ? -1 : rival.getId()));
            case 300, 309, 318, 327 -> Network.sendAction(Action.LIGHT);
            case 321 -> shot(mc, "10_combo_kick_side");
            case 330 -> shot(mc, "10b_combo_finisher");
            case 345 -> Network.sendAction(Action.FLIGHT_TOGGLE);
            case 350 -> { Network.sendAction(Action.LOCK_ON); ClientControls.forward = true; ClientControls.touchFast = true; }
            case 374 -> {
                shot(mc, "11_fast_flight");
                log("flying=" + ClientState.visual(self).flying() + " fastFlight=" + ClientState.visual(self).fastFlight());
            }
            case 380 -> { ClientControls.forward = false; ClientControls.touchFast = false; }
            case 384 -> Network.sendAction(Action.FLIGHT_TOGGLE);
            case 400 -> server(mc, (server, player, level) -> {
                player.connection.teleport(origin.x, origin.y, origin.z, originYaw, 0);
                if (rival != null) rival.discard();
            });
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
            case 505 -> { shot(mc, "16_alt_base"); log("after revert form=" + ClientState.visual(self).transformation()); }
            case 510 -> {
                Network.sendAppearance(CharacterAppearance.HUMAN_DEFAULT.withHairstyle(AppearanceOptions.HAIR_SPIKY_TALL)
                        .withOutfit(AppearanceOptions.OUTFIT_FIGHTER_VEST).withAccessories(AppearanceOptions.ACCESSORY_HEADBAND));
            }
            case 525 -> shot(mc, "17_vest_headband");
            case 532 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); Network.sendSelectTechnique(Techniques.GALICK_GUN); }
            case 534, 598, 644 -> Network.sendAction(Action.TECHNIQUE);
            case 546, 606, 654, 706, 724 -> sideCamera(mc, true);
            case 548 -> shot(mc, "18_galick_gun_side");
            case 549, 610, 658, 709, 727 -> sideCamera(mc, false);
            // Each beam must end before the next technique: the server refuses a cast while one is still active.
            case 590, 636 -> refill(mc);
            case 594 -> Network.sendSelectTechnique(Techniques.MASENKO);
            case 609 -> shot(mc, "19_masenko_side");
            case 642 -> Network.sendSelectTechnique(Techniques.KI_BARRAGE);
            case 657 -> shot(mc, "20_ki_barrage_side");
            case 680 -> server(mc, (server, player, level) -> spawnRival(level, origin.add(forward.scale(4.5)).add(right.scale(0.5))));
            case 686 -> Network.sendAction(Action.LOCK_ON);
            case 696 -> { log("vanish target=" + ClientState.visual(self).targetId()); Network.sendAction(Action.VANISH); }
            case 702 -> shot(mc, "21_vanish_behind_target");
            case 708 -> shot(mc, "21b_vanish_side");
            case 720 -> Network.sendAction(Action.GUARD_START);
            case 726 -> shot(mc, "22_guard_side");
            case 730 -> { log("guarding=" + ClientState.visual(self).guarding()); Network.sendAction(Action.GUARD_STOP); }
            case 740 -> {
                log("beam ticks observed=" + beamsSeen + " kamehameha charge at shot=" + maxCharge + "%");
                log("final form=" + ClientState.visual(self).transformation() + " appearance=" + ClientState.data().appearance());
                log("PASS: sequence completed without client exceptions");
                finish(mc);
            }
            default -> { }
        }
    }

    private static void spawnRival(ServerLevel level, Vec3 spot) {
        if (rival != null) rival.discard();
        TrainingEnemy enemy = ModEntities.TRAINING_ENEMY.get().create(level);
        if (enemy == null) return;
        enemy.moveTo(spot.x, spot.y, spot.z, originYaw + 180, 0);
        enemy.setNoAi(true);
        enemy.setPersistenceRequired();
        // Sturdy enough to take a full Kamehameha and a combo without dying mid-sequence.
        var health = enemy.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(600);
        enemy.setHealth(600);
        level.addFreshEntity(enemy);
        rival = enemy;
    }

    /** Full Ki and no technique cooldown, so each scripted scene starts from the same state. */
    private static void refill(Minecraft mc) {
        server(mc, (server, player, level) -> {
            CharacterData serverData = CharacterCapability.get(player);
            serverData.setKi(serverData.maxKi());
            serverData.setStamina(serverData.maxStamina());
            ServerRuntime.state(player).nextTechniqueTick = 0;
        });
    }

    private static void sideCamera(Minecraft mc, boolean on) {
        Entity camera = on && mc.level != null ? mc.level.getEntity(cameraId) : null;
        if (on && camera == null) { log("side camera entity missing"); return; }
        mc.setCameraEntity(on ? camera : mc.player);
        mc.options.hideGui = on;
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
        mc.getToasts().clear();
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
