#!/usr/bin/env python3
"""Verify the SDK source bytes against the reviewed upstream commit."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent
pin = json.loads((root / 'sdk.lock').read_text())
vendor = root / 'vendor/lmcode-java'
actual = {str(p.relative_to(vendor)): hashlib.sha256(p.read_bytes()).hexdigest()
          for p in sorted(vendor.rglob('*')) if p.is_file()}
if actual != pin['files']:
    raise SystemExit('Vendored SDK differs from sdk.lock')
print('PASS SDK pin ' + pin['commit'])
