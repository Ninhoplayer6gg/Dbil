#!/usr/bin/env python3
"""Development preview: renders HairModels.java hairstyles (base and Super Saiyan) with a tiny software
rasterizer, by translating the builder calls to Python. Usage: python3 tools/preview_hair.py out.png"""
import math, re, sys
from PIL import Image, ImageDraw

SRC = open(__file__.replace('tools/preview_hair.py', 'src/main/java/dev/dbil/client/render/character/HairModels.java')).read()

def java_to_py(body):
    body = re.sub(r'(\d+(?:\.\d+)?)F\b', r'\1', body)
    body = body.replace('PI', 'math.pi')
    out = []
    indent = 1
    for raw in body.split('\n'):
        line = re.sub(r'\s*//.*$', '', raw.strip())
        if not line or line.startswith('//'):
            continue
        if line.startswith('if (AppearanceOptions'):
            continue
        line = re.sub(r'float (\w+) = (\w+) \? ([^:]+) : ([^;]+);', r'\1 = (\3 if \2 else \4)', line)
        line = re.sub(r'\(ssj \? ([^:]+) : ([^)]+)\)', r'(\1 if ssj else \2)', line)
        line = re.sub(r'ssj \? ([-\d.]+) : ([-\d.]+)', r'(\1 if ssj else \2)', line)
        if line == '} else {':
            out.append('    ' * (indent - 1) + 'else:'); continue
        if line == '}':
            indent -= 1; continue
        m = re.match(r'if \((!?)ssj\) \{', line)
        if m:
            out.append('    ' * indent + ('if not ssj:' if m.group(1) else 'if ssj:')); indent += 1; continue
        line = line.rstrip(';')
        out.append('    ' * indent + line)
    return '\n'.join(out)

def extract(name):
    m = re.search(r'private static void ' + name + r'\(PartBuilder b(?:, boolean ssj|, float backDepth)\) \{(.*?)\n    \}', SRC, re.S)
    return m.group(1)

class B:
    def __init__(self): self.parts = []
    def box(self, slot, x, y, z, w, h, d): self.parts.append(((0,0,0,0,0,0), [(x,y,z,w,h,d)]))
    def spike(self, slot, px, py, pz, xr, yr, zr, *segs):
        boxes = []; off = 0
        for (s, l) in segs:
            boxes.append((-s/2, -off-l, -s/2, s, l, s)); off += l - 0.15
        self.parts.append(((px,py,pz,xr,yr,zr), boxes))
def seg(s, l): return (s, l)

env = {'math': math, 'seg': seg}
exec('def cap(b, backDepth):\n' + java_to_py(extract('cap')), env)
STYLES = ['shortHair', 'spiky', 'spikyTall', 'messy', 'medium', 'straight']
for st in STYLES:
    exec('def ' + st + '(b, ssj):\n' + java_to_py(extract(st)), env)

def rot(v, xr, yr, zr):
    x, y, z = v
    c, s = math.cos(xr), math.sin(xr); y, z = y*c - z*s, y*s + z*c
    c, s = math.cos(yr), math.sin(yr); x, z = x*c + z*s, -x*s + z*c
    c, s = math.cos(zr), math.sin(zr); x, y = x*c - y*s, x*s + y*c
    return (x, y, z)

FACES = [(0,1,2,3),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,3,7,4)]
def cube(b):
    x,y,z,w,h,d = b
    return [(x,y,z),(x+w,y,z),(x+w,y+h,z),(x,y+h,z),(x,y,z+d),(x+w,y,z+d),(x+w,y+h,z+d),(x,y+h,z+d)]

def render(parts, view_yaw, hair_rgb, size=160):
    img = Image.new('RGB', (size, size), (32, 38, 50))
    dr = ImageDraw.Draw(img)
    polys = []
    def add(verts, color):
        cy, sy = math.cos(view_yaw), math.sin(view_yaw)
        tv = []
        for (x, y, z) in verts:
            vx, vz = x*cy + z*sy, -x*sy + z*cy
            vy, vz2 = y*0.94 - vz*0.34, y*0.34 + vz*0.94  # slight top-down tilt
            tv.append((vx, vy, vz2))
        for f in FACES:
            pts = [tv[i] for i in f]
            ax, ay, az = [pts[1][i]-pts[0][i] for i in range(3)]
            bx, by, bz = [pts[3][i]-pts[0][i] for i in range(3)]
            nx, ny, nz = ay*bz-az*by, az*bx-ax*bz, ax*by-ay*bx
            ln = math.sqrt(nx*nx+ny*ny+nz*nz) or 1
            depth = sum(p[2] for p in pts) / 4
            light = 0.55 + 0.45 * abs(-ny/ln*0.7 - nz/ln*0.5)
            polys.append((depth, [(size/2 + p[0]*6.5, size*0.62 + p[1]*6.5) for p in pts], tuple(int(c*light) for c in color)))
    add(cube((-4,-8,-4,8,8,8)), (232, 180, 143))
    add(cube((-4,0,-2,8,6,4)), (224, 112, 42))
    for (px,py,pz,xr,yr,zr), boxes in parts:
        for bx in boxes:
            add([tuple(a+b for a,b in zip(rot(v, xr, yr, zr), (px,py,pz))) for v in cube(bx)], hair_rgb)
    for depth, pts, color in sorted(polys, key=lambda p: -p[0]):
        dr.polygon(pts, fill=color, outline=tuple(max(0,c-40) for c in color))
    return img

views = [0.0, math.pi * 0.75, math.pi]
cols = len(views) * 2
sheet = Image.new('RGB', (160 * cols, 160 * len(STYLES)), (20, 24, 32))
for row, st in enumerate(STYLES):
    for k, ssj in enumerate((False, True)):
        b = B(); env[st](b, ssj)
        for j, vyaw in enumerate(views):
            sheet.paste(render(b.parts, vyaw, (255, 225, 90) if ssj else (40, 34, 30)), ((k*len(views)+j)*160, row*160))
sheet.save(sys.argv[1])
print('ok')
