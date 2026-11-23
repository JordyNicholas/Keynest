#!/usr/bin/env python3
"""Developer-only synthetic vector generator; never use these fixed inputs for real backups.

Requires cryptography with OpenSSL Argon2id support. Not a production or deployment dependency.
This independent writer does not call Java, KeynestSeal or the Manager API.
"""
import base64
import json
import struct
from pathlib import Path

import cryptography
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.argon2 import Argon2id
from cryptography.hazmat.backends.openssl.backend import backend


def main():
  root = Path(__file__).resolve().parents[1]
  payload = {
    'format': 'keynest.vault', 'version': 1, 'exportedAt': '2026-11-22T12:00:00Z',
    'accountPolicy': {'id': 'keynest.default.v1', 'rules': {
      'minLength': 16, 'maxLength': 128, 'requireUpper': True, 'requireLower': True,
      'requireDigit': True, 'requireSymbol': True, 'minCategories': 3,
      'excludeAmbiguous': True, 'forbidWhitespace': True, 'forbidUsernameSubstring': True,
      'maxAgeDays': None, 'bannedSubstrings': [], 'bannedRegexes': []}},
    'items': [{
      'id': '00000000-0000-0000-0000-000000000001', 'label': 'Synthetic item',
      'url': 'https://example.invalid/login', 'username': 'sample',
      'password': 'not-a-real-password,\n"雪"', 'policyId': 'keynest.historical.v1',
      'expiresAt': None, 'lastRotatedAt': '2026-11-20T00:00:00Z', 'notifyBeforeDays': 14,
      'createdAt': '2026-11-20T00:00:00Z', 'updatedAt': '2026-11-21T00:00:00Z'}]
  }
  phrase = 'synthetic-backup-passphrase-雪🔑'
  salt, nonce = bytes(range(16)), bytes(range(16, 28))
  plaintext = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
  key_id = b'keynest.backup.v1'
  seal_prefix = b'\x01\x01' + struct.pack('>H', len(key_id)) + key_id
  envelope_length = len(seal_prefix) + len(nonce) + len(plaintext) + 16
  header = b'KEYNESTB\x01\x01' + salt + struct.pack('>I', envelope_length)
  key = Argon2id(salt=salt, length=32, iterations=3, lanes=4, memory_cost=65536).derive(phrase.encode('utf-8'))
  file_bytes = header + seal_prefix + nonce + AESGCM(key).encrypt(nonce, plaintext, header)
  vector = {
    'notice': 'Synthetic test data only. Fixed entropy must never be used for a real backup.',
    'writer': f'Python cryptography {cryptography.__version__}; {backend.openssl_version_text()}',
    'passphrase': phrase, 'saltHex': salt.hex(), 'nonceHex': nonce.hex(),
    'payload': payload, 'containerBase64': base64.b64encode(file_bytes).decode('ascii')
  }
  target = root / 'apps/manager-api/src/test/resources/vectors/portable-backup-v1.json'
  target.write_text(json.dumps(vector, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
  print(f'Wrote synthetic vector: {target.relative_to(root)}')


if __name__ == '__main__':
  main()
