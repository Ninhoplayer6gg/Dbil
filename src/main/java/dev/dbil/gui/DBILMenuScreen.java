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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Non-pausing RPG menu; snapshots drive availability and touch buttons send validated intents. */
public final class DBILMenuScreen extends Screen {
    private static final String[] TABS = {"character", "attributes", "actions", "techniques", "transformations", "training", "settings"};
    private static final int ACTIONS = 2, TECHNIQUES = 3, TRANSFORMATIONS = 4, TRAINING = 5, SETTINGS = 6;
    private int tab, challengeIndex;
    private Button flightButton, techniqueButton;
    private final Map<ResourceLocation, Button> techniqueButtons = new LinkedHashMap<>();
    private final Map<ResourceLocation, Button> transformationButtons = new LinkedHashMap<>();

    public DBILMenuScreen() { super(Component.translatable("screen.dbil.menu")); }
    public boolean isActionsTab() { return tab == ACTIONS; }

    @Override protected void init() {
        techniqueButtons.clear(); transformationButtons.clear();
        flightButton = techniqueButton = null;
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
        if (tab == ACTIONS) buildActions(x, y, w);
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
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(doneX, top + 208, doneWidth, 25).build());
    }
    private void switchTab(int selected) {
        ClientControls.clearTouchMovement(); ClientControls.touchCharging = ClientControls.touchGuarding = false;
        tab = selected; rebuildWidgets();
    }

    private void buildActions(int x, int y, int w) {
        int col = (w - 10) / 3, step = height < 260 ? 26 : 28;
        action(x, y, col, "light", Action.LIGHT);
        action(x + col + 5, y, col, "heavy", Action.HEAVY);
        touch(x + 2 * (col + 5), y, col, "guard", ClientControls.touchGuarding, () -> ClientControls.touchGuarding = !ClientControls.touchGuarding);
        touch(x, y + step, col, "charge", ClientControls.touchCharging, () -> ClientControls.touchCharging = !ClientControls.touchCharging);
        flightButton = addRenderableWidget(Button.builder(flightLabel(), b -> Network.sendAction(Action.FLIGHT_TOGGLE)).bounds(x + col + 5, y + step, col, 24).build());
        action(x + 2 * (col + 5), y + step, col, "dash", Action.DASH);
        techniqueButton = addRenderableWidget(Button.builder(selectedTechniqueName(), b -> Network.sendAction(Action.TECHNIQUE)).bounds(x, y + step * 2, col, 24).build());
        action(x + col + 5, y + step * 2, col, "target", Action.LOCK_ON);
        addRenderableWidget(Button.builder(Component.translatable("action.dbil.stop"), b -> { ClientControls.stopAll(); rebuildWidgets(); }).bounds(x + 2 * (col + 5), y + step * 2, col, 24).build());
        touch(x, y + step * 3, col, "forward", ClientControls.forward, () -> { ClientControls.forward = !ClientControls.forward; ClientControls.backward = false; });
        touch(x + col + 5, y + step * 3, col, "backward", ClientControls.backward, () -> { ClientControls.backward = !ClientControls.backward; ClientControls.forward = false; });
        touch(x + 2 * (col + 5), y + step * 3, col, "ascend", ClientControls.ascend, () -> { ClientControls.ascend = !ClientControls.ascend; ClientControls.descend = false; });
        touch(x, y + step * 4, col, "left", ClientControls.left, () -> { ClientControls.left = !ClientControls.left; ClientControls.right = false; });
        touch(x + col + 5, y + step * 4, col, "right", ClientControls.right, () -> { ClientControls.right = !ClientControls.right; ClientControls.left = false; });
        touch(x + 2 * (col + 5), y + step * 4, col, "descend", ClientControls.descend, () -> { ClientControls.descend = !ClientControls.descend; ClientControls.ascend = false; });
    }
    private void action(int x, int y, int w, String key, Action action) {
        addRenderableWidget(Button.builder(Component.translatable("action.dbil." + key), b -> Network.sendAction(action)).bounds(x, y, w, 24).build());
    }
    private void touch(int x, int y, int w, String key, boolean enabled, Runnable callback) {
        addRenderableWidget(Button.builder(toggleLabel("action.dbil." + key, enabled), b -> { callback.run(); rebuildWidgets(); }).bounds(x, y, w, 24).build());
    }
    private static Component toggleLabel(String key, boolean enabled) { return Component.literal(enabled ? "[+] " : "[ ] ").append(Component.translatable(key)); }
    private Component flightLabel() { return toggleLabel("action.dbil.flight", minecraft.player != null && ClientState.visual(minecraft.player.getId()).flying()); }
    private Component selectedTechniqueName() {
        TechniqueDefinition definition = Techniques.get(ClientState.data().selectedTechnique());
        return definition == null ? Component.translatable("action.dbil.technique") : definition.displayName();
    }

    private void buildTechniques(int x, int y, int w) {
        int row = 0;
        for (TechniqueDefinition technique : Techniques.values()) {
            int offset = row++ * 43;
            if (offset >= 129) break;
            Button select = Button.builder(Component.translatable("screen.dbil.select"), b -> Network.sendSelectTechnique(technique.id()))
                    .bounds(x + w - 104, y + offset + 1, 96, 23).build();
            techniqueButtons.put(technique.id(), addRenderableWidget(select));
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
        addRenderableWidget(Button.builder(Component.translatable("action.dbil.spar"), b -> Network.sendAction(Action.SPAR_START)).bounds(x, y + 156, w / 2 - 3, 25).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { challengeIndex = (challengeIndex + challenges.size() - 1) % challenges.size(); rebuildWidgets(); }).bounds(x, y, 25, 23).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { challengeIndex = (challengeIndex + 1) % challenges.size(); rebuildWidgets(); }).bounds(x + w - 25, y, 25, 23).build());
    }
    private void buildSettings(int x, int y, int w) {
        addRenderableWidget(Button.builder(toggleLabel("setting.dbil.hud", ClientConfig.hud.get()), b -> {
            ClientConfig.hud.set(!ClientConfig.hud.get()); ClientConfig.SPEC.save(); rebuildWidgets();
        }).bounds(x, y, w, 25).build());
        addRenderableWidget(Button.builder(toggleLabel("setting.dbil.particles", ClientConfig.particles.get()), b -> {
            ClientConfig.particles.set(!ClientConfig.particles.get()); ClientConfig.SPEC.save(); rebuildWidgets();
        }).bounds(x, y + 30, w, 25).build());
        addRenderableWidget(Button.builder(toggleLabel("setting.dbil.camera", ClientConfig.lockOnCamera.get()), b -> {
            ClientConfig.lockOnCamera.set(!ClientConfig.lockOnCamera.get()); ClientConfig.SPEC.save(); rebuildWidgets();
        }).bounds(x, y + 60, w, 25).build());
        addRenderableWidget(Button.builder(toggleLabel("setting.dbil.power_level", ClientConfig.powerLevel.get()), b -> {
            ClientConfig.powerLevel.set(!ClientConfig.powerLevel.get()); ClientConfig.SPEC.save(); rebuildWidgets();
        }).bounds(x, y + 90, w, 25).build());
    }

    private void refreshAvailability() {
        CharacterData data = ClientState.data();
        for (var entry : techniqueButtons.entrySet()) {
            TechniqueDefinition definition = Techniques.get(entry.getKey());
            boolean unlocked = data.unlockedTechniques().contains(entry.getKey()) && definition != null && definition.requirements().test(data);
            boolean selected = entry.getKey().equals(data.selectedTechnique());
            entry.getValue().active = unlocked && !selected;
            entry.getValue().setMessage(Component.translatable(selected ? "screen.dbil.selected" : unlocked ? "screen.dbil.select" : "screen.dbil.locked"));
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
        graphics.fill(x - 8, top - 7, x + w + 8, Math.min(height, top + 240), 0xE5141F2B);
        graphics.drawCenteredString(font, title, width / 2, top + 2, 0x73D6FF);
        CharacterData data = ClientState.data();
        if (!data.compatibleSchema()) graphics.drawWordWrap(font, Component.translatable("screen.dbil.schema_protected"), x + 8, y, w - 16, 0xFFB0AD);
        else if (tab == 0) renderCharacter(graphics, data, x, y);
        else if (tab == 1) {
            int i = 0;
            for (Stat stat : Stat.values()) line(graphics, x + 8, y + i++ * 17,
                    Component.translatable("screen.dbil.stat", Component.translatable("stat.dbil." + stat.name().toLowerCase(java.util.Locale.ROOT)), Math.round(data.stat(stat))), 0xC9DEED);
        } else if (tab == ACTIONS) line(graphics, x + 8, top + 190, Component.translatable("screen.dbil.touch_hint"), 0x94ACBF);
        else if (tab == TECHNIQUES) renderTechniques(graphics, data, x, top + 52, w);
        else if (tab == TRANSFORMATIONS) renderTransformations(graphics, data, x, top + 52, w);
        else if (tab == TRAINING) renderTraining(graphics, data, x, top + 52, w);
        else if (tab == SETTINGS) graphics.drawWordWrap(font, Component.translatable("setting.dbil.mobile_hint"), x + 8, y + 126, w - 16, 0x94ACBF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private void renderCharacter(GuiGraphics graphics, CharacterData data, int x, int y) {
        line(graphics, x + 8, y, Component.literal(data.name()), 0xFFFFFF);
        line(graphics, x + 8, y + 18, Component.translatable("screen.dbil.race", Component.translatable("race.dbil." + data.raceId().getPath())), 0xC9DEED);
        line(graphics, x + 8, y + 36, Component.translatable("screen.dbil.origin", Component.translatable("origin.dbil." + data.originId().getPath())), 0xC9DEED);
        line(graphics, x + 8, y + 54, Component.translatable("screen.dbil.style", Component.translatable("style.dbil." + data.styleId())), 0xC9DEED);
        line(graphics, x + 8, y + 76, Component.translatable("hud.dbil.level", data.level()), 0xFFE08A);
        line(graphics, x + 8, y + 94, Component.translatable("screen.dbil.experience", data.experience()), 0xC9DEED);
        line(graphics, x + 8, y + 112, Component.translatable("screen.dbil.power", data.basePower(), data.currentPower()), 0xC9DEED);
        line(graphics, x + 8, y + 136, Component.translatable("screen.dbil.controls_hint"), 0x94ACBF);
    }
    private void renderTechniques(GuiGraphics graphics, CharacterData data, int x, int y, int w) {
        int row = 0;
        for (TechniqueDefinition technique : Techniques.values()) {
            int offset = row++ * 43;
            if (offset >= 129) break;
            graphics.fill(x, y + offset, x + w, y + offset + 40, 0x552A3B4C);
            line(graphics, x + 8, y + offset + 5, technique.displayName(), 0xDDEEFF);
            line(graphics, x + 8, y + offset + 28, Component.translatable("screen.dbil.technique_details", Math.round(technique.kiCost()), Math.round(technique.range()), technique.cooldown() / 20.0), 0xA9BECF);
        }
        line(graphics, x + 8, y + 134, Component.translatable("screen.dbil.learn_hint"), 0x94ACBF);
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
    @Override public void removed() {
        ClientControls.clearTouchMovement(); ClientControls.touchCharging = ClientControls.touchGuarding = false;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.MENU.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
