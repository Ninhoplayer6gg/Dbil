package dev.dbil.animation;

import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.rendering.AuraRenderer;
import dev.dbil.rendering.Auras;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Isolated presentation hooks; no animation dependency or gameplay state changes. */
public final class PlayerPresentation {
    private PlayerPresentation() {}
    public static void tick(Minecraft minecraft) {
        if (!ClientConfig.particles.get() || minecraft.level == null || minecraft.player == null
                || minecraft.level.getGameTime() % 4 != 0) return;
        double intensity = ClientConfig.auraIntensity.get();
        int quality = ClientConfig.effectQuality.get();
        int count = Math.min(4, Math.max(0, (int) Math.ceil(intensity * quality)));
        if (count == 0) return;
        double distance = ClientConfig.effectDistance.get();
        double limitSquared = distance * distance;
        // Player list is bounded in our two-player use case; only four particles per active aura.
        for (Player player : minecraft.level.players()) {
            if (player.distanceToSqr(minecraft.player) > limitSquared) continue;
            ClientState.VisualState state = ClientState.visual(player.getId());
            boolean transformed = !state.transformation().getPath().equals("base");
            if (!state.charging() && state.techniqueTicks() == 0 && !transformed && state.transformationTicks() == 0) continue;
            var aura = transformed || state.transformationTicks() > 0 ? Auras.get(state.transformation())
                    : state.techniqueTicks() > 0 ? Auras.TECHNIQUE : Auras.CHARGING;
            AuraRenderer.emit(player, aura, count);
        }
    }
}
