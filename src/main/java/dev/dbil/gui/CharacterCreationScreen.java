package dev.dbil.gui;

import dev.dbil.DBIL;
import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import dev.dbil.client.ClientState;
import dev.dbil.client.render.character.CharacterPreview;
import dev.dbil.network.Network;
import dev.dbil.race.Races;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Character creation and appearance editing with a live 3D preview of the DBIL model.
 * Requests contain choices only; the server validates, clamps and stores everything.
 */
public final class CharacterCreationScreen extends Screen {
    private static final String[] STYLES = {"balanced", "brawler", "speed", "ki_specialist", "defensive"};
    private static final String[] PAGES = {"identity", "body", "face", "hair", "outfit"};
    private final boolean edit;
    private EditBox name;
    private Button createButton;
    private boolean saiyan, survivor, waiting, previewSuper;
    private int style, waitTicks, page;
    private float rotation = 25;
    private long requestSerial;
    private String retainedName = "";
    private Component status = Component.empty();
    private CharacterAppearance look;
    private final List<Row> rows = new ArrayList<>();

    private record Row(Component label, Supplier<Component> value, IntSupplier swatch, IntConsumer change) {}

    public CharacterCreationScreen() { this(false); }

    public CharacterCreationScreen(boolean edit) {
        super(Component.translatable(edit ? "screen.dbil.appearance" : "screen.dbil.create"));
        this.edit = edit;
        if (edit) {
            look = ClientState.data().appearance();
            saiyan = Races.SAIYAN.equals(ClientState.data().raceId());
            page = 1;
        } else {
            look = CharacterAppearance.HUMAN_DEFAULT;
        }
    }

    public boolean editing() { return edit; }

    private ResourceLocation race() { return saiyan ? Races.SAIYAN : Races.HUMAN; }

    private int previewWidth() { return Math.max(110, Math.min(180, width * 2 / 5)); }

    @Override protected void init() {
        rows.clear();
        int pw = previewWidth();
        int left = 8 + pw + 8, right = width - 8, w = right - left;
        int top = 30;
        // Page tabs.
        int first = edit ? 1 : 0;
        int count = PAGES.length - first;
        int tabW = Math.max(40, w / count);
        for (int i = 0; i < count; i++) {
            int chosen = first + i;
            Button tab = Button.builder(Component.translatable("screen.dbil.page." + PAGES[chosen]), b -> { page = chosen; rebuildWidgets(); })
                    .bounds(left + i * tabW, top, tabW - 2, 20).build();
            tab.active = chosen != page;
            addRenderableWidget(tab);
        }
        int y = top + 30;
        if (page == 0) {
            name = new EditBox(font, left, y, w, 20, Component.translatable("screen.dbil.name"));
            name.setMaxLength(24);
            name.setHint(Component.translatable("screen.dbil.name"));
            if (retainedName.isEmpty() && minecraft.player != null) retainedName = minecraft.player.getGameProfile().getName();
            name.setValue(retainedName);
            name.setResponder(value -> retainedName = value);
            addRenderableWidget(name);
            y += 26;
            row(Component.translatable("screen.dbil.race_label"), () -> Component.translatable("race.dbil." + (saiyan ? "saiyan" : "human")), null,
                    d -> {
                        CharacterAppearance previousDefault = CharacterAppearance.defaultFor(race());
                        saiyan = !saiyan;
                        if (!saiyan) survivor = false;
                        // Untouched defaults follow the race; customized looks are kept (the tail follows the race).
                        if (look.equals(previousDefault.forRace(previousDefault == CharacterAppearance.SAIYAN_DEFAULT ? Races.SAIYAN : Races.HUMAN))) {
                            look = CharacterAppearance.defaultFor(race());
                        } else if (saiyan) {
                            look = look.withAccessories(look.accessories() | AppearanceOptions.ACCESSORY_TAIL);
                        }
                    });
            if (saiyan) row(Component.translatable("screen.dbil.origin_label"), () -> Component.translatable("origin.dbil." + (survivor ? "survivor" : "earth_warrior")), null,
                    d -> survivor = !survivor);
            row(Component.translatable("screen.dbil.style_label"), () -> Component.translatable("style.dbil." + STYLES[style]), null,
                    d -> style = Math.floorMod(style + d, STYLES.length));
        } else if (page == 1) {
            row(Component.translatable("screen.dbil.body_type"), () -> Component.translatable("screen.dbil.body_type." + look.bodyType()), null,
                    d -> look = look.withBodyType(1 - look.bodyType()));
            row(Component.translatable("screen.dbil.skin_tone"), () -> Component.empty(), () -> look.skinTone(),
                    d -> look = look.withSkinTone(AppearanceOptions.cycle(AppearanceOptions.SKIN_TONES, look.skinTone(), d)));
            if (saiyan) row(Component.translatable("screen.dbil.tail"), () -> toggle(look.has(AppearanceOptions.ACCESSORY_TAIL)), null,
                    d -> look = look.toggle(AppearanceOptions.ACCESSORY_TAIL));
        } else if (page == 2) {
            row(Component.translatable("screen.dbil.eye_style"), () -> Component.translatable("screen.dbil.eye_style." + look.eyeStyle()), null,
                    d -> look = look.withEyeStyle(Math.floorMod(look.eyeStyle() + d, AppearanceOptions.EYE_STYLES)));
            row(Component.translatable("screen.dbil.eye_color"), () -> Component.empty(), () -> look.eyeColor(),
                    d -> look = look.withEyeColor(AppearanceOptions.cycle(AppearanceOptions.EYE_COLORS, look.eyeColor(), d)));
            row(Component.translatable("screen.dbil.eyebrows"), () -> Component.translatable("screen.dbil.eyebrows." + look.eyebrowStyle()), null,
                    d -> look = look.withEyebrowStyle(Math.floorMod(look.eyebrowStyle() + d, AppearanceOptions.EYEBROW_STYLES)));
            row(Component.translatable("screen.dbil.mouth"), () -> Component.translatable("screen.dbil.mouth." + look.mouthStyle()), null,
                    d -> look = look.withMouthStyle(Math.floorMod(look.mouthStyle() + d, AppearanceOptions.MOUTH_STYLES)));
        } else if (page == 3) {
            row(Component.translatable("screen.dbil.hairstyle"), () -> Component.translatable("hair.dbil." + look.hairstyle().getPath()), null,
                    d -> look = look.withHairstyle(AppearanceOptions.cycle(AppearanceOptions.HAIRSTYLES, look.hairstyle(), d)));
            row(Component.translatable("screen.dbil.hair_color"), () -> Component.empty(), () -> look.hairColor(),
                    d -> look = look.withHairColor(AppearanceOptions.cycle(AppearanceOptions.HAIR_COLORS, look.hairColor(), d)));
            row(Component.translatable("screen.dbil.headband"), () -> toggle(look.has(AppearanceOptions.ACCESSORY_HEADBAND)), null,
                    d -> look = look.toggle(AppearanceOptions.ACCESSORY_HEADBAND));
            if (saiyan) row(Component.translatable("screen.dbil.preview_super"), () -> toggle(previewSuper), null, d -> previewSuper = !previewSuper);
        } else {
            row(Component.translatable("screen.dbil.outfit"), () -> Component.translatable("outfit.dbil." + look.outfit().getPath()), null,
                    d -> look = look.withOutfit(AppearanceOptions.cycle(AppearanceOptions.OUTFITS, look.outfit(), d)));
            row(Component.translatable("screen.dbil.color_primary"), () -> Component.empty(), () -> look.outfitPrimary(),
                    d -> look = look.withOutfitPrimary(AppearanceOptions.cycle(AppearanceOptions.CLOTH_COLORS, look.outfitPrimary(), d)));
            row(Component.translatable("screen.dbil.color_secondary"), () -> Component.empty(), () -> look.outfitSecondary(),
                    d -> look = look.withOutfitSecondary(AppearanceOptions.cycle(AppearanceOptions.CLOTH_COLORS, look.outfitSecondary(), d)));
            row(Component.translatable("screen.dbil.color_accent"), () -> Component.empty(), () -> look.outfitAccent(),
                    d -> look = look.withOutfitAccent(AppearanceOptions.cycle(AppearanceOptions.CLOTH_COLORS, look.outfitAccent(), d)));
            row(Component.translatable("screen.dbil.wristbands"), () -> toggle(look.has(AppearanceOptions.ACCESSORY_WRISTBANDS)), null,
                    d -> look = look.toggle(AppearanceOptions.ACCESSORY_WRISTBANDS));
        }
        int rowY = y;
        for (Row row : rows) {
            final Row current = row;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { current.change().accept(-1); rebuildWidgets(); })
                    .bounds(right - 140, rowY, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { current.change().accept(1); rebuildWidgets(); })
                    .bounds(right - 22, rowY, 22, 20).build());
            rowY += 24;
        }
        // Preview rotation and the main action.
        int previewBottom = height - 40;
        addRenderableWidget(Button.builder(Component.literal("<<"), b -> rotation -= 30).bounds(8, previewBottom - 22, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">>"), b -> rotation += 30).bounds(8 + pw - 30, previewBottom - 22, 30, 20).build());
        int buttonW = edit ? (w - 4) / 2 : w;
        createButton = addRenderableWidget(Button.builder(Component.translatable(edit ? "screen.dbil.save" : "screen.dbil.begin"), b -> submit())
                .bounds(left, height - 30, buttonW, 22).build());
        if (edit) addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + buttonW + 4, height - 30, buttonW, 22).build());
        if (page == 0) setInitialFocus(name);
        refresh();
    }

    private void row(Component label, Supplier<Component> value, IntSupplier swatch, IntConsumer change) {
        rows.add(new Row(label, value, swatch, change));
    }

    private static Component toggle(boolean on) {
        return Component.translatable(on ? "screen.dbil.on" : "screen.dbil.off");
    }

    private void refresh() {
        look = look.forRace(race());
        CharacterPreview.set(look, race(), previewSuper && saiyan);
        if (createButton != null) createButton.active = !waiting;
    }

    private void submit() {
        if (edit) {
            Network.sendAppearance(look);
            onClose();
            return;
        }
        String chosenName = retainedName.trim();
        if (chosenName.isBlank()) { status = Component.translatable("screen.dbil.invalid_name"); page = 0; rebuildWidgets(); return; }
        Network.sendCreate(chosenName, race(), DBIL.id(survivor ? "survivor" : "earth_warrior"), STYLES[style], look);
        waiting = true; waitTicks = 0; requestSerial = ClientState.snapshotSerial();
        status = Component.translatable("screen.dbil.waiting");
        refresh();
    }

    @Override public void tick() {
        if (name != null) name.tick();
        refresh();
        if (!edit && ClientState.data().created()) { minecraft.setScreen(null); return; }
        if (waiting && ClientState.snapshotSerial() > requestSerial) {
            waiting = false;
            status = Component.translatable("message.dbil.creation_rejected");
            refresh();
        }
        if (waiting && ++waitTicks >= 200) {
            waiting = false;
            status = Component.translatable("screen.dbil.retry");
            refresh();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int pw = previewWidth();
        int left = 8 + pw + 8, right = width - 8;
        graphics.fill(4, 4, width - 4, height - 4, 0xD80B1220);
        graphics.fill(4, 4, width - 4, 24, 0xFF12223A);
        graphics.fill(4, 24, width - 4, 25, 0xFF3FA8FF);
        graphics.drawString(font, title, 12, 10, 0xFF73D6FF, true);
        // Preview panel with a soft radial-ish backdrop.
        int previewTop = 30, previewBottom = height - 64;
        graphics.fillGradient(8, previewTop, 8 + pw, previewBottom, 0xFF1A2A44, 0xFF0B1220);
        graphics.fill(8, previewBottom, 8 + pw, previewBottom + 1, 0xFF3FA8FF);
        if (minecraft.player != null) {
            int size = Math.max(24, Math.min(pw / 2, (previewBottom - previewTop) / 3));
            renderPreview(graphics, 8 + pw / 2, previewBottom - 8, size);
        }
        int y = 60 + (page == 0 ? 26 : 0);
        for (Row row : rows) {
            graphics.drawString(font, row.label(), left, y + 6, 0xFFC9DEED, false);
            int valueLeft = right - 116, valueRight = right - 24;
            if (row.swatch() != null) {
                int color = row.swatch().getAsInt();
                graphics.fill(valueLeft + 6, y + 3, valueRight - 6, y + 17, 0xFF000000);
                graphics.fill(valueLeft + 7, y + 4, valueRight - 7, y + 16, 0xFF000000 | color);
            } else {
                Component value = row.value().get();
                graphics.drawCenteredString(font, value, (valueLeft + valueRight) / 2, y + 6, 0xFFFFFFFF);
            }
            y += 24;
        }
        if (page == 0) graphics.drawString(font, Component.translatable("screen.dbil.choose_hint"), left, y + 6, 0xFF94ACBF, false);
        graphics.drawWordWrap(font, status, left, height - 50, right - left, waiting ? 0xFFFFDC8A : 0xFFFFB0AD);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphics graphics, int x, int y, int scale) {
        var player = minecraft.player;
        float bodyRot = player.yBodyRot, yRot = player.getYRot(), xRot = player.getXRot(), headO = player.yHeadRotO, head = player.yHeadRot;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-8F * Mth.DEG_TO_RAD);
        pose.mul(camera);
        player.yBodyRot = 180.0F + rotation;
        player.setYRot(180.0F + rotation);
        player.setXRot(0);
        player.yHeadRot = player.getYRot();
        player.yHeadRotO = player.getYRot();
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, pose, camera, player);
        player.yBodyRot = bodyRot;
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.yHeadRotO = headO;
        player.yHeadRot = head;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mouseX < 8 + previewWidth()) { rotation += (float) dragX * 2; return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public void removed() { CharacterPreview.clear(); }
    @Override public boolean shouldCloseOnEsc() { return edit; }
    @Override public boolean isPauseScreen() { return false; }
}
