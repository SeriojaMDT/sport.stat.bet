#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v406.sh
python tools/apply_v407_patch.py

echo "Football Stats Analyzer V4.0.7 source prepared"
