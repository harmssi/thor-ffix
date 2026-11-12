# Entpackt die Nutzlast eines Memoria.Patcher (Format: siehe Memoria.Patcher/Program.cs) in ein Verzeichnis.
import struct, sys, os, zlib, gzip, io
src, out = sys.argv[1], sys.argv[2]
f = open(src, 'rb'); f.seek(-0x18, 2)
magic, usize, cpos = struct.unpack('<qqq', f.read(24))
assert magic == 0x004149524F4D454D, hex(magic)
f.seek(cpos)
g = gzip.GzipFile(fileobj=f)
def rd(n):
    b = g.read(n); assert len(b) == n; return b
left = usize; pm = {}; n = 0
while left > 0:
    size, ticks = struct.unpack('<Iq', rd(12)); cnt = rd(1)[0]; parts = []
    for _ in range(cnt):
        i, = struct.unpack('<h', rd(2)); i &= 0xffff
        if i & 0x8000:
            i &= 0x7fff; l = rd(1)[0]; s = rd(l).decode(); pm[i] = s
        else: s = pm[i]
        parts.append(s.replace('{PLATFORM}', 'x64'))
    p = os.path.join(out, *[x.replace('\\', '/') for x in parts])
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'wb') as o:
        r = size
        while r: c = g.read(min(r, 1 << 20)); o.write(c); r -= len(c)
    left -= size; n += 1
print(n, 'Dateien')
