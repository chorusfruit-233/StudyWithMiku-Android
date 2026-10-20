"""Signing output compatibility and rejection cases; no keystore is needed."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("prepare_release", Path(__file__).with_name("prepare-release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
CERT = release.CERT_SHA256


class FixedCertificateTest(unittest.TestCase):
    def test_build_tools_35_signer_label(self):
        release.require_fixed_certificate(f"Number of signers: 1\nSigner #1 certificate SHA-256 digest: {CERT}\n")

    def test_build_tools_37_scheme_label(self):
        release.require_fixed_certificate(f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {CERT}\n")

    def test_same_certificate_across_signing_schemes(self):
        release.require_fixed_certificate(
            f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {CERT}\n"
            f"V3 Signer: certificate SHA-256 digest: {CERT.upper()}\n")

    def test_different_certificate_rejected(self):
        with self.assertRaises(SystemExit):
            release.require_fixed_certificate(f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {'f' * 64}\n")

    def test_matching_certificate_cannot_hide_another_certificate(self):
        with self.assertRaises(SystemExit):
            release.require_fixed_certificate(
                f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {CERT}\n"
                f"V3 Signer: certificate SHA-256 digest: {'f' * 64}\n")

    def test_public_key_fingerprint_is_not_a_certificate_fingerprint(self):
        with self.assertRaises(SystemExit):
            release.require_fixed_certificate(f"Number of signers: 1\nV2 Signer: public key SHA-256 digest: {CERT}\n")

    def test_truncated_certificate_fingerprint_rejected(self):
        with self.assertRaises(SystemExit):
            release.require_fixed_certificate(f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {CERT[:8]}\n")

    def test_multiple_signers_rejected(self):
        with self.assertRaises(SystemExit):
            release.require_fixed_certificate(f"Number of signers: 2\nV2 Signer: certificate SHA-256 digest: {CERT}\n")


if __name__ == "__main__":
    unittest.main()
