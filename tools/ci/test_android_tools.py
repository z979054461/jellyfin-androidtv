#!/usr/bin/env python3
"""Offline fixtures for the shared Android build-tools preflight; never install an SDK."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("prepare-android-tools.sh").resolve()


class AndroidToolsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "sdk with spaces"
        self.root.mkdir()
        self.tools = self.root / "build-tools/37.0.0"
        self.output = Path(self.temp.name) / "github-env"
        self.env = os.environ.copy()
        self.env.pop("ANDROID_SDK_ROOT", None)
        self.env.update(ANDROID_HOME=str(self.root), GITHUB_ENV=str(self.output))

    def executable(self, path, text="#!/bin/bash\nexit 0\n"):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        path.chmod(0o755)

    def installed(self):
        for name in ("apksigner", "aapt"):
            self.executable(self.tools / name)

    def manager(self, version="latest", install=True, status=0):
        script = '''#!/bin/bash
set -euo pipefail
test "$#" = 2
test "$1" = "--sdk_root=$ANDROID_HOME"
test "$2" = 'build-tools;37.0.0'
printf '%s' 'VERSION' > "$ANDROID_HOME/called"
'''.replace("VERSION", version)
        if install:
            script += '''mkdir -p "$ANDROID_HOME/build-tools/37.0.0"
for tool in apksigner aapt; do
    printf '#!/bin/bash\\nexit 0\\n' > "$ANDROID_HOME/build-tools/37.0.0/$tool"
    chmod +x "$ANDROID_HOME/build-tools/37.0.0/$tool"
done
'''
        script += f"exit {status}\n"
        self.executable(self.root / f"cmdline-tools/{version}/bin/sdkmanager", script)

    def run_preflight(self, success):
        result = subprocess.run(["bash", str(SCRIPT)], env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stderr)
        if success:
            self.assertEqual(self.output.read_text(), f"RELEASE_BUILD_TOOLS={self.tools}\n")
        else:
            self.assertFalse(self.output.exists())

    def test_installed_tools_do_not_call_manager(self):
        self.installed()
        self.manager(status=99)
        self.run_preflight(True)
        self.assertFalse((self.root / "called").exists())

    def test_sdk_root_fallback(self):
        self.installed()
        self.env.pop("ANDROID_HOME")
        self.env["ANDROID_SDK_ROOT"] = str(self.root)
        self.run_preflight(True)

    def test_latest_manager_install(self):
        self.manager()
        self.run_preflight(True)
        self.assertEqual((self.root / "called").read_text(), "latest")

    def test_versioned_manager_prefers_newest(self):
        self.manager("9.0", status=99)
        self.manager("19.0")
        self.run_preflight(True)
        self.assertEqual((self.root / "called").read_text(), "19.0")

    def test_missing_sdk(self):
        self.env.pop("ANDROID_HOME")
        self.run_preflight(False)

    def test_missing_manager(self):
        self.run_preflight(False)

    def test_install_failure(self):
        self.manager(install=False, status=1)
        self.run_preflight(False)

    def test_successful_install_without_tools_fails(self):
        self.manager(install=False)
        self.run_preflight(False)

    def test_nonexecutable_tool_fails(self):
        self.installed()
        (self.tools / "aapt").chmod(0o644)
        self.run_preflight(False)

    def test_partial_install_is_repaired(self):
        self.executable(self.tools / "aapt")
        self.manager()
        self.run_preflight(True)


if __name__ == "__main__":
    unittest.main()
