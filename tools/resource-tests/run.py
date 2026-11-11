#!/usr/bin/env python3
"""Reject same-configuration named value collisions before the Android build."""
from collections import defaultdict
from pathlib import Path
import re
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
            if kind == 'string' and name == 'lbl_tv_channel_status':
                label = ''.join(node.itertext())
                if set(re.findall(r'%([12])\$d', label)) != {'1', '2'} or re.search(r'\\+%', label):
                    errors.append(f'{file.relative_to(root)}: channel status must format both integer arguments without escaped placeholders')
            if name:
                seen[(kind, name)].append(str(file.relative_to(root)))
    for key, files in seen.items():
        if len(files) > 1:
            errors.append(f'{directory.name}/{key[0]}/{key[1]}: {", ".join(files)}')
# The custom panel supports English and Simplified Chinese with explicit fallback elsewhere.
custom = [ET.parse(root / f'app/src/main/res/{locale}/playback_info.xml').getroot()
          for locale in ('values', 'values-zh-rCN')]
keys = [{node.attrib['name'] for node in locale.findall('string')} for locale in custom]
if keys[0] != keys[1] or len(keys[0]) != 14:
    errors.append('Custom playback labels must keep English and Simplified Chinese in sync')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f'Android value resources: {checked} XML files checked, no duplicate names or playback-label format errors')
