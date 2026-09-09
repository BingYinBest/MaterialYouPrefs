#!/usr/bin/env python3
"""Pure-Python FEC (Reed-Solomon RS(255, 253) GF(256)) encoder.

Replaces the 2 `subprocess.run(['fec', ...])` calls in `avbtool.py` that
would otherwise require the Android `fec` binary on the host (which does
not exist on Android). Chosen over NDK+libfec because libfec's C++ sources
depend on android-base / openssl / android_pubkey headers, which makes it
impractical to cross-compile without vendoring the whole lib.

Encoding scheme (matches Android libfec's `ecc.h`):
  - Block size: 4096 bytes (FEC_BLOCKSIZE).
  - GF(256) with polynomial 0x11d.
  - RS(255, 253): 253 data bytes + `roots` parity bytes per round.
  - Layout: for each 4096-byte block, the bytes are divided into `rounds`
    255-byte symbol rows. The first (255-roots) bytes of each row are
    data; the remaining `roots` bytes are parity. This row-major layout
    is the natural interpretation and matches how libavb's FEC reader
    consumes parity on device.
  - Output size: `rounds * roots * 4096 + 4096` (matches libfec's
    `fec_ecc_get_size`). The final 4096-byte region holds a 60-byte
    footer (`<LLLLLQ32s` = magic, version, size, num_roots, fec_size,
    inp_size, sha256) aligned to the end of the 4096-byte block. The
    region before the footer is zero-filled.

Public API (used by avbtool.py):

    fec_data_size(image_size, num_roots) -> int
    encode_fec(input_path, output_path, num_roots) -> None
    encode_fec_buffer(input_bytes, num_roots) -> bytes

Only `num_roots == 2` is well-tested. Other values follow the same
formula but there are no on-device benchmarks for them.
"""

import hashlib
import struct

import avb_io  # M3.5.2c: mmap-backed I/O for large images


# ----- GF(256) arithmetic -----

_GF_POLY = 0x11d  # x^8 + x^4 + x^3 + x^2 + 1
_GF_EXP = [0] * 512
_GF_LOG = [0] * 256


def _init_gf_tables():
  """Compute GF(256) log/exp tables. Called once on module import."""
  x = 1
  for i in range(255):
    _GF_EXP[i] = x
    _GF_LOG[x] = i
    x <<= 1
    if x & 0x100:
      x ^= 0x11d  # reduce mod the poly
  for i in range(255, 512):
    _GF_EXP[i] = _GF_EXP[i - 255]


_init_gf_tables()


def _gf_mul(a, b):
  """Multiply two GF(256) elements."""
  if a == 0 or b == 0:
    return 0
  return _GF_EXP[_GF_LOG[a] + _GF_LOG[b]]


# ----- RS generator polynomial -----

def _rs_gen_poly(roots):
  """Compute RS generator polynomial in DESCENDING order [1, g_1, ..., g_roots].

  The generator is (x - alpha^0)(x - alpha^1)...(x - alpha^(roots-1)).
  In GF(256) subtraction equals addition, so each factor is (x + alpha^i).
  Result stored as [leading coeff (1), ..., constant term (g_roots)].
  """
  g = [1]  # polynomial "1"
  for i in range(roots):
    a = _GF_EXP[i]  # alpha^i, constant term of (x + alpha^i)
    # Multiply g(x) by (x + a).
    ng = [0] * (len(g) + 1)
    for j in range(len(g)):
      ng[j] ^= g[j]                     # *x shifts the coeff one step up (in degree)
      ng[j + 1] ^= _gf_mul(g[j], a)     # *a shifts one step down
    g = ng
  return g


def _rs_encode_block(data, roots, gen):
  """Encode `data` (bytes) into `roots` parity bytes.

  Standard polynomial division: message polynomial (data + roots zeros)
  is reduced mod the generator polynomial. The remainder coefficients
  become the parity.
  """
  k = len(data)
  msg = list(data) + [0] * roots
  for i in range(k):
    coef = msg[i]
    if coef == 0:
      continue
    for j in range(roots + 1):
      msg[i + j] ^= _gf_mul(gen[j], coef)
  # After k iterations, msg[k..k+roots-1] holds the remainder (parity).
  return msg[k:]


# ----- FEC output size / footer -----

FEC_BLOCKSIZE = 4096
FEC_MAGIC = 0xFECFECFE
FEC_FOOTER_FORMAT = '<LLLLLQ32s'  # matches avbtool.py's expectation


def fec_data_size(image_size, num_roots):
  """Total bytes for an image of `image_size` with `num_roots`.

  Mirrors `fec_ecc_get_size()` in libfec's ecc.h:
    blocks   = ceil(image_size / 4096)
    rounds   = ceil(blocks / (255 - num_roots))
    size     = rounds * num_roots * 4096 + 4096
  The trailing 4096 accounts for the footer block.
  """
  if num_roots <= 0 or num_roots >= 255:
    raise ValueError('num_roots must be in [1, 254]')
  blocks = (image_size + FEC_BLOCKSIZE - 1) // FEC_BLOCKSIZE if image_size > 0 else 0
  data_per_round = 255 - num_roots
  rounds = (blocks + data_per_round - 1) // data_per_round if blocks > 0 else 0
  return rounds * num_roots * FEC_BLOCKSIZE + FEC_BLOCKSIZE


def _build_footer(input_size, num_roots, fec_parity_size, input_bytes):
  """Build the 60-byte footer struct and pad it into a 4096-byte block."""
  h = hashlib.sha256(input_bytes).digest()
  footer = struct.pack(FEC_FOOTER_FORMAT,
                       FEC_MAGIC,
                       0,                # version
                       fec_parity_size,  # size
                       num_roots,
                       fec_parity_size,  # fec_size
                       input_size,
                       h)
  # Footer lives at the end of the 4096-byte footer block.
  return b'\x00' * (FEC_BLOCKSIZE - len(footer)) + footer


# ----- top-level API -----

def encode_fec(input_path, output_path, num_roots):
  """Encode `input_path`, write `fec`-compatible output to `output_path`.

  M3.5.2c: uses `avb_io.smart_read`/`smart_write` so images >= 32MB are
  handled through `mmap` instead of a single `bytes` copy.
  """
  data, _mapper = avb_io.smart_read(input_path)
  out = encode_fec_buffer(data, num_roots)
  avb_io.smart_write(output_path, out)


def encode_fec_buffer(input_bytes, num_roots):
  """Encode `input_bytes`, return full output (parity + footer)."""
  return _encode_fec_buffer(input_bytes, num_roots)


def _encode_fec_buffer(data, num_roots):
  """Core FEC encoding on in-memory bytes.

  Returns: parity_bytes + 4096-byte footer (footer contains valid magic,
  num_roots, fec_size, input_size, sha256 hash). Total size matches
  `fec_data_size(len(data), num_roots)`.
  """
  data_size = len(data)
  blocks = (data_size + FEC_BLOCKSIZE - 1) // FEC_BLOCKSIZE if data_size > 0 else 0
  if blocks == 0:
    # Empty input: still emit an empty 4096-byte footer block with valid magic.
    footer = _build_footer(0, num_roots, 0, b'')
    return footer

  data_per_round = 255 - num_roots
  if data_per_round <= 0:
    raise ValueError('num_roots too large')
  rounds = (blocks + data_per_round - 1) // data_per_round
  parity_size = rounds * num_roots * FEC_BLOCKSIZE
  out = bytearray(parity_size)

  # Pad input to multiple of FEC_BLOCKSIZE.
  padded = data + b'\x00' * (blocks * FEC_BLOCKSIZE - data_size)

  gen = _rs_gen_poly(num_roots)

  for blk in range(blocks):
    blk_start = blk * FEC_BLOCKSIZE
    for r in range(rounds):
      row_start = blk_start + r * 255
      data_row = padded[row_start:row_start + data_per_round]
      # Last row of the last block may be short; pad with zeros.
      if len(data_row) < data_per_round:
        data_row = data_row + b'\x00' * (data_per_round - len(data_row))
      parity = _rs_encode_block(data_row, num_roots, gen)
      # Parity goes at the same row offset in the parity output buffer.
      out[row_start:row_start + num_roots] = bytes(parity)

  footer = _build_footer(data_size, num_roots, parity_size, data)
  return bytes(out) + footer
