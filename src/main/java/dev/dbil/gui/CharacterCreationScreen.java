package dev.dbil.gui;

import dev.dbil.DBIL;
import dev.dbil.client.ClientState;
import dev.dbil.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Creation requests contain choices only; the server initializes and validates all character data. */
public final class CharacterCreationScreen extends Screen {
    private static final String[] STYLES = {"balanced", "brawler", "speed", "ki_specialist", "defensive"};
    private EditBox name;
    private Button raceButton, originButton, styleButton, createButton;
    private boolean saiyan, survivor, waiting;
    private int style, waitTicks;
    private long requestSerial;
    private String retainedName = "";
    private Component status = Component.empty();

    public CharacterCreationScreen() { super(Component.translatable("screen.dbil.create")); }

    @Override protected void init() {
        int w = Math.min(340, width - 24), x = (width - w) / 2;
        int top = Math.max(18, (height - 226) / 2);
        name = new EditBox(font, x, top + 35, w, 22, Component.translatable("screen.dbil.name"));
        name.setMaxLength(24);
        name.setHint(Component.translatable("screen.dbil.name"));
        if (retainedName.isEmpty() && minecraft.player != null) retainedName = minecraft.player.getGameProfile().getName();
        name.setValue(retainedName);
        name.setResponder(value -> retainedName = value);
        addRenderableWidget(name);
        raceButton = addRenderableWidget(Button.builder(raceLabel(), button -> {
            saiyan = !saiyan;
            if (!saiyan) survivor = false;
            refreshLabels();
        }).bounds(x, top + 70, w, 25).build());
        originButton = addRenderableWidget(Button.builder(originLabel(), button -> {
            if (saiyan) survivor = !survivor;
            refreshLabels();
        }).bounds(x, top + 100, w, 25).build());
        styleButton = addRenderableWidget(Button.builder(styleLabel(), button -> {
            style = (style + 1) % STYLES.length;
            refreshLabels();
        }).bounds(x, top + 130, w, 25).build());
        createButton = addRenderableWidget(Button.builder(Component.translatable("screen.dbil.begin"), button -> submit())
                .bounds(x, top + 169, w, 27).build());
        setInitialFocus(name);
        refreshLabels();
    }

    private void refreshLabels() {
        raceButton.setMessage(raceLabel()); originButton.setMessage(originLabel()); styleButton.setMessage(styleLabel());
        raceButton.active = styleButton.active = name.active = !waiting;
        originButton.active = !waiting && saiyan;
        createButton.active = !waiting;
    }
    private Component raceLabel() { return Component.translatable("screen.dbil.race", Component.translatable("race.dbil." + (saiyan ? "saiyan" : "human"))); }
    private Component originLabel() { return Component.translatable("screen.dbil.origin", Component.translatable("origin.dbil." + (survivor ? "survivor" : "earth_warrior"))); }
    private Component styleLabel() { return Component.translatable("screen.dbil.style", Component.translatable("style.dbil." + STYLES[style])); }

    private void submit() {
        String chosenName = name.getValue().trim();
        if (chosenName.isBlank()) { status = Component.translatable("screen.dbil.invalid_name"); return; }
        Network.sendCreate(chosenName, DBIL.id(saiyan ? "saiyan" : "human"),
                DBIL.id(survivor ? "survivor" : "earth_warrior"), STYLES[style]);
        waiting = true; waitTicks = 0; requestSerial = ClientState.snapshotSerial();
        status = Component.translatable("screen.dbil.waiting");
        refreshLabels();
    }

    @Override public void tick() {
        name.tick();
        if (ClientState.data().created()) { minecraft.setScreen(null); return; }
        if (waiting && ClientState.snapshotSerial() > requestSerial) {
            waiting = false;
            status = Component.translatable("message.dbil.creation_rejected");
            refreshLabels();
        }
        if (waiting && ++waitTicks >= 200) {
            waiting = false;
            status = Component.translatable("screen.dbil.retry");
            refreshLabels();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int w = Math.min(340, width - 24), x = (width - w) / 2, top = Math.max(18, (height - 226) / 2);
        graphics.fill(x - 9, top - 12, x + w + 9, top + 222, 0xE5141F2B);
        graphics.drawCenteredString(font, title, width / 2, top, 0x73D6FF);
        graphics.drawCenteredString(font, Component.translatable("screen.dbil.name"), width / 2, top + 23, 0xC2D0DE);
        graphics.drawCenteredString(font, Component.translatable("screen.dbil.choose_hint"), width / 2, top + 157, 0xA4B7C9);
        graphics.drawWordWrap(font, status, x + 3, top + 203, w - 6, waiting ? 0xFFDC8A : 0xFFB0AD);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
