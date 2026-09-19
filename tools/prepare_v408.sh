#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v407.sh
python tools/apply_v408_patch.py

echo "Football Stats Analyzer V4.0.8 source prepared"
