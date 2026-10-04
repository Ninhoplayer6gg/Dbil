package dev.dbil.rendering;

import net.minecraft.world.entity.player.Player;

public final class AuraRenderer {
    private AuraRenderer() {}
    public static void emit(Player player, AuraDefinition aura, int configuredCount) {
        int count = Math.min(aura.particlesPerBurst(), configuredCount);
        for (int i = 0; i < count; i++) {
            double angle = player.level().random.nextDouble() * Math.PI * 2;
            double radius = aura.radius() + player.level().random.nextDouble() * 0.15;
            player.level().addParticle(aura.particle(),
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + player.level().random.nextDouble() * player.getBbHeight(),
                    player.getZ() + Math.sin(angle) * radius, 0, aura.riseSpeed(), 0);
        }
    }
}
