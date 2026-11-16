#!/usr/bin/env python3
"""Execute publication shell against an offline gh fake, never GitHub."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('publish_release.sh').resolve()
MOCK = r'''#!/usr/bin/env python3
import hashlib, json, os, sys
from pathlib import Path
args = sys.argv[1:]
mode = os.environ['TEST_MODE']
root = Path(os.environ['RUNNER_TEMP'])
with (root / 'calls').open('a') as out:
    out.write(json.dumps(args) + '\n')
def emit(value):
    print(json.dumps(value))
def absent():
    print('gh: Not Found (HTTP 404)', file=sys.stderr)
    sys.exit(1)
def release():
    assets = [dict(id=734565367, name=os.environ['RELEASE_APK'], state='uploaded',
                   digest='sha256:' + hashlib.sha256(b'apk').hexdigest())]
    if mode == 'bad_digest': assets[0]['digest'] = 'sha256:' + '0' * 64
    if mode == 'extra_asset': assets.append(dict(name='SHA256SUMS.txt'))
    if mode == 'empty_draft': assets = []
    return dict(id=441348211, tag_name=os.environ['RELEASE_TAG'], assets=assets,
                draft=mode != 'published', prerelease=mode == 'prerelease')
if args[:2] == ['release', 'create']:
    (root / 'created').touch()
elif args[0] == 'api':
    route = next(arg for arg in args if arg.startswith('repos/'))
    if '/releases/tags/' in route:
        absent()  # Regression: tag lookup can never discover a draft.
    elif '/git/ref/tags/' in route:
        if mode == 'new': absent()
        emit(dict(object=dict(type='commit', sha='conflict' if mode == 'bad_tag' else os.environ['GITHUB_SHA'])))
    elif route.endswith('/git/refs'):
        emit(dict(object=dict(sha=os.environ['GITHUB_SHA'])))
    elif '/releases?per_page=100' in route:
        assert '--paginate' in args and '--slurp' in args
        if mode == 'list_failure':
            print('HTTP 503', file=sys.stderr)
            sys.exit(1)
        exists = mode != 'new' or (root / 'created').exists()
        # Keep target on the second page to test pagination, not only first-page match.
        pages = [[dict(id=1, tag_name='old-version')], [release()] if exists else []]
        if mode == 'duplicate': pages[1].append(release())
        emit(pages)
    elif '/releases/assets/' in route:
        sys.stdout.buffer.write(b'bad bytes' if mode == 'bad_bytes' else b'apk')
    elif route.endswith('/releases/441348211'):
        value = release()
        if '--method' in args and args[args.index('--method') + 1] == 'PATCH':
            assert 'draft=false' in args and 'prerelease=false' in args and 'make_latest=true' in args
            value['draft'] = False
            value['prerelease'] = False
        if mode == 'wrong_release': value['tag_name'] = 'wrong-tag'
        emit(value)
    else:
        sys.exit(97)
else:
    sys.exit(98)
'''


class PublicationTest(unittest.TestCase):
    def run_case(self, mode, success):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            binary = root / 'bin'
            binary.mkdir()
            (binary / 'gh').write_text(MOCK)
            (binary / 'gh').chmod(0o755)
            output = root / 'build/danmaku-release'
            output.mkdir(parents=True)
            (output / 'jellyfin-danmaku-200001.apk').write_bytes(b'apk')
            env = os.environ | dict(TEST_MODE=mode, RUNNER_TEMP=temp,
                GITHUB_REPOSITORY='owner/repo', GITHUB_SHA='checked-commit',
                RELEASE_TAG='v0.20.0-danmaku.1', RELEASE_APK='jellyfin-danmaku-200001.apk',
                GITHUB_STEP_SUMMARY=str(root / 'summary'), PATH=str(binary) + ':' + os.environ['PATH'])
            result = subprocess.run(['bash', str(SCRIPT)], cwd=root, env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode == 0, success, result.stderr)
            calls = [json.loads(line) for line in (root / 'calls').read_text().splitlines()]
            mutations = [call for call in calls if call[:2] == ['release', 'create'] or 'PATCH' in call or 'POST' in call]
            if not success or mode == 'published':
                self.assertEqual(mutations, [])
            else:
                self.assertEqual(sum('PATCH' in call for call in calls), 1)
                if mode == 'draft':
                    self.assertEqual(len(mutations), 1)  # Existing tag/APK untouched.
                download = next(i for i, call in enumerate(calls) if any('/releases/assets/' in arg for arg in call))
                publish = next(i for i, call in enumerate(calls) if 'PATCH' in call)
                self.assertLess(download, publish)
            self.assertFalse(any('/releases/tags/' in arg for call in calls for arg in call))

    def test_publication_scenarios(self):
        for mode in ('new', 'draft', 'published', 'bad_tag', 'bad_digest', 'extra_asset',
                     'empty_draft', 'list_failure', 'duplicate', 'bad_bytes', 'wrong_release'):
            with self.subTest(mode=mode):
                self.run_case(mode, mode in ('new', 'draft', 'published'))


if __name__ == '__main__':
    unittest.main()
