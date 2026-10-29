#!/usr/bin/env python3
"""Execute every catalogued .lms example through normal preflight and real Lucene."""
import json
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
gallery = root / 'lmcode/examples/gallery'
manifest = json.loads((gallery / 'manifest.json').read_text())
compiler = os.environ.get('LMSCRIPT_BIN', 'lmscript')
listed = [e['file'] for e in manifest['examples']]
actual = sorted(p.name for p in gallery.glob('*.lms'))
assert sorted(listed) == actual and len(set(listed)) == len(listed), 'Gallery manifest drift'
failures = []
for example in manifest['examples']:
    path = gallery / example['file']
    source = path.read_text()
    assert example['source'] in source, f'Missing source attribution: {path}'
    assert 'assert_true(' in source, f'Missing executable expectation: {path}'
    result = subprocess.run([compiler, str(path)], cwd=root, capture_output=True, text=True, timeout=90)
    try:
        if result.returncode:
            raise AssertionError(f'exit {result.returncode}\n{result.stdout}\n{result.stderr}')
        data = json.loads(result.stdout)
        assert data['ok'] is True, data
        assert data['result']['searches'] or data['result']['details'], 'No visible result'
    except (ValueError, KeyError, AssertionError) as error:
        failures.append((path.name, str(error)))
        print(f'FAIL {path.name}: {error}', flush=True)
    else:
        print(f'PASS {path.name}: {example["expected"]}', flush=True)
if failures:
    sys.exit(f'{len(failures)} of {len(listed)} gallery examples failed')
print(f'PASS all {len(listed)} gallery examples (normal preflight, assertions, JSON results)')
