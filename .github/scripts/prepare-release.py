#!/usr/bin/env python3
"""Verify a signed, non-debuggable APK before naming and publishing it."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess

PACKAGE = "io.github.chorusfruit233.studywithmiku"
CERT_SHA256 = "e07a05095f2b19e1b7b37d48e67016da4b5cf78386a03020410a3a134e1f12ad"
MIN_SDK = "26"
TARGET_SDK = "37"

def command(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def require_fixed_certificate(report):
    # Build Tools 35 uses "Signer #1"; 37 uses scheme labels such as "V2 Signer".
    # Check every certificate digest and keep the single-signer upgrade contract.
    digests = re.findall(r"^.*?\bcertificate SHA-256 digest:\s*([^\r\n]+)", report, re.M)
    signers = re.search(r"^Number of signers:\s*(\d+)\s*$", report, re.M)
    if not signers or signers.group(1) != "1" or not digests or any(
        digest.strip().lower() != CERT_SHA256 for digest in digests
    ):
        raise SystemExit("Release APK must use the single StudyWithMiku certificate; refusing to publish.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    sdk = Path(os.environ["ANDROID_HOME"])
    binaries = sdk / "build-tools" / "37.0.0"
    signed = command(str(binaries / "apksigner"), "verify", "--verbose", "--print-certs", str(args.apk))
    require_fixed_certificate(signed)
    badging = command(str(binaries / "aapt"), "dump", "badging", str(args.apk))
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package.group(1) != PACKAGE:
        raise SystemExit("Unexpected release package; refusing to publish.")
    if re.search(r"^application-debuggable\b", badging, re.M):
        raise SystemExit("Debuggable APK cannot be published as a Release APK.")
    if not re.search(rf"^sdkVersion:'{MIN_SDK}'$", badging, re.M) or not re.search(rf"^targetSdkVersion:'{TARGET_SDK}'$", badging, re.M):
        raise SystemExit("Unexpected minimum or target SDK; refusing to publish.")
    version = package.group(3)
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit("Expected a three-part numeric release version.")
    tag = f"v{version}"
    if os.environ.get("GITHUB_REF_TYPE") == "tag" and os.environ.get("GITHUB_REF_NAME") != tag:
        raise SystemExit("Git tag must match the APK version.")
    args.output.mkdir(parents=True, exist_ok=True)
    filename = f"StudyWithMiku-Android-{version}-release.apk"
    target = args.output / filename
    shutil.copyfile(args.apk, target)
    sha256 = hashlib.sha256(target.read_bytes()).hexdigest()
    (args.output / "SHA256SUMS.txt").write_text(f"{sha256}  {filename}\n")
    (args.output / "SIGNING-CERTIFICATE.txt").write_text(
        f"Package: {PACKAGE}\nVersion: {version}\nVersion code: {package.group(2)}\n"
        f"Minimum SDK: {MIN_SDK}\nTarget SDK: {TARGET_SDK}\n"
        f"Certificate SHA-256: {CERT_SHA256}\nDebuggable: false\n"
    )
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f"version={version}\ntag={tag}\n")
    print(f"Verified signed Release APK: {filename}")
    print(f"Certificate SHA-256: {CERT_SHA256}")

if __name__ == "__main__":
    main()
