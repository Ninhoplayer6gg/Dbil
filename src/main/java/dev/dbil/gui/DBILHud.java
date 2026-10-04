package dev.dbil.gui;

import dev.dbil.character.CharacterData;
import dev.dbil.client.ClientState;
import dev.dbil.client.ClientEvents;
import dev.dbil.config.ClientConfig;
import dev.dbil.technique.Techniques;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Small screen-relative status panel; independent from vanilla hunger and experience. */
public final class DBILHud {
    private static final int PANEL_WIDTH = 170, PANEL_HEIGHT = 110;
    private DBILHud() {}

    public static void render(ForgeGui forgeGui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.hud.get() || minecraft.player == null || minecraft.options.hideGui
                || !ClientState.received() || !ClientState.data().created()) return;
        CharacterData data = ClientState.data();
        ClientState.VisualState visual = ClientState.visual(minecraft.player.getId());
        float scale = (float) Math.min(ClientConfig.hudScale.get(), Math.min(width / 190.0, height / 140.0));
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1);
        int availableWidth = (int) (width / scale) - PANEL_WIDTH;
        int availableHeight = (int) (height / scale) - PANEL_HEIGHT;
        int x = Mth.clamp((int) Math.round(ClientConfig.hudX.get() * availableWidth), 7, Math.max(7, availableWidth - 7));
        int y = Mth.clamp((int) Math.round(ClientConfig.hudY.get() * availableHeight), 7, Math.max(7, availableHeight - 7));
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xBA101721);
        graphics.fill(x, y, x + 2, y + PANEL_HEIGHT, 0xFF55CCFF);
        graphics.drawString(minecraft.font, Component.translatable("hud.dbil.level", data.level()), x + 7, y + 5, 0xFFFFFF, false);
        if (ClientConfig.powerLevel.get()) graphics.drawString(minecraft.font, "PL " + compactPower(data.currentPower()), x + 73, y + 5, 0xFFE08A, false);
        bar(graphics, minecraft, x + 7, y + 19, 155, "HP", minecraft.player.getHealth(), minecraft.player.getMaxHealth(), 0xFFD94B59);
        bar(graphics, minecraft, x + 7, y + 35, 155, "Ki", data.ki(), data.maxKi(), 0xFF299BCD);
        bar(graphics, minecraft, x + 7, y + 51, 155, "STA", data.stamina(), data.maxStamina(), 0xFF48AA6F);
        int cooldown = visual.cooldownTicks();
        var definition = Techniques.get(data.selectedTechnique());
        Component name = definition == null ? Component.translatable("action.dbil.technique") : definition.displayName();
        Component technique = cooldown > 0 ? Component.translatable("hud.dbil.technique_cooldown", name, (cooldown + 19) / 20)
                : visual.techniqueTicks() > 0 ? Component.translatable("hud.dbil.technique_charging", name)
                : Component.translatable("hud.dbil.technique_ready", name, ClientEvents.TECHNIQUE.getTranslatedKeyMessage());
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(technique.getString(), 155), x + 7, y + 70, 0xDFECFA, false);
        graphics.drawString(minecraft.font, Component.translatable(visual.flying() ? "hud.dbil.flying" : "hud.dbil.grounded", ClientEvents.FLIGHT.getTranslatedKeyMessage(), ClientEvents.MENU.getTranslatedKeyMessage()), x + 7, y + 83, 0xA8C5DF, false);
        Component status = visual.guardBreakTicks() > 0 ? Component.translatable("hud.dbil.guard_broken", (visual.guardBreakTicks() + 19) / 20)
                : visual.guarding() ? Component.translatable("hud.dbil.guarding")
                : visual.transformationTicks() > 0 ? Component.translatable("hud.dbil.transforming")
                : !CharacterData.BASE_FORM.equals(visual.transformation()) ? Component.translatable("transformation.dbil." + visual.transformation().getPath())
                : Component.translatable("hud.dbil.guard_hint");
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(status.getString(), 155), x + 7, y + 97, visual.guardBreakTicks() > 0 ? 0xFF8B88 : 0xFFE08A, false);
        graphics.pose().popPose();
        if (visual.targetId() >= 0 && minecraft.level.getEntity(visual.targetId()) instanceof LivingEntity target && target.isAlive()) {
            targetPanel(graphics, minecraft, target, visual, width, height,
                    (int) (x * scale), (int) (y * scale), (int) ((x + PANEL_WIDTH) * scale), (int) ((y + PANEL_HEIGHT) * scale));
        }
    }

    private static void targetPanel(GuiGraphics graphics, Minecraft minecraft, LivingEntity target,
                                    ClientState.VisualState visual, int width, int height,
                                    int ownLeft, int ownTop, int ownRight, int ownBottom) {
        int panelWidth = Math.min(170, width - 14), panelHeight = 57;
        int x = width - panelWidth - 7, y = 7;
        if (x < ownRight + 7 && x + panelWidth > ownLeft - 7 && y < ownBottom + 7 && y + panelHeight > ownTop - 7) {
            int rightSpace = width - ownRight - 14;
            int leftSpace = ownLeft - 14;
            if (rightSpace >= 110) {
                panelWidth = Math.min(panelWidth, rightSpace); x = ownRight + 7;
            } else if (leftSpace >= 110) {
                panelWidth = Math.min(panelWidth, leftSpace); x = 7;
            } else {
                y = Math.min(height - panelHeight - 7, ownBottom + 7);
            }
        }
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xC7101721);
        graphics.fill(x, y, x + 2, y + panelHeight, 0xFFFFCD70);
        String name = Component.translatable("hud.dbil.target_name", target.getDisplayName()).getString();
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(name, panelWidth - 14), x + 7, y + 5, 0xFFFFD275, false);
        bar(graphics, minecraft, x + 7, y + 18, panelWidth - 14, "HP", target.getHealth(), target.getMaxHealth(), 0xFFAD5559);
        String power = visual.targetPower() >= 0 ? compactPower(visual.targetPower()) : "?";
        Component stats = Component.translatable("hud.dbil.target_stats", power, (int) target.distanceTo(minecraft.player));
        graphics.drawString(minecraft.font, stats, x + 7, y + 35, 0xFFE6B0, false);
        Component camera = Component.translatable(ClientConfig.lockOnCamera.get() ? "hud.dbil.camera_on" : "hud.dbil.camera_off", ClientEvents.TARGET.getTranslatedKeyMessage());
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(camera.getString(), panelWidth - 14), x + 7, y + 46, 0xA8C5DF, false);
        int cx = width / 2, cy = height / 2;
        graphics.fill(cx - 10, cy - 9, cx - 7, cy + 9, 0xDDFFD275);
        graphics.fill(cx + 7, cy - 9, cx + 10, cy + 9, 0xDDFFD275);
    }

    private static String compactPower(long power) {
        if (power >= 1_000_000) return (power / 100_000) / 10.0 + "M";
        if (power >= 10_000) return (power / 100) / 10.0 + "k";
        return Long.toString(power);
    }

    private static void bar(GuiGraphics graphics, Minecraft minecraft, int x, int y, int width,
                            String label, double current, double max, int color) {
        int fill = max <= 0 ? 0 : (int) (width * Mth.clamp(current / max, 0, 1));
        graphics.fill(x, y, x + width, y + 13, 0xFF243140);
        graphics.fill(x, y, x + fill, y + 13, color);
        graphics.drawString(minecraft.font, label, x + 3, y + 2, 0xFFFFFF, true);
        String value = Math.round(current) + "/" + Math.round(max);
        graphics.drawString(minecraft.font, value, x + width - minecraft.font.width(value) - 3, y + 2, 0xFFFFFF, true);
    }
}
