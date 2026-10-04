package dev.dbil.client.fx;

import dev.dbil.client.ClientFlightController;
import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Aura hum for charging/transformed characters nearby and wind for the local player's fast flight. */
public final class LoopingSounds {
    private static final Map<Integer, AuraSound> AURAS = new HashMap<>();
    private static WindSound wind;

    private LoopingSounds() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) return;
        for (Player player : minecraft.level.players()) {
            if (player.distanceToSqr(minecraft.player) > 24 * 24) continue;
            ClientState.VisualState visual = ClientState.visual(player.getId());
            if (!(visual.charging() || visual.transforming() || visual.transformed())) continue;
            AuraSound sound = AURAS.get(player.getId());
            if (sound == null || sound.isStopped()) {
                sound = new AuraSound(player);
                AURAS.put(player.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
        }
        AURAS.values().removeIf(AbstractTickableSoundInstance::isStopped);
        boolean fast = ClientConfig.SPEC.isLoaded() && ClientConfig.windSound.get() && ClientFlightController.active()
                && ClientState.visual(minecraft.player.getId()).fastFlight();
        if (fast && (wind == null || wind.isStopped())) {
            wind = new WindSound(minecraft.player);
            minecraft.getSoundManager().play(wind);
        }
    }

    public static void reset() {
        AURAS.clear();
        wind = null;
    }

    private static final class AuraSound extends AbstractTickableSoundInstance {
        private final Player player;

        AuraSound(Player player) {
            super(ModSounds.AURA_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            looping = true;
            delay = 0;
            volume = 0.01F;
            x = player.getX(); y = player.getY(); z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !player.isAlive()) { stop(); return; }
            ClientState.VisualState visual = ClientState.visual(player.getId());
            float target = visual.transforming() ? 1.0F : visual.charging() ? 0.8F : visual.transformed() ? 0.35F : 0;
            volume += (target - volume) * 0.15F;
            pitch = visual.transforming() ? 0.8F + visual.transformationProgress() * 0.5F : 0.9F;
            x = player.getX(); y = player.getY() + 1; z = player.getZ();
            if (target == 0 && volume < 0.02F) stop();
        }
    }

    private static final class WindSound extends AbstractTickableSoundInstance {
        private final Player player;

        WindSound(Player player) {
            super(ModSounds.FLIGHT_WIND.get(), SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            looping = true;
            delay = 0;
            volume = 0.01F;
            attenuation = SoundInstance.Attenuation.NONE;
            relative = true;
        }

        @Override
        public void tick() {
            if (player.isRemoved()) { stop(); return; }
            boolean fast = ClientFlightController.active() && ClientState.visual(player.getId()).fastFlight();
            double speed = ClientFlightController.velocity().length();
            float target = fast ? (float) Math.min(0.9, speed / Math.max(0.2, ClientFlightController.fastCruiseSpeed())) : 0;
            volume += (target - volume) * 0.12F;
            pitch = 0.8F + volume * 0.5F;
            if (target == 0 && volume < 0.02F) stop();
        }
    }

    static SoundEvent unused() { return ModSounds.AURA_LOOP.get(); }
}
