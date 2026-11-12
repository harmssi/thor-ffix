#!/usr/bin/env python3
"""Setzt Werte in einer Memoria-INI (Kommentare/Reihenfolge bleiben erhalten).
Aufruf: iniset.py datei.ini Abschnitt.Schlüssel=Wert [...]"""
import sys
path, assigns = sys.argv[1], sys.argv[2:]
raw = open(path, 'rb').read()
text = raw.decode('utf-8-sig') if raw[:3] == b'\xef\xbb\xbf' else raw.decode('latin-1')
lines = text.split('\n')
for a in assigns:
    key, val = a.split('=', 1)
    sec, name = key.split('.', 1)
    start = next((i for i, l in enumerate(lines) if l.strip() == f'[{sec}]'), None)
    if start is None:
        lines += ['', f'[{sec}]', f'{name} = {val}']
        continue
    end = next((i for i in range(start + 1, len(lines)) if lines[i].strip().startswith('[')), len(lines))
    for i in range(start + 1, end):
        if lines[i].split('=', 1)[0].strip() == name and not lines[i].lstrip().startswith(';'):
            lines[i] = f'{name} = {val}'
            break
    else:
        j = end
        while j > start + 1 and lines[j - 1].strip() == '':
            j -= 1
        lines.insert(j, f'{name} = {val}')
out = '\n'.join(lines)
open(path, 'wb').write(out.encode('utf-8-sig') if raw[:3] == b'\xef\xbb\xbf' else out.encode('latin-1'))
