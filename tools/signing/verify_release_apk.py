#!/usr/bin/env python3
"""Fail-closed release verification with stage diagnostics, never raw tool logs."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

PACKAGE = "io.github.z979054461.jellyfin.danmaku"
VERSION_CODE = 200001
VERSION_NAME = "0.20.0-danmaku.1"


class VerificationError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def metadata_apk(root):
    metadata = json.loads((root / "output-metadata.json").read_text())
    require(metadata["applicationId"] == PACKAGE, "metadata: package mismatch")
    require(len(metadata["elements"]) == 1, "metadata: expected one APK")
    element = metadata["elements"][0]
    require(element["versionCode"] == VERSION_CODE, "metadata: versionCode mismatch")
    require(element["versionName"] == VERSION_NAME, "metadata: versionName mismatch")
    apk = (root / element["outputFile"]).resolve()
    require(apk.parent == root.resolve(), "metadata: APK must be inside release directory")
    require(apk.is_file() and 0 < apk.stat().st_size < 200 * 1024 * 1024,
            "metadata: missing or invalid APK size")
    return apk


def verify_badging(text):
    lines = [line for line in text.splitlines() if line.startswith("package: ")]
    require(len(lines) == 1, "package: expected one package declaration")
    pairs = re.findall(r"(\w+)='([^']*)'", lines[0])
    require(len({key for key, _ in pairs}) == len(pairs), "package: duplicate fields")
    fields = dict(pairs)
    require(fields.get("name") == PACKAGE, "package: application ID mismatch")
    require(fields.get("versionCode") == str(VERSION_CODE), "package: versionCode mismatch")
    require(fields.get("versionName") == VERSION_NAME, "package: versionName mismatch")


def verify_manifest(text):
    require(re.search(r"^\s*E: manifest(?:\s|$)", text, re.M) is not None,
            "manifest: missing manifest element")
    require(re.search(r"^\s*E: application(?:\s|$)", text, re.M) is not None,
            "manifest: missing application element")
    # Absent android:debuggable defaults to false. Present values must be an explicit
    # typed boolean zero; true, references and unknown representations fail closed.
    declarations = [line.strip() for line in text.splitlines() if "android:debuggable" in line]
    require(len(declarations) <= 1, "manifest: duplicate debuggable attributes")
    for line in declarations:
        match = re.fullmatch(r"A: android:debuggable(?:\(0x[0-9a-fA-F]+\))?=\(type 0x12\)(0x[0-9a-fA-F]+)", line)
        require(match is not None and int(match[1], 16) == 0,
                "manifest: debuggable must be false")


def tool_output(command, stage):
    result = subprocess.run(command, capture_output=True, text=True)
    require(result.returncode == 0, f"{stage}: tool failed (exit {result.returncode})")
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tools", required=True, type=Path)
    args = parser.parse_args()
    stage = "metadata"
    try:
        apk = metadata_apk(Path("app/build/outputs/apk/release"))
        print("APK metadata: expected package, version and size verified", flush=True)
        stage = "signature"
        jar = args.tools / "lib/apksigner.jar"
        require(jar.is_file(), "signature: SDK apksigner.jar is missing")
        result = subprocess.run([
            "java", "--class-path", str(jar), "tools/signing/VerifyReleaseApk.java",
            str(apk), "config/danmaku-signing.sha256",
        ], capture_output=True, text=True)
        # Only our fixed diagnostics and a public digest may reach the workflow log.
        for line in (result.stdout + result.stderr).splitlines():
            if re.fullmatch(r"APK signature: [a-zA-Z0-9 ;=.-]+", line):
                print(line, flush=True)
        require(result.returncode == 0, "signature: verifier failed; APK will not be published")
        stage = "package"
        verify_badging(tool_output([str(args.tools / "aapt"), "dump", "badging", str(apk)], stage))
        print("APK package: expected package and version verified", flush=True)
        stage = "manifest"
        verify_manifest(tool_output([str(args.tools / "aapt"), "dump", "xmltree", str(apk), "AndroidManifest.xml"], stage))
        print("APK manifest: non-debuggable verified", flush=True)
        stage = "staging"
        output = Path("build/danmaku-release")
        if output.exists():
            shutil.rmtree(output)
        output.mkdir(parents=True)
        shutil.copyfile(apk, output / "jellyfin-danmaku-200001.apk")
    except VerificationError as error:
        print(f"Release verification failed: {error}", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError, TypeError):
        print(f"Release verification failed: {stage}: unreadable or malformed input", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
