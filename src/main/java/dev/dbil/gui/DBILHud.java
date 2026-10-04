package dev.dbil.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.dbil.DBIL;
import dev.dbil.character.CharacterData;
import dev.dbil.client.ClientEvents;
import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.race.Races;
import dev.dbil.technique.TechniqueDefinition;
import dev.dbil.technique.TechniqueProfile;
import dev.dbil.technique.TechniqueService;
import dev.dbil.technique.Techniques;
import dev.dbil.transformation.TransformationDefinition;
import dev.dbil.transformation.Transformations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * DBIL 0.3 HUD. Slanted energetic bars (HP with damage trail, flowing Ki, segmented Stamina), an emblem that
 * takes the aura color, and context panels that only appear when they matter: technique, transformation,
 * target, technique charge, combo and chase prompts. AUTO mode compacts the HUD outside combat.
 * Everything is drawn from server snapshots; nothing here changes gameplay.
 */
public final class DBILHud {
    private static final ResourceLocation ICONS = DBIL.id("textures/gui/hud_icons.png");
    private static final int HP_TOP = 0xFFFF6B6B, HP_BOTTOM = 0xFFB3262F, HP_TRAIL = 0xFFFFE3C2;
    private static final int KI_TOP = 0xFF7FE3FF, KI_BOTTOM = 0xFF1F7FD6;
    private static final int STA_ON = 0xFF8EF29A, STA_OFF = 0x5531463A;
    private static final int PANEL = 0xB00B1220, PANEL_EDGE = 0xFF2C4A6E, TEXT = 0xFFEAF4FF, MUTED = 0xFF9FB4C8;
    private static float shownHp = -1, trailHp = -1, shownKi = -1, expand = 1;
    private static long trailHold;
    private static ResourceLocation lastTechnique, lastForm;
    private static long techniqueChanged = -1000, formChanged = -1000;
    private static long lastNanos;

    private DBILHud() {}

    public static void render(ForgeGui forgeGui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.hud.get() || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || !ClientState.received() || !ClientState.data().created()) return;
        CharacterData data = ClientState.data();
        ClientState.VisualState visual = ClientState.visual(minecraft.player.getId());
        long nowNanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Math.min(0.1F, (nowNanos - lastNanos) / 1.0e9F);
        lastNanos = nowNanos;
        long tick = minecraft.level.getGameTime();
        float time = tick + partialTick;
        boolean combat = visual.inCombat() || visual.targetId() >= 0 || visual.charging() || visual.chargingTechnique()
                || visual.transforming() || visual.guarding() || HudState.combo() > 0;
        float expandTarget = switch (ClientConfig.hudMode.get()) {
            case FULL -> 1;
            case COMPACT -> 0;
            case AUTO -> combat ? 1 : 0;
        };
        expand += (expandTarget - expand) * Math.min(1, dt * 6);
        if (!data.selectedTechnique().equals(lastTechnique)) {
            if (lastTechnique != null) techniqueChanged = tick;
            lastTechnique = data.selectedTechnique();
        }
        if (!visual.transformation().equals(lastForm)) {
            if (lastForm != null && visual.transformed()) formChanged = tick;
            lastForm = visual.transformation();
        }
        float auto = Mth.clamp(Math.min(width / 380F, height / 230F), 0.62F, 1.0F);
        float scale = (float) (ClientConfig.hudScale.get() * auto);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        int sw = (int) (width / scale), sh = (int) (height / scale);
        int panelW = 196;
        int x = 6 + (int) ((sw - panelW - 12) * ClientConfig.hudX.get());
        int y = 6 + (int) ((sh - 90) * ClientConfig.hudY.get());
        renderStatus(g, minecraft, data, visual, x, y, time, dt);
        renderTechnique(g, minecraft, data, visual, x, y + 44 + (int) (8 * expand), tick, time);
        g.pose().popPose();
        renderTarget(g, minecraft, visual, width, height, scale);
        renderCenter(g, minecraft, data, visual, width, height, time, tick, scale);
        g.setColor(1, 1, 1, 1);
    }

    // ------------------------------------------------------------------------------------------------ status

    private static void renderStatus(GuiGraphics g, Minecraft mc, CharacterData data, ClientState.VisualState visual,
                                     int x, int y, float time, float dt) {
        Font font = mc.font;
        float hp = mc.player.getHealth(), maxHp = Math.max(1, mc.player.getMaxHealth());
        float hpFraction = Mth.clamp(hp / maxHp, 0, 1);
        if (shownHp < 0) shownHp = trailHp = hpFraction;
        if (hpFraction < shownHp) trailHold = (long) time + 12;
        shownHp += (hpFraction - shownHp) * Math.min(1, dt * 14);
        if (hpFraction > trailHp || time > trailHold) trailHp += (shownHp - trailHp) * Math.min(1, dt * 3);
        float kiFraction = (float) Mth.clamp(data.ki() / Math.max(1, data.maxKi()), 0, 1);
        if (shownKi < 0) shownKi = kiFraction;
        shownKi += (kiFraction - shownKi) * Math.min(1, dt * 10);
        float staFraction = (float) Mth.clamp(data.stamina() / Math.max(1, data.maxStamina()), 0, 1);
        int aura = auraColor(visual);
        float e = expand;
        int barW = (int) (112 + 54 * e);
        int hpH = Math.round(5 + 4 * e), kiH = Math.round(4 + 3 * e), staH = Math.round(3 + 1 * e);
        // Backing plate keeps the HUD readable over bright terrain.
        int plateW = barW + 46;
        slant(g, x, y, plateW, 30 + Math.round(10 * e), 8, 1, PANEL, PANEL, PANEL);
        emblem(g, x + 1, y + 2, aura, visual, time);
        int bx = x + 30;
        if (e > 0.25F) {
            int alpha = (int) (255 * Mth.clamp((e - 0.25F) / 0.75F, 0, 1));
            String name = font.plainSubstrByWidth(data.name(), 80);
            g.drawString(font, name, bx + 6, y + 2, (alpha << 24) | (TEXT & 0xFFFFFF), true);
            String level = Component.translatable("hud.dbil.level", data.level()).getString();
            int levelX = bx + barW - font.width(level) + 4;
            g.drawString(font, level, levelX, y + 2, (alpha << 24) | 0xFFE08A, true);
            if (ClientConfig.powerLevel.get()) {
                String power = "PL " + compactPower(visual.power() > 0 ? visual.power() : data.currentPower());
                g.drawString(font, power, levelX - font.width(power) - 8, y + 2, (alpha << 24) | 0xB8D8FF, true);
            }
        }
        int top = y + 2 + Math.round(10 * e);
        boolean lowHp = hpFraction < 0.25F && Mth.sin(time * 0.5F) > 0;
        slantBar(g, bx + 6, top, barW, hpH, 4, shownHp, trailHp, lowHp ? 0xFFFFA0A0 : HP_TOP, HP_BOTTOM, HP_TRAIL);
        if (e > 0.6F) {
            String value = Math.round(hp) + "/" + Math.round(maxHp);
            g.drawString(font, value, bx + 6 + barW - font.width(value) - 2, top + hpH - 8, 0xFFFFFFFF, true);
        }
        int kiTop = top + hpH + 2;
        boolean charging = visual.charging();
        if (charging) {
            int glow = (int) (120 + 100 * Mth.sin(time * 0.6F));
            slant(g, bx + 1, kiTop - 1, barW + 2, kiH + 2, 4, 1, (glow << 24) | (aura & 0xFFFFFF), (glow << 24) | (aura & 0xFFFFFF), 0);
        }
        slantBar(g, bx + 2, kiTop, barW, kiH, 4, shownKi, shownKi, KI_TOP, KI_BOTTOM, 0);
        kiShimmer(g, bx + 2, kiTop, barW, kiH, 4, shownKi, time, charging ? 2.2F : 1);
        if (charging) sparkles(g, bx + 2, kiTop, (int) (barW * shownKi), time, aura);
        int staTop = kiTop + kiH + 2;
        segments(g, bx - 2, staTop, barW - 6, staH, staFraction, visual.guarding() ? 0xFFBFE6FF : STA_ON);
        int iconX = bx - 2, iconY = staTop + staH + 3;
        if (e > 0.3F || visual.flying() || visual.guarding() || visual.transformed()) {
            iconY = statusIcons(g, mc, data, visual, iconX, iconY, time);
        }
    }

    private static int statusIcons(GuiGraphics g, Minecraft mc, CharacterData data, ClientState.VisualState visual,
                                   int x, int y, float time) {
        Font font = mc.font;
        int cursor = x;
        if (visual.flying()) {
            icon(g, cursor, y, visual.fastFlight() ? 2 : 1, 1, visual.fastFlight() ? 0xFFFFFFFF : 0xFFBFE6FF, 0.6F);
            cursor += 11;
        }
        if (visual.guarding()) {
            icon(g, cursor, y, 0, 1, 0xFFBFE6FF, 0.6F);
            cursor += 11;
        }
        if (visual.guardBreakTicks() > 0) {
            g.drawString(font, Component.translatable("hud.dbil.guard_broken_short"), cursor, y + 1, 0xFFFF8B88, true);
            cursor += 60;
        }
        if (visual.transformed()) {
            TransformationDefinition form = Transformations.get(visual.transformation()).orElse(null);
            int color = auraColor(visual);
            icon(g, cursor, y, 4, 1, color, 0.6F);
            cursor += 11;
            if (form != null) {
                String name = Component.translatable(form.displayName()).getString();
                g.drawString(font, name, cursor, y + 1, color | 0xFF000000, true);
                cursor += font.width(name) + 4;
                double mastery = data.mastery().getOrDefault(form.id(), 0.0);
                float fraction = (float) (mastery / form.mastery().maximum());
                int barX = cursor, barW = 34;
                g.fill(barX, y + 3, barX + barW, y + 6, 0x99000000);
                g.fill(barX, y + 3, barX + Math.round(barW * fraction), y + 6, color | 0xFF000000);
                cursor += barW + 3;
                double drain = form.mastery().drain(form.kiDrainPerTick(), mastery) * 20;
                String drainText = Component.translatable("hud.dbil.drain", String.format(java.util.Locale.ROOT, "%.1f", drain)).getString();
                g.drawString(font, drainText, cursor, y + 1, 0xFF9FD8FF, true);
            }
        }
        return y + 10;
    }

    // ------------------------------------------------------------------------------------------------ technique

    private static void renderTechnique(GuiGraphics g, Minecraft mc, CharacterData data, ClientState.VisualState visual,
                                        int x, int y, long tick, float time) {
        float recent = Mth.clamp(1 - (tick - techniqueChanged) / 40F, 0, 1);
        float show = Math.max(expand, recent);
        if (show < 0.05F) return;
        TechniqueDefinition definition = Techniques.get(data.selectedTechnique());
        if (definition == null) return;
        TechniqueProfile profile = Techniques.profile(definition.id());
        Font font = mc.font;
        int alpha = (int) (Mth.clamp(show, 0, 1) * 230);
        int w = 104 + (int) (recent * 18);
        slant(g, x + 2, y, w, 20, 6, 1, (alpha << 24) | 0x0B1220, (alpha << 24) | 0x0B1220, (alpha << 24) | (profile.color() & 0xFFFFFF));
        int cooldown = visual.cooldownTicks();
        icon(g, x + 8, y + 2, profile.pose().ordinal(), 0, cooldown > 0 ? 0xFF6F7F90 : profile.color() | 0xFF000000, show);
        if (cooldown > 0 && definition.cooldown() > 0) {
            float fraction = Mth.clamp(cooldown / (float) TechniqueService.cooldown(definition), 0, 1);
            g.fill(x + 8, y + 2 + Math.round(16 * (1 - fraction)), x + 24, y + 18, 0x88000000);
        }
        String name = font.plainSubstrByWidth(definition.displayName().getString(), w - 34);
        g.drawString(font, name, x + 28, y + 2, (alpha << 24) | 0xFFFFFF, true);
        // While preparing/holding, the server's next-use tick still includes the charge, so show the charge instead.
        boolean charging = visual.chargingTechnique() && definition.id().equals(visual.technique());
        String detail = charging ? (profile.chargeable() ? Component.translatable("hud.dbil.charge_percent",
                        Math.round(visual.techniqueChargeNow(0) * 100)) : Component.translatable("hud.dbil.preparing")).getString()
                : cooldown > 0 ? Component.translatable("hud.dbil.cooldown_short", (cooldown + 19) / 20).getString()
                : Component.translatable("hud.dbil.ki_cost", Math.round(TechniqueService.kiCost(definition, data))).getString();
        g.drawString(font, detail, x + 28, y + 11, (alpha << 24) | (charging ? 0xFFE7A0 : cooldown > 0 ? 0xFF9A88 : 0x9FD8FF), false);
        if (recent > 0 && expand < 0.5F) {
            String key = ClientEvents.TECHNIQUE.getTranslatedKeyMessage().getString();
            g.drawString(font, "[" + key + "]", x + w - font.width("[" + key + "]") + 2, y + 11, (alpha << 24) | 0xFFE08A, false);
        }
    }

    // ------------------------------------------------------------------------------------------------ target

    private static void renderTarget(GuiGraphics g, Minecraft mc, ClientState.VisualState visual, int width, int height, float scale) {
        if (visual.targetId() < 0 || !(mc.level.getEntity(visual.targetId()) instanceof LivingEntity target) || !target.isAlive()) return;
        Font font = mc.font;
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        int sw = (int) (width / scale);
        int w = 150, x = sw / 2 - w / 2, y = 4;
        slant(g, x, y, w, 26, 6, 1, PANEL, PANEL, 0xFFFFC870);
        icon(g, x + 6, y + 2, 5, 1, 0xFFFFC870, 1);
        String name = font.plainSubstrByWidth(target.getDisplayName().getString(), w - 70);
        g.drawString(font, name, x + 22, y + 3, 0xFFFFE0A0, true);
        float fraction = Mth.clamp(target.getHealth() / Math.max(1, target.getMaxHealth()), 0, 1);
        slantBar(g, x + 22, y + 13, w - 34, 5, 3, fraction, fraction, 0xFFFF8A6A, 0xFFB33A2E, 0);
        String power = visual.targetPower() >= 0 ? "PL " + compactPower(visual.targetPower()) : "PL ?";
        String distance = Math.round(target.distanceTo(mc.player)) + "m";
        g.drawString(font, power, x + w - font.width(power) - 6, y + 3, 0xFFFFE6B0, true);
        g.drawString(font, distance, x + 22, y + 19, 0xFFA8C5DF, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------------ center

    private static void renderCenter(GuiGraphics g, Minecraft mc, CharacterData data, ClientState.VisualState visual,
                                     int width, int height, float time, long tick, float scale) {
        Font font = mc.font;
        int cx = width / 2, cy = height / 2;
        if (visual.chargingTechnique()) {
            TechniqueProfile profile = Techniques.profile(visual.technique());
            float charge = profile.chargeable() ? visual.techniqueChargeNow(0) : 1 - visual.techniqueTicks() / 20F;
            chargeRing(g, cx, cy, 13, charge, profile.color(), time);
            if (profile.chargeable()) {
                String percent = Math.round(charge * 100) + "%";
                g.drawString(font, percent, cx - font.width(percent) / 2, cy + 17, charge >= 0.9F ? 0xFFFFFFFF : profile.color() | 0xFF000000, true);
            }
        }
        if (visual.transforming()) {
            float progress = visual.transformationProgress();
            int w = 90;
            g.fill(cx - w / 2 - 1, cy + 29, cx + w / 2 + 1, cy + 34, 0xAA000000);
            g.fill(cx - w / 2, cy + 30, cx - w / 2 + Math.round(w * progress), cy + 33, auraColor(visual) | 0xFF000000);
            Component text = Component.translatable("hud.dbil.transforming");
            g.drawString(font, text, cx - font.width(text) / 2, cy + 37, 0xFFFFE08A, true);
        }
        float banner = Mth.clamp(1 - (tick - formChanged) / 60F, 0, 1);
        if (banner > 0 && visual.transformed()) {
            TransformationDefinition form = Transformations.get(visual.transformation()).orElse(null);
            if (form != null) {
                String name = Component.translatable(form.displayName()).getString().toUpperCase(java.util.Locale.ROOT);
                float slide = Mth.clamp((tick - formChanged) / 6F, 0, 1);
                int alpha = (int) (255 * Math.min(1, banner * 3));
                int color = auraColor(visual);
                g.pose().pushPose();
                g.pose().translate(cx, height * 0.28F, 0);
                g.pose().scale(2.0F, 2.0F, 1);
                int textW = font.width(name);
                int offset = (int) ((1 - slide) * -60);
                g.fill(-textW / 2 - 14 + offset, -2, textW / 2 + 14 + offset, 11, (alpha / 2 << 24));
                g.fill(-textW / 2 - 14 + offset, 11, textW / 2 + 14 + offset, 12, (alpha << 24) | (color & 0xFFFFFF));
                g.drawString(font, name, -textW / 2 + offset, 1, (alpha << 24) | (color & 0xFFFFFF), true);
                g.pose().popPose();
            }
        }
        if (visual.guardBreakTicks() > 0 && Mth.sin(time * 0.8F) > -0.3F) {
            Component text = Component.translatable("hud.dbil.guard_broken_center");
            g.drawString(font, text, cx - font.width(text) / 2, cy - 26, 0xFFFF6B6B, true);
        }
        if (HudState.chasePrompt()) {
            String text = Component.translatable("hud.dbil.chase_prompt", ClientEvents.DASH.getTranslatedKeyMessage()).getString();
            g.drawString(font, text, cx - font.width(text) / 2, cy + 22, 0xFFFFFFFF, true);
        }
        int combo = HudState.combo();
        if (combo >= 2) {
            float fade = HudState.comboFade();
            int alpha = (int) (255 * Math.min(1, fade * 2));
            g.pose().pushPose();
            g.pose().translate(width - 20, height * 0.42F, 0);
            float pop = 1.6F + 0.25F * Math.max(0, fade - 0.85F) * 6;
            g.pose().scale(pop, pop, 1);
            String hits = combo + " HITS";
            g.drawString(font, hits, -font.width(hits), 0, (alpha << 24) | 0xFFD23C, true);
            g.pose().popPose();
        }
        if (visual.targetId() >= 0) {
            g.fill(cx - 9, cy - 1, cx - 6, cy + 1, 0xCCFFC870);
            g.fill(cx + 6, cy - 1, cx + 9, cy + 1, 0xCCFFC870);
        }
    }

    // ------------------------------------------------------------------------------------------------ primitives

    private static int auraColor(ClientState.VisualState visual) {
        if (Transformations.SUPER_SAIYAN.equals(visual.transformation())) return 0xFFFFC93A;
        if (Transformations.POTENTIAL_UNLEASHED.equals(visual.transformation())) return 0xFFEAF4FF;
        if (visual.transforming()) return 0xFFFFE08A;
        return 0xFF6FC4FF;
    }

    private static void emblem(GuiGraphics g, int x, int y, int color, ClientState.VisualState visual, float time) {
        float pulse = visual.charging() || visual.transforming() ? 0.75F + 0.25F * Mth.sin(time * 0.6F) : 1;
        g.setColor(((color >> 16) & 0xFF) / 255F * pulse, ((color >> 8) & 0xFF) / 255F * pulse, (color & 0xFF) / 255F * pulse, 1);
        g.blit(ICONS, x, y, 0, 32, 32, 32, 128, 64);
        g.setColor(1, 1, 1, 1);
        CharacterData data = ClientState.data();
        String mark = Races.SAIYAN.equals(data.raceId()) ? "S" : "H";
        if (visual.transformed()) mark = "★";
        Font font = Minecraft.getInstance().font;
        g.drawString(font, mark, x + 16 - font.width(mark) / 2, y + 12, 0xFFFFFFFF, true);
    }

    private static void icon(GuiGraphics g, int x, int y, int column, int row, int color, float alpha) {
        g.setColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, Mth.clamp(alpha, 0, 1));
        g.blit(ICONS, x, y, column * 16, row * 16, 16, 16, 128, 64);
        g.setColor(1, 1, 1, 1);
    }

    /** Parallelogram leaning right: each row shifts left as it goes down. */
    private static void slant(GuiGraphics g, int x, int y, int w, int h, int lean, float fraction, int top, int bottom, int edge) {
        for (int r = 0; r < h; r++) {
            int shift = h <= 1 ? 0 : Math.round((h - 1 - r) * lean / (float) (h - 1));
            int color = lerpColor(top, bottom, h <= 1 ? 0 : r / (float) (h - 1));
            g.fill(x + shift, y + r, x + shift + Math.round(w * fraction), y + r + 1, color);
            if (edge != 0 && (r == 0 || r == h - 1)) g.fill(x + shift, y + r, x + shift + w, y + r + 1, edge);
        }
    }

    private static void slantBar(GuiGraphics g, int x, int y, int w, int h, int lean, float fraction, float trail,
                                 int top, int bottom, int trailColor) {
        slant(g, x - 1, y - 1, w + 2, h + 2, lean, 1, 0xE0050A12, 0xE0050A12, 0);
        slant(g, x, y, w, h, lean, 1, 0xFF1A2433, 0xFF101722, 0);
        if (trailColor != 0 && trail > fraction) {
            for (int r = 0; r < h; r++) {
                int shift = h <= 1 ? 0 : Math.round((h - 1 - r) * lean / (float) (h - 1));
                g.fill(x + shift + Math.round(w * fraction), y + r, x + shift + Math.round(w * trail), y + r + 1, trailColor);
            }
        }
        slant(g, x, y, w, h, lean, Mth.clamp(fraction, 0, 1), top, bottom, 0);
        if (h >= 3) {
            int shift = Math.round(lean * (h - 1) / (float) Math.max(1, h - 1));
            g.fill(x + shift, y, x + shift + Math.round(w * fraction), y + 1, 0x55FFFFFF);
        }
    }

    private static void kiShimmer(GuiGraphics g, int x, int y, int w, int h, int lean, float fraction, float time, float speed) {
        int filled = Math.round(w * fraction);
        if (filled < 4) return;
        for (int band = 0; band < 2; band++) {
            float position = ((time * 2.2F * speed + band * w * 0.5F) % (w + 16)) - 8;
            for (int r = 0; r < h; r++) {
                int shift = h <= 1 ? 0 : Math.round((h - 1 - r) * lean / (float) (h - 1));
                int bx = (int) position + (h - 1 - r);
                int from = Math.max(0, bx - 3), to = Math.min(filled, bx + 3);
                if (to > from) g.fill(x + shift + from, y + r, x + shift + to, y + r + 1, 0x66FFFFFF);
            }
        }
    }

    private static void sparkles(GuiGraphics g, int x, int y, int width, float time, int color) {
        for (int i = 0; i < 6; i++) {
            float t = (time * 0.05F + i * 0.17F) % 1F;
            int sx = x + (int) ((i * 37 + (int) (time * 0.7F) * 13) % Math.max(1, width));
            int sy = y - (int) (t * 6);
            int alpha = (int) (200 * (1 - t));
            g.fill(sx, sy, sx + 1, sy + 1, (alpha << 24) | (color & 0xFFFFFF));
        }
    }

    private static void segments(GuiGraphics g, int x, int y, int w, int h, float fraction, int on) {
        int count = 10, gap = 2;
        int segW = (w - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            int sx = x + i * (segW + gap);
            float fill = Mth.clamp(fraction * count - i, 0, 1);
            g.fill(sx - 1, y - 1, sx + segW + 1, y + h + 1, 0xC0050A12);
            g.fill(sx, y, sx + segW, y + h, STA_OFF);
            if (fill > 0) g.fill(sx, y, sx + Math.round(segW * fill), y + h, on);
        }
    }

    private static void chargeRing(GuiGraphics g, int cx, int cy, int radius, float charge, int color, float time) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            float angle = -Mth.HALF_PI + i * Mth.TWO_PI / segments;
            int px = cx + Math.round(Mth.cos(angle) * radius), py = cy + Math.round(Mth.sin(angle) * radius);
            boolean lit = i < Math.round(charge * segments);
            boolean tier = i == Math.round(0.3F * segments) || i == Math.round(0.6F * segments) || i == Math.round(0.9F * segments);
            int c = lit ? (charge >= 0.9F && Mth.sin(time) > 0 ? 0xFFFFFFFF : color | 0xFF000000) : tier ? 0x99FFFFFF : 0x55000000;
            g.fill(px - 1, py - 1, px + 1, py + 1, c);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static String compactPower(long power) {
        if (power >= 1_000_000) return (power / 100_000) / 10.0 + "M";
        if (power >= 10_000) return (power / 100) / 10.0 + "k";
        return Long.toString(power);
    }

    public static void reset() {
        shownHp = trailHp = shownKi = -1;
        expand = 1;
        lastTechnique = lastForm = null;
    }
}
