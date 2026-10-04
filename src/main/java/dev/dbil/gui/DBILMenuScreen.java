package dev.dbil.gui;

import dev.dbil.character.CharacterData;
import dev.dbil.client.ClientControls;
import dev.dbil.client.ClientEvents;
import dev.dbil.client.ClientState;
import dev.dbil.config.ClientConfig;
import dev.dbil.network.Network;
import dev.dbil.server.Action;
import dev.dbil.stats.Stat;
import dev.dbil.technique.TechniqueDefinition;
import dev.dbil.technique.TechniqueService;
import dev.dbil.technique.Techniques;
import dev.dbil.training.ChallengeDefinition;
import dev.dbil.training.TrainingChallenges;
import dev.dbil.transformation.TransformationDefinition;
import dev.dbil.transformation.TransformationEligibility;
import dev.dbil.transformation.Transformations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Non-pausing RPG menu; snapshots drive availability and touch buttons send validated intents. */
public final class DBILMenuScreen extends Screen {
    private static final String[] TABS = {"character", "attributes", "actions", "techniques", "transformations", "training", "settings"};
    private static final int ACTIONS = 2, TECHNIQUES = 3, TRANSFORMATIONS = 4, TRAINING = 5, SETTINGS = 6;
    private static final int TECHNIQUE_ROWS = 4;
    private int tab, challengeIndex, actionPage, techniquePage, settingsPage;
    private Button flightButton, techniqueButton, holdButton;
    private final Map<ResourceLocation, Button> techniqueButtons = new LinkedHashMap<>();
    private final Map<ResourceLocation, Button> equipButtons = new LinkedHashMap<>();
    private final Map<ResourceLocation, Button> transformationButtons = new LinkedHashMap<>();
    private boolean touchTechniqueHeld;

    public DBILMenuScreen() { super(Component.translatable("screen.dbil.menu")); }
    public boolean isActionsTab() { return tab == ACTIONS; }

    @Override protected void init() {
        techniqueButtons.clear(); equipButtons.clear(); transformationButtons.clear();
        flightButton = techniqueButton = holdButton = null;
        int w = panelWidth(), x = panelX(), top = panelTop();
        if (!ClientState.data().compatibleSchema()) { done(x, top, w); return; }
        int page = tab / 4;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> switchTab(page == 0 ? 4 : 0)).bounds(x, top, 22, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> switchTab(page == 0 ? 4 : 0)).bounds(x + w - 22, top, 22, 18).build());
        int first = page * 4, count = Math.min(4, TABS.length - first), tabWidth = w / count;
        for (int i = 0; i < count; i++) {
            final int chosen = first + i;
            Button button = Button.builder(Component.translatable("screen.dbil.tab." + TABS[chosen]), b -> switchTab(chosen))
                    .bounds(x + i * tabWidth, top + 22, tabWidth - 3, 22).build();
            button.active = chosen != tab;
            addRenderableWidget(button);
        }
        int y = top + 52;
        if (tab == 0) buildCharacter(x, y, w);
        else if (tab == ACTIONS) buildActions(x, y, w);
        else if (tab == TECHNIQUES) buildTechniques(x, y, w);
        else if (tab == TRANSFORMATIONS) buildTransformations(x, y, w);
        else if (tab == TRAINING) buildTraining(x, y, w);
        else if (tab == SETTINGS) buildSettings(x, y, w);
        done(x, top, w);
        refreshAvailability();
    }

    private int panelWidth() { return Math.min(420, width - 24); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelTop() { return Math.max(4, (height - 250) / 2); }
    private void done(int x, int top, int w) {
        int doneX = tab == TRAINING ? x + w / 2 + 3 : x;
        int doneWidth = tab == TRAINING ? w / 2 - 3 : w;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(doneX, top + 212, doneWidth, 24).build());
    }
    private void switchTab(int selected) {
        releaseTouch();
        tab = selected; rebuildWidgets();
    }

    private void buildCharacter(int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.translatable("screen.dbil.edit_appearance"),
                b -> minecraft.setScreen(new CharacterCreationScreen(true))).bounds(x + w - 150, y, 150, 22).build());
    }

    private void buildActions(int x, int y, int w) {
        int col = (w - 10) / 3, step = height < 260 ? 25 : 27;
        addRenderableWidget(Button.builder(Component.translatable(actionPage == 0 ? "screen.dbil.actions_movement" : "screen.dbil.actions_combat"),
                b -> { actionPage = 1 - actionPage; releaseTouch(); rebuildWidgets(); }).bounds(x, y, w, 20).build());
        y += 24;
        if (actionPage == 0) {
            action(x, y, col, "light", Action.LIGHT);
            action(x + col + 5, y, col, "heavy", Action.HEAVY);
            action(x + 2 * (col + 5), y, col, "launcher", Action.LAUNCHER);
            action(x, y + step, col, "smash", Action.SMASH);
            touch(x + col + 5, y + step, col, "guard", ClientControls.touchGuarding, () -> ClientControls.touchGuarding = !ClientControls.touchGuarding);
            action(x + 2 * (col + 5), y + step, col, "vanish", Action.VANISH);
            techniqueButton = addRenderableWidget(Button.builder(selectedTechniqueName(), b -> Network.sendAction(Action.TECHNIQUE))
                    .bounds(x, y + step * 2, col, 22).build());
            holdButton = addRenderableWidget(Button.builder(holdLabel(), b -> {
                touchTechniqueHeld = !touchTechniqueHeld;
                Network.sendAction(touchTechniqueHeld ? Action.TECHNIQUE_HOLD : Action.TECHNIQUE_RELEASE);
                b.setMessage(holdLabel());
            }).bounds(x + col + 5, y + step * 2, col, 22).build());
            addRenderableWidget(Button.builder(Component.translatable("action.dbil.technique_next"), b -> ClientEvents.cycleTechnique())
                    .bounds(x + 2 * (col + 5), y + step * 2, col, 22).build());
            touch(x, y + step * 3, col, "charge", ClientControls.touchCharging, () -> ClientControls.touchCharging = !ClientControls.touchCharging);
            action(x + col + 5, y + step * 3, col, "target", Action.LOCK_ON);
            action(x + 2 * (col + 5), y + step * 3, col, "target_next", Action.TARGET_NEXT);
            action(x, y + step * 4, col, "dash", Action.DASH);
            addRenderableWidget(Button.builder(Component.translatable("action.dbil.stop"), b -> { releaseTouch(); ClientControls.stopAll(); rebuildWidgets(); })
                    .bounds(x + col + 5, y + step * 4, col * 2 + 5, 22).build());
        } else {
            flightButton = addRenderableWidget(Button.builder(flightLabel(), b -> Network.sendAction(Action.FLIGHT_TOGGLE)).bounds(x, y, col, 22).build());
            touch(x + col + 5, y, col, "fast_flight", ClientControls.touchFast, () -> ClientControls.touchFast = !ClientControls.touchFast);
            action(x + 2 * (col + 5), y, col, "dash", Action.DASH);
            touch(x, y + step, col, "forward", ClientControls.forward, () -> { ClientControls.forward = !ClientControls.forward; ClientControls.backward = false; });
            touch(x + col + 5, y + step, col, "backward", ClientControls.backward, () -> { ClientControls.backward = !ClientControls.backward; ClientControls.forward = false; });
            touch(x + 2 * (col + 5), y + step, col, "ascend", ClientControls.ascend, () -> { ClientControls.ascend = !ClientControls.ascend; ClientControls.descend = false; });
            touch(x, y + step * 2, col, "left", ClientControls.left, () -> { ClientControls.left = !ClientControls.left; ClientControls.right = false; });
            touch(x + col + 5, y + step * 2, col, "right", ClientControls.right, () -> { ClientControls.right = !ClientControls.right; ClientControls.left = false; });
            touch(x + 2 * (col + 5), y + step * 2, col, "descend", ClientControls.descend, () -> { ClientControls.descend = !ClientControls.descend; ClientControls.ascend = false; });
            action(x, y + step * 3, col, "dash_left", Action.DASH_LEFT);
            action(x + col + 5, y + step * 3, col, "dash_back", Action.DASH_BACK);
            action(x + 2 * (col + 5), y + step * 3, col, "dash_right", Action.DASH_RIGHT);
            addRenderableWidget(Button.builder(Component.translatable("action.dbil.stop"), b -> { releaseTouch(); ClientControls.stopAll(); rebuildWidgets(); })
                    .bounds(x, y + step * 4, w, 22).build());
        }
    }
    private void action(int x, int y, int w, String key, Action action) {
        addRenderableWidget(Button.builder(Component.translatable("action.dbil." + key), b -> Network.sendAction(action)).bounds(x, y, w, 22).build());
    }
    private void touch(int x, int y, int w, String key, boolean enabled, Runnable callback) {
        addRenderableWidget(Button.builder(toggleLabel("action.dbil." + key, enabled), b -> { callback.run(); rebuildWidgets(); }).bounds(x, y, w, 22).build());
    }
    private static Component toggleLabel(String key, boolean enabled) { return Component.literal(enabled ? "[+] " : "[ ] ").append(Component.translatable(key)); }
    private Component flightLabel() { return toggleLabel("action.dbil.flight", minecraft.player != null && ClientState.visual(minecraft.player.getId()).flying()); }
    private Component holdLabel() { return toggleLabel(touchTechniqueHeld ? "action.dbil.technique_release" : "action.dbil.technique_hold", touchTechniqueHeld); }
    private Component selectedTechniqueName() {
        TechniqueDefinition definition = Techniques.get(ClientState.data().selectedTechnique());
        return definition == null ? Component.translatable("action.dbil.technique") : definition.displayName();
    }
    private void releaseTouch() {
        ClientControls.clearTouchMovement();
        ClientControls.touchCharging = ClientControls.touchGuarding = false;
        if (touchTechniqueHeld) { Network.sendAction(Action.TECHNIQUE_RELEASE); touchTechniqueHeld = false; }
    }

    private List<TechniqueDefinition> techniques() { return new ArrayList<>(Techniques.values()); }

    private void buildTechniques(int x, int y, int w) {
        List<TechniqueDefinition> all = techniques();
        int pages = Math.max(1, (all.size() + TECHNIQUE_ROWS - 1) / TECHNIQUE_ROWS);
        techniquePage = Math.min(techniquePage, pages - 1);
        for (int i = 0; i < TECHNIQUE_ROWS; i++) {
            int index = techniquePage * TECHNIQUE_ROWS + i;
            if (index >= all.size()) break;
            TechniqueDefinition technique = all.get(index);
            int offset = i * 36;
            Button select = Button.builder(Component.translatable("screen.dbil.select"), b -> Network.sendSelectTechnique(technique.id()))
                    .bounds(x + w - 158, y + offset + 6, 76, 20).build();
            techniqueButtons.put(technique.id(), addRenderableWidget(select));
            Button equip = Button.builder(Component.translatable("screen.dbil.equip"), b -> {
                boolean equipped = ClientState.data().equippedTechniques().contains(technique.id());
                Network.sendEquipTechnique(technique.id(), !equipped);
            }).bounds(x + w - 78, y + offset + 6, 76, 20).build();
            equipButtons.put(technique.id(), addRenderableWidget(equip));
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("< " + (techniquePage + 1) + "/" + pages + " >"),
                    b -> { techniquePage = (techniquePage + 1) % pages; rebuildWidgets(); }).bounds(x, y + 146, 90, 18).build());
        }
    }
    private void buildTransformations(int x, int y, int w) {
        int row = 0;
        for (TransformationDefinition form : Transformations.values()) {
            int offset = row++ * 64;
            if (offset >= 128) break;
            Button activate = Button.builder(Component.translatable("screen.dbil.transform"), b -> Network.sendTransform(form.id()))
                    .bounds(x + w - 104, y + offset, 96, 23).build();
            transformationButtons.put(form.id(), addRenderableWidget(activate));
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.dbil.revert"), b -> Network.sendTransform(CharacterData.BASE_FORM))
                .bounds(x, y + 133, w, 23).build());
    }
    private void buildTraining(int x, int y, int w) {
        List<ChallengeDefinition> challenges = TrainingChallenges.progress(ClientState.data());
        if (challenges.isEmpty()) return;
        challengeIndex = Math.min(challengeIndex, challenges.size() - 1);
        addRenderableWidget(Button.builder(Component.translatable("action.dbil.spar"), b -> Network.sendAction(Action.SPAR_START)).bounds(x, y + 160, w / 2 - 3, 24).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { challengeIndex = (challengeIndex + challenges.size() - 1) % challenges.size(); rebuildWidgets(); }).bounds(x, y, 25, 23).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { challengeIndex = (challengeIndex + 1) % challenges.size(); rebuildWidgets(); }).bounds(x + w - 25, y, 25, 23).build());
    }

    private void buildSettings(int x, int y, int w) {
        int half = (w - 6) / 2, step = 24;
        List<Runnable> entries = new ArrayList<>();
        if (settingsPage == 0) {
            bool(x, y, half, "setting.dbil.hud", ClientConfig.hud);
            cycle(x + half + 6, y, half, Component.translatable("setting.dbil.hud_mode", Component.translatable("setting.dbil.hud_mode." + ClientConfig.hudMode.get().name().toLowerCase(java.util.Locale.ROOT))),
                    () -> ClientConfig.hudMode.set(ClientConfig.HudMode.values()[(ClientConfig.hudMode.get().ordinal() + 1) % ClientConfig.HudMode.values().length]));
            double[] scales = {0.75, 1.0, 1.25, 1.5};
            cycle(x, y + step, half, Component.translatable("setting.dbil.hud_scale", String.format(java.util.Locale.ROOT, "%.2f", ClientConfig.hudScale.get())),
                    () -> ClientConfig.hudScale.set(next(scales, ClientConfig.hudScale.get())));
            bool(x + half + 6, y + step, half, "setting.dbil.power_level", ClientConfig.powerLevel);
            bool(x, y + step * 2, half, "setting.dbil.camera", ClientConfig.lockOnCamera);
            bool(x + half + 6, y + step * 2, half, "setting.dbil.reticle", ClientConfig.targetReticle);
            bool(x, y + step * 3, half, "setting.dbil.hide_hearts", ClientConfig.hideVanillaHealth);
            bool(x + half + 6, y + step * 3, half, "setting.dbil.character_model", ClientConfig.dbilCharacterModel);
            bool(x, y + step * 4, half, "setting.dbil.special_camera", ClientConfig.specialCamera);
            bool(x + half + 6, y + step * 4, half, "setting.dbil.animation_extras", ClientConfig.animationExtras);
        } else {
            cycle(x, y, half, Component.translatable("setting.dbil.aura_quality", Component.translatable("setting.dbil.quality." + ClientConfig.auraQuality.get().name().toLowerCase(java.util.Locale.ROOT))),
                    () -> ClientConfig.auraQuality.set(ClientConfig.Quality.values()[(ClientConfig.auraQuality.get().ordinal() + 1) % ClientConfig.Quality.values().length]));
            cycle(x + half + 6, y, half, Component.translatable("setting.dbil.particle_density", Component.translatable("setting.dbil.density." + ClientConfig.particleDensity.get().name().toLowerCase(java.util.Locale.ROOT))),
                    () -> ClientConfig.particleDensity.set(ClientConfig.Density.values()[(ClientConfig.particleDensity.get().ordinal() + 1) % ClientConfig.Density.values().length]));
            bool(x, y + step, half, "setting.dbil.particles", ClientConfig.particles);
            double[] shakes = {0.0, 0.3, 0.6, 1.0};
            cycle(x + half + 6, y + step, half, Component.translatable("setting.dbil.screen_shake", Math.round(ClientConfig.screenShake.get() * 100)),
                    () -> ClientConfig.screenShake.set(next(shakes, ClientConfig.screenShake.get())));
            bool(x, y + step * 2, half, "setting.dbil.fov_effects", ClientConfig.fovEffects);
            bool(x + half + 6, y + step * 2, half, "setting.dbil.impact_effects", ClientConfig.impactEffects);
            bool(x, y + step * 3, half, "setting.dbil.speed_lines", ClientConfig.speedLines);
            bool(x + half + 6, y + step * 3, half, "setting.dbil.terrain_debris", ClientConfig.terrainDebris);
            int[] distances = {16, 32, 48, 64, 96};
            cycle(x, y + step * 4, half, Component.translatable("setting.dbil.effect_distance", ClientConfig.effectDistance.get()),
                    () -> ClientConfig.effectDistance.set(nextInt(distances, ClientConfig.effectDistance.get())));
            cycle(x + half + 6, y + step * 4, half, Component.translatable("setting.dbil.transformation_effects", Component.translatable("setting.dbil.transformation_effects." + ClientConfig.transformationEffects.get().name().toLowerCase(java.util.Locale.ROOT))),
                    () -> ClientConfig.transformationEffects.set(ClientConfig.TransformationEffects.values()[(ClientConfig.transformationEffects.get().ordinal() + 1) % 2]));
        }
        addRenderableWidget(Button.builder(Component.literal((settingsPage + 1) + "/2 >"), b -> { settingsPage = 1 - settingsPage; rebuildWidgets(); })
                .bounds(x, y + step * 5 + 4, 60, 20).build());
    }
    private void bool(int x, int y, int w, String key, ForgeConfigSpec.BooleanValue value) {
        addRenderableWidget(Button.builder(toggleLabel(key, value.get()), b -> {
            value.set(!value.get()); ClientConfig.SPEC.save(); rebuildWidgets();
        }).bounds(x, y, w, 22).build());
    }
    private void cycle(int x, int y, int w, Component label, Runnable change) {
        addRenderableWidget(Button.builder(label, b -> { change.run(); ClientConfig.SPEC.save(); rebuildWidgets(); }).bounds(x, y, w, 22).build());
    }
    private static double next(double[] values, double current) {
        for (int i = 0; i < values.length; i++) if (current < values[i] - 1.0e-6) return values[i];
        return values[0];
    }
    private static int nextInt(int[] values, int current) {
        for (int value : values) if (current < value) return value;
        return values[0];
    }

    private void refreshAvailability() {
        CharacterData data = ClientState.data();
        for (var entry : techniqueButtons.entrySet()) {
            TechniqueDefinition definition = Techniques.get(entry.getKey());
            boolean unlocked = data.unlockedTechniques().contains(entry.getKey()) && definition != null && definition.requirements().test(data);
            boolean equipped = data.equippedTechniques().contains(entry.getKey());
            boolean selected = entry.getKey().equals(data.selectedTechnique());
            entry.getValue().active = unlocked && equipped && !selected;
            entry.getValue().setMessage(Component.translatable(selected ? "screen.dbil.selected" : unlocked ? "screen.dbil.select" : "screen.dbil.locked"));
            Button equip = equipButtons.get(entry.getKey());
            if (equip != null) {
                equip.active = unlocked && (equipped ? data.equippedTechniques().size() > 1 : data.equippedTechniques().size() < CharacterData.MAX_EQUIPPED);
                equip.setMessage(Component.translatable(equipped ? "screen.dbil.unequip" : "screen.dbil.equip"));
            }
        }
        for (var entry : transformationButtons.entrySet()) {
            var visual = minecraft.player == null ? ClientState.VisualState.IDLE : ClientState.visual(minecraft.player.getId());
            boolean active = entry.getKey().equals(visual.transformation());
            entry.getValue().active = !active && visual.transformationTicks() == 0 && TransformationEligibility.check(data, entry.getKey()) == TransformationEligibility.Result.READY;
            entry.getValue().setMessage(Component.translatable(active ? "screen.dbil.active" : "screen.dbil.transform"));
        }
    }
    @Override public void tick() {
        if (flightButton != null) flightButton.setMessage(flightLabel());
        if (techniqueButton != null) techniqueButton.setMessage(selectedTechniqueName());
        refreshAvailability();
        if (ClientState.data().compatibleSchema() && !ClientState.data().created()) minecraft.setScreen(new CharacterCreationScreen());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int w = panelWidth(), x = panelX(), top = panelTop(), y = top + 57;
        graphics.fill(x - 8, top - 7, x + w + 8, Math.min(height, top + 242), 0xE50B1220);
        graphics.fill(x - 8, top - 7, x + w + 8, top - 6, 0xFF3FA8FF);
        graphics.drawCenteredString(font, title, width / 2, top + 4, 0x73D6FF);
        CharacterData data = ClientState.data();
        if (!data.compatibleSchema()) graphics.drawWordWrap(font, Component.translatable("screen.dbil.schema_protected"), x + 8, y, w - 16, 0xFFB0AD);
        else if (tab == 0) renderCharacter(graphics, data, x, y);
        else if (tab == 1) {
            int i = 0;
            for (Stat stat : Stat.values()) line(graphics, x + 8, y + i++ * 17,
                    Component.translatable("screen.dbil.stat", Component.translatable("stat.dbil." + stat.name().toLowerCase(java.util.Locale.ROOT)), Math.round(data.stat(stat))), 0xC9DEED);
        } else if (tab == ACTIONS) line(graphics, x + 8, top + 200, Component.translatable("screen.dbil.touch_hint"), 0x94ACBF);
        else if (tab == TECHNIQUES) renderTechniques(graphics, data, x, top + 52, w);
        else if (tab == TRANSFORMATIONS) renderTransformations(graphics, data, x, top + 52, w);
        else if (tab == TRAINING) renderTraining(graphics, data, x, top + 52, w);
        else if (tab == SETTINGS) graphics.drawWordWrap(font, Component.translatable("setting.dbil.mobile_hint"), x + 70, top + 52 + 124, w - 78, 0x94ACBF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private void renderCharacter(GuiGraphics graphics, CharacterData data, int x, int y) {
        line(graphics, x + 8, y, Component.literal(data.name()), 0xFFFFFF);
        line(graphics, x + 8, y + 16, Component.translatable("screen.dbil.race", Component.translatable("race.dbil." + data.raceId().getPath())), 0xC9DEED);
        line(graphics, x + 8, y + 32, Component.translatable("screen.dbil.origin", Component.translatable("origin.dbil." + data.originId().getPath())), 0xC9DEED);
        line(graphics, x + 8, y + 48, Component.translatable("screen.dbil.style", Component.translatable("style.dbil." + data.styleId())), 0xC9DEED);
        line(graphics, x + 8, y + 66, Component.translatable("hud.dbil.level", data.level()), 0xFFE08A);
        line(graphics, x + 8, y + 82, Component.translatable("screen.dbil.experience", data.experience()), 0xC9DEED);
        line(graphics, x + 8, y + 98, Component.translatable("screen.dbil.power", data.basePower(), data.currentPower()), 0xC9DEED);
        // The hint gets the space left above the Done button (top + 212); extra lines are dropped, never drawn over it.
        int hintY = y + 116, maxLines = Math.max(1, (panelTop() + 208 - hintY) / font.lineHeight);
        var lines = font.split(Component.translatable("screen.dbil.controls_hint"), panelWidth() - 16);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++)
            graphics.drawString(font, lines.get(i), x + 8, hintY + i * font.lineHeight, 0x94ACBF, false);
    }
    private void renderTechniques(GuiGraphics graphics, CharacterData data, int x, int y, int w) {
        List<TechniqueDefinition> all = techniques();
        for (int i = 0; i < TECHNIQUE_ROWS; i++) {
            int index = techniquePage * TECHNIQUE_ROWS + i;
            if (index >= all.size()) break;
            TechniqueDefinition technique = all.get(index);
            int offset = i * 36;
            boolean unlocked = data.unlockedTechniques().contains(technique.id());
            graphics.fill(x, y + offset, x + w, y + offset + 33, unlocked ? 0x552A3B4C : 0x33202A36);
            graphics.fill(x, y + offset, x + 2, y + offset + 33, 0xFF000000 | Techniques.profile(technique.id()).color());
            line(graphics, x + 8, y + offset + 4, technique.displayName(), unlocked ? 0xDDEEFF : 0x8090A0);
            int cost = (int) Math.round(TechniqueService.kiCost(technique, data));
            Component details = Component.translatable(Techniques.profile(technique.id()).chargeable() ? "screen.dbil.technique_details_charge" : "screen.dbil.technique_details",
                    cost, Math.round(technique.range()), TechniqueService.cooldown(technique) / 20.0);
            line(graphics, x + 8, y + offset + 19, details, 0xA9BECF);
        }
        line(graphics, x + 100, y + 150, Component.translatable("screen.dbil.equipped", data.equippedTechniques().size(), CharacterData.MAX_EQUIPPED), 0x94ACBF);
    }
    private void renderTransformations(GuiGraphics graphics, CharacterData data, int x, int y, int w) {
        int row = 0;
        for (TransformationDefinition form : Transformations.values()) {
            int offset = row++ * 64;
            if (offset >= 128) break;
            graphics.fill(x, y + offset, x + w, y + offset + 60, 0x552A3B4C);
            line(graphics, x + 8, y + offset + 5, Component.translatable(form.displayName()), 0xFFE08A);
            var reason = TransformationEligibility.check(data, form);
            line(graphics, x + 8, y + offset + 26, Component.translatable("transformation.dbil.status." + reason.name().toLowerCase(java.util.Locale.ROOT)), reason == TransformationEligibility.Result.READY ? 0x8BE0AE : 0xC0B2B2);
            line(graphics, x + 8, y + offset + 43, Component.translatable("screen.dbil.form_details", Math.round(form.activationKiCost()), Math.round(data.mastery().getOrDefault(form.id(), 0.0)), Math.round(form.mastery().maximum())), 0xA9BECF);
        }
    }
    private void renderTraining(GuiGraphics graphics, CharacterData data, int x, int y, int w) {
        List<ChallengeDefinition> challenges = TrainingChallenges.progress(data);
        if (challenges.isEmpty()) return;
        ChallengeDefinition challenge = challenges.get(Math.min(challengeIndex, challenges.size() - 1));
        graphics.drawCenteredString(font, challenge.displayName(), width / 2, y + 7, 0xFFE08A);
        boolean completed = TrainingChallenges.completed(data, challenge.id());
        line(graphics, x + 8, y + 31, Component.translatable(completed ? "screen.dbil.challenge_done" : TrainingChallenges.available(data, challenge) ? "screen.dbil.challenge_active" : "screen.dbil.challenge_locked", challengeIndex + 1, challenges.size()), completed ? 0x8BE0AE : 0xC9DEED);
        int index = 0;
        for (ChallengeDefinition.Objective objective : challenge.objectives()) {
            double value = Math.min(objective.target(), TrainingChallenges.counter(data, objective.key()));
            boolean seconds = objective.key().equals("flight_ticks");
            Component text = Component.translatable("screen.dbil.objective", objective.displayName(), Math.round(seconds ? value / 20 : value), Math.round(seconds ? objective.target() / 20 : objective.target()));
            line(graphics, x + 8, y + 52 + index++ * 19, text, value >= objective.target() ? 0x8BE0AE : 0xA9BECF);
        }
        TechniqueDefinition rewardTechnique = challenge.rewardTechnique() == null ? null : Techniques.get(challenge.rewardTechnique());
        Component reward = rewardTechnique != null ? rewardTechnique.displayName()
                : Component.translatable(challenge.unlockRaceTransformation() ? "screen.dbil.reward_form" : "screen.dbil.reward_xp");
        line(graphics, x + 8, y + 134, Component.translatable("screen.dbil.reward", challenge.rewardXp(), reward), 0xFFE08A);
        line(graphics, x + 8, y + 147, Component.translatable("screen.dbil.reward_automatic"), 0x94ACBF);
    }
    private void line(GuiGraphics graphics, int x, int y, Component text, int color) { graphics.drawString(font, text, x, y, color, false); }
    @Override public void removed() { releaseTouch(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.MENU.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
