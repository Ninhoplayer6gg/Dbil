package dev.dbil.client.render.character;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dbil.appearance.CharacterAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded cache of painted character skins. A texture is painted once per (appearance, form variant, expression),
 * reused every frame and released when unused for a while or when the cache is full.
 */
public final class CharacterTextures {
    private static final int MAX_ENTRIES = 48;
    private static final long EXPIRE_NANOS = 90_000_000_000L;
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static int counter;

    private record Key(CharacterAppearance appearance, int variant, int expression) {}
    private record Entry(ResourceLocation location, DynamicTexture texture, long[] lastUsed) {}

    private CharacterTextures() {}

    public static ResourceLocation get(CharacterAppearance appearance, int variant, int expression) {
        Key key = new Key(appearance, variant, expression);
        Entry entry = CACHE.get(key);
        long now = System.nanoTime();
        if (entry == null) {
            entry = create(key);
            CACHE.put(key, entry);
            trim(now);
        }
        entry.lastUsed()[0] = now;
        return entry.location();
    }

    private static Entry create(Key key) {
        int[] argb = SkinPainter.paint(key.appearance(), key.variant(), key.expression());
        NativeImage image = new NativeImage(SkinPainter.SIZE, SkinPainter.SIZE, true);
        for (int y = 0; y < SkinPainter.SIZE; y++) {
            for (int x = 0; x < SkinPainter.SIZE; x++) {
                int c = argb[y * SkinPainter.SIZE + x];
                // NativeImage stores ABGR.
                int abgr = (c & 0xFF000000) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = Minecraft.getInstance().getTextureManager().register("dbil_character_" + (counter++), texture);
        return new Entry(location, texture, new long[] {System.nanoTime()});
    }

    private static void trim(long now) {
        Iterator<Map.Entry<Key, Entry>> iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext() && CACHE.size() > 0) {
            Map.Entry<Key, Entry> next = iterator.next();
            boolean expired = now - next.getValue().lastUsed()[0] > EXPIRE_NANOS;
            if (!expired && CACHE.size() <= MAX_ENTRIES) break;
            Minecraft.getInstance().getTextureManager().release(next.getValue().location());
            iterator.remove();
        }
    }

    /** Called periodically from the client tick so long sessions do not keep stale skins. */
    public static void prune() {
        trim(System.nanoTime());
    }

    public static void clear() {
        for (Entry entry : CACHE.values()) Minecraft.getInstance().getTextureManager().release(entry.location());
        CACHE.clear();
    }
}
