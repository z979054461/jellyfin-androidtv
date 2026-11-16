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
# These CLDR categories require fractional input, unavailable to Android's int quantity API.
# https://github.com/unicode-org/cldr/blob/main/common/supplemental/plurals.xml
decimal_only_many = {
    'values-cs': set('hours episodes minutes tracks seconds items tv_series movies songs albums'.split()),
    'values-lt': set('movies minutes songs albums tracks seconds hours'.split()),
    'values-sk': set('tracks minutes episodes seconds hours items movies albums tv_series songs'.split()),
}
expected_annotations = {(locale, name) for locale, names in decimal_only_many.items() for name in names}
annotations = set()
tools_ignore = '{http://schemas.android.com/tools}ignore'
for directory in sorted((root / 'app/src/main/res').glob('values*')):
    seen = defaultdict(list)
    for file in sorted(directory.glob('*.xml')):
        checked += 1
        resource = ET.parse(file).getroot()
        for node in resource.iter():
            ignored = {value.strip() for value in node.get(tools_ignore, '').split(',')}
            if 'all' in ignored:
                errors.append(f'{file.relative_to(root)}: blanket resource lint suppression is not allowed')
            if 'MissingQuantity' not in ignored:
                continue
            key = (directory.name, node.get('name'))
            if node.tag != 'plurals' or node not in list(resource) or key not in expected_annotations:
                errors.append(f'{file.relative_to(root)}: unexpected or broad MissingQuantity suppression: {key}')
                continue
            annotations.add(key)
            quantities = [item.get('quantity') for item in node.findall('item')]
            if set(quantities) != {'one', 'few', 'other'} or len(quantities) != 3:
                errors.append(f'{file.relative_to(root)}: {key[1]} must retain exactly one, few and other; revisit decimal-only annotation')
        for node in resource:
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
if annotations != expected_annotations:
    errors.append('Decimal-only MissingQuantity annotations must match the reviewed 27-resource allowlist')

# Guard the reviewed lookup paths, not translation grammar. Android and Compose accept
# int quantities; our Number wrapper must retain its explicit conversion. New lookup
# paths require review, especially a future decimal-aware ICU implementation.
source_root = root / 'app/src/main/java/org/jellyfin/androidtv'
expected_lookups = {
    'util/TimeUtils.java': 3,
    'util/ContextExtensions.kt': 2,  # Declaration plus the Android call.
    'ui/RecordPopup.java': 8,
    'util/sdk/BaseItemExtensions.kt': 2,
    'ui/settings/screen/home/SettingsHomeNextUpCutoffScreen.kt': 1,
    'ui/settings/screen/screensaver/strings.kt': 7,
    'ui/browsing/composable/inforow/BaseItemInfoRow.kt': 6,
}
lookups = {}
for file in (root / 'app/src/main').rglob('*'):
    if file.suffix not in {'.java', '.kt'}:
        continue
    source = file.read_text()
    count = len(re.findall(r'\b(?:getQuantityString|getQuantityText|pluralStringResource)\s*\(', source))
    if count:
        key = str(file.relative_to(source_root)) if file.is_relative_to(source_root) else str(file.relative_to(root))
        lookups[key] = count
    if re.search(r'\bPluralRules\b', source):
        errors.append(f'{file.relative_to(root)}: review decimal-only annotations before introducing custom plural selection')
if lookups != expected_lookups:
    errors.append('Plural lookup paths changed; review integer-only selection before updating the inventory')
wrapper = re.sub(r'\s+', '', (source_root / 'util/ContextExtensions.kt').read_text())
if 'funContext.getQuantityString(@PluralsResid:Int,quantity:Number,varargargs:Any)=resources.getQuantityString(id,quantity.toInt(),quantity,*args)' not in wrapper:
    errors.append('Plural Number wrapper must retain the reviewed integer quantity conversion')

seconds = ET.parse(root / 'app/src/main/res/values-sr/strings.xml').getroot().find("plurals[@name='seconds']")
if seconds is None or {item.get('quantity'): item.text for item in seconds} != {
    'one': '%1$s секунда', 'few': '%1$s секунде', 'other': '%1$s секунди',
}:
    errors.append('Serbian seconds must retain count placeholders and the reviewed CLDR unit forms')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f'Android value resources: {checked} XML files checked, no duplicate names or playback-label format errors')
