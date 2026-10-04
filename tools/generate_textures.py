#!/usr/bin/env python3
"""Generates DBIL 0.3 procedural textures. Run from the repository root: python3 tools/generate_textures.py

Every PNG written here is original procedural art. Grayscale images are tinted at runtime (hair, outfit parts,
particles); effect textures are RGB intensity on black for additive rendering.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'dbil', 'textures')
random.seed(20261004)


def out(path, image):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    image.save(full)
    print('wrote', path, image.size)


def radial(size, power=2.0, inner=0.0):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / (size / 2)
            a = max(0.0, 1.0 - max(0.0, d - inner) / (1.0 - inner)) if d < 1 else 0.0
            a = a ** power
            px[x, y] = (255, 255, 255, int(255 * a))
    return img


def particles():
    out('particle/ki_spark.png', radial(8, 1.6, 0.15))
    # Elongated rising mote.
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y in range(8):
        for x in range(8):
            dx = (x - 3.5) / 2.2
            dy = (y - 3.5) / 4.0
            a = max(0.0, 1 - math.sqrt(dx * dx + dy * dy)) ** 1.4
            px[x, y] = (255, 255, 255, int(255 * a))
    out('particle/aura_mote.png', img)
    out('particle/ki_trail.png', radial(16, 1.8, 0.1))
    ring = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = ring.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 16
            a = max(0.0, 1 - abs(d - 0.78) / 0.16)
            px[x, y] = (255, 255, 255, int(255 * a ** 1.5))
    out('particle/impact_ring.png', ring)
    wave = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = wave.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 16
            a = max(0.0, 1 - abs(d - 0.7) / 0.3) if d < 1 else 0
            px[x, y] = (255, 255, 255, int(220 * a ** 2))
    out('particle/shockwave.png', wave)
    dust = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = dust.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 8
            n = random.uniform(0.75, 1.0)
            a = max(0.0, 1 - d) ** 0.9 * n
            v = int(255 * random.uniform(0.82, 1.0))
            px[x, y] = (v, v, v, int(235 * a))
    out('particle/dust_cloud.png', dust)
    flash = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = flash.load()
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            d = math.hypot(dx, dy) / 16
            ang = math.atan2(dy, dx)
            rays = 0.55 + 0.45 * abs(math.cos(ang * 4)) ** 6
            a = max(0.0, 1 - d / rays) ** 1.3
            px[x, y] = (255, 255, 255, int(255 * min(1, a)))
    out('particle/flash.png', flash)


def hair():
    # 64x64 grayscale strands. All hair boxes sample the same pattern, so any UV region reads as hair.
    img = Image.new('RGBA', (64, 64), (255, 255, 255, 255))
    px = img.load()
    strand = [random.uniform(0.82, 1.0) for _ in range(64)]
    for y in range(64):
        for x in range(64):
            v = strand[x] * random.uniform(0.94, 1.0)
            if (y + x * 3) % 11 == 0:
                v *= 0.86
            g = int(255 * min(1.0, v))
            px[x, y] = (g, g, g, 255)
    out('entity/character/hair.png', img)


def outfit_parts():
    # Region (0,0)-(64,32): cloth. (0,32)-(64,48): armor plate with bright edges. (0,48)-(64,64): leather/metal.
    img = Image.new('RGBA', (64, 64), (255, 255, 255, 255))
    px = img.load()
    for y in range(64):
        for x in range(64):
            if y < 32:
                v = random.uniform(0.88, 1.0) * (0.96 if (x + y) % 5 == 0 else 1.0)
            elif y < 48:
                v = 1.0 if (x % 8 in (0,)) or (y % 8 == 0) else random.uniform(0.86, 0.95)
            else:
                v = random.uniform(0.72, 0.9)
            g = int(255 * v)
            px[x, y] = (g, g, g, 255)
    out('entity/character/outfit_parts.png', img)


def effect_textures():
    # Additive textures: intensity in RGB on black.
    glow = Image.new('RGB', (64, 64), (0, 0, 0))
    px = glow.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5) / 32
            a = max(0.0, 1 - d) ** 2.2
            v = int(255 * a)
            px[x, y] = (v, v, v)
    out('entity/effect/glow.png', glow)

    core = Image.new('RGB', (64, 64), (0, 0, 0))
    px = core.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5) / 32
            a = 1.0 if d < 0.55 else max(0.0, 1 - (d - 0.55) / 0.45) ** 1.5
            v = int(255 * a)
            px[x, y] = (v, v, v)
    out('entity/effect/core.png', core)

    # Flame tongue for aura shells: u across width, v from bottom (1) to top (0). Tileable vertically noise.
    flame = Image.new('RGB', (64, 128), (0, 0, 0))
    px = flame.load()
    for y in range(128):
        t = y / 127.0  # 0 top, 1 bottom
        for x in range(64):
            u = (x - 31.5) / 32
            width = 0.18 + 0.82 * (t ** 0.65)
            edge = max(0.0, 1 - abs(u) / width)
            flick = 0.75 + 0.25 * math.sin(y * 0.35 + math.sin(x * 0.4) * 2.0)
            a = edge ** 1.3 * (t ** 0.45) * flick
            a *= 1.0 if t > 0.08 else t / 0.08
            v = int(255 * max(0.0, min(1.0, a)))
            px[x, y] = (v, v, v)
    out('entity/effect/aura_flame.png', flame.filter(ImageFilter.GaussianBlur(0.8)))

    # Beam bands: horizontal = along beam (u), vertical = across (v). Scrolling u gives moving energy.
    beam = Image.new('RGB', (128, 32), (0, 0, 0))
    px = beam.load()
    for y in range(32):
        across = abs(y - 15.5) / 16
        for x in range(128):
            wave = 0.7 + 0.3 * math.sin(x / 128 * math.pi * 8 + across * 3)
            streak = 0.85 + 0.15 * math.sin(x / 128 * math.pi * 22 + y * 0.7)
            a = max(0.0, 1 - across ** 1.6) * wave * streak
            v = int(255 * min(1.0, a))
            px[x, y] = (v, v, v)
    out('entity/effect/beam.png', beam)

    # Spiral ring used along beams and auras.
    ring = Image.new('RGB', (64, 64), (0, 0, 0))
    px = ring.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5) / 32
            a = max(0.0, 1 - abs(d - 0.8) / 0.18) ** 2
            v = int(255 * a)
            px[x, y] = (v, v, v)
    out('entity/effect/ring.png', ring)

    white = Image.new('RGB', (8, 8), (255, 255, 255))
    out('entity/effect/white.png', white)

    # Speed streak: thin bright line fading at both ends.
    streak = Image.new('RGB', (64, 8), (0, 0, 0))
    px = streak.load()
    for y in range(8):
        for x in range(64):
            a = math.sin(math.pi * x / 63) ** 0.8 * max(0.0, 1 - abs(y - 3.5) / 3.5) ** 1.5
            v = int(255 * a)
            px[x, y] = (v, v, v)
    out('entity/effect/streak.png', streak)


def icon(draw, ox, oy, kind):
    w = (255, 255, 255, 255)
    s = (255, 255, 255, 150)
    if kind == 'blast':
        draw.ellipse((ox + 5, oy + 5, ox + 11, oy + 11), fill=w)
        draw.line((ox + 1, oy + 8, ox + 5, oy + 8), fill=s)
        draw.line((ox + 2, oy + 5, ox + 5, oy + 6), fill=s)
        draw.line((ox + 2, oy + 11, ox + 5, oy + 10), fill=s)
    elif kind == 'barrage':
        for cx, cy in ((5, 4), (11, 7), (5, 11)):
            draw.ellipse((ox + cx - 2, oy + cy - 2, ox + cx + 2, oy + cy + 2), fill=w)
        draw.line((ox + 1, oy + 4, ox + 3, oy + 4), fill=s)
        draw.line((ox + 7, oy + 7, ox + 9, oy + 7), fill=s)
        draw.line((ox + 1, oy + 11, ox + 3, oy + 11), fill=s)
    elif kind == 'wave':
        draw.ellipse((ox + 4, oy + 3, ox + 13, oy + 12), outline=w)
        draw.ellipse((ox + 6, oy + 5, ox + 11, oy + 10), fill=w)
        draw.line((ox + 1, oy + 7, ox + 4, oy + 7), fill=s)
    elif kind == 'kamehameha':
        draw.rectangle((ox + 5, oy + 6, ox + 15, oy + 9), fill=w)
        draw.ellipse((ox + 1, oy + 4, ox + 7, oy + 11), fill=w)
    elif kind == 'galick':
        draw.polygon([(ox + 2, oy + 3), (ox + 15, oy + 6), (ox + 15, oy + 9), (ox + 2, oy + 12)], fill=w)
        draw.rectangle((ox + 1, oy + 6, ox + 4, oy + 9), fill=s)
    elif kind == 'masenko':
        draw.polygon([(ox + 8, oy + 1), (ox + 14, oy + 8), (ox + 8, oy + 15), (ox + 2, oy + 8)], outline=w)
        draw.polygon([(ox + 8, oy + 4), (ox + 11, oy + 8), (ox + 8, oy + 12), (ox + 5, oy + 8)], fill=w)
    elif kind == 'generic':
        draw.ellipse((ox + 3, oy + 3, ox + 12, oy + 12), outline=w)
    elif kind == 'locked':
        draw.rectangle((ox + 4, oy + 7, ox + 11, oy + 13), fill=w)
        draw.arc((ox + 5, oy + 2, ox + 10, oy + 10), 180, 360, fill=w, width=2)
    elif kind == 'shield':
        draw.polygon([(ox + 3, oy + 2), (ox + 12, oy + 2), (ox + 12, oy + 8), (ox + 7, oy + 14), (ox + 3, oy + 8)], fill=w)
    elif kind == 'flight':
        draw.polygon([(ox + 8, oy + 2), (ox + 13, oy + 12), (ox + 8, oy + 9), (ox + 3, oy + 12)], fill=w)
    elif kind == 'fast':
        draw.polygon([(ox + 9, oy + 2), (ox + 14, oy + 12), (ox + 9, oy + 9), (ox + 4, oy + 12)], fill=w)
        draw.line((ox + 1, oy + 8, ox + 4, oy + 5), fill=s)
        draw.line((ox + 1, oy + 12, ox + 5, oy + 9), fill=s)
    elif kind == 'aura':
        draw.polygon([(ox + 8, oy + 1), (ox + 12, oy + 9), (ox + 8, oy + 14), (ox + 4, oy + 9)], fill=w)
        draw.line((ox + 2, oy + 5, ox + 3, oy + 10), fill=s)
        draw.line((ox + 13, oy + 5, ox + 12, oy + 10), fill=s)
    elif kind == 'star':
        pts = []
        for i in range(10):
            r = 7 if i % 2 == 0 else 3
            a = -math.pi / 2 + i * math.pi / 5
            pts.append((ox + 7.5 + r * math.cos(a), oy + 7.5 + r * math.sin(a)))
        draw.polygon(pts, fill=w)
    elif kind == 'target':
        draw.ellipse((ox + 2, oy + 2, ox + 13, oy + 13), outline=w)
        draw.line((ox + 7, oy + 0, ox + 7, oy + 4), fill=w)
        draw.line((ox + 7, oy + 11, ox + 7, oy + 15), fill=w)
        draw.line((ox + 0, oy + 7, ox + 4, oy + 7), fill=w)
        draw.line((ox + 11, oy + 7, ox + 15, oy + 7), fill=w)
    elif kind == 'heart':
        draw.polygon([(ox + 8, oy + 14), (ox + 2, oy + 7), (ox + 2, oy + 4), (ox + 5, oy + 2), (ox + 8, oy + 5),
                      (ox + 11, oy + 2), (ox + 14, oy + 4), (ox + 14, oy + 7)], fill=w)
    elif kind == 'ki':
        draw.polygon([(ox + 8, oy + 1), (ox + 13, oy + 9), (ox + 8, oy + 14), (ox + 3, oy + 9)], fill=w)
    elif kind == 'stamina':
        draw.polygon([(ox + 9, oy + 1), (ox + 4, oy + 9), (ox + 8, oy + 9), (ox + 6, oy + 15), (ox + 12, oy + 6), (ox + 8, oy + 6)], fill=w)


def hud():
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    kinds = ['blast', 'barrage', 'wave', 'kamehameha', 'galick', 'masenko', 'generic', 'locked',
             'shield', 'flight', 'fast', 'aura', 'star', 'target', 'heart', 'ki']
    for i, kind in enumerate(kinds):
        icon(draw, (i % 8) * 16, (i // 8) * 16, kind)
    icon(draw, 64, 32, 'stamina')
    # Hexagonal emblem frame (0,32) 32x32.
    hexagon = [(16 + 14 * math.cos(math.pi / 6 + i * math.pi / 3), 48 + 14 * math.sin(math.pi / 6 + i * math.pi / 3)) for i in range(6)]
    draw.polygon(hexagon, fill=(255, 255, 255, 60), outline=(255, 255, 255, 255))
    inner = [(16 + 10 * math.cos(math.pi / 6 + i * math.pi / 3), 48 + 10 * math.sin(math.pi / 6 + i * math.pi / 3)) for i in range(6)]
    draw.polygon(inner, outline=(255, 255, 255, 140))
    # Reticle (32,32) 32x32: four corner brackets.
    r = (255, 255, 255, 255)
    for (x0, y0, dx, dy) in ((34, 34, 1, 1), (61, 34, -1, 1), (34, 61, 1, -1), (61, 61, -1, -1)):
        for k in range(7):
            draw.point((x0 + dx * k, y0), fill=r)
            draw.point((x0, y0 + dy * k), fill=r)
            draw.point((x0 + dx * k, y0 + dy), fill=r)
            draw.point((x0 + dx, y0 + dy * k), fill=r)
    draw.polygon([(48, 40), (51, 44), (45, 44)], fill=(255, 255, 255, 200))
    out('gui/hud_icons.png', img)


if __name__ == '__main__':
    particles()
    hair()
    outfit_parts()
    effect_textures()
    hud()
