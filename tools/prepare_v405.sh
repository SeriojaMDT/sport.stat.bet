#!/usr/bin/env bash
set -euo pipefail

bash tools/prepare_v404.sh
python tools/apply_v405_patch.py

echo "Football Stats Analyzer V4.0.5 source prepared"
