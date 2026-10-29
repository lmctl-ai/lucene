#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./lmcode/build.sh
python3 lmcode/tests/integration.py
python3 lmcode/tests/gallery.py
