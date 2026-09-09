#!/usr/bin/env python3
"""Pure-Python RSA helpers for avbtool on Android.

Replaces the 4 `subprocess.call(['openssl', ...])` sites in the vendored
`avbtool.py`. There is no Android wheel for `cryptography` and no system
`openssl` binary on Android, so RSA is implemented directly on top of
Python big integers. Only SHA-256 and SHA-512 are supported, matching
the `ALGORITHMS` dict in avbtool.py. The exponent is assumed to be
65537 throughout (avbtool's `RSAPublicKey` enforces this).

Key input formats accepted:
  - PEM private key  ("-----BEGIN PRIVATE KEY-----" / RSA PRIVATE KEY)
  - DER private key  (PKCS#8 or traditional RSAPrivateKey)
  - PEM public key   ("-----BEGIN PUBLIC KEY-----")
  - DER public key   (SubjectPublicKeyInfo)
  - Raw AVB-encoded public key  (num_bits | n0_inv | n | rr)

Key output format: `(n, e, d_or_none)` -- `d` is None for public keys.
"""

import base64
import struct


# ---------- low-level DER helpers ----------

def _read_len(buf, pos):
  """Read a DER length at buf[pos]. Returns (length, new_pos)."""
  first = buf[pos]
  pos += 1
  if first < 0x80:
    return first, pos
  num_len = first & 0x7f
  if num_len == 0:
    raise ValueError('Indefinite DER length not supported')
  length = int.from_bytes(buf[pos:pos+num_len], 'big')
  return length, pos + num_len


def _read_seq(buf, pos):
  """Read a DER SEQUENCE at buf[pos]. Returns (inner, new_pos)."""
  if buf[pos] != 0x30:
    raise ValueError('Expected SEQUENCE tag (0x30)')
  length, pos = _read_len(buf, pos + 1)
  return buf[pos:pos+length], pos + length


def _read_int(buf, pos):
  """Read a DER INTEGER at buf[pos]. Returns (value, new_pos)."""
  if buf[pos] != 0x02:
    raise ValueError('Expected INTEGER tag (0x02), got 0x{:02x}'.format(buf[pos]))
  length, pos = _read_len(buf, pos + 1)
  raw = buf[pos:pos+length]
  return int.from_bytes(raw, 'big'), pos + length


def _read_octet_string(buf, pos):
  """Read a DER OCTET STRING at buf[pos]. Returns (raw_bytes, new_pos)."""
  if buf[pos] != 0x04:
    raise ValueError('Expected OCTET STRING tag (0x04), got 0x{:02x}'.format(buf[pos]))
  length, pos = _read_len(buf, pos + 1)
  return buf[pos:pos+length], pos + length


# ---------- key parsing ----------

def _parse_traditional_rsa_private_key(der):
  """Parse traditional RSAPrivateKey SEQUENCE. Returns (n, e, d).

  Structure: SEQUENCE {
    INTEGER version (0), INTEGER n, INTEGER e, INTEGER d,
    INTEGER p, INTEGER q, INTEGER dp, INTEGER dq, INTEGER qinv
  }
  """
  seq, _ = _read_seq(der, 0)
  _version, pos = _read_int(seq, 0)
  n, pos = _read_int(seq, pos)
  e, pos = _read_int(seq, pos)
  d, pos = _read_int(seq, pos)
  return n, e, d


def _parse_pkcs8_private_key(der):
  """Parse PKCS#8 PrivateKeyInfo. Returns (n, e, d).

  Structure: SEQUENCE {
    INTEGER version, SEQUENCE { OID, params }, OCTET STRING (RSAPrivateKey)
  }
  """
  seq, _ = _read_seq(der, 0)
  _version, pos = _read_int(seq, 0)
  _alg, pos = _read_seq(seq, pos)  # AlgorithmIdentifier -- skip
  raw, _ = _read_octet_string(seq, pos)
  return _parse_traditional_rsa_private_key(raw)


def _parse_rsa_private_key(der):
  """Parse either traditional RSAPrivateKey or PKCS#8-wrapped private key."""
  seq, _ = _read_seq(der, 0)
  # Peek at the 2nd element tag: SEQUENCE (0x30) means AlgorithmIdentifier
  # (=> PKCS#8). INTEGER (0x02) means we're already in RSAPrivateKey.
  _version, pos = _read_int(seq, 0)
  if pos < len(seq) and seq[pos] == 0x30:
    _alg, pos = _read_seq(seq, pos)
    if pos < len(seq) and seq[pos] == 0x04:
      raw, _ = _read_octet_string(seq, pos)
      return _parse_traditional_rsa_private_key(raw)
  return _parse_traditional_rsa_private_key(der)


def _parse_rsa_public_key(der):
  """Parse DER-encoded SubjectPublicKeyInfo. Returns (n, e).

  Structure: SEQUENCE {
    SEQUENCE { OID, params }, BIT STRING { RSAPublicKey SEQUENCE { n, e } }
  }
  """
  seq, _ = _read_seq(der, 0)
  _alg, pos = _read_seq(seq, 0)
  if seq[pos] != 0x03:
    raise ValueError('Expected BIT STRING tag (0x03), got 0x{:02x}'.format(seq[pos]))
  bs_len, pos = _read_len(seq, pos + 1)
  bs = seq[pos:pos+bs_len]
  rsa_pub = bs[1:]  # skip unused-bit count byte (always 0 for RSA)
  rsa_seq, _ = _read_seq(rsa_pub, 0)
  n, p2 = _read_int(rsa_seq, 0)
  e, _ = _read_int(rsa_seq, p2)
  return n, e


def _parse_subject_public_key_info(der):
  """Parse a SubjectPublicKeyInfo DER blob. Returns (n, e)."""
  return _parse_rsa_public_key(der)


def parse_key_from_pem_or_der(text_or_bytes):
  """Parse an RSA key from PEM text or DER bytes. Returns (n, e, d_or_none)."""
  if isinstance(text_or_bytes, str):
    data = text_or_bytes
  else:
    data = text_or_bytes.decode('latin-1', errors='replace')
  # Detect PEM armor and base64-decode the body.
  pem_start = data.find('-----BEGIN ')
  if pem_start >= 0:
    body_start = data.index('\n', pem_start) + 1
    body_end = data.find('-----END ', body_start)
    if body_end < 0:
      raise ValueError('PEM body not terminated')
    b64_text = data[body_start:body_end]
    der = base64.b64decode(''.join(b64_text.split()))
    is_private = 'PRIVATE KEY' in data[:body_start]
    if is_private:
      n, e, d = _parse_rsa_private_key(der)
      return (n, e, d)
    n, e = _parse_subject_public_key_info(der)
    return (n, e, None)
  # No PEM armor: treat as DER bytes.
  der = text_or_bytes if isinstance(text_or_bytes, (bytes, bytearray)) \
        else text_or_bytes.encode('latin-1')
  try:
    n, e, d = _parse_rsa_private_key(der)
    return (n, e, d)
  except (ValueError, IndexError):
    pass
  n, e = _parse_subject_public_key_info(der)
  return (n, e, None)


def parse_key_file(path):
  """Read a key file (PEM text or DER bytes) and return (n, e, d_or_none)."""
  with open(path, 'rb') as f:
    blob = f.read()
  try:
    text = blob.decode('ascii')
    if '-----BEGIN' in text:
      return parse_key_from_pem_or_der(text)
  except UnicodeDecodeError:
    pass
  return parse_key_from_pem_or_der(blob)


def parse_key_blob(blob):
  """Parse key bytes with fallback to raw AVB-encoded public key.

  A raw AVB-encoded key is: `<4 bytes num_bits:BE> <4 bytes n0_inv:BE>
  <n> <rr>` where n and rr are each `num_bits/8` bytes big-endian.
  See `RSAPublicKey.encode()` in avbtool.py.
  """
  try:
    return parse_key_from_pem_or_der(blob)
  except (ValueError, IndexError):
    pass
  try:
    (num_bits,) = struct.unpack('!I', blob[0:4])
    n_bytes = num_bits // 8
    n = int.from_bytes(blob[8:8+n_bytes], 'big')
    return (n, 65537, None)
  except Exception:
    raise ValueError('Cannot parse key blob')


# ---------- public API used by avbtool.py ----------

def parse_modulus(key_path):
  """Extract modulus `n` from a key file.

  Replacement for:
    `openssl rsa -in <key> -modulus -noout`  (and `-pubin` fallback)
  """
  (n, _e, _d) = parse_key_file(key_path)
  return n


def rsa_sign_raw(d, n, padding_and_hash):
  """RSA private-key signature over pre-padded input.

  Replacement for:
    `openssl rsautl -sign -inkey <key> -raw`

  `padding_and_hash` is `algorithm.padding + digest` from avbtool's
  `ALGORITHMS` dict; it already carries PKCS#1 v1.5 padding and the
  DigestInfo DER prefix.
  """
  em = int.from_bytes(padding_and_hash, 'big')
  sig = pow(em, d, n)
  return sig.to_bytes((n.bit_length() + 7) // 8, 'big')


def rsa_verify_raw(n, e, padding_and_digest, signature):
  """RSA public-key verification against pre-padded expected value.

  Replacement for:
    `openssl rsautl -verify -pubin -inkey <der> -keyform DER -raw`

  Returns True iff the recovered message matches `padding_and_digest`
  byte-for-byte.
  """
  m = int.from_bytes(signature, 'big')
  recovered = pow(m, e, n)
  rec_bytes = recovered.to_bytes((n.bit_length() + 7) // 8, 'big')
  return rec_bytes == padding_and_digest
