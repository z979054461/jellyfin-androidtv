#!/usr/bin/env python3
"""Offline checks for the read-only published-version gate."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('check_release_state.sh').resolve()
MOCK = r'''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
a = sys.argv[1:]
m = os.environ['TEST_MODE']
r = Path(os.environ['RUNNER_TEMP'])
with (r / 'calls').open('a') as f: f.write(json.dumps(a) + '\n')
assert a[0] == 'api' and '--method' not in a and '-X' not in a
route = next(x for x in a if x.startswith('repos/'))
release = dict(id=12, tag_name=os.environ['RELEASE_TAG'], draft=m=='draft', prerelease=m=='prerelease',
    assets=[dict(name=os.environ['RELEASE_APK'], state='uploaded', size=10, digest='sha256:'+'a'*64)])
if m=='extra': release['assets'].append(dict(name='extra'))
if m=='digest': release['assets'][0]['digest']='invalid'
if m=='empty': release['assets']=[]
if m=='wrong_id': release['id']=13
if '/releases?' in route:
    assert '--paginate' in a and '--slurp' in a
    if m=='error': sys.exit(1)
    items=[] if m=='absent' else [dict(id=12,tag_name=os.environ['RELEASE_TAG'])]
    if m=='duplicate': items *= 2
    print(json.dumps([[],items]))
elif '/releases/12' in route:
    print(json.dumps(release))
elif '/git/ref/tags/' in route:
    if m=='missing_tag': sys.exit(1)
    print(json.dumps(dict(object=dict(type='tag' if m=='annotated' else 'commit',sha='b'*40))))
elif '/git/tags/' in route:
    print(json.dumps(dict(object=dict(type='commit',sha='c'*40))))
else: sys.exit(99)
'''


class ReleaseStateTest(unittest.TestCase):
    def test_read_only_gate(self):
        for mode in ('absent', 'draft', 'published', 'annotated', 'prerelease', 'extra',
                     'digest', 'empty', 'wrong_id', 'error', 'duplicate', 'missing_tag'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                (root / 'bin').mkdir()
                gh = root / 'bin/gh'
                gh.write_text(MOCK)
                gh.chmod(0o755)
                env = os.environ | dict(TEST_MODE=mode, RUNNER_TEMP=temp, GITHUB_REPOSITORY='owner/repo',
                    RELEASE_TAG='v0.20.0-danmaku.1', RELEASE_APK='jellyfin-danmaku-200001.apk',
                    GITHUB_SHA='a'*40, GITHUB_OUTPUT=str(root/'output'), GITHUB_STEP_SUMMARY=str(root/'summary'),
                    PATH=str(root/'bin')+':'+os.environ['PATH'])
                result = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
                expected = mode in ('absent', 'draft', 'published', 'annotated')
                self.assertEqual(result.returncode == 0, expected, result.stderr)
                if expected:
                    self.assertEqual((root/'output').read_text(),
                        'should_run=' + ('true' if mode in ('absent','draft') else 'false') + '\n')
                else:
                    self.assertFalse((root/'output').exists())
                calls = [json.loads(line) for line in (root/'calls').read_text().splitlines()]
                self.assertTrue(all(call[0]=='api' and '--method' not in call for call in calls))
                if mode == 'draft':
                    self.assertFalse((root/'summary').exists())


if __name__ == '__main__':
    unittest.main()
