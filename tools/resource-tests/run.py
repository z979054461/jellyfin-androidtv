#!/usr/bin/env python3
"""Reject same-configuration named value collisions before the Android build."""
from collections import defaultdict
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
errors = []
checked = 0
for directory in sorted((root / 'app/src/main/res').glob('values*')):
    seen = defaultdict(list)
    for file in sorted(directory.glob('*.xml')):
        checked += 1
        for node in ET.parse(file).getroot():
            kind = node.attrib.get('type') if node.tag == 'item' else node.tag
            if kind in {'id', 'attr', 'declare-styleable'}:
                continue
            if kind in {'string-array', 'integer-array'}:
                kind = 'array'
            name = node.attrib.get('name')
            if name:
                seen[(kind, name)].append(str(file.relative_to(root)))
    for key, files in seen.items():
        if len(files) > 1:
            errors.append(f'{directory.name}/{key[0]}/{key[1]}: {", ".join(files)}')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f'Android value resources: {checked} XML files checked, no duplicate names')
