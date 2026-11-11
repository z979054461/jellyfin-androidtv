#!/usr/bin/env python3
"""Offline negative/positive fixtures for release identity and manifest policy."""
import contextlib
import io
import json
import os
from types import SimpleNamespace
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from verify_release_apk import (PACKAGE, VERSION_CODE, VERSION_NAME, VerificationError,
                                metadata_apk, verify_badging, verify_manifest, tool_output, main)


class ReleaseVerificationTest(unittest.TestCase):
    def test_badging_field_order_and_extra_fields(self):
        verify_badging(f"package: versionName='{VERSION_NAME}' platformBuildVersionName='37' "
                       f"name='{PACKAGE}' versionCode='{VERSION_CODE}'\n")

    def test_badging_rejects_identity_errors(self):
        values = dict(name=PACKAGE, versionCode=str(VERSION_CODE), versionName=VERSION_NAME)
        for key in values:
            with self.subTest(key=key):
                changed = values | {key: 'wrong'}
                with self.assertRaises(VerificationError):
                    verify_badging('package: ' + ' '.join(f"{k}='{v}'" for k, v in changed.items()))

    def test_badging_rejects_missing_duplicate_and_multiple_fields(self):
        valid = f"package: name='{PACKAGE}' versionCode='{VERSION_CODE}' versionName='{VERSION_NAME}'"
        for text in ('', 'package: ', valid + " name='other'", valid + '\n' + valid):
            with self.subTest(text=text), self.assertRaises(VerificationError):
                verify_badging(text)

    def test_manifest_omitted_false(self):
        verify_manifest('E: manifest (line=1)\n  E: application (line=2)\n')

    def test_manifest_explicit_false(self):
        verify_manifest('E: manifest\n  E: application\n    A: android:debuggable(0x0101000f)=(type 0x12)0x0\n')

    def test_manifest_rejects_nonzero_and_unknown_values(self):
        for value in ('0xffffffff', '0x1', '0x00000001', '0X0', 'false', '@0x7f010001'):
            with self.subTest(value=value), self.assertRaises(VerificationError):
                verify_manifest('E: manifest\n  E: application\n'
                                f'    A: android:debuggable(0x0101000f)=(type 0x12){value}\n')

    def test_manifest_rejects_reference_type(self):
        with self.assertRaises(VerificationError):
            verify_manifest('E: manifest\n  E: application\n    A: android:debuggable(0x0101000f)=(type 0x01)0x0\n')

    def test_manifest_rejects_missing_structure(self):
        for text in ('', 'E: manifest', 'E: application'):
            with self.subTest(text=text), self.assertRaises(VerificationError):
                verify_manifest(text)

    def test_manifest_rejects_duplicate_attribute(self):
        with self.assertRaises(VerificationError):
            verify_manifest('E: manifest\n E: application\n' + ' A: android:debuggable=(type 0x12)0x0\n' * 2)

    def test_metadata_and_negative_cases(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'app.apk').write_bytes(b'fixture')
            metadata = dict(applicationId=PACKAGE, elements=[dict(versionCode=VERSION_CODE,
                            versionName=VERSION_NAME, outputFile='app.apk')])
            path = root / 'output-metadata.json'
            path.write_text(json.dumps(metadata))
            self.assertEqual(metadata_apk(root), root / 'app.apk')
            cases = [metadata | {'applicationId': 'wrong'}, metadata | {'elements': []}]
            for key, value in [('versionCode', 191005), ('versionName', 'wrong'),
                               ('outputFile', '../outside.apk'), ('outputFile', 'missing.apk')]:
                cases.append(metadata | {'elements': [metadata['elements'][0] | {key: value}]})
            for case in cases:
                with self.subTest(case=case), self.assertRaises(VerificationError):
                    path.write_text(json.dumps(case))
                    metadata_apk(root)

    def test_tool_errors_do_not_echo_output(self):
        with patch('verify_release_apk.subprocess.run') as run:
            run.return_value.returncode = 1
            run.return_value.stdout = 'must not print raw output'
            with self.assertRaisesRegex(VerificationError, '^signature: tool failed \\(exit 1\\)$'):
                tool_output(['unused'], 'signature')

    def test_end_to_end_stages_and_failure_blocks_asset(self):
        valid_badging = f"package: name='{PACKAGE}' versionCode='{VERSION_CODE}' versionName='{VERSION_NAME}'"
        manifest = 'E: manifest\n  E: application\n'
        for stage in ('success', 'signature', 'package', 'manifest'):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                release = root / 'app/build/outputs/apk/release'
                release.mkdir(parents=True)
                (release / 'app.apk').write_bytes(b'fixture')
                (release / 'output-metadata.json').write_text(json.dumps(dict(applicationId=PACKAGE,
                    elements=[dict(versionCode=VERSION_CODE, versionName=VERSION_NAME, outputFile='app.apk')])))
                jar = root / 'sdk/lib/apksigner.jar'
                jar.parent.mkdir(parents=True)
                jar.touch()
                outputs = [SimpleNamespace(returncode=1 if stage == 'signature' else 0,
                    stdout='APK signature: verified one signer', stderr=''),
                    SimpleNamespace(returncode=0, stdout='package: ' if stage == 'package' else valid_badging),
                    SimpleNamespace(returncode=0, stdout='' if stage == 'manifest' else manifest)]
                original = Path.cwd()
                try:
                    os.chdir(root)
                    with patch('sys.argv', ['verify', '--tools', str(root / 'sdk')]), \
                         patch('verify_release_apk.subprocess.run', side_effect=outputs), \
                         contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                        self.assertEqual(main(), 0 if stage == 'success' else 1)
                    self.assertEqual((root / 'build/danmaku-release/jellyfin-danmaku-200001.apk').exists(), stage == 'success')
                finally:
                    os.chdir(original)


if __name__ == '__main__':
    unittest.main()
