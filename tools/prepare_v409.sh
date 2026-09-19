#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v408.sh
python tools/apply_v409_patch.py

echo "Football Stats Analyzer V4.0.9 source prepared"
