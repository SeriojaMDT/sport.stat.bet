#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v405.sh
python tools/apply_v406_patch.py

echo "Football Stats Analyzer V4.0.6 source prepared"
