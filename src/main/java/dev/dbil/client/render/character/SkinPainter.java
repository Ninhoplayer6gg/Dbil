package dev.dbil.client.render.character;

import dev.dbil.appearance.AppearanceOptions;
import dev.dbil.appearance.CharacterAppearance;
import net.minecraft.resources.ResourceLocation;

/**
 * Paints a 64x64 Steve-layout skin from a {@link CharacterAppearance}: skin, a stylized face, hair under the 3D
 * hair, and layered clothing (base layer + the vanilla 0.25px overlay layer for 3D thickness). Output is ARGB.
 * Pure CPU work, run once per appearance/variant/expression and cached by {@link CharacterTextures}.
 */
public final class SkinPainter {
    public static final int SIZE = 64;
    public static final int VARIANT_BASE = 0, VARIANT_SUPER_SAIYAN = 1, VARIANT_POTENTIAL = 2;
    public static final int EXPRESSION_NEUTRAL = 0, EXPRESSION_FOCUSED = 1, EXPRESSION_SHOUT = 2;
    public static final int SSJ_HAIR = 0xFFE15A;
    public static final int SSJ_IRIS = 0x37D6C4;
    public static final int SSJ_BROW = 0xE6B42C;

    /** Box UV layout: texOffs (u, v) and dimensions (w, h, d). */
    private record Box(int u, int v, int w, int h, int d) {
        int frontX() { return u + d; }
        int sideY() { return v + d; }
    }

    private static final Box HEAD = new Box(0, 0, 8, 8, 8), HAT = new Box(32, 0, 8, 8, 8);
    private static final Box BODY = new Box(16, 16, 8, 12, 4), JACKET = new Box(16, 32, 8, 12, 4);
    private static final Box RIGHT_LEG = new Box(0, 16, 4, 12, 4), RIGHT_PANTS = new Box(0, 32, 4, 12, 4);
    private static final Box LEFT_LEG = new Box(16, 48, 4, 12, 4), LEFT_PANTS = new Box(0, 48, 4, 12, 4);

    private final int[] pixels = new int[SIZE * SIZE];
    private final CharacterAppearance look;
    private final int variant, expression;
    private final Box rightArm, leftArm, rightSleeve, leftSleeve;
    private long noise;

    private SkinPainter(CharacterAppearance look, int variant, int expression) {
        this.look = look;
        this.variant = variant;
        this.expression = expression;
        int arm = look.slim() ? 3 : 4;
        rightArm = new Box(40, 16, arm, 12, 4);
        leftArm = new Box(32, 48, arm, 12, 4);
        rightSleeve = new Box(40, 32, arm, 12, 4);
        leftSleeve = new Box(48, 48, arm, 12, 4);
        noise = 0x9E3779B97F4A7C15L ^ look.hashCode() * 31L;
    }

    public static int[] paint(CharacterAppearance look, int variant, int expression) {
        SkinPainter painter = new SkinPainter(look, variant, expression);
        painter.paintAll();
        return painter.pixels;
    }

    private void paintAll() {
        int skin = look.skinTone();
        for (Box box : new Box[] {HEAD, BODY, rightArm, leftArm, RIGHT_LEG, LEFT_LEG}) fillBox(box, skin, 0.025);
        paintHead();
        paintOutfit();
        if (look.has(AppearanceOptions.ACCESSORY_WRISTBANDS)) {
            int band = AppearanceOptions.OUTFIT_BATTLE_ARMOR.equals(look.outfit()) ? look.outfitAccent() : look.outfitSecondary();
            limbBand(rightSleeve, 8, 10, band, 0.03);
            limbBand(leftSleeve, 8, 10, band, 0.03);
        }
        if (look.has(AppearanceOptions.ACCESSORY_HEADBAND)) {
            for (int row = 1; row <= 2; row++) limbRow(HAT, row, row == 1 ? shade(look.outfitAccent(), 1.08) : look.outfitAccent());
        }
    }

    // ---------------------------------------------------------------- head and face

    private void paintHead() {
        int skin = look.skinTone();
        boolean bald = AppearanceOptions.HAIR_BALD.equals(look.hairstyle());
        int hair = variant == VARIANT_SUPER_SAIYAN ? SSJ_HAIR : look.hairColor();
        if (!bald) {
            fillRect(HEAD.u + HEAD.d, HEAD.v, HEAD.w, HEAD.d, hair, 0.05);
            // Back and sides under the voxel hair, so gaps never show bare skin.
            fillRect(HEAD.u + HEAD.d * 2 + HEAD.w, HEAD.sideY(), HEAD.w, 5, hair, 0.05);
            fillRect(HEAD.u, HEAD.sideY(), HEAD.d, 2, hair, 0.05);
            fillRect(HEAD.u + HEAD.d + HEAD.w, HEAD.sideY(), HEAD.d, 2, hair, 0.05);
            fillRect(HEAD.frontX(), HEAD.sideY(), HEAD.w, 1, hair, 0.05);
        } else {
            // A slightly lighter crown keeps a bald head from reading as flat.
            fillRect(HEAD.u + HEAD.d, HEAD.v, HEAD.w, HEAD.d, shade(skin, 1.05), 0.02);
        }
        // Simple ears: a single darker notch on each side of the head.
        set(HEAD.u + 4, HEAD.sideY() + 4, shade(skin, 0.82));
        set(HEAD.u + 4, HEAD.sideY() + 5, shade(skin, 0.86));
        set(HEAD.u + HEAD.d + HEAD.w + 3, HEAD.sideY() + 4, shade(skin, 0.82));
        set(HEAD.u + HEAD.d + HEAD.w + 3, HEAD.sideY() + 5, shade(skin, 0.86));
        paintFace(skin);
    }

    private void paintFace(int skin) {
        int fx = HEAD.frontX(), fy = HEAD.sideY();
        int white = 0xF2F2F0, dark = 0x1E1616;
        int iris = variant == VARIANT_SUPER_SAIYAN ? SSJ_IRIS : look.eyeColor();
        int shine = blend(iris, 0xFFFFFF, 0.65);
        int brow = variant == VARIANT_SUPER_SAIYAN ? SSJ_BROW
                : AppearanceOptions.HAIR_BALD.equals(look.hairstyle()) ? shade(skin, 0.55) : shade(look.hairColor(), 0.85);
        if (variant == VARIANT_POTENTIAL) iris = blend(iris, 0xFFFFFF, 0.25);
        // Eyes: two-column blocks, right eye at columns 1-2 (viewer's left), mirrored for the left eye.
        String[] eye = switch (look.eyeStyle()) {
            case 1 -> new String[] {"DD", "WI", ".."};
            case 2 -> new String[] {"DD", "IS", "II"};
            case 3 -> new String[] {"..", "DI", ".."};
            case 4 -> new String[] {"DD", "DI", "WI"};
            default -> new String[] {"DD", "WI", "WI"};
        };
        boolean narrowed = expression != EXPRESSION_NEUTRAL;
        for (int row = 0; row < 3; row++) {
            String line = eye[row];
            if (narrowed && row == 2 && look.eyeStyle() != 2) line = "..";
            for (int col = 0; col < 2; col++) {
                int color = switch (line.charAt(col)) {
                    case 'W' -> white; case 'I' -> iris; case 'S' -> shine; case 'D' -> dark; default -> -1; };
                if (color < 0) continue;
                set(fx + 1 + col, fy + 3 + row, color);
                set(fx + 6 - col, fy + 3 + row, color);
            }
        }
        // Eyebrows. Focused and shouting expressions pull the inner end down.
        boolean angry = expression != EXPRESSION_NEUTRAL || look.eyebrowStyle() == 3;
        switch (look.eyebrowStyle()) {
            case 1 -> { browPixel(fx, fy, 0, 2, brow); browPixel(fx, fy, 1, 2, brow); browPixel(fx, fy, 2, angry ? 3 : 2, brow); browPixel(fx, fy, 1, 1, shade(brow, 0.9)); }
            case 2 -> browPixel(fx, fy, 2, angry ? 3 : 2, brow);
            case 3 -> { browPixel(fx, fy, 1, 1, brow); browPixel(fx, fy, 2, 2, brow); }
            default -> { browPixel(fx, fy, 1, 2, brow); browPixel(fx, fy, 2, angry ? 3 : 2, brow); }
        }
        if (angry && look.eyebrowStyle() == 3 && expression != EXPRESSION_NEUTRAL) browPixel(fx, fy, 2, 3, brow);
        int mouth = shade(skin, 0.55);
        if (expression == EXPRESSION_SHOUT) {
            for (int x = 2; x <= 5; x++) set(fx + x, fy + 6, dark);
            set(fx + 3, fy + 7, dark);
            set(fx + 4, fy + 7, dark);
            set(fx + 3, fy + 6, 0x5A1E1E);
            set(fx + 4, fy + 6, 0x5A1E1E);
        } else if (look.mouthStyle() == 0) {
            set(fx + 3, fy + 6, mouth);
            set(fx + 4, fy + 6, mouth);
        } else if (look.mouthStyle() == 1) {
            set(fx + 3, fy + 6, mouth);
            set(fx + 4, fy + 6, mouth);
            set(fx + 2, fy + 5, shade(skin, 0.72));
            set(fx + 5, fy + 5, shade(skin, 0.72));
        }
        // A barely visible nose shadow keeps the face Minecraft-like.
        set(fx + 3, fy + 5, shade(skin, 0.93));
    }

    private void browPixel(int fx, int fy, int col, int row, int color) {
        set(fx + col, fy + row, color);
        set(fx + 7 - col, fy + row, color);
    }

    // ---------------------------------------------------------------- clothing

    private void paintOutfit() {
        ResourceLocation outfit = look.outfit();
        int primary = look.outfitPrimary(), secondary = look.outfitSecondary(), accent = look.outfitAccent();
        if (AppearanceOptions.OUTFIT_BATTLE_ARMOR.equals(outfit)) {
            // Dark bodysuit everywhere, white-ish chest plate on the jacket overlay, gloves and boots in 3D.
            fillBox(BODY, secondary, 0.03);
            limbBand(rightArm, 0, 12, secondary, 0.03);
            limbBand(leftArm, 0, 12, secondary, 0.03);
            limbBand(RIGHT_LEG, 0, 12, secondary, 0.03);
            limbBand(LEFT_LEG, 0, 12, secondary, 0.03);
            fillBox(JACKET, primary, 0.02);
            limbRow(JACKET, 0, accent);
            limbRow(JACKET, 8, shade(accent, 0.9));
            for (int row = 9; row < 12; row++) clearRow(JACKET, row);
            limbBand(JACKET, 9, 10, shade(accent, 0.75), 0.02);
            sideStripes(JACKET, 1, 8, accent);
            limbBand(rightArm, 9, 12, primary, 0.02);
            limbBand(leftArm, 9, 12, primary, 0.02);
            limbBand(rightSleeve, 8, 12, primary, 0.02);
            limbBand(leftSleeve, 8, 12, primary, 0.02);
            limbRow(rightSleeve, 8, accent);
            limbRow(leftSleeve, 8, accent);
            boots(primary, accent, 6);
        } else if (AppearanceOptions.OUTFIT_FIGHTER_VEST.equals(outfit)) {
            fillBox(BODY, secondary, 0.03);
            limbBand(rightArm, 0, 3, secondary, 0.03);
            limbBand(leftArm, 0, 3, secondary, 0.03);
            limbBand(RIGHT_LEG, 0, 12, shade(secondary, 0.9), 0.03);
            limbBand(LEFT_LEG, 0, 12, shade(secondary, 0.9), 0.03);
            // Open vest: primary on sides/back/shoulders, an open front strip shows the shirt underneath.
            fillBox(JACKET, primary, 0.03);
            int fx = JACKET.frontX(), fy = JACKET.sideY();
            for (int y = 1; y < 10; y++) for (int x = 3; x <= 4; x++) clear(fx + x, fy + y);
            for (int row = 10; row < 12; row++) clearRow(JACKET, row);
            limbBand(JACKET, 9, 10, accent, 0.02);
            limbRow(JACKET, 0, shade(primary, 1.1));
            set(fx + 2, fy + 4, shade(primary, 0.75));
            set(fx + 5, fy + 4, shade(primary, 0.75));
            limbBand(rightSleeve, 9, 11, accent, 0.03);
            limbBand(leftSleeve, 9, 11, accent, 0.03);
            limbBand(RIGHT_PANTS, 0, 6, shade(secondary, 0.85), 0.03);
            limbBand(LEFT_PANTS, 0, 6, shade(secondary, 0.85), 0.03);
            boots(accent, shade(accent, 1.15), 8);
        } else if (AppearanceOptions.OUTFIT_SLEEVELESS.equals(outfit)) {
            fillBox(BODY, primary, 0.035);
            limbRow(BODY, 0, shade(primary, 0.85));
            limbBand(BODY, 9, 10, accent, 0.02);
            limbBand(RIGHT_LEG, 0, 12, secondary, 0.03);
            limbBand(LEFT_LEG, 0, 12, secondary, 0.03);
            limbBand(RIGHT_PANTS, 0, 8, shade(secondary, 1.06), 0.04);
            limbBand(LEFT_PANTS, 0, 8, shade(secondary, 1.06), 0.04);
            limbBand(JACKET, 9, 10, accent, 0.02);
            boots(shade(accent, 0.8), accent, 9);
        } else {
            // Training gi (default): undershirt, open V-neck top with sash, baggy pants and wrapped boots.
            fillBox(BODY, secondary, 0.03);
            limbBand(RIGHT_LEG, 0, 12, primary, 0.035);
            limbBand(LEFT_LEG, 0, 12, primary, 0.035);
            fillBox(JACKET, primary, 0.035);
            int fx = JACKET.frontX(), fy = JACKET.sideY();
            clear(fx + 2, fy); clear(fx + 3, fy); clear(fx + 4, fy); clear(fx + 5, fy);
            clear(fx + 3, fy + 1); clear(fx + 4, fy + 1);
            clear(fx + 3, fy + 2); clear(fx + 4, fy + 2);
            set(fx + 2, fy + 1, shade(primary, 0.8));
            set(fx + 5, fy + 1, shade(primary, 0.8));
            for (int row = 11; row < 12; row++) clearRow(JACKET, row);
            limbBand(JACKET, 9, 11, secondary, 0.02);
            set(fx + 2, fy + 10, shade(secondary, 0.75));
            emblem(JACKET, primary);
            limbBand(rightSleeve, 0, 3, primary, 0.035);
            limbBand(leftSleeve, 0, 3, primary, 0.035);
            limbBand(RIGHT_PANTS, 0, 8, shade(primary, 1.04), 0.04);
            limbBand(LEFT_PANTS, 0, 8, shade(primary, 1.04), 0.04);
            limbBand(RIGHT_PANTS, 7, 8, shade(primary, 0.8), 0.02);
            limbBand(LEFT_PANTS, 7, 8, shade(primary, 0.8), 0.02);
            boots(accent, shade(accent, 1.12), 9);
        }
    }

    private void boots(int color, int cuff, int startRow) {
        limbBand(RIGHT_LEG, startRow, 12, shade(color, 0.92), 0.02);
        limbBand(LEFT_LEG, startRow, 12, shade(color, 0.92), 0.02);
        limbBand(RIGHT_PANTS, startRow, 12, color, 0.025);
        limbBand(LEFT_PANTS, startRow, 12, color, 0.025);
        limbRow(RIGHT_PANTS, startRow, cuff);
        limbRow(LEFT_PANTS, startRow, cuff);
        fillRect(RIGHT_PANTS.u + RIGHT_PANTS.d + RIGHT_PANTS.w, RIGHT_PANTS.v, RIGHT_PANTS.w, RIGHT_PANTS.d, shade(color, 0.6), 0.02);
        fillRect(LEFT_PANTS.u + LEFT_PANTS.d + LEFT_PANTS.w, LEFT_PANTS.v, LEFT_PANTS.w, LEFT_PANTS.d, shade(color, 0.6), 0.02);
    }

    /** Small original emblem: a ringed four-point star on the back of the top. */
    private void emblem(Box box, int base) {
        int bx = box.u + box.d * 2 + box.w, by = box.sideY() + 2;
        int ring = 0xF4F1E8, mark = shade(base, 0.55);
        for (int x = 2; x <= 5; x++) { set(bx + x, by, ring); set(bx + x, by + 5, ring); }
        for (int y = 1; y <= 4; y++) { set(bx + 1, by + y, ring); set(bx + 6, by + y, ring); }
        for (int y = 1; y <= 4; y++) for (int x = 2; x <= 5; x++) set(bx + x, by + y, ring);
        set(bx + 3, by + 1, mark); set(bx + 4, by + 1, mark);
        for (int x = 2; x <= 5; x++) { set(bx + x, by + 2, mark); set(bx + x, by + 3, mark); }
        set(bx + 3, by + 4, mark); set(bx + 4, by + 4, mark);
    }

    private void sideStripes(Box box, int from, int to, int color) {
        for (int y = from; y < to; y++) {
            set(box.u, box.sideY() + y, color);
            set(box.u + box.d + box.w + box.d - 1, box.sideY() + y, color);
        }
    }

    // ---------------------------------------------------------------- primitive helpers

    private void fillBox(Box box, int color, double jitter) {
        fillRect(box.u + box.d, box.v, box.w * 2, box.d, color, jitter);
        fillRect(box.u, box.sideY(), box.d * 2 + box.w * 2, box.h, color, jitter);
        // Slightly darker lowest row on every side face gives limbs a grounded edge.
        for (int x = box.u; x < box.u + box.d * 2 + box.w * 2; x++) {
            int index = (box.sideY() + box.h - 1) * SIZE + x;
            pixels[index] = 0xFF000000 | shade(pixels[index] & 0xFFFFFF, 0.9);
        }
    }

    /** Rows [from, to) on the four side faces of a limb, plus the bottom cap when reaching the end. */
    private void limbBand(Box box, int from, int to, int color, double jitter) {
        fillRect(box.u, box.sideY() + from, box.d * 2 + box.w * 2, to - from, color, jitter);
        if (to >= box.h) fillRect(box.u + box.d + box.w, box.v, box.w, box.d, shade(color, 0.85), jitter);
    }

    private void limbRow(Box box, int row, int color) {
        fillRect(box.u, box.sideY() + row, box.d * 2 + box.w * 2, 1, color, 0.015);
    }

    private void clearRow(Box box, int row) {
        for (int x = box.u; x < box.u + box.d * 2 + box.w * 2; x++) clear(x, box.sideY() + row);
    }

    private void fillRect(int x0, int y0, int w, int h, int color, double jitter) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) continue;
                pixels[y * SIZE + x] = 0xFF000000 | shade(color, 1 + (random() - 0.5) * 2 * jitter);
            }
        }
    }

    private void set(int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) pixels[y * SIZE + x] = 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private void clear(int x, int y) {
        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) pixels[y * SIZE + x] = 0;
    }

    private double random() {
        noise ^= noise << 13;
        noise ^= noise >>> 7;
        noise ^= noise << 17;
        return (noise >>> 11) * 0x1.0p-53;
    }

    public static int shade(int rgb, double factor) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) * factor));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) * factor));
        int b = (int) Math.max(0, Math.min(255, (rgb & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }

    public static int blend(int a, int b, double t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }
}
